package io.github.minunglee.gateway.limit;

import java.math.BigDecimal;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용량 제한과 예산 설정.
 *
 * @param hourlyPerIp IP당 시간당 요청 수
 * @param dailyPerIp IP당 하루 요청 수
 * @param globalDailyBudgetUsd 서비스 전체 하루 예산 (USD)
 * @param ipSalt IP를 해시할 때 쓰는 비밀값. 원본 IP는 저장하지 않는다
 * @param zone 하루 경계를 정하는 시간대
 */
@ConfigurationProperties("gateway.limit")
public record LimitProperties(int hourlyPerIp, int dailyPerIp, BigDecimal globalDailyBudgetUsd, String ipSalt,
		ZoneId zone) {

	public long globalDailyBudgetMicroUsd() {
		return globalDailyBudgetUsd.movePointRight(6).longValueExact();
	}

}
