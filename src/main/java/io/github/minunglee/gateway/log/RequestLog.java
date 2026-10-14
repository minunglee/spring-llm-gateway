package io.github.minunglee.gateway.log;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * LLM 요청 1건의 기록. 사용량·비용 대시보드의 원천 데이터다.
 */
@Entity
public class RequestLog {

	public enum Status {
		SUCCESS, ERROR,
		/** 예산 소진·긴급 차단 등으로 LLM을 호출하지 않고 데모 응답을 준 경우 */
		DEGRADED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String mode;

	@Column(nullable = false)
	private String model;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	private long inputTokens;

	private long outputTokens;

	private long costMicroUsd;

	private long latencyMs;

	@Column(nullable = false)
	private Instant createdAt;

	protected RequestLog() {
	}

	public RequestLog(String mode, String model, Status status, long inputTokens, long outputTokens,
			long costMicroUsd, long latencyMs, Instant createdAt) {
		this.mode = mode;
		this.model = model;
		this.status = status;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.costMicroUsd = costMicroUsd;
		this.latencyMs = latencyMs;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getMode() {
		return mode;
	}

	public String getModel() {
		return model;
	}

	public Status getStatus() {
		return status;
	}

	public long getInputTokens() {
		return inputTokens;
	}

	public long getOutputTokens() {
		return outputTokens;
	}

	public long getCostMicroUsd() {
		return costMicroUsd;
	}

	public long getLatencyMs() {
		return latencyMs;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
