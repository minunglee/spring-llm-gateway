package io.github.minunglee.gateway.limit;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 서비스 전체 일일 예산.
 * <p>
 * 응답이 오기 전에는 실제 비용을 모르므로, 호출 전에 <b>나올 수 있는 최대 비용</b>을 먼저 차감(reserve)하고
 * 응답의 실제 사용량으로 차액을 돌려준다(settle). 호출이 실패하면 전액 돌려준다(refund).
 * 이렇게 하면 동시에 수백 건이 들어와도 예산을 넘는 호출은 시작조차 되지 않는다.
 */
@Component
public class BudgetGuard {

	private static final Duration KEY_TTL = Duration.ofDays(2);

	private final RedisScript<Long> reserveScript = RedisScript
		.of(new ClassPathResource("scripts/budget_reserve.lua"), Long.class);

	private final StringRedisTemplate redis;

	private final LimitProperties properties;

	private final Clock clock;

	public BudgetGuard(StringRedisTemplate redis, LimitProperties properties, Clock clock) {
		this.redis = redis;
		this.properties = properties;
		this.clock = clock;
	}

	public Optional<Reservation> reserve(long amountMicroUsd) {
		String key = todayKey();
		Long remaining = redis.execute(reserveScript, List.of(key), String.valueOf(amountMicroUsd),
				String.valueOf(properties.globalDailyBudgetMicroUsd()), String.valueOf(KEY_TTL.toSeconds()));
		if (remaining == null || remaining < 0) {
			return Optional.empty();
		}
		return Optional.of(new Reservation(key, amountMicroUsd));
	}

	/** 실제 비용으로 정산한다. 선차감액보다 적게 나오면 차액이 예산으로 돌아간다. */
	public void settle(Reservation reservation, long actualMicroUsd) {
		long delta = actualMicroUsd - reservation.amountMicroUsd();
		if (delta != 0) {
			redis.opsForValue().increment(reservation.key(), delta);
		}
	}

	public void refund(Reservation reservation) {
		redis.opsForValue().decrement(reservation.key(), reservation.amountMicroUsd());
	}

	public long usedTodayMicroUsd() {
		String value = redis.opsForValue().get(todayKey());
		return value == null ? 0 : Long.parseLong(value);
	}

	public long limitMicroUsd() {
		return properties.globalDailyBudgetMicroUsd();
	}

	private String todayKey() {
		return "budget:global:" + LocalDate.now(clock.withZone(properties.zone()));
	}

	public record Reservation(String key, long amountMicroUsd) {
	}

}
