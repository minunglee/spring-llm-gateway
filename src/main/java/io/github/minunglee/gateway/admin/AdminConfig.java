package io.github.minunglee.gateway.admin;

import java.time.Clock;

import io.github.minunglee.gateway.invite.InviteProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
class AdminConfig {

	/** 관리자 로그인 쿠키를 만들고 검증하는 객체. 초대코드 쿠키와 같은 비밀값을 쓰되 용도 문자열을 붙여 서명을 분리한다 */
	@Bean
	AdminSession adminSession(AdminProperties adminProperties, InviteProperties inviteProperties, Clock clock) {
		return new AdminSession(adminProperties, inviteProperties, clock);
	}

	/** /admin 과 그 아래 주소에만 관리자 인증 필터를 건다 (포트폴리오·체험 페이지에는 영향 없음) */
	@Bean
	FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(AdminSession adminSession) {
		FilterRegistrationBean<AdminTokenFilter> registration = new FilterRegistrationBean<>(
				new AdminTokenFilter(adminSession));
		registration.addUrlPatterns("/admin", "/admin/*");
		return registration;
	}

}
