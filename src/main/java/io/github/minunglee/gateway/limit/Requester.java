package io.github.minunglee.gateway.limit;

/**
 * 요청한 사람. 등급과 사용량을 셀 기준 키, 적용할 한도를 담는다.
 *
 * @param tier 등급
 * @param rateKey 사용량을 세는 키 (게스트는 IP 해시, 초대코드는 코드 ID)
 * @param inviteCodeId 초대코드 ID (게스트면 null)
 * @param inviteLabel 초대코드 이름 (예: 회사명, 게스트면 null)
 * @param model 사용할 모델
 * @param hourly 시간당 한도
 * @param daily 하루 한도
 */
public record Requester(Tier tier, String rateKey, Long inviteCodeId, String inviteLabel, String model, int hourly,
		int daily) {

	public boolean invited() {
		return tier == Tier.INVITE;
	}

}
