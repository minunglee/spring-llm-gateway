package io.github.minunglee.gateway.invite;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

	Optional<InviteCode> findByCodeHash(String codeHash);

}
