package io.github.minunglee.gateway.limit;

import java.time.Duration;

public record RateLimitResult(boolean allowed, int remainingThisHour, int remainingToday, Duration retryAfter) {

	/** 하루 한도 때문에 막혔는지 (시간당 한도로 막힌 것과 안내 문구를 다르게 하려고) */
	public boolean dailyLimitReached() {
		return remainingToday == 0 && !allowed;
	}

}
