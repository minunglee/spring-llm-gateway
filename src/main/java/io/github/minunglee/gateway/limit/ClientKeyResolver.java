package io.github.minunglee.gateway.limit;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

/**
 * 요청자를 구분하는 키를 만든다. 원본 IP는 저장하지 않고 비밀값으로 HMAC 해시한 값만 쓴다.
 * <p>
 * 클라이언트 IP는 Nginx가 덮어쓴 X-Forwarded-For를 Spring이 해석한 값이다
 * (server.forward-headers-strategy=framework). 클라이언트가 보낸 헤더를 그대로 믿으면
 * IP를 바꿔가며 제한을 우회할 수 있으므로 Nginx 설정에서 반드시 덮어써야 한다.
 */
@Component
public class ClientKeyResolver {

	private final SecretKeySpec key;

	public ClientKeyResolver(LimitProperties properties) {
		this.key = new SecretKeySpec(properties.ipSalt().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	public String resolve(HttpServletRequest request) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] hash = mac.doFinal(request.getRemoteAddr().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 16);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
