package io.github.minunglee.gateway.invite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

	private final InviteCodeRepository inviteCodeRepository;

	private final InviteAccessLogRepository accessLogRepository;

	private final Clock clock;

	InviteService(InviteCodeRepository inviteCodeRepository, InviteAccessLogRepository accessLogRepository,
			Clock clock) {
		this.inviteCodeRepository = inviteCodeRepository;
		this.accessLogRepository = accessLogRepository;
		this.clock = clock;
	}

	/** 새 코드를 발급한다. 원문 코드는 이 반환값으로 한 번만 볼 수 있다 */
	@Transactional
	public CreatedInvite create(String label, Duration validFor, int dailyLimit) {
		String code = InviteCodes.generate();
		Instant now = clock.instant();
		InviteCode saved = inviteCodeRepository
			.save(new InviteCode(InviteCodes.hash(code), label, dailyLimit, now.plus(validFor), now));
		return new CreatedInvite(saved, code);
	}

	/** 입력한 코드를 해시로 찾아, 유효하면 "코드 입력" 기록을 남기고 돌려준다 */
	@Transactional
	public Optional<InviteCode> redeem(String input) {
		Optional<InviteCode> code = inviteCodeRepository.findByCodeHash(InviteCodes.hash(input))
			.filter(found -> found.isActive(clock.instant()));
		code.ifPresent(found -> log(found.getId(), InviteAccessLog.Action.REDEEM));
		return code;
	}

	/** 쿠키에 담긴 코드가 아직 유효한지 매 요청 확인한다. 관리자가 폐기하면 바로 막힌다 */
	public Optional<InviteCode> findActive(long id) {
		return inviteCodeRepository.findById(id).filter(code -> code.isActive(clock.instant()));
	}

	/** 열람 기록 한 건 저장 (이력서 열람, PDF 다운로드 등) */
	@Transactional
	public void log(long inviteCodeId, InviteAccessLog.Action action) {
		accessLogRepository.save(new InviteAccessLog(inviteCodeId, action, clock.instant()));
	}

	/** 코드 폐기 (없는 ID면 아무것도 하지 않음) */
	@Transactional
	public void revoke(long id) {
		inviteCodeRepository.findById(id).ifPresent(InviteCode::revoke);
	}

	/** 관리자용: 코드별 열람 현황 */
	@Transactional(readOnly = true)
	public List<InviteSummary> summaries() {
		Map<Long, List<InviteAccessLog>> logsByCode = accessLogRepository.findAll()
			.stream()
			.collect(Collectors.groupingBy(InviteAccessLog::getInviteCodeId));
		return inviteCodeRepository.findAll().stream().map(code -> {
			List<InviteAccessLog> logs = logsByCode.getOrDefault(code.getId(), List.of());
			Map<InviteAccessLog.Action, Long> counts = logs.stream()
				.collect(Collectors.groupingBy(InviteAccessLog::getAction, Collectors.counting()));
			Instant lastAccess = logs.stream()
				.map(InviteAccessLog::getAccessedAt)
				.max(Instant::compareTo)
				.orElse(null);
			return new InviteSummary(code.getId(), code.getLabel(), code.getDailyLimit(), code.getExpiresAt(),
					code.isRevoked(), counts.getOrDefault(InviteAccessLog.Action.REDEEM, 0L),
					counts.getOrDefault(InviteAccessLog.Action.RESUME_VIEW, 0L),
					counts.getOrDefault(InviteAccessLog.Action.RESUME_PDF, 0L), lastAccess, code.getCreatedAt());
		}).sorted((a, b) -> Long.compare(b.id(), a.id())).toList();
	}

	/** 발급 결과. code는 원문이라 이때만 볼 수 있다 */
	public record CreatedInvite(InviteCode invite, String code) {
	}

	/** 관리자 화면용 코드별 요약: 코드 입력·이력서 열람·PDF 다운로드 횟수, 마지막 접근 시각, 발급 시각 */
	public record InviteSummary(long id, String label, int dailyLimit, Instant expiresAt, boolean revoked,
			long redeemCount, long resumeViews, long resumePdfDownloads, Instant lastAccessAt, Instant createdAt) {
	}

}
