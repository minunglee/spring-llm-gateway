package io.github.minunglee.gateway.limit;

public class RateLimitExceededException extends RuntimeException {

	private final RateLimitResult result;

	public RateLimitExceededException(RateLimitResult result) {
		super(result.dailyLimitReached() ? "오늘 사용 가능한 횟수를 모두 사용했습니다. 내일 다시 이용해주세요."
				: "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
		this.result = result;
	}

	public RateLimitResult result() {
		return result;
	}

}
