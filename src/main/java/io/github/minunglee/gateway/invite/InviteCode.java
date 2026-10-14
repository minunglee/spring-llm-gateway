package io.github.minunglee.gateway.invite;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 초대코드. 지원하는 회사마다 하나씩 발급해 누가 언제 열람했는지 알 수 있게 한다.
 */
@Entity
public class InviteCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String codeHash;

	@Column(nullable = false)
	private String label;

	private int dailyLimit;

	@Column(nullable = false)
	private Instant expiresAt;

	private boolean revoked;

	@Column(nullable = false)
	private Instant createdAt;

	protected InviteCode() {
	}

	public InviteCode(String codeHash, String label, int dailyLimit, Instant expiresAt, Instant createdAt) {
		this.codeHash = codeHash;
		this.label = label;
		this.dailyLimit = dailyLimit;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public boolean isActive(Instant now) {
		return !revoked && now.isBefore(expiresAt);
	}

	public void revoke() {
		this.revoked = true;
	}

	public Long getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public int getDailyLimit() {
		return dailyLimit;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isRevoked() {
		return revoked;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
