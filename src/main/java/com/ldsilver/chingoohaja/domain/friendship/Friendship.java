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
    @Column(nullable = false)
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

    private static void validateFriendship(User requester, User addressee) {
        if (requester.equals(addressee)) {
            throw new IllegalArgumentException("자기 자신과는 친구가 될 수 없습니다.");
        }
    }
}
