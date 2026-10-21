package io.github.minunglee.gateway.visit;

import java.time.LocalDate;
import java.util.List;

/**
 * 방문 통계 (관리자 화면용). jipdon-api의 /internal/stats 응답과 같은 모양이라 그대로 읽어 쓴다.
 *
 * @param today 오늘, last7 최근 7일, last30 최근 30일 (오늘 포함)
 */
public record VisitStats(int days, Count today, Count last7, Count last30, List<Day> daily, List<Rank> topPages,
		List<Rank> topReferrers, List<Rank> devices) {

	/** 조회 수와 순방문자 수 (순방문자는 날짜마다 따로 센 값의 합) */
	public record Count(long views, long visitors) {
	}

	public record Day(LocalDate date, long views, long visitors) {

		/** 관리자 표의 막대 길이 (해당 기간 최대 조회 수 대비 %) */
		public int barPercent(long max) {
			return max == 0 ? 0 : (int) Math.round(views * 100.0 / max);
		}

	}

	public record Rank(String key, long count) {
	}

	public long maxDailyViews() {
		return daily.stream().mapToLong(Day::views).max().orElse(0);
	}

}
