package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("CategoryRepository 테스트")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private Category activeRandom;
    private Category activeGuardian;
    private Category inactiveRandom;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
        activeRandom = categoryRepository.save(Category.of("일상대화", true, CategoryType.RANDOM));
        activeGuardian = categoryRepository.save(Category.of("고민상담", true, CategoryType.GUARDIAN));
        inactiveRandom = categoryRepository.save(Category.of("비활성주제", false, CategoryType.RANDOM));
    }

    @Nested
    @DisplayName("existsByName")
    class ExistsByName {

        @Test
        @DisplayName("존재하는 이름이면 true를 반환한다")
        void givenExistingName_whenCheck_thenReturnsTrue() {
            assertThat(categoryRepository.existsByName("일상대화")).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 이름이면 false를 반환한다")
        void givenNonExistingName_whenCheck_thenReturnsFalse() {
            assertThat(categoryRepository.existsByName("없는주제")).isFalse();
        }
    }

    @Nested
    @DisplayName("findByIsActiveTrueOrderByName")
    class FindByIsActiveTrueOrderByName {

        @Test
        @DisplayName("활성 카테고리만 이름순으로 반환한다")
        void whenFind_thenReturnsActiveOnlyOrderedByName() {
            List<Category> result = categoryRepository.findByIsActiveTrueOrderByName();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("고민상담", "일상대화"); // 가나다순
            assertThat(result).noneMatch(c -> c.getName().equals("비활성주제"));
        }
    }

    @Nested
    @DisplayName("findByCategoryTypeAndIsActiveTrueOrderByName")
    class FindByCategoryTypeAndIsActiveTrue {

        @Test
        @DisplayName("활성 RANDOM 카테고리만 반환한다")
        void givenRandomType_whenFind_thenReturnsActiveRandomOnly() {
            List<Category> result = categoryRepository.findByCategoryTypeAndIsActiveTrueOrderByName(CategoryType.RANDOM);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("일상대화");
        }

        @Test
        @DisplayName("활성 GUARDIAN 카테고리만 반환한다")
        void givenGuardianType_whenFind_thenReturnsActiveGuardianOnly() {
            List<Category> result = categoryRepository.findByCategoryTypeAndIsActiveTrueOrderByName(CategoryType.GUARDIAN);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("고민상담");
        }
    }

    @Nested
    @DisplayName("findByNameAndIsActiveTrue")
    class FindByNameAndIsActiveTrue {

        @Test
        @DisplayName("활성 카테고리를 이름으로 조회한다")
        void givenActiveName_whenFind_thenReturnsCategory() {
            Optional<Category> result = categoryRepository.findByNameAndIsActiveTrue("일상대화");

            assertThat(result).isPresent();
            assertThat(result.get().getCategoryType()).isEqualTo(CategoryType.RANDOM);
        }

        @Test
        @DisplayName("비활성 카테고리는 조회되지 않는다")
        void givenInactiveName_whenFind_thenReturnsEmpty() {
            Optional<Category> result = categoryRepository.findByNameAndIsActiveTrue("비활성주제");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findRandomMatchingCategories")
    class FindRandomMatchingCategories {

        @Test
        @DisplayName("활성 RANDOM 카테고리 목록을 반환한다")
        void whenFind_thenReturnsActiveRandomCategories() {
            List<Category> result = categoryRepository.findRandomMatchingCategories();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategoryType()).isEqualTo(CategoryType.RANDOM);
        }
    }

    @Nested
    @DisplayName("getCategoryStatsByType")
    class GetCategoryStatsByType {

        @Test
        @DisplayName("활성 카테고리의 타입별 통계를 반환한다")
        void whenGetStats_thenReturnsCategoryTypeCounts() {
            List<Object[]> result = categoryRepository.getCategoryStatsByType();

            assertThat(result).hasSize(2);
        }
    }
}
