package io.github.minunglee.gateway.pricing;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CostCalculatorTest {

	private final CostCalculator calculator = new CostCalculator(new PricingProperties(Map.of("claude-haiku-5-5",
			new PricingProperties.ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50")))));

	@Test
	void 토큰_사용량을_마이크로달러로_환산한다() {
		// 입력 1,000 × $0.10 + 출력 500 × $0.50 (100만 토큰당) = 100 + 250 마이크로달러
		assertThat(calculator.costMicroUsd("claude-haiku-5-5", 1_000, 500)).isEqualTo(350);
	}

	@Test
	void 마이크로달러_미만은_올림한다() {
		// 입력 1토큰 × $0.10 = 0.1 마이크로달러 → 1
		assertThat(calculator.costMicroUsd("claude-haiku-5-5", 1, 0)).isEqualTo(1);
	}

	@Test
	void 단가가_없는_모델은_거부한다() {
		assertThatIllegalArgumentException().isThrownBy(() -> calculator.costMicroUsd("unknown-model", 1, 1));
	}

	@Test
	void 달러_표기로_변환한다() {
		assertThat(CostCalculator.formatUsd(350)).isEqualTo("$0.00035");
		assertThat(CostCalculator.formatUsd(1_000_000)).isEqualTo("$1");
		assertThat(CostCalculator.formatUsd(0)).isEqualTo("$0");
	}

}
