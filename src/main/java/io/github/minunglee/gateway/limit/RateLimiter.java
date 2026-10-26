package io.github.minunglee.gateway.limit;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * IP별 사용량 제한. 시간당 토큰 버킷과 일일 횟수를 Redis Lua 스크립트 하나로 검사한다.
 * <p>
 * GET → 계산 → SET을 애플리케이션에서 나눠 하면 동시 요청이 같은 값을 읽고 둘 다 통과할 수 있다.
 * Lua 스크립트는 Redis에서 한 번에 실행되므로 그 사이에 다른 명령이 끼어들지 않는다.
 */
@Component
public class RateLimiter {

	/** 토큰 버킷이 빈 상태에서 가득 차기까지 걸리는 시간. 시간당 한도를 1시간에 걸쳐 조금씩 채운다 */
	private static final Duration REFILL_PERIOD = Duration.ofHours(1);

	/** 날짜별 하루 카운터 키 보관 기간 (이틀 뒤 자동 삭제) */
	private static final Duration DAILY_KEY_TTL = Duration.ofDays(2);

	@SuppressWarnings("rawtypes")
	private final RedisScript<List> script = RedisScript.of(new ClassPathResource("scripts/rate_limit.lua"),
			List.class);

	private final StringRedisTemplate redis;

	private final LimitProperties properties;

	private final Clock clock;

	public RateLimiter(StringRedisTemplate redis, LimitProperties properties, Clock clock) {
		this.redis = redis;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param key 사용량을 세는 키. 용도가 다른 제한(LLM 호출, 초대코드 입력 시도)은 접두어로 구분한다
	 * @param hourly 시간당 한도 (토큰 버킷 용량)
	 * @param daily 하루 한도
	 */
	public RateLimitResult tryAcquire(String key, int hourly, int daily) {
		LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
		List<String> keys = List.of("rl:bucket:" + key, "rl:day:" + key + ":" + today);
		@SuppressWarnings("unchecked")
		List<Long> result = redis.execute(script, keys, String.valueOf(hourly),
				String.valueOf(REFILL_PERIOD.toMillis()), String.valueOf(clock.millis()), String.valueOf(daily),
				String.valueOf(DAILY_KEY_TTL.toSeconds()));

		boolean allowed = result.get(0) == 1;
		long retryAfterMs = result.get(3);
		Duration retryAfter = retryAfterMs < 0 ? untilTomorrow(today) : Duration.ofMillis(retryAfterMs);
		return new RateLimitResult(allowed, result.get(1).intValue(), result.get(2).intValue(), retryAfter);
	}

	/** 하루 한도를 다 썼을 때 다시 쓸 수 있는 시각(내일 0시, 한국 시간)까지 남은 시간 */
	private Duration untilTomorrow(LocalDate today) {
		return Duration.between(clock.instant(), today.plusDays(1).atStartOfDay(properties.zone()).toInstant());
	}

}
