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

    // 사용자의 친구 목록 조회
    @Query("SELECT CASE WHEN f.requester = :user THEN f.addressee ELSE f.requester END " +
            "FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) " +
            "AND f.friendshipStatus = 'ACCEPTED'")
    List<User> findFriendsByUser(@Param("user") User user);

    // 받은 친구 요청 조회
    List<Friendship> findByAddresseeAndFriendshipStatusOrderByCreatedAtDesc(User addressee, FriendshipStatus status);

    // 보낸 친구 요청 조회
    List<Friendship> findByRequesterAndFriendshipStatusOrderByCreatedAtDesc(User requester, FriendshipStatus status);

}
