package io.github.minunglee.gateway.visit;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.minunglee.gateway.limit.LimitProperties;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * minunglee.cloud 방문 기록과 통계.
 *
 * 원래 IP는 저장하지 않는다. 사용량 제한에 쓰는 IP 해시(비밀값 HMAC)에 브라우저와 날짜를 더해 다시 해시하므로,
 * 같은 날 같은 사람인지만 셀 수 있고 다음 날이면 연결되지 않는다. 쿠키도 쓰지 않는다.
 */
@Service
public class VisitService {

	private static final Pattern BOT = Pattern.compile(
			"bot|crawl|spider|slurp|preview|headless|lighthouse|curl|wget|python|java/|k6", Pattern.CASE_INSENSITIVE);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final ZoneId zone;

	VisitService(JdbcTemplate jdbc, Clock clock, LimitProperties limitProperties) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.zone = limitProperties.zone();
	}

	/** @param clientKey 사용량 제한과 같은 IP 해시 (원래 IP 아님) */
	public boolean record(String path, String referrer, String clientKey, String userAgent) {
		String ua = userAgent == null ? "" : userAgent;
		if (ua.isBlank() || BOT.matcher(ua).find()) {
			return false;
		}
		LocalDate today = LocalDate.now(clock.withZone(zone));
		jdbc.update("INSERT INTO page_view (viewed_at, path, referrer_host, visitor, device) VALUES (?, ?, ?, ?, ?)",
				Timestamp.from(clock.instant()), path, referrerHost(referrer), visitor(clientKey, ua, today),
				ua.contains("Mobi") ? "mobile" : "desktop");
		return true;
	}

	/** 최근 days일 통계 (오늘 포함) */
	public VisitStats stats(int days) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		Timestamp since = Timestamp.from(today.minusDays(days - 1L).atStartOfDay(zone).toInstant());
		String day = "(viewed_at AT TIME ZONE '" + zone.getId() + "')::date";
		List<VisitStats.Day> daily = jdbc.query("SELECT " + day + " AS d, count(*) AS views,"
				+ " count(DISTINCT visitor) AS visitors FROM page_view WHERE viewed_at >= ? GROUP BY 1 ORDER BY 1",
				(rs, i) -> new VisitStats.Day(rs.getDate("d").toLocalDate(), rs.getLong("views"), rs.getLong("visitors")),
				since);
		return new VisitStats(days, sum(daily, today, 1), sum(daily, today, 7), sum(daily, today, 30), daily,
				rank("path", since), rank("referrer_host", since), rank("device", since));
	}

	private static VisitStats.Count sum(List<VisitStats.Day> daily, LocalDate today, int days) {
		LocalDate from = today.minusDays(days - 1L);
		long views = 0;
		long visitors = 0;
		for (VisitStats.Day d : daily) {
			if (!d.date().isBefore(from)) {
				views += d.views();
				visitors += d.visitors();
			}
		}
		return new VisitStats.Count(views, visitors);
	}

	private List<VisitStats.Rank> rank(String column, Timestamp since) {
		return jdbc.query("SELECT " + column + " AS k, count(*) AS c FROM page_view WHERE viewed_at >= ? AND " + column
				+ " IS NOT NULL GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10",
				(rs, i) -> new VisitStats.Rank(rs.getString("k"), rs.getLong("c")), since);
	}

	/** 다른 사이트에서 왔을 때 그 호스트만 (같은 사이트 안 이동이면 null) */
	static String referrerHost(String referrer) {
		if (referrer == null || referrer.isBlank()) {
			return null;
		}
		try {
			String host = URI.create(referrer.strip()).getHost();
			if (host == null) {
				return null;
			}
			host = host.toLowerCase(Locale.ROOT);
			if (host.equals("minunglee.cloud") || host.endsWith(".minunglee.cloud") || host.equals("localhost")) {
				return null;
			}
			return host.length() > 100 ? host.substring(0, 100) : host;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String visitor(String clientKey, String userAgent, LocalDate date) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest((clientKey + "|" + userAgent + "|" + date).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 8);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
