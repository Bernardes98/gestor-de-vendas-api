package com.gestordevendas.api.invite;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserInviteRepository extends JpaRepository<UserInvite, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvite i join fetch i.company where i.tokenHash = :tokenHash")
    Optional<UserInvite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
