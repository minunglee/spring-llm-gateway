package io.github.minunglee.gateway.limit;

import java.math.BigDecimal;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용량 제한과 예산 설정.
 *
 * @param ipSalt IP를 해시할 때 쓰는 비밀값. 원본 IP는 저장하지 않는다
 * @param zone 하루 경계를 정하는 시간대
 * @param guest 게스트 등급 정책
 * @param invite 초대코드 등급 정책
 */
@ConfigurationProperties("gateway.limit")
public record LimitProperties(String ipSalt, ZoneId zone, TierPolicy guest, TierPolicy invite) {

	public TierPolicy policy(Tier tier) {
		return switch (tier) {
			case GUEST -> guest;
			case INVITE -> invite;
		};
	}

	/**
	 * @param model 사용할 Claude 모델
	 * @param hourly 시간당 요청 수
	 * @param daily 하루 요청 수 (초대코드는 코드별 한도가 우선)
	 * @param dailyBudgetUsd 이 등급 전체의 하루 예산 (USD)
	 */
	public record TierPolicy(String model, int hourly, int daily, BigDecimal dailyBudgetUsd) {

		public long dailyBudgetMicroUsd() {
			return dailyBudgetUsd.movePointRight(6).longValueExact();
		}

	}

}
