package io.github.minunglee.gateway.invite;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.minunglee.gateway.limit.ClientKeyResolver;
import io.github.minunglee.gateway.limit.RateLimitExceededException;
import io.github.minunglee.gateway.limit.RateLimitResult;
import io.github.minunglee.gateway.limit.RateLimiter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대코드 입력·해제.
 */
@RestController
class InviteController {

	/** 코드 무작위 대입을 막기 위한 IP당 입력 시도 한도 */
	private static final int REDEEM_HOURLY = 10;

	private static final int REDEEM_DAILY = 30;

	private final InviteService inviteService;

	private final InviteCookie inviteCookie;

	private final RateLimiter rateLimiter;

	private final ClientKeyResolver clientKeyResolver;

	InviteController(InviteService inviteService, InviteCookie inviteCookie, RateLimiter rateLimiter,
			ClientKeyResolver clientKeyResolver) {
		this.inviteService = inviteService;
		this.inviteCookie = inviteCookie;
		this.rateLimiter = rateLimiter;
		this.clientKeyResolver = clientKeyResolver;
	}

	@PostMapping("/invite")
	ResponseEntity<?> redeem(@Valid @RequestBody RedeemRequest request, HttpServletRequest servletRequest) {
		RateLimitResult limit = rateLimiter.tryAcquire("redeem:" + clientKeyResolver.resolve(servletRequest),
				REDEEM_HOURLY, REDEEM_DAILY);
		if (!limit.allowed()) {
			throw new RateLimitExceededException(limit);
		}
		return inviteService.redeem(request.code())
			.<ResponseEntity<?>>map(code -> ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, inviteCookie.issue(code, servletRequest.isSecure()).toString())
				.body(new RedeemResponse(code.getLabel(), code.getExpiresAt())))
			.orElseGet(() -> ResponseEntity.badRequest()
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 초대코드입니다.")));
	}

	@DeleteMapping("/invite")
	ResponseEntity<Void> clear(HttpServletRequest servletRequest) {
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, inviteCookie.clear(servletRequest.isSecure()).toString())
			.build();
	}

	record RedeemRequest(@NotBlank(message = "초대코드를 입력해주세요") @Size(max = 40) String code) {
	}

	record RedeemResponse(String label, Instant expiresAt) {
	}

}
