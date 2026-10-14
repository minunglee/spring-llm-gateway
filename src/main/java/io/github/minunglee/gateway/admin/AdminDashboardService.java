package io.github.minunglee.gateway.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.minunglee.gateway.invite.InviteService;
import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.KillSwitch;
import io.github.minunglee.gateway.limit.LimitProperties;
import io.github.minunglee.gateway.limit.Tier;
import io.github.minunglee.gateway.llm.LlmClient;
import io.github.minunglee.gateway.pricing.CostCalculator;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 관리자 대시보드에 보여줄 운영 현황. 본인만 볼 수 있다.
 */
@Service
class AdminDashboardService {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

	private static final int DAYS = 7;

	private final JdbcClient jdbc;

	private final InviteService inviteService;

	private final BudgetGuard budgetGuard;

	private final KillSwitch killSwitch;

	private final LlmClient llmClient;

	private final ZoneId zone;

	private final Clock clock;

	AdminDashboardService(JdbcClient jdbc, InviteService inviteService, BudgetGuard budgetGuard,
			KillSwitch killSwitch, LlmClient llmClient, LimitProperties limitProperties, Clock clock) {
		this.jdbc = jdbc;
		this.inviteService = inviteService;
		this.budgetGuard = budgetGuard;
		this.killSwitch = killSwitch;
		this.llmClient = llmClient;
		this.zone = limitProperties.zone();
		this.clock = clock;
	}

	Dashboard dashboard() {
		return new Dashboard(llmClient.mode(), killSwitch.isOn(), List.of(budget(Tier.GUEST), budget(Tier.INVITE)),
				dailyStats(), invites(), recentAccess());
	}

	private Budget budget(Tier tier) {
		long used = budgetGuard.usedTodayMicroUsd(tier);
		long limit = budgetGuard.limitMicroUsd(tier);
		return new Budget(tier == Tier.GUEST ? "게스트" : "초대코드", CostCalculator.formatUsd(used),
				CostCalculator.formatUsd(limit), Math.min(100.0, used * 100.0 / limit));
	}

	/** 최근 7일 일별 요청 수와 비용 (하루 경계는 한국 시간) */
	private List<DailyStat> dailyStats() {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		Instant from = today.minusDays(DAYS - 1).atStartOfDay(zone).toInstant();
		Map<LocalDate, DailyStat> rows = jdbc.sql("""
				SELECT (created_at AT TIME ZONE :zone)::date AS day,
				       COUNT(*) AS requests,
				       COUNT(*) FILTER (WHERE tier = 'INVITE') AS invite_requests,
				       COUNT(*) FILTER (WHERE status <> 'SUCCESS') AS not_success,
				       COALESCE(SUM(cost_micro_usd), 0) AS cost
				FROM request_log
				WHERE created_at >= :from
				GROUP BY day
				""")
			.param("zone", zone.getId())
			.param("from", java.sql.Timestamp.from(from))
			.query((rs, i) -> new DailyStat(rs.getDate("day").toLocalDate().toString(), rs.getLong("requests"),
					rs.getLong("invite_requests"), rs.getLong("not_success"),
					CostCalculator.formatUsd(rs.getLong("cost")), 0))
			.list()
			.stream()
			.collect(Collectors.toMap(stat -> LocalDate.parse(stat.day()), stat -> stat));
		long max = rows.values().stream().mapToLong(DailyStat::requests).max().orElse(0);
		return today.minusDays(DAYS - 1).datesUntil(today.plusDays(1)).map(day -> {
			DailyStat stat = rows.get(day);
			if (stat == null) {
				return new DailyStat(day.toString(), 0, 0, 0, "$0", 0);
			}
			return new DailyStat(stat.day(), stat.requests(), stat.inviteRequests(), stat.notSuccess(), stat.cost(),
					max == 0 ? 0 : stat.requests() * 100.0 / max);
		}).toList();
	}

	private List<InviteRow> invites() {
		Map<Long, Long> chats = jdbc
			.sql("SELECT invite_code_id, COUNT(*) AS c FROM request_log WHERE invite_code_id IS NOT NULL GROUP BY 1")
			.query((rs, i) -> Map.entry(rs.getLong("invite_code_id"), rs.getLong("c")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		Instant now = clock.instant();
		return inviteService.summaries()
			.stream()
			.map(s -> new InviteRow(s.id(), s.label(), status(s, now), format(s.expiresAt()), s.dailyLimit(),
					s.redeemCount(), s.resumeViews(), s.resumePdfDownloads(), chats.getOrDefault(s.id(), 0L),
					s.lastAccessAt() == null ? "-" : format(s.lastAccessAt()), s.revoked()))
			.toList();
	}

	private List<AccessRow> recentAccess() {
		return jdbc.sql("""
				SELECT l.accessed_at, c.label, l.action
				FROM invite_access_log l JOIN invite_code c ON c.id = l.invite_code_id
				ORDER BY l.accessed_at DESC
				LIMIT 30
				""")
			.query((rs, i) -> new AccessRow(format(rs.getTimestamp("accessed_at").toInstant()), rs.getString("label"),
					actionName(rs.getString("action"))))
			.list();
	}

	private String status(InviteService.InviteSummary summary, Instant now) {
		if (summary.revoked()) {
			return "폐기";
		}
		return now.isBefore(summary.expiresAt()) ? "사용 중" : "만료";
	}

	private String actionName(String action) {
		return switch (action) {
			case "REDEEM" -> "코드 입력";
			case "RESUME_VIEW" -> "이력서 열람";
			case "RESUME_PDF" -> "PDF 다운로드";
			default -> action;
		};
	}

	private String format(Instant instant) {
		return DATE_TIME.format(instant.atZone(zone));
	}

	record Dashboard(String llmMode, boolean killSwitch, List<Budget> budgets, List<DailyStat> daily,
			List<InviteRow> invites, List<AccessRow> recentAccess) {
	}

	record Budget(String tier, String used, String limit, double usedPercent) {
	}

	record DailyStat(String day, long requests, long inviteRequests, long notSuccess, String cost,
			double barPercent) {
	}

	record InviteRow(long id, String label, String status, String expiresAt, int dailyLimit, long redeems,
			long resumeViews, long pdfDownloads, long llmRequests, String lastAccess, boolean revoked) {
	}

	record AccessRow(String at, String label, String action) {
	}

}
