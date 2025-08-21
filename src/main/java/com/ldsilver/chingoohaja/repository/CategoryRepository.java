package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByName(String name);

    List<Category> findByIsActiveOrderByName(boolean isActive);

    List<Category> findByIsActiveTrueOrderByName();
    List<Category> findAllByOrderByName();
    List<Category> findByCategoryTypeAndIsActiveTrueOrderByName(CategoryType categoryType);
    Optional<Category> findByNameAndIsActiveTrue(String name);

    default List<Category> findRandomMatchingCategories() {
        return findByCategoryTypeAndIsActiveTrueOrderByName(CategoryType.RANDOM);
    }

    default List<Category> findGuardianCategories() {
        return findByCategoryTypeAndIsActiveTrueOrderByName(CategoryType.GUARDIAN);
    }

    // 카테고리 통계 (관리자용)
    @Query("SELECT c.categoryType, COUNT(c) FROM Category c WHERE c.isActive = true GROUP BY c.categoryType")
    List<Object[]> getCategoryStatsByType();
}
