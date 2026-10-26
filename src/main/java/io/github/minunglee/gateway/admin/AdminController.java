package io.github.minunglee.gateway.admin;

import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.KillSwitch;
import io.github.minunglee.gateway.limit.Tier;
import io.github.minunglee.gateway.pricing.CostCalculator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
class AdminController {

	private final BudgetGuard budgetGuard;

	private final KillSwitch killSwitch;

	AdminController(BudgetGuard budgetGuard, KillSwitch killSwitch) {
		this.budgetGuard = budgetGuard;
		this.killSwitch = killSwitch;
	}

	/** GET /admin/status: 게스트·초대코드 등급의 오늘 예산 사용액과 긴급 차단 상태 (스크립트·CLI용 JSON) */
	@GetMapping("/status")
	StatusResponse status() {
		return new StatusResponse(budget(Tier.GUEST), budget(Tier.INVITE), killSwitch.isOn());
	}

	/** PUT /admin/killswitch: 긴급 차단을 켜거나 끈다. 켜면 모든 AI 호출이 멈추고 데모 응답만 나간다 */
	@PutMapping("/killswitch")
	StatusResponse killSwitch(@RequestBody KillSwitchRequest request) {
		killSwitch.set(request.enabled());
		return status();
	}

	/** 등급의 오늘 사용액과 하루 한도를 "$0.12" 형태 문자열로 */
	private Budget budget(Tier tier) {
		return new Budget(CostCalculator.formatUsd(budgetGuard.usedTodayMicroUsd(tier)),
				CostCalculator.formatUsd(budgetGuard.limitMicroUsd(tier)));
	}

	/** @param enabled true면 긴급 차단을 켠다 */
	record KillSwitchRequest(boolean enabled) {
	}

	/** @param usedToday 오늘 쓴 금액, limit 하루 한도 (둘 다 $ 표기) */
	record Budget(String usedToday, String limit) {
	}

	/** 관리 API 응답: 게스트·초대코드 예산과 긴급 차단 여부 */
	record StatusResponse(Budget guestBudget, Budget inviteBudget, boolean killSwitch) {
	}

}
