package com.ldsilver.chingoohaja.domain.guardian;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.guardian.enums.RelationshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guardian_relationships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianRelationship extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_id", nullable = false)
    private User guardian;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private User senior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipStatus relationshipStatus;

    public static GuardianRelationship of(
            User guardian,
            User senior,
            RelationshipStatus relationshipStatus
    ) {
        validateRelationship(guardian, senior);

        GuardianRelationship guardianRelationship = new GuardianRelationship();
        guardianRelationship.guardian = guardian;
        guardianRelationship.senior = senior;
        guardianRelationship.relationshipStatus = relationshipStatus;
        return guardianRelationship;
    }

    public static GuardianRelationship from(User guardian, User senior) {
        return of(guardian, senior, RelationshipStatus.PENDING);
    }

    private static void validateRelationship(User guardian, User senior) {
        if (guardian.equals(senior)) {
            throw new IllegalArgumentException("보호자와 시니어는 동일한 사용자일 수 없습니다.");
        }
        if (guardian.getUserType() != UserType.GUARDIAN) {
            throw new IllegalArgumentException("보호자 역할은 GUARDIAN 타입 사용자만 가능합니다.");
        }
    }
}
