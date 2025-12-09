package com.ldsilver.chingoohaja.domain.friendship;

import com.ldsilver.chingoohaja.domain.common.BaseEntity;
import com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    private FriendshipStatus friendshipStatus;

    public static Friendship of(User requester, User addressee, FriendshipStatus friendshipStatus) {
        validateFriendship(requester, addressee);

        Friendship friendship = new Friendship();
        friendship.requester = requester;
        friendship.addressee = addressee;
        friendship.friendshipStatus = friendshipStatus;
        return friendship;
    }

    public static Friendship from(User requester, User addressee) {
        return of(requester, addressee, FriendshipStatus.PENDING);
    }

    // ========== 도메인 비즈니스 규칙 (상태 전환) ==========

    /**
     * 친구 요청 수락
     * 도메인 규칙: PENDING 상태에서만 ACCEPTED로 전환 가능
     */
    public void accept() {
        validateStatusTransition(FriendshipStatus.PENDING, "수락");
        this.friendshipStatus = FriendshipStatus.ACCEPTED;
    }

    /**
     * 친구 요청 거절
     * 도메인 규칙: PENDING 상태에서만 REJECTED로 전환 가능
     */
    public void reject() {
        validateStatusTransition(FriendshipStatus.PENDING, "거절");
        this.friendshipStatus = FriendshipStatus.REJECTED;
    }

    /**
     * 친구 차단
     * 도메인 규칙: REJECTED, BLOCKED 상태가 아닐 때만 차단 가능
     */
    public void block() {
        if (this.friendshipStatus == FriendshipStatus.REJECTED ||
                this.friendshipStatus == FriendshipStatus.BLOCKED) {
            throw new IllegalStateException(
                    String.format("현재 상태(%s)에서는 차단할 수 없습니다.", this.friendshipStatus)
            );
        }
        this.friendshipStatus = FriendshipStatus.BLOCKED;
    }

    /**
     * 친구 삭제 (소프트 삭제)
     * 도메인 규칙: ACCEPTED 상태에서만 DELETED로 전환 가능
     */
    public void delete() {
        validateStatusTransition(FriendshipStatus.ACCEPTED, "삭제");
        this.friendshipStatus = FriendshipStatus.DELETED;
    }

    // ========== 도메인 상태 확인 (상태 조회만) ==========

    public boolean isPending() {
        return this.friendshipStatus == FriendshipStatus.PENDING;
    }

    public boolean isAccepted() {
        return this.friendshipStatus == FriendshipStatus.ACCEPTED;
    }

    public boolean isRejected() {
        return this.friendshipStatus == FriendshipStatus.REJECTED;
    }

    public boolean isBlocked() {
        return this.friendshipStatus == FriendshipStatus.BLOCKED;
    }

    public boolean isDeleted() {
        return this.friendshipStatus == FriendshipStatus.DELETED;
    }

    /**
     * 활성 상태인지 확인 (프론트에서 보여줄 친구인지)
     */
    public boolean isActive() {
        return this.friendshipStatus == FriendshipStatus.ACCEPTED;
    }

    // ========== 도메인 제약사항 검증 ==========

    /**
     * 상태 전환 규칙 검증 (도메인 제약사항)
     */
    private void validateStatusTransition(FriendshipStatus requiredStatus, String action) {
        if (this.friendshipStatus != requiredStatus) {
            throw new IllegalStateException(
                    String.format("%s 상태의 친구 요청만 %s할 수 있습니다. 현재 상태: %s",
                            requiredStatus, action, this.friendshipStatus)
            );
        }
    }

    /**
     * 친구 관계 생성 제약사항 검증
     */
    private static void validateFriendship(User requester, User addressee) {
        if (requester.equals(addressee)) {
            throw new IllegalArgumentException("자기 자신과는 친구가 될 수 없습니다.");
        }
    }

    // ========== equals & hashCode ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Friendship that = (Friendship) obj;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
