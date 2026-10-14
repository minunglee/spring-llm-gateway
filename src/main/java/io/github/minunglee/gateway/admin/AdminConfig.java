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

	@Bean
	AdminSession adminSession(AdminProperties adminProperties, InviteProperties inviteProperties, Clock clock) {
		return new AdminSession(adminProperties, inviteProperties, clock);
	}

	@Bean
	FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(AdminSession adminSession) {
		FilterRegistrationBean<AdminTokenFilter> registration = new FilterRegistrationBean<>(
				new AdminTokenFilter(adminSession));
		registration.addUrlPatterns("/admin", "/admin/*");
		return registration;
	}

}
