package io.github.minunglee.gateway.visit;

import java.time.Instant;
import java.util.List;

import io.github.minunglee.gateway.visit.JipdonCollectStatus.Health;
import io.github.minunglee.gateway.visit.JipdonCollectStatus.Run;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JipdonCollectStatusTest {

	static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z"); // 12:00 KST

	static JipdonCollectStatus status(boolean enabled, boolean key, Instant last, int missing) {
		return new JipdonCollectStatus(enabled, key, "0 0 */6 * * *", 8000, last, 49152, 0, missing, List.of());
	}

	@Test
	void 하루_넘게_새_수집이_없으면_멈춤_의심() {
		assertThat(status(true, true, NOW.minusSeconds(6 * 3600), 0).health(NOW)).isEqualTo(Health.OK);
		assertThat(status(true, true, NOW.minusSeconds(25 * 3600), 0).health(NOW)).isEqualTo(Health.STALE);
		assertThat(status(true, true, null, 0).health(NOW)).isEqualTo(Health.STALE);
		assertThat(status(false, true, null, 0).health(NOW)).isEqualTo(Health.DISABLED);
		assertThat(status(true, false, NOW, 0).health(NOW)).isEqualTo(Health.NO_KEY);
	}

	@Test
	void 화면용_표시는_한국_시간과_비율() {
		var status = status(true, true, NOW, 4915);
		assertThat(status.lastCollectedText()).isEqualTo("10/22 12:00");
		assertThat(status.coveragePercent()).isEqualTo(90);

		var run = new Run(NOW, NOW.plusSeconds(125), 2048, 2100, 37, 0, false, 0);
		assertThat(run.startedText()).isEqualTo("10/22 12:00");
		assertThat(run.durationText()).isEqualTo("2분 5초");
	}

}
