package io.github.minunglee.gateway.visit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * jipdon-api의 실거래가 수집 현황 (관리자 화면용). /internal/collect 응답과 같은 모양이다.
 *
 * @param totalTasks 지역 × 종류 × 보관 개월 수
 * @param refreshDue 최근 달 중 다시 받을 때가 된 일
 * @param missing 아직 한 번도 받지 않은 달
 */
public record JipdonCollectStatus(boolean enabled, boolean keyConfigured, String cron, int maxCallsPerRun,
		Instant lastCollectedAt, int totalTasks, int refreshDue, int missing, List<Run> recentRuns) {

	/** 수집은 6시간마다 돈다. 하루 넘게 새 기록이 없으면 멈춘 것으로 본다 */
	static final Duration STALE_AFTER = Duration.ofHours(24);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("M/d HH:mm")
		.withZone(ZoneId.of("Asia/Seoul"));

	public record Run(Instant startedAt, Instant finishedAt, int tasks, int calls, int changedRows, int failedTasks,
			boolean stoppedByQuota, int remainingTasks) {

		public String startedText() {
			return TIME.format(startedAt);
		}

		public String durationText() {
			long seconds = Duration.between(startedAt, finishedAt).toSeconds();
			return seconds < 60 ? seconds + "초" : (seconds / 60) + "분 " + (seconds % 60) + "초";
		}

	}

	public enum Health {

		OK("정상"), STALE("멈춤 의심 (24시간 넘게 새 수집 없음)"), DISABLED("수집 꺼짐"), NO_KEY("인증키 없음");

		private final String label;

		Health(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

	}

	public Health health(Instant now) {
		if (!enabled) {
			return Health.DISABLED;
		}
		if (!keyConfigured) {
			return Health.NO_KEY;
		}
		return lastCollectedAt == null || lastCollectedAt.isBefore(now.minus(STALE_AFTER)) ? Health.STALE
				: Health.OK;
	}

	public String lastCollectedText() {
		return lastCollectedAt == null ? "-" : TIME.format(lastCollectedAt);
	}

	/** 보관 기간(24개월) 중 한 번이라도 받은 비율 */
	public int coveragePercent() {
		return totalTasks == 0 ? 0 : (int) ((totalTasks - missing) * 100L / totalTasks);
	}

}
