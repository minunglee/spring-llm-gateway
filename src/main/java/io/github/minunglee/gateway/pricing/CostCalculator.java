package io.github.minunglee.gateway.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토큰 사용량을 비용으로 환산한다.
 * <p>
 * 비용은 부동소수점 오차를 피하려고 마이크로달러(1 USD = 1,000,000) 정수로 다룬다.
 * 단가가 "USD / 100만 토큰"이므로 {@code 토큰 수 × 단가}가 곧 마이크로달러 값이 된다.
 */
@Component
@EnableConfigurationProperties(PricingProperties.class)
public class CostCalculator {

	private final PricingProperties pricing;

	public CostCalculator(PricingProperties pricing) {
		this.pricing = pricing;
	}

	public long costMicroUsd(String model, long inputTokens, long outputTokens) {
		PricingProperties.ModelPrice price = pricing.models().get(model);
		if (price == null) {
			throw new IllegalArgumentException("단가가 등록되지 않은 모델입니다: " + model);
		}
		BigDecimal cost = price.input()
			.multiply(BigDecimal.valueOf(inputTokens))
			.add(price.output().multiply(BigDecimal.valueOf(outputTokens)));
		// 예산 계산이 실제보다 작게 잡히지 않도록 올림
		return cost.setScale(0, RoundingMode.CEILING).longValueExact();
	}

	public static String formatUsd(long microUsd) {
		return "$" + BigDecimal.valueOf(microUsd, 6).stripTrailingZeros().toPlainString();
	}

}
