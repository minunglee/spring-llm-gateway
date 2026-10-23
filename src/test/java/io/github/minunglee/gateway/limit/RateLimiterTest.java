package io.github.minunglee.gateway.limit;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import io.github.minunglee.gateway.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RateLimiterTest {

	@Autowired
	StringRedisTemplate redis;

	// 2026-10-14 12:00 KST
	MutableClock clock = new MutableClock(Instant.parse("2026-10-14T03:00:00Z"));

	RateLimiter rateLimiter;

	@BeforeEach
	void setUp() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
		LimitProperties.TierPolicy policy = new LimitProperties.TierPolicy("model", 5, 20, BigDecimal.ONE);
		LimitProperties properties = new LimitProperties("salt", ZoneId.of("Asia/Seoul"), policy, policy, policy,
				java.time.Duration.ZERO);
		rateLimiter = new RateLimiter(redis, properties, clock);
	}

	@Test
	void 시간당_한도까지만_허용한다() {
		for (int i = 0; i < 5; i++) {
			assertThat(acquire("ip-a").allowed()).isTrue();
		}
		RateLimitResult denied = acquire("ip-a");

		assertThat(denied.allowed()).isFalse();
		// 토큰 1개가 다시 차는 데 12분(1시간 / 5)
		assertThat(denied.retryAfter()).isEqualTo(Duration.ofMinutes(12));
	}

	@Test
	void 시간이_지나면_토큰이_다시_찬다() {
		for (int i = 0; i < 5; i++) {
			acquire("ip-a");
		}
		clock.advance(Duration.ofMinutes(12));

		assertThat(acquire("ip-a").allowed()).isTrue();
		assertThat(acquire("ip-a").allowed()).isFalse();
	}

	@Test
	void IP마다_따로_센다() {
		for (int i = 0; i < 5; i++) {
			acquire("ip-a");
		}

		assertThat(acquire("ip-b").allowed()).isTrue();
	}

	@Test
	void 하루_한도를_넘으면_다음날_0시까지_막는다() {
		for (int hour = 0; hour < 4; hour++) {
			for (int i = 0; i < 5; i++) {
				assertThat(acquire("ip-a").allowed()).isTrue();
			}
			clock.advance(Duration.ofHours(1));
		}
		// 16:00 KST, 오늘 20회 사용
		RateLimitResult denied = acquire("ip-a");

		assertThat(denied.allowed()).isFalse();
		assertThat(denied.dailyLimitReached()).isTrue();
		assertThat(denied.retryAfter()).isEqualTo(Duration.ofHours(8));

		clock.advance(Duration.ofHours(8));
		assertThat(acquire("ip-a").allowed()).isTrue();
	}

	@Test
	void 한도는_호출할_때마다_다르게_줄_수_있다() {
		assertThat(rateLimiter.tryAcquire("code-1", 1, 100).allowed()).isTrue();
		assertThat(rateLimiter.tryAcquire("code-1", 1, 100).allowed()).isFalse();
	}

	private RateLimitResult acquire(String key) {
		return rateLimiter.tryAcquire(key, 5, 20);
	}

	@Test
	void 남은_횟수를_알려준다() {
		RateLimitResult first = acquire("ip-a");

		assertThat(first.remainingThisHour()).isEqualTo(4);
		assertThat(first.remainingToday()).isEqualTo(19);
	}

}
