package io.github.minunglee.gateway.invite;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteAccessLogRepository extends JpaRepository<InviteAccessLog, Long> {

	List<InviteAccessLog> findByInviteCodeIdOrderByAccessedAtDesc(long inviteCodeId);

}
