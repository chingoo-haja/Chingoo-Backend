package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.UserConsent;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUserOrderByAgreedAtDesc(User user);

    Optional<UserConsent> findByUserAndConsentTypeAndWithdrawnAtIsNull(User user, ConsentType consentType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserConsent uc WHERE uc.user = :user " +
            "AND uc.consentType = :consentType " +
            "AND uc.isActive = true")
    Optional<UserConsent> findActiveConsentByUserAndType(
            @Param("user") User user,
            @Param("consentType") ConsentType consentType
    );

    @Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END " +
            "FROM UserConsent uc WHERE uc.user = :user AND uc.consentType = :consentType " +
            "AND uc.agreed = true AND uc.withdrawnAt IS NULL")
    boolean hasActiveConsent(
            @Param("user") User user,
            @Param("consentType") ConsentType consentType
    );

    List<UserConsent> findByUserAndConsentType(User user, ConsentType consentType);
}