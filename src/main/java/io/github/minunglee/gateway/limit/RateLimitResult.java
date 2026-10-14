package io.github.minunglee.gateway.limit;

import java.time.Duration;

public record RateLimitResult(boolean allowed, int remainingThisHour, int remainingToday, Duration retryAfter) {

	public boolean dailyLimitReached() {
		return remainingToday == 0 && !allowed;
	}

}
