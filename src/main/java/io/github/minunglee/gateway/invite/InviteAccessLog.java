package io.github.minunglee.gateway.invite;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class InviteAccessLog {

	public enum Action {

		/** 초대코드 입력 */
		REDEEM,

		/** 이력서·경력기술서 열람 */
		RESUME_VIEW,

		/** 이력서 PDF 다운로드 */
		RESUME_PDF

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long inviteCodeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Action action;

	@Column(nullable = false)
	private Instant accessedAt;

	protected InviteAccessLog() {
	}

	public InviteAccessLog(long inviteCodeId, Action action, Instant accessedAt) {
		this.inviteCodeId = inviteCodeId;
		this.action = action;
		this.accessedAt = accessedAt;
	}

	public long getInviteCodeId() {
		return inviteCodeId;
	}

	public Action getAction() {
		return action;
	}

	public Instant getAccessedAt() {
		return accessedAt;
	}

}
