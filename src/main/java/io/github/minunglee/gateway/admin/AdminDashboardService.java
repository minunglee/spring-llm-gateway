package io.github.minunglee.gateway.admin;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>
 * 초대코드 관련 기록은 두 곳에 있다: 열람 기록(invite_access_log)과 AI 질문(request_log).
 * 화면에서는 둘을 하나의 "이벤트" 흐름으로 합쳐 보여준다.
 */
@Service
class AdminDashboardService {

	/** "열람 기록 전체" 화면의 한 페이지 줄 수 */
	static final int PAGE_SIZE = 50;

	/** 화면에 보여줄 시각 형식 (한국 시간으로 바꾼 뒤 적용) */
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/** 열람 기록과 AI 질문을 하나로 합친 이벤트 (시각, 코드, 행동, 비용) */
	private static final String EVENTS = """
			WITH events AS (
			    SELECT l.accessed_at AS at, l.invite_code_id AS code_id, l.action AS action, NULL::bigint AS cost
			    FROM invite_access_log l
			    UNION ALL
			    SELECT r.created_at, r.invite_code_id, 'AI_CHAT', r.cost_micro_usd
			    FROM request_log r
			    WHERE r.invite_code_id IS NOT NULL
			)
			""";

	/** 이벤트 목록·개수 쿼리에 함께 붙이는 조건. 코드나 행동을 고르지 않았으면(null) 그 조건은 건너뛴다 */
	private static final String EVENT_FILTER = """
			WHERE (CAST(:codeId AS BIGINT) IS NULL OR e.code_id = :codeId)
			  AND (CAST(:action AS VARCHAR) IS NULL OR e.action = :action)
			""";

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

	/** 관리자 첫 화면 데이터: AI 모드, 긴급 차단, 등급별 예산(게스트·초대코드·집돈), 기간별 사용량, 초대코드 현황, 최근 이벤트 */
	Dashboard dashboard(UsageRange range) {
		return new Dashboard(llmClient.mode(), killSwitch.isOn(), List.of(budget(Tier.GUEST), budget(Tier.INVITE), budget(Tier.JIPDON)),
				range, dailyStats(range), invites(), events(null, null, 30, 0));
	}

	/** 회사(초대코드)별 상세: 요약, 날짜별 집계, 전체 기록 */
	Optional<InviteDetail> inviteDetail(long id) {
		return invites().stream().filter(row -> row.id() == id).findFirst().map(invite -> {
			List<InviteDay> days = jdbc.sql(EVENTS + """
					SELECT (e.at AT TIME ZONE :zone)::date AS day,
					       COUNT(*) FILTER (WHERE e.action = 'REDEEM') AS redeems,
					       COUNT(*) FILTER (WHERE e.action = 'RESUME_VIEW') AS views,
					       COUNT(*) FILTER (WHERE e.action = 'RESUME_PDF') AS pdfs,
					       COUNT(*) FILTER (WHERE e.action = 'AI_CHAT') AS chats
					FROM events e
					WHERE e.code_id = :codeId
					GROUP BY day
					ORDER BY day DESC
					""")
				.param("zone", zone.getId())
				.param("codeId", id)
				.query((rs, i) -> new InviteDay(rs.getDate("day").toLocalDate().toString(), rs.getLong("redeems"),
						rs.getLong("views"), rs.getLong("pdfs"), rs.getLong("chats")))
				.list();
			return new InviteDetail(invite, days, events(id, null, 500, 0));
		});
	}

	/** 전체 열람 기록 (페이지, 회사·행동 필터) */
	AccessPage accessPage(Long codeId, String action, int page) {
		int safePage = Math.max(page, 1);
		long total = jdbc.sql(EVENTS + "SELECT COUNT(*) FROM events e " + EVENT_FILTER)
			.param("codeId", codeId, Types.BIGINT)
			.param("action", blankToNull(action), Types.VARCHAR)
			.query(Long.class)
			.single();
		int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
		List<Event> rows = events(codeId, action, PAGE_SIZE, (long) (safePage - 1) * PAGE_SIZE);
		List<Option> codes = invites().stream().map(row -> new Option(String.valueOf(row.id()), row.label())).toList();
		return new AccessPage(rows, safePage, totalPages, total, codeId, blankToNull(action), codes, ACTIONS);
	}

	/** 열람 기록과 초대코드 사용자의 AI 질문을 합친 이벤트를 최신순으로 (코드·행동 필터, limit/offset으로 페이지 나누기) */
	private List<Event> events(Long codeId, String action, int limit, long offset) {
		return jdbc.sql(EVENTS + """
				SELECT e.at, e.code_id, c.label, e.action, e.cost
				FROM events e JOIN invite_code c ON c.id = e.code_id
				""" + EVENT_FILTER + """
				ORDER BY e.at DESC
				LIMIT :limit OFFSET :offset
				""")
			.param("codeId", codeId, Types.BIGINT)
			.param("action", blankToNull(action), Types.VARCHAR)
			.param("limit", limit)
			.param("offset", offset)
			.query((rs, i) -> {
				long cost = rs.getLong("cost");
				String detail = rs.wasNull() ? "" : CostCalculator.formatUsd(cost);
				return new Event(format(rs.getTimestamp("at").toInstant()), rs.getLong("code_id"),
						rs.getString("label"), actionName(rs.getString("action")), detail);
			})
			.list();
	}

	/** 등급의 오늘 예산 사용 현황 (진행 막대에 쓸 퍼센트 포함) */
	private Budget budget(Tier tier) {
		long used = budgetGuard.usedTodayMicroUsd(tier);
		long limit = budgetGuard.limitMicroUsd(tier);
		String name = switch (tier) {
			case GUEST -> "게스트";
			case INVITE -> "초대코드";
			case JIPDON -> "집돈 AI 안내";
		};
		return new Budget(name, CostCalculator.formatUsd(used),
				CostCalculator.formatUsd(limit), Math.min(100.0, used * 100.0 / limit));
	}

	/** 기간별 일별 요청 수와 비용 (하루 경계는 한국 시간, 최근 날짜가 위) */
	private List<DailyStat> dailyStats(UsageRange range) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		LocalDate firstDay = range.days() > 0 ? today.minusDays(range.days() - 1) : firstRecordDay().orElse(today);
		Instant from = firstDay.atStartOfDay(zone).toInstant();
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
			.param("from", Timestamp.from(from))
			.query((rs, i) -> new DailyStat(rs.getDate("day").toLocalDate().toString(), rs.getLong("requests"),
					rs.getLong("invite_requests"), rs.getLong("not_success"),
					CostCalculator.formatUsd(rs.getLong("cost")), 0))
			.list()
			.stream()
			.collect(Collectors.toMap(stat -> LocalDate.parse(stat.day()), stat -> stat));
		long max = rows.values().stream().mapToLong(DailyStat::requests).max().orElse(0);
		return firstDay.datesUntil(today.plusDays(1)).map(day -> {
			DailyStat stat = rows.get(day);
			if (stat == null) {
				return new DailyStat(day.toString(), 0, 0, 0, "$0", 0);
			}
			return new DailyStat(stat.day(), stat.requests(), stat.inviteRequests(), stat.notSuccess(), stat.cost(),
					max == 0 ? 0 : stat.requests() * 100.0 / max);
		}).sorted((a, b) -> b.day().compareTo(a.day())).toList();
	}

	/** 요청 기록이 처음 생긴 날 ("전체" 기간 사용량 표의 시작일) */
	private Optional<LocalDate> firstRecordDay() {
		// 기록이 없으면 MIN이 NULL 한 행을 돌려준다
		return jdbc.sql("SELECT MIN(created_at) FROM request_log")
			.query((rs, i) -> Optional.ofNullable(rs.getTimestamp(1)))
			.single()
			.map(timestamp -> timestamp.toInstant().atZone(zone).toLocalDate());
	}

	/** 초대코드별 현황 표: 상태, 만료일, 코드 입력·이력서 열람·PDF·AI 질문 횟수, 마지막 접근 시각 */
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

	/** 초대코드 상태: 폐기 / 사용 중 / 만료 */
	private String status(InviteService.InviteSummary summary, Instant now) {
		if (summary.revoked()) {
			return "폐기";
		}
		return now.isBefore(summary.expiresAt()) ? "사용 중" : "만료";
	}

	/** 이벤트 종류와 화면에 보일 이름 (열람 기록 화면의 행동 필터 선택지로도 쓴다) */
	private static final List<Option> ACTIONS = List.of(new Option("REDEEM", "코드 입력"),
			new Option("RESUME_VIEW", "이력서 열람"), new Option("RESUME_PDF", "PDF 다운로드"),
			new Option("AI_CHAT", "AI 질문"));

	/** 이벤트 코드(REDEEM 등)를 화면 이름(코드 입력 등)으로 */
	private String actionName(String action) {
		return ACTIONS.stream().filter(option -> option.value().equals(action)).map(Option::label).findFirst()
			.orElse(action);
	}

	/** 시각을 한국 시간 "yyyy-MM-dd HH:mm" 으로 */
	private String format(Instant instant) {
		return DATE_TIME.format(instant.atZone(zone));
	}

	/** 빈 필터 값은 null로 바꿔 쿼리에서 "조건 없음"으로 처리되게 한다 */
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	/** 사용량 표의 기간 */
	enum UsageRange {

		WEEK("7", "7일", 7), MONTH("30", "30일", 30), ALL("all", "전체", 0);

		private final String param;

		private final String label;

		private final int days;

		UsageRange(String param, String label, int days) {
			this.param = param;
			this.label = label;
			this.days = days;
		}

		static UsageRange of(String param) {
			for (UsageRange range : values()) {
				if (range.param.equals(param)) {
					return range;
				}
			}
			return WEEK;
		}

		public String param() {
			return param;
		}

		public String label() {
			return label;
		}

		int days() {
			return days;
		}

	}

	/**
	 * 관리자 첫 화면 전체 데이터
	 *
	 * @param llmMode mock(가짜 응답) 또는 anthropic(실제 호출)
	 * @param recentEvents 최근 열람·AI 질문 이벤트
	 */
	record Dashboard(String llmMode, boolean killSwitch, List<Budget> budgets, UsageRange range,
			List<DailyStat> daily, List<InviteRow> invites, List<Event> recentEvents) {

		public List<UsageRange> ranges() {
			return List.of(UsageRange.values());
		}

	}

	/** @param tier 화면에 보일 등급 이름, used 오늘 쓴 금액, limit 하루 한도, usedPercent 진행 막대 길이(0~100) */
	record Budget(String tier, String used, String limit, double usedPercent) {
	}

	/**
	 * 사용량 표의 하루 한 줄
	 *
	 * @param inviteRequests 그중 초대코드 사용자 요청
	 * @param notSuccess 실패하거나 데모 응답으로 대체된 요청
	 * @param barPercent 기간 중 가장 많은 날 대비 막대 길이
	 */
	record DailyStat(String day, long requests, long inviteRequests, long notSuccess, String cost,
			double barPercent) {
	}

	/** 초대코드 현황 표의 한 줄 (label은 회사명 등 관리자만 보는 이름) */
	record InviteRow(long id, String label, String status, String expiresAt, int dailyLimit, long redeems,
			long resumeViews, long pdfDownloads, long llmRequests, String lastAccess, boolean revoked) {
	}

	/** 회사별 상세 화면의 날짜별 집계 */
	record InviteDay(String day, long redeems, long resumeViews, long pdfDownloads, long chats) {
	}

	/** 회사(초대코드)별 상세: 요약, 날짜별 집계, 전체 기록 */
	record InviteDetail(InviteRow invite, List<InviteDay> days, List<Event> events) {
	}

	/**
	 * 이벤트 한 건
	 *
	 * @param detail AI 질문이면 그 비용, 그 밖에는 빈 값
	 */
	record Event(String at, long codeId, String label, String action, String detail) {
	}

	/** 선택 상자 항목 (값, 화면 이름) */
	record Option(String value, String label) {
	}

	/** "열람 기록 전체" 한 페이지와 필터 상태 */
	record AccessPage(List<Event> events, int page, int totalPages, long total, Long codeId, String action,
			List<Option> codes, List<Option> actions) {

		public boolean hasPrevious() {
			return page > 1;
		}

		public boolean hasNext() {
			return page < totalPages;
		}

	}

}
