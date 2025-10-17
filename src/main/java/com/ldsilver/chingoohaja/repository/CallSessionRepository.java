package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallSession;
import com.ldsilver.chingoohaja.domain.call.enums.SessionStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    // ========== 기본 조회 ==========
    Optional<CallSession> findByCallAndUser(Call call, User user);

    @Query("SELECT cs FROM CallSession cs WHERE cs.call.id = :callId AND cs.user.id = :userId")
    Optional<CallSession> findByCallIdAndUserId(@Param("callId") Long callId, @Param("userId") Long userId);

    List<CallSession> findByCallOrderByCreatedAtAsc(Call call);

    @Query("SELECT cs FROM CallSession cs WHERE cs.call.id = :callId ORDER BY cs.createdAt ASC")
    List<CallSession> findByCallIdOrderByCreatedAtAsc(@Param("callId") Long callId);

    List<CallSession> findByUserAndSessionStatusOrderByCreatedAtDesc(User user, SessionStatus status);

    Optional<CallSession> findByAgoraUid(Integer agoraUid);

    // ========== 상태별 조회 ==========

    List<CallSession> findBySessionStatusOrderByCreatedAtDesc(SessionStatus status);

    @Query("SELECT COUNT(cs) FROM CallSession cs WHERE cs.call.id = :callId AND cs.sessionStatus = :status")
    long countByCallIdAndSessionStatus(@Param("callId") Long callId, @Param("status") SessionStatus status);

    @Query("SELECT cs FROM CallSession cs WHERE cs.call.id = :callId AND cs.sessionStatus = com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.JOINED")
    List<CallSession> findJoinedSessionsByCallId(@Param("callId") Long callId);

    // ========== 시간 기반 조회 ==========

    List<CallSession> findByJoinedAtAfterOrderByJoinedAtDesc(LocalDateTime after);

    @Query("SELECT cs FROM CallSession cs WHERE cs.joinedAt BETWEEN :startDate AND :endDate ORDER BY cs.joinedAt DESC")
    List<CallSession> findByJoinedAtBetween(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT cs FROM CallSession cs WHERE cs.sessionStatus = com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.READY AND cs.createdAt < :expiredTime")
    List<CallSession> findExpiredSessions(@Param("expiredTime") LocalDateTime expiredTime);

    // ========== 통계 쿼리 ==========

    /**
     * 사용자별 세션 통계
     */
    @Query(value = "SELECT cs.user_id, COUNT(*), AVG(TIMESTAMPDIFF(SECOND, cs.joined_at, cs.left_at)) " +
            "FROM call_sessions cs WHERE cs.joined_at IS NOT NULL AND cs.left_at IS NOT NULL " +
            "GROUP BY cs.user_id",
            nativeQuery = true)
    List<Object[]> getUserSessionStats();


    // ========== 업데이트 쿼리 ==========

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CallSession cs SET cs.sessionStatus = com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.EXPIRED " +
            "WHERE cs.sessionStatus = com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.READY AND cs.createdAt < :expiredTime")
    int markExpiredSessions(@Param("expiredTime") LocalDateTime expiredTime);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CallSession cs SET cs.sessionStatus = com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.LEFT, cs.leftAt = :leftAt " +
            "WHERE cs.call.id = :callId AND cs.sessionStatus = com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.JOINED")
    int endAllSessionsForCall(@Param("callId") Long callId, @Param("leftAt") LocalDateTime leftAt);

    // ========== 삭제 쿼리 ==========

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM CallSession cs WHERE cs.sessionStatus = :status AND cs.createdAt < :before")
    int deleteOldSessionsByStatus(@Param("status") SessionStatus status, @Param("before") LocalDateTime before);

    /**
     * Call이 삭제될 때 관련 세션들도 함께 삭제 (CASCADE로 자동 처리되지만 명시적 쿼리 제공)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteByCall(Call call);

    // 기존 메서드를 더 구체적으로 수정
    @Query("SELECT cs FROM CallSession cs WHERE cs.call.id = :callId AND cs.user.id = :userId " +
            "ORDER BY cs.createdAt DESC")
    List<CallSession> findByCallIdAndUserIdOrderByCreatedAtDesc(@Param("callId") Long callId,
                                                                @Param("userId") Long userId);

    // 가장 최근의 활성 세션만 조회
    @Query("SELECT cs FROM CallSession cs WHERE cs.call.id = :callId AND cs.user.id = :userId " +
            "AND cs.sessionStatus IN (com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.READY, " +
            "com.ldsilver.chingoohaja.domain.call.enums.SessionStatus.JOINED) " +
            "ORDER BY cs.createdAt DESC")
    Optional<CallSession> findActiveSessionByCallIdAndUserId(@Param("callId") Long callId,
                                                             @Param("userId") Long userId);

}
