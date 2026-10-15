package io.github.minunglee.gateway.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.KillSwitch;
import io.github.minunglee.gateway.limit.RateLimitExceededException;
import io.github.minunglee.gateway.limit.RateLimitResult;
import io.github.minunglee.gateway.limit.RateLimiter;
import io.github.minunglee.gateway.limit.Requester;
import io.github.minunglee.gateway.llm.LlmClient;
import io.github.minunglee.gateway.llm.LlmException;
import io.github.minunglee.gateway.llm.LlmProperties;
import io.github.minunglee.gateway.llm.LlmRequest;
import io.github.minunglee.gateway.llm.LlmResponse;
import io.github.minunglee.gateway.log.RequestLog;
import io.github.minunglee.gateway.log.RequestLogRepository;
import io.github.minunglee.gateway.pricing.CostCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 요청 1건의 흐름: 사용량 제한 → 긴급 차단 확인 → 예산 선차감 → LLM 호출 → 정산·기록.
 * <p>
 * 예산을 확인할 수 없거나(Redis 장애) 예산이 소진되면 LLM을 호출하지 않고 데모 응답을 돌려준다.
 * 비용 통제가 보장되지 않는 상태에서는 호출하지 않는 쪽(fail-closed)을 택한다.
 */
@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	/** 선차감 시 입력 토큰 상한 추정: 글자당 최대 2토큰 + 메시지 구조 여유분 */
	private static final int TOKENS_PER_CHAR_UPPER_BOUND = 2;

	private static final int INPUT_TOKEN_OVERHEAD = 100;

	private final LlmClient llmClient;

	private final LlmProperties llmProperties;

	private final CostCalculator costCalculator;

	private final RequestLogRepository requestLogRepository;

	private final RateLimiter rateLimiter;

	private final BudgetGuard budgetGuard;

	private final KillSwitch killSwitch;

	private final Clock clock;

	public ChatService(LlmClient llmClient, LlmProperties llmProperties, CostCalculator costCalculator,
			RequestLogRepository requestLogRepository, RateLimiter rateLimiter, BudgetGuard budgetGuard,
			KillSwitch killSwitch, Clock clock) {
		this.llmClient = llmClient;
		this.llmProperties = llmProperties;
		this.costCalculator = costCalculator;
		this.requestLogRepository = requestLogRepository;
		this.rateLimiter = rateLimiter;
		this.budgetGuard = budgetGuard;
		this.killSwitch = killSwitch;
		this.clock = clock;
	}

	public ChatResult chat(Requester requester, String message, Trace trace) {
		Instant startedAt = clock.instant();
		LlmRequest request = new LlmRequest(requester.model(), message, llmProperties.maxTokens());
		long reserveAmount = maxCostMicroUsd(request);

		RateLimitResult limit;
		Optional<BudgetGuard.Reservation> reservation;
		try {
			long t = System.nanoTime();
			limit = rateLimiter.tryAcquire(requester.rateKey(), requester.hourly(), requester.daily());
			if (!limit.allowed()) {
				trace.blocked("사용량 제한", limit.dailyLimitReached() ? "오늘 사용 횟수를 모두 사용함" : "이번 시간 한도 초과",
						"Redis Lua 토큰 버킷", t);
				throw new TracedChatException(new RateLimitExceededException(limit), trace);
			}
			trace.ok("사용량 제한", "이번 시간 %d회 · 오늘 %d회 남음".formatted(limit.remainingThisHour(),
					limit.remainingToday()), "Redis Lua 토큰 버킷", t);
			if (killSwitch.isOn()) {
				trace.blocked("긴급 차단", "관리자가 AI 호출을 멈춤 → 데모 응답", "Redis 스위치", 0);
				return degraded(requester, request, limit, startedAt, Degraded.KILL_SWITCH, trace);
			}
			t = System.nanoTime();
			reservation = budgetGuard.reserve(requester.tier(), reserveAmount);
			if (reservation.isEmpty()) {
				trace.blocked("예산 선차감", "오늘 예산 소진 → AI를 호출하지 않고 데모 응답", "Redis Lua", t);
				return degraded(requester, request, limit, startedAt, Degraded.BUDGET_EXHAUSTED, trace);
			}
			trace.ok("예산 선차감", CostCalculator.formatUsd(reserveAmount) + " 확보 (나올 수 있는 최대 비용)", "Redis Lua",
					t);
		}
		catch (DataAccessException ex) {
			log.error("Redis 장애로 사용량·예산을 확인할 수 없어 데모 응답으로 전환합니다", ex);
			trace.blocked("사용량·예산 확인", "Redis 응답 없음 → 비용 통제 불가라 AI 호출 차단", "fail-closed", 0);
			return degraded(requester, request, null, startedAt, Degraded.GUARD_UNAVAILABLE, trace);
		}

		LlmResponse response;
		long t = System.nanoTime();
		try {
			response = llmClient.complete(request);
		}
		catch (LlmException ex) {
			budgetGuard.refund(reservation.get());
			trace.blocked("Claude 호출", "실패 → 선차감한 " + CostCalculator.formatUsd(reserveAmount) + " 전액 반환",
					request.model(), t);
			requestLogRepository.save(new RequestLog(requester, llmClient.mode(), request.model(),
					RequestLog.Status.ERROR, 0, 0, 0, elapsedMs(startedAt), startedAt));
			throw new TracedChatException(ex, trace);
		}
		boolean mock = "mock".equals(llmClient.mode());
		trace.ok(mock ? "AI 호출 (가짜 응답 모드)" : "Claude 호출",
				"%s · 입력 %,d · 출력 %,d 토큰".formatted(response.model(), response.inputTokens(),
						response.outputTokens()),
				mock ? "비용 0원 테스트 모드" : "Anthropic Java SDK", t);

		long costMicroUsd = costCalculator.costMicroUsd(response.model(), response.inputTokens(),
				response.outputTokens());
		t = System.nanoTime();
		budgetGuard.settle(reservation.get(), costMicroUsd);
		trace.ok("정산", "실제 %s 사용 → %s 반환".formatted(CostCalculator.formatUsd(costMicroUsd),
				CostCalculator.formatUsd(reserveAmount - costMicroUsd)), "Redis INCRBY", t);
		long latencyMs = elapsedMs(startedAt);
		requestLogRepository.save(new RequestLog(requester, llmClient.mode(), response.model(),
				RequestLog.Status.SUCCESS, response.inputTokens(), response.outputTokens(), costMicroUsd, latencyMs,
				startedAt));

		return new ChatResult(response.text(), response.model(), llmClient.mode(), response.inputTokens(),
				response.outputTokens(), costMicroUsd, latencyMs, limit.remainingThisHour(), limit.remainingToday(),
				trace);
	}

	/** 나올 수 있는 최대 비용. 입력은 상한 추정치, 출력은 max_tokens 전부를 쓴다고 가정한다 */
	long maxCostMicroUsd(LlmRequest request) {
		long maxInputTokens = (long) request.userMessage().length() * TOKENS_PER_CHAR_UPPER_BOUND
				+ INPUT_TOKEN_OVERHEAD;
		return costCalculator.costMicroUsd(request.model(), maxInputTokens, request.maxTokens());
	}

	private ChatResult degraded(Requester requester, LlmRequest request, RateLimitResult limit, Instant startedAt,
			Degraded reason, Trace trace) {
		long latencyMs = elapsedMs(startedAt);
		requestLogRepository.save(new RequestLog(requester, "demo", request.model(), RequestLog.Status.DEGRADED, 0,
				0, 0, latencyMs, startedAt));
		int remainingThisHour = limit == null ? 0 : limit.remainingThisHour();
		int remainingToday = limit == null ? 0 : limit.remainingToday();
		return new ChatResult(reason.message, request.model(), "demo", 0, 0, 0, latencyMs, remainingThisHour,
				remainingToday, trace);
	}

	private long elapsedMs(Instant startedAt) {
		return Duration.between(startedAt, clock.instant()).toMillis();
	}

	enum Degraded {

		KILL_SWITCH("[데모 응답] 관리자가 AI 호출을 일시 중단했습니다. 잠시 후 다시 시도해주세요."),
		BUDGET_EXHAUSTED("[데모 응답] 오늘 준비된 AI 예산이 모두 소진되었습니다. 매일 0시(한국 시간)에 초기화됩니다."),
		GUARD_UNAVAILABLE("[데모 응답] 사용량을 확인할 수 없어 AI 호출을 잠시 멈췄습니다. 잠시 후 다시 시도해주세요.");

		private final String message;

		Degraded(String message) {
			this.message = message;
		}

	}

	public record ChatResult(String answer, String model, String mode, long inputTokens, long outputTokens,
			long costMicroUsd, long latencyMs, int remainingThisHour, int remainingToday, Trace trace) {
	}

}
