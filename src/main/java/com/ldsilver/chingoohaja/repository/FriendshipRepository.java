package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.friendship.Friendship;
import com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    // 친구 관계 조회
    @Query("SELECT f FROM Friendship f WHERE " +
            "((f.requester = :user1 AND f.addressee = :user2) OR " +
            "(f.requester = :user2 AND f.addressee = :user1)) " +
            "AND f.friendshipStatus = :status")
    Optional<Friendship> findFriendshipBetweenUsers(@Param("user1") User user1,
                                                    @Param("user2") User user2,
                                                    @Param("status") FriendshipStatus status);

    // 받은 친구 요청 조회
    List<Friendship> findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(User addressee, FriendshipStatus status);

    // 보낸 친구 요청 조회
    List<Friendship> findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(User requester, FriendshipStatus status);

    /**
     * 두 사용자 간의 관계 조회 (모든 상태)
     */
    @Query("SELECT f FROM Friendship f WHERE " +
            "((f.requester = :user1 AND f.addressee = :user2) OR " +
            "(f.requester = :user2 AND f.addressee = :user1))")
    Optional<Friendship> findFriendshipBetweenUsersAnyStatus(
            @Param("user1") User user1,
            @Param("user2") User user2
    );

    /**
     * 특정 사용자가 차단한 사용자 ID 목록
     */
    @Query("SELECT CASE " +
            "WHEN f.requester.id = :userId THEN f.addressee.id " +
            "ELSE f.requester.id END " +
            "FROM Friendship f " +
            "WHERE (f.requester.id = :userId OR f.addressee.id = :userId) " +
            "AND f.friendshipStatus = com.ldsilver.chingoohaja.domain.friendship.enums.FriendshipStatus.BLOCKED")
    List<Long> findBlockedUserIds(@Param("userId") Long userId);

}
