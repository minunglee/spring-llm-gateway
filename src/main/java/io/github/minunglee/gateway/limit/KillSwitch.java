package io.github.minunglee.gateway.limit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 긴급 차단 스위치. 켜면 모든 외부 LLM 호출을 멈추고 데모 응답만 돌려준다.
 * Redis에 두어 앱 재시작 없이, 여러 인스턴스에 동시에 적용된다.
 */
@Component
public class KillSwitch {

	/** 긴급 차단 상태를 담는 Redis 키 (값 "1"이면 켜짐) */
	private static final String KEY = "gateway:killswitch";

	private final StringRedisTemplate redis;

	public KillSwitch(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 긴급 차단이 켜져 있는지 */
	public boolean isOn() {
		return "1".equals(redis.opsForValue().get(KEY));
	}

	/** 켜면 키를 쓰고, 끄면 키를 지운다 */
	public void set(boolean on) {
		if (on) {
			redis.opsForValue().set(KEY, "1");
		}
		else {
			redis.delete(KEY);
		}
	}

}
