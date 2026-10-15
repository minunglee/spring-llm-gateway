package io.github.minunglee.gateway.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 요청 하나가 게이트웨이를 지나며 거친 단계 기록. 체험 화면에 "게이트웨이가 방금 한 일"로 보여준다.
 * <p>
 * 사용자가 단순한 챗봇이 아니라 AI 호출 앞단에서 사용량·예산을 통제하는 서버라는 것을 직접 볼 수 있게 하려는 용도다.
 */
public final class Trace {

	public enum Status {

		/** 통과 */
		OK,

		/** 여기서 막힘 (이후 단계는 실행하지 않음) */
		BLOCKED,

		/** 이 요청에는 해당 없음 */
		SKIPPED

	}

	/**
	 * @param name 단계 이름
	 * @param status 결과
	 * @param detail 사람이 읽을 설명
	 * @param tech 사용한 기술
	 * @param elapsed 걸린 시간 (측정하지 않은 단계는 null)
	 */
	public record Step(String name, Status status, String detail, String tech, String elapsed) {
	}

	private final List<Step> steps = new ArrayList<>();

	public void ok(String name, String detail, String tech, long startNanos) {
		steps.add(new Step(name, Status.OK, detail, tech, since(startNanos)));
	}

	public void blocked(String name, String detail, String tech, long startNanos) {
		steps.add(new Step(name, Status.BLOCKED, detail, tech, startNanos == 0 ? null : since(startNanos)));
	}

	public void skipped(String name, String detail, String tech) {
		steps.add(new Step(name, Status.SKIPPED, detail, tech, null));
	}

	public List<Step> steps() {
		return List.copyOf(steps);
	}

	private static String since(long startNanos) {
		double ms = (System.nanoTime() - startNanos) / 1_000_000.0;
		return ms < 10 ? String.format("%.1fms", ms) : String.format("%,dms", Math.round(ms));
	}

}
