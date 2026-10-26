package io.github.minunglee.gateway.visit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class VisitConfig implements WebMvcConfigurer {

	private final VisitInterceptor visitInterceptor;

	VisitConfig(VisitInterceptor visitInterceptor) {
		this.visitInterceptor = visitInterceptor;
	}

	/** 방문 기록 인터셉터를 포트폴리오 페이지 주소에만 건다 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(visitInterceptor).addPathPatterns(VisitInterceptor.PAGES.toArray(String[]::new));
	}

}
