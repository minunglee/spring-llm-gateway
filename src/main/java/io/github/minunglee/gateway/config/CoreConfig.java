package io.github.minunglee.gateway.config;

import java.time.Clock;

import io.github.minunglee.gateway.limit.LimitProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LimitProperties.class)
class CoreConfig {

	/** 시간 의존 로직(사용량 제한, 일일 예산)을 테스트에서 제어할 수 있도록 주입한다 */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
