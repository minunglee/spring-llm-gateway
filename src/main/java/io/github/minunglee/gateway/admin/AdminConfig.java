package io.github.minunglee.gateway.admin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
class AdminConfig {

	@Bean
	FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(AdminProperties properties) {
		FilterRegistrationBean<AdminTokenFilter> registration = new FilterRegistrationBean<>(
				new AdminTokenFilter(properties.token()));
		registration.addUrlPatterns("/admin/*");
		return registration;
	}

}
