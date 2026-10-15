package io.github.minunglee.gateway.limit;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
		// 일일 예산 $0.001 = 1,000 마이크로달러, 자리가 나길 최대 300ms 기다림
		LimitProperties.TierPolicy policy = new LimitProperties.TierPolicy("model", 5, 20, new BigDecimal("0.001"));
		LimitProperties properties = new LimitProperties("salt", ZoneId.of("Asia/Seoul"), policy, policy,
				Duration.ofMillis(300));
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
					return budgetGuard.reserve(Tier.GUEST, 100);
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
		// 아무도 정산하지 않으므로 1,000 / 100 = 정확히 10건만 통과 (나머지는 기다리다 포기)
		assertThat(granted).isEqualTo(10);
		assertThat(redis.opsForValue().get("budget:guest:2026-10-14")).isEqualTo("1000");
	}

	@Test
	void 진행_중인_선차감_때문에_꽉_찼으면_정산을_기다렸다가_확보한다() throws Exception {
		List<BudgetGuard.Reservation> holders = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			holders.add(budgetGuard.reserve(Tier.GUEST, 100).orElseThrow());
		}

		CompletableFuture<Optional<BudgetGuard.Reservation>> waiting = CompletableFuture
			.supplyAsync(() -> budgetGuard.reserve(Tier.GUEST, 100));
		Thread.sleep(100);
		// 진행 중이던 요청 두 개가 실제 10씩만 쓰고 정산 → 180이 돌아와 100짜리 자리가 생김
		budgetGuard.settle(holders.get(0), 10);
		budgetGuard.settle(holders.get(1), 10);

		Optional<BudgetGuard.Reservation> reservation = waiting.get();
		assertThat(reservation).isPresent();
		assertThat(reservation.get().waitedMs()).isPositive();
	}

	@Test
	void 실제로_쓴_돈이_한도에_닿았으면_기다리지_않고_바로_포기한다() {
		BudgetGuard.Reservation reservation = budgetGuard.reserve(Tier.GUEST, 1_000).orElseThrow();
		budgetGuard.settle(reservation, 1_000);

		long started = System.nanoTime();
		assertThat(budgetGuard.reserve(Tier.GUEST, 1)).isEmpty();
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(100));
	}

	@Test
	void 실제_비용으로_정산하면_차액이_돌아온다() {
		BudgetGuard.Reservation reservation = budgetGuard.reserve(Tier.GUEST, 700).orElseThrow();
		budgetGuard.settle(reservation, 30);

		assertThat(budgetGuard.usedTodayMicroUsd(Tier.GUEST)).isEqualTo(30);
		assertThat(budgetGuard.reserve(Tier.GUEST, 900)).isPresent();
	}

	@Test
	void 정산_전에는_실제_사용액에_선차감액을_넣지_않는다() {
		budgetGuard.reserve(Tier.GUEST, 700).orElseThrow();

		assertThat(budgetGuard.usedTodayMicroUsd(Tier.GUEST)).isZero();
	}

	@Test
	void 호출이_실패하면_전액_돌려준다() {
		BudgetGuard.Reservation reservation = budgetGuard.reserve(Tier.GUEST, 700).orElseThrow();
		budgetGuard.refund(reservation);

		assertThat(budgetGuard.usedTodayMicroUsd(Tier.GUEST)).isZero();
		assertThat(redis.opsForValue().get("budget:guest:2026-10-14")).isEqualTo("0");
		assertThat(redis.opsForValue().get("budget-inflight:guest:2026-10-14")).isEqualTo("0");
	}

	@Test
	void 등급마다_예산을_따로_센다() {
		BudgetGuard.Reservation guest = budgetGuard.reserve(Tier.GUEST, 1_000).orElseThrow();
		budgetGuard.settle(guest, 1_000);

		assertThat(budgetGuard.reserve(Tier.GUEST, 1)).isEmpty();
		assertThat(budgetGuard.reserve(Tier.INVITE, 1_000)).isPresent();
	}

	@Test
	void 한도를_넘는_선차감은_거부한다() {
		assertThat(budgetGuard.reserve(Tier.GUEST, 1_001)).isEmpty();
		assertThat(budgetGuard.usedTodayMicroUsd(Tier.GUEST)).isZero();
	}

}
