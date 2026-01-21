package com.ldsilver.chingoohaja.domain.user;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentChannel;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents",
        indexes = {
            @Index(name = "idx_user_consent_type", columnList = "user_id, consent_type"),
            @Index(name = "idx_user_agreed_at", columnList = "user_id, agreed_at")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_user_consent_type_active",
                    columnNames = {"user_id", "consent_type"}
            )
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ConsentType consentType;

    @Column(nullable = false)
    private Boolean agreed;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    @Column(nullable = true)
    private LocalDateTime withdrawnAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentChannel channel;

    @Column(nullable = false)
    private Boolean isActive = true;

    public static UserConsent of(
            User user,
            ConsentType consentType,
            Boolean agreed,
            String version,
            ConsentChannel channel
    ) {
        UserConsent consent = new UserConsent();
        consent.user = user;
        consent.consentType = consentType;
        consent.agreed = agreed;
        consent.version = version;
        consent.agreedAt = LocalDateTime.now();
        consent.channel = channel;
        consent.isActive = true;
        return consent;
    }

    public void withdraw() {
        this.agreed = false;
        this.withdrawnAt = LocalDateTime.now();
        this.isActive = false;
    }

    public boolean isWithdrawn() {
        return this.withdrawnAt != null;
    }

    public boolean isActive() {
        return this.agreed && !isWithdrawn();
    }

    public void updateConsent(Boolean agreed, String version, ConsentChannel channel) {
        this.agreed = agreed;
        this.version = version;
        this.channel = channel;
        this.agreedAt = LocalDateTime.now();  // 업데이트 시각 갱신
        this.isActive = true;
    }


}