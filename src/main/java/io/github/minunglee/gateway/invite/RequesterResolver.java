package io.github.minunglee.gateway.invite;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.limit.ClientKeyResolver;
import io.github.minunglee.gateway.limit.LimitProperties;
import io.github.minunglee.gateway.limit.Requester;
import io.github.minunglee.gateway.limit.Tier;

import org.springframework.stereotype.Component;

/**
 * 요청자의 등급을 정한다. 유효한 초대코드 쿠키가 있으면 INVITE, 없으면 GUEST.
 */
@Component
public class RequesterResolver {

	private final InviteCookie inviteCookie;

	private final InviteService inviteService;

	private final ClientKeyResolver clientKeyResolver;

	private final LimitProperties limitProperties;

	RequesterResolver(InviteCookie inviteCookie, InviteService inviteService, ClientKeyResolver clientKeyResolver,
			LimitProperties limitProperties) {
		this.inviteCookie = inviteCookie;
		this.inviteService = inviteService;
		this.clientKeyResolver = clientKeyResolver;
		this.limitProperties = limitProperties;
	}

	public Requester resolve(HttpServletRequest request) {
		Optional<InviteCode> invite = inviteCookie.read(request).flatMap(inviteService::findActive);
		if (invite.isPresent()) {
			InviteCode code = invite.get();
			LimitProperties.TierPolicy policy = limitProperties.invite();
			return new Requester(Tier.INVITE, "invite:" + code.getId(), code.getId(), code.getLabel(),
					policy.model(), policy.hourly(), code.getDailyLimit());
		}
		LimitProperties.TierPolicy policy = limitProperties.guest();
		return new Requester(Tier.GUEST, "ip:" + clientKeyResolver.resolve(request), null, null, policy.model(),
				policy.hourly(), policy.daily());
	}

}
