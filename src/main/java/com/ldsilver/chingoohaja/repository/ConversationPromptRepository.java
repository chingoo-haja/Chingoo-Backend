package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.ConversationPrompt;
import com.ldsilver.chingoohaja.domain.category.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationPromptRepository extends JpaRepository<ConversationPrompt, Long> {

    List<ConversationPrompt> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(Category category);

    List<ConversationPrompt> findByCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long categoryId);

    List<ConversationPrompt> findByDifficultyAndIsActiveTrueOrderByDisplayOrderAsc(Integer difficulty);

    // 특정 카테고리의 랜덤 질문 조회
    @Query("SELECT cp FROM ConversationPrompt cp " +
            "WHERE cp.isActive = true " +
            "AND cp.category.id = :categoryId " +
            "AND cp.difficulty <= :maxDifficulty " +
            "ORDER BY FUNCTION('RAND')")
    List<ConversationPrompt> findRandomPromptsByCategory(
            @Param("categoryId") Long categoryId,
            @Param("maxDifficulty") Integer maxDifficulty
    );

    // 전체 활성 질문 중 랜덤 조회
    @Query("SELECT cp FROM ConversationPrompt cp " +
            "WHERE cp.isActive = true " +
            "ORDER BY FUNCTION('RAND')")
    List<ConversationPrompt> findRandomActivePrompts();

    // 카테고리별 질문 개수 조회
    @Query("SELECT cp.category.id, COUNT(cp) FROM ConversationPrompt cp " +
            "WHERE cp.isActive = true " +
            "GROUP BY cp.category.id")
    List<Object[]> countPromptsByCategory();
}