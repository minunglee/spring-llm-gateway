package io.github.minunglee.gateway.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.minunglee.gateway.invite.InviteService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대코드 발급·조회·폐기. 회사마다 코드를 따로 발급해 열람 현황을 확인한다.
 */
@RestController
@RequestMapping("/admin/invites")
class InviteAdminController {

	private final InviteService inviteService;

	InviteAdminController(InviteService inviteService) {
		this.inviteService = inviteService;
	}

	/** POST /admin/invites: 회사용 초대코드를 새로 만든다 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CreateResponse create(@Valid @RequestBody CreateRequest request) {
		InviteService.CreatedInvite created = inviteService.create(request.label(),
				Duration.ofDays(request.validDays()), request.dailyLimit());
		return new CreateResponse(created.invite().getId(), created.code(), created.invite().getLabel(),
				created.invite().getExpiresAt());
	}

	/** GET /admin/invites: 초대코드별 열람 현황 */
	@GetMapping
	List<InviteService.InviteSummary> list() {
		return inviteService.summaries();
	}

	/** DELETE /admin/invites/{id}: 코드 폐기. 그 코드를 쓰던 사람은 다음 요청부터 게스트가 된다 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@PathVariable long id) {
		inviteService.revoke(id);
	}

	/**
	 * @param label 관리자만 보는 이름 (회사명 등)
	 * @param validDays 유효 기간 1~180일
	 * @param dailyLimit 이 코드의 하루 AI 질문 한도 1~1000회
	 */
	record CreateRequest(@NotBlank @Size(max = 100) String label, @Min(1) @Max(180) int validDays,
			@Min(1) @Max(1000) int dailyLimit) {
	}

	/** 원문 코드는 이 응답에서 한 번만 보여준다 */
	record CreateResponse(long id, String code, String label, Instant expiresAt) {
	}

}
