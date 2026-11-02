package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    List<AppUser> findAllByOrderByNameAsc();
    List<AppUser> findAllByPhoneNormalizedIsNull();
    Optional<AppUser> findFirstByPhoneNormalizedAndRecipientDiscoverableTrue(String phoneNormalized);
    boolean existsByPhone(String phone);
    boolean existsByPhoneNormalized(String phoneNormalized);
    boolean existsByUpiId(String upiId);
    Optional<AppUser> findByUpiId(String upiId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.upiId = :upiId")
    Optional<AppUser> findByUpiIdForUpdate(@Param("upiId") String upiId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);
}
