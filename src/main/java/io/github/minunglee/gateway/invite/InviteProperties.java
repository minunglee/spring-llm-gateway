package io.github.minunglee.gateway.invite;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param cookieSecret 초대코드 쿠키 서명용 비밀값
 * @param cookieMaxAge 코드를 한 번 입력하면 기억하는 기간 (코드 만료일을 넘지는 않는다)
 */
@ConfigurationProperties("gateway.invite")
public record InviteProperties(String cookieSecret, Duration cookieMaxAge) {
}
