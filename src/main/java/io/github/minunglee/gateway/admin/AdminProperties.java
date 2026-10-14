package io.github.minunglee.gateway.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param token 관리자 API 토큰. 비어 있으면 관리자 API를 모두 막는다
 */
@ConfigurationProperties("gateway.admin")
public record AdminProperties(String token) {
}
