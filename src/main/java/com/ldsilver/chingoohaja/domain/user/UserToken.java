package com.ldsilver.chingoohaja.domain.user;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserToken extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private String deviceInfo;

    @Column(nullable = false)
    private Boolean isActive;

    public static UserToken of(
            User user,
            String refreshToken,
            LocalDateTime expiresAt,
            String deviceInfo,
            Boolean isActive
    ) {
        UserToken userToken = new UserToken();
        userToken.user = user;
        userToken.refreshToken = refreshToken;
        userToken.expiresAt = expiresAt;
        userToken.deviceInfo = deviceInfo;
        userToken.isActive = isActive;
        return userToken;
    }

    public static UserToken from (User user) {
        return of(user, null, null, null, true);
    }
}
