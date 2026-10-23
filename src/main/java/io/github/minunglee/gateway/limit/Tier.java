package io.github.minunglee.gateway.limit;

/**
 * 사용자 등급. 등급마다 모델, 사용량 한도, 일일 예산이 다르다.
 */
public enum Tier {

	/** 초대코드 없이 들어온 방문자. IP 기준으로 횟수를 센다 */
	GUEST,

	/** 초대코드를 입력한 방문자 (면접관 등). 코드 기준으로 횟수를 센다 */
	INVITE,

	/** 다른 서비스(jipdon.com)가 내부 API로 보낸 요청. 그 서비스가 넘겨준 최종 사용자 키 기준으로 센다 */
	JIPDON

}
