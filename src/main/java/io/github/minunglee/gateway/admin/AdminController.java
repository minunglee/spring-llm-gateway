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

	@GetMapping("/status")
	StatusResponse status() {
		return new StatusResponse(budget(Tier.GUEST), budget(Tier.INVITE), killSwitch.isOn());
	}

	@PutMapping("/killswitch")
	StatusResponse killSwitch(@RequestBody KillSwitchRequest request) {
		killSwitch.set(request.enabled());
		return status();
	}

	private Budget budget(Tier tier) {
		return new Budget(CostCalculator.formatUsd(budgetGuard.usedTodayMicroUsd(tier)),
				CostCalculator.formatUsd(budgetGuard.limitMicroUsd(tier)));
	}

	record KillSwitchRequest(boolean enabled) {
	}

	record Budget(String usedToday, String limit) {
	}

	record StatusResponse(Budget guestBudget, Budget inviteBudget, boolean killSwitch) {
	}

}
