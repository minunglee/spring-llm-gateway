package io.github.minunglee.gateway.limit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class BudgetGuardTest {

	@Autowired
	StringRedisTemplate redis;

	MutableClock clock = new MutableClock(Instant.parse("2026-10-14T03:00:00Z"));

	BudgetGuard budgetGuard;

	@BeforeEach
	void setUp() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
		// 일일 예산 $0.001 = 1,000 마이크로달러
		LimitProperties properties = new LimitProperties(5, 20, new BigDecimal("0.001"), "salt",
				ZoneId.of("Asia/Seoul"));
		budgetGuard = new BudgetGuard(redis, properties, clock);
	}

	@Test
	void 동시에_요청이_몰려도_예산을_넘지_않는다() throws Exception {
		int threads = 200;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Optional<BudgetGuard.Reservation>>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return budgetGuard.reserve(100);
				}));
			}
			start.countDown();
		}

		long granted = 0;
		for (Future<Optional<BudgetGuard.Reservation>> result : results) {
			if (result.get().isPresent()) {
				granted++;
			}
		}
		// 1,000 / 100 = 정확히 10건만 통과
		assertThat(granted).isEqualTo(10);
		assertThat(budgetGuard.usedTodayMicroUsd()).isEqualTo(1_000);
	}

	@Test
	void 실제_비용으로_정산하면_차액이_돌아온다() {
		BudgetGuard.Reservation reservation = budgetGuard.reserve(700).orElseThrow();
		budgetGuard.settle(reservation, 30);

		assertThat(budgetGuard.usedTodayMicroUsd()).isEqualTo(30);
		assertThat(budgetGuard.reserve(900)).isPresent();
	}

	@Test
	void 호출이_실패하면_전액_돌려준다() {
		BudgetGuard.Reservation reservation = budgetGuard.reserve(700).orElseThrow();
		budgetGuard.refund(reservation);

		assertThat(budgetGuard.usedTodayMicroUsd()).isZero();
	}

	@Test
	void 한도를_넘는_선차감은_거부한다() {
		assertThat(budgetGuard.reserve(1_001)).isEmpty();
		assertThat(budgetGuard.usedTodayMicroUsd()).isZero();
	}

}
