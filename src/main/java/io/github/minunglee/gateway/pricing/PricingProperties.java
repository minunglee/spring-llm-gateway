package io.github.minunglee.gateway.pricing;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 모델별 단가 (USD / 100만 토큰).
 */
@ConfigurationProperties("gateway.pricing")
public record PricingProperties(Map<String, ModelPrice> models) {

	/** @param input 입력 100만 토큰당 USD, output 출력 100만 토큰당 USD */
	public record ModelPrice(BigDecimal input, BigDecimal output) {
	}

}
