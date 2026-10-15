package io.github.minunglee.gateway.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurnstileVerifierTest {

	@Test
	void 키가_없으면_꺼진_상태로_모두_통과시킨다() {
		TurnstileVerifier verifier = new TurnstileVerifier(new TurnstileVerifier.Properties("", ""));

		assertThat(verifier.enabled()).isFalse();
		assertThat(verifier.verify(null, "1.2.3.4")).isTrue();
	}

	@Test
	void 켜진_상태에서_토큰이_비어있으면_Cloudflare에_묻지_않고_거부한다() {
		TurnstileVerifier verifier = new TurnstileVerifier(new TurnstileVerifier.Properties("site", "secret"));

		assertThat(verifier.enabled()).isTrue();
		assertThat(verifier.verify("", "1.2.3.4")).isFalse();
	}

}
