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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
				.body(new RedeemResponse(code.getExpiresAt())))
			.orElseGet(() -> ResponseEntity.badRequest()
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 초대코드입니다.")));
	}

	/**
	 * 초대 링크 (https://minunglee.cloud/i/코드). 취업 사이트의 포트폴리오 URL 칸 하나로 초대코드까지 전달하려고 둔다.
	 * 열면 코드를 적용하고 포트폴리오 메인으로 보낸다. 코드가 틀리거나 만료됐으면 메인에서 안내만 보인다.
	 */
	@GetMapping("/i/{code}")
	ResponseEntity<Void> redeemLink(@PathVariable String code, HttpServletRequest servletRequest) {
		RateLimitResult limit = rateLimiter.tryAcquire("redeem:" + clientKeyResolver.resolve(servletRequest),
				REDEEM_HOURLY, REDEEM_DAILY);
		if (!limit.allowed() || code.length() > 40) {
			return redirect("/?invite=invalid", null);
		}
		return inviteService.redeem(code)
			.map(found -> redirect("/", inviteCookie.issue(found, servletRequest.isSecure()).toString()))
			.orElseGet(() -> redirect("/?invite=invalid", null));
	}

	private static ResponseEntity<Void> redirect(String location, String cookie) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.SEE_OTHER)
			.header(HttpHeaders.LOCATION, location)
			// 초대 링크 주소가 다른 사이트로 넘어가지 않게
			.header("Referrer-Policy", "no-referrer");
		if (cookie != null) {
			builder.header(HttpHeaders.SET_COOKIE, cookie);
		}
		return builder.build();
	}

	@DeleteMapping("/invite")
	ResponseEntity<Void> clear(HttpServletRequest servletRequest) {
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, inviteCookie.clear(servletRequest.isSecure()).toString())
			.build();
	}

	record RedeemRequest(@NotBlank(message = "초대코드를 입력해주세요") @Size(max = 40) String code) {
	}

	/** 코드 이름(회사명)은 방문자에게 돌려주지 않는다 */
	record RedeemResponse(Instant expiresAt) {
	}

}
