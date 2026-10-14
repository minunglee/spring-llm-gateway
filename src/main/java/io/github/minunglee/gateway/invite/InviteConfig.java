package io.github.minunglee.gateway.invite;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InviteProperties.class)
class InviteConfig {

}
