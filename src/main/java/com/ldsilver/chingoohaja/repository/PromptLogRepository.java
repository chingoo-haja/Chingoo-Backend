package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.PromptLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptLogRepository extends JpaRepository<PromptLog, Long> {

    List<PromptLog> findByCallIdOrderByDisplayedAtDesc(Long callId);

    @Query("SELECT pl.prompt.id FROM PromptLog pl " +
            "WHERE pl.call.id = :callId")
    List<Long> findDisplayedPromptIdsByCallId(@Param("callId") Long callId);

    // 효과 분석용
    @Query("SELECT pl.prompt.id, COUNT(pl), " +
            "SUM(CASE WHEN pl.wasHelpful = true THEN 1 ELSE 0 END) " +
            "FROM PromptLog pl " +
            "WHERE pl.wasHelpful IS NOT NULL " +
            "GROUP BY pl.prompt.id")
    List<Object[]> getPromptEffectivenessStats();

    @Query("SELECT pl FROM PromptLog pl " +
            "WHERE pl.call.id = :callId " +
            "AND pl.isCurrentlyDisplayed = true " +
            "ORDER BY pl.displayedAt DESC")
    Optional<PromptLog> findCurrentPromptByCallId(@Param("callId") Long callId);

}