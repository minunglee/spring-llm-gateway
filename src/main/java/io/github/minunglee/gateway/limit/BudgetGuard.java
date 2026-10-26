package io.github.minunglee.gateway.limit;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 등급별 일일 예산.
 * <p>
 * 응답이 오기 전에는 실제 비용을 모르므로, 호출 전에 <b>나올 수 있는 최대 비용</b>을 먼저 차감(reserve)하고
 * 응답의 실제 사용량으로 차액을 돌려준다(settle). 호출이 실패하면 전액 돌려준다(refund).
 * 이렇게 하면 동시에 수백 건이 들어와도 예산을 넘는 호출은 시작조차 되지 않는다.
 * <p>
 * 최대 비용으로 잡아두기 때문에 요청이 몰리면 실제로 쓴 돈은 적어도 예산 칸이 일시적으로 꽉 찬다.
 * 그래서 "실제로 돈을 다 쓴 경우"와 "진행 중인 요청들이 칸을 잡고 있는 경우"를 구분해, 후자는
 * 다른 요청이 정산하며 돌려주는 예산을 잠깐 기다렸다가 다시 시도한다. 가상 스레드라 기다리는 비용이 작다.
 * <p>
 * 예산은 등급마다 따로 둔다. 게스트가 몰려 예산을 다 써도 초대코드 사용자(면접관) 몫은 남는다.
 */
@Component
public class BudgetGuard {

	/** 날짜별 예산 키 보관 기간. 하루가 지나면 쓸모없지만 시간대 경계를 넉넉히 덮으려고 이틀 */
	private static final Duration KEY_TTL = Duration.ofDays(2);

	/** 자리가 나길 기다릴 때 다시 시도하는 간격 (여러 요청이 한꺼번에 몰리지 않도록 조금씩 흔든다) */
	private static final long RETRY_MIN_MS = 20;

	/** 재시도 간격에 더하는 무작위 0~30ms (기다리던 요청들이 한꺼번에 다시 몰리지 않게) */
	private static final long RETRY_JITTER_MS = 30;

	/** budget_reserve.lua 반환값: 실제로 쓴 돈만으로 한도를 넘음 (기다려도 소용없어 바로 포기) */
	private static final long EXHAUSTED = -1;

	private final RedisScript<Long> reserveScript = RedisScript
		.of(new ClassPathResource("scripts/budget_reserve.lua"), Long.class);

	private final RedisScript<Long> settleScript = RedisScript.of(new ClassPathResource("scripts/budget_settle.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	private final LimitProperties properties;

	private final Clock clock;

	public BudgetGuard(StringRedisTemplate redis, LimitProperties properties, Clock clock) {
		this.redis = redis;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 선차감을 시도한다. 진행 중인 요청들 때문에 자리가 없으면 최대 {@code budgetWait}까지 기다리며 다시 시도하고,
	 * 실제 비용만으로 한도를 넘었으면 바로 포기한다.
	 */
	public Optional<Reservation> reserve(Tier tier, long amountMicroUsd) {
		String day = LocalDate.now(clock.withZone(properties.zone())).toString();
		List<String> keys = List.of(totalKey(tier, day), inflightKey(tier, day));
		long startedNanos = System.nanoTime();
		long deadline = startedNanos + properties.budgetWait().toNanos();
		boolean retried = false;
		while (true) {
			Long result = redis.execute(reserveScript, keys, String.valueOf(amountMicroUsd),
					String.valueOf(limitMicroUsd(tier)), String.valueOf(KEY_TTL.toSeconds()));
			if (result != null && result >= 0) {
				// 재시도한 경우에만 기다린 시간으로 친다 (첫 시도의 Redis 처리 시간은 대기가 아니다)
				long waitedMs = retried ? (System.nanoTime() - startedNanos) / 1_000_000 : 0;
				return Optional.of(new Reservation(keys.get(0), keys.get(1), amountMicroUsd, waitedMs));
			}
			if (result == null || result == EXHAUSTED || System.nanoTime() >= deadline || !sleepBeforeRetry()) {
				return Optional.empty();
			}
			retried = true;
		}
	}

	/** 실제 비용으로 정산한다. 선차감액보다 적게 나오면 차액이 예산으로 돌아간다. */
	public void settle(Reservation reservation, long actualMicroUsd) {
		redis.execute(settleScript, List.of(reservation.totalKey(), reservation.inflightKey()),
				String.valueOf(reservation.amountMicroUsd()), String.valueOf(actualMicroUsd));
	}

	/** AI 호출이 실패했을 때 선차감액을 전부 돌려준다 (실제 비용 0으로 정산) */
	public void refund(Reservation reservation) {
		settle(reservation, 0);
	}

	/** 오늘 실제로 쓴 금액 (정산 전 선차감액은 빼고) */
	public long usedTodayMicroUsd(Tier tier) {
		String day = LocalDate.now(clock.withZone(properties.zone())).toString();
		return parse(redis.opsForValue().get(totalKey(tier, day)))
				- parse(redis.opsForValue().get(inflightKey(tier, day)));
	}

	/** 등급의 하루 예산 (마이크로달러) */
	public long limitMicroUsd(Tier tier) {
		return properties.policy(tier).dailyBudgetMicroUsd();
	}

	/** Redis 값(없으면 null)을 숫자로 */
	private static long parse(String value) {
		return value == null ? 0 : Long.parseLong(value);
	}

	/** 오늘 누적 금액 키 (정산된 실제 비용 + 아직 정산 전 선차감액) */
	private static String totalKey(Tier tier, String day) {
		return "budget:" + tier.name().toLowerCase() + ":" + day;
	}

	/** 아직 정산 전인 선차감액 합계 키 ("실제 소진"과 "일시적으로 꽉 참"을 구분하는 데 쓴다) */
	private static String inflightKey(Tier tier, String day) {
		return "budget-inflight:" + tier.name().toLowerCase() + ":" + day;
	}

	/** @return 중단(interrupt)되면 false */
	private static boolean sleepBeforeRetry() {
		try {
			Thread.sleep(RETRY_MIN_MS + ThreadLocalRandom.current().nextLong(RETRY_JITTER_MS));
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * @param waitedMs 자리가 나길 기다린 시간 (바로 확보했으면 0)
	 */
	public record Reservation(String totalKey, String inflightKey, long amountMicroUsd, long waitedMs) {
	}

}
