package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import com.ldsilver.chingoohaja.dto.category.response.CategoryResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("CategoryController 테스트")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private CategoryResponse createCategoryResponse(Long id, String name, boolean isActive, CategoryType type) {
        return new CategoryResponse(id, name, isActive, type,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    @Nested
    @DisplayName("GET /api/v1/categories - 카테고리 목록 조회")
    class GetCategories {

        @Test
        @DisplayName("전체 카테고리 목록을 조회한다")
        void getCategories_whenNoFilter_thenReturnsAllCategories() throws Exception {
            // given
            List<CategoryResponse> categories = List.of(
                    createCategoryResponse(1L, "일상", true, CategoryType.RANDOM),
                    createCategoryResponse(2L, "고민상담", true, CategoryType.RANDOM),
                    createCategoryResponse(3L, "비활성", false, CategoryType.RANDOM)
            );
            given(categoryService.getAllCategories(null)).willReturn(categories);

            // when & then
            mockMvc.perform(get("/api/v1/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.message").value("전체 카테고리 조회 성공"));
        }

        @Test
        @DisplayName("활성 카테고리만 필터링하여 조회한다")
        void getCategories_whenFilterActive_thenReturnsActiveOnly() throws Exception {
            // given
            List<CategoryResponse> activeCategories = List.of(
                    createCategoryResponse(1L, "일상", true, CategoryType.RANDOM),
                    createCategoryResponse(2L, "고민상담", true, CategoryType.RANDOM)
            );
            given(categoryService.getAllCategories(true)).willReturn(activeCategories);

            // when & then
            mockMvc.perform(get("/api/v1/categories")
                            .param("is_active", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.message").value("활성 카테고리 조회 성공"));
        }

        @Test
        @DisplayName("비활성 카테고리만 필터링하여 조회한다")
        void getCategories_whenFilterInactive_thenReturnsInactiveOnly() throws Exception {
            // given
            List<CategoryResponse> inactiveCategories = List.of(
                    createCategoryResponse(3L, "비활성", false, CategoryType.RANDOM)
            );
            given(categoryService.getAllCategories(false)).willReturn(inactiveCategories);

            // when & then
            mockMvc.perform(get("/api/v1/categories")
                            .param("is_active", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.message").value("비활성 카테고리 조회 성공"));
        }

        @Test
        @DisplayName("카테고리가 없는 경우 빈 목록을 반환한다")
        void getCategories_whenEmpty_thenReturnsEmptyList() throws Exception {
            // given
            given(categoryService.getAllCategories(any())).willReturn(Collections.emptyList());

            // when & then
            mockMvc.perform(get("/api/v1/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/categories/active - 활성 카테고리 목록 조회")
    class GetActiveCategories {

        @Test
        @DisplayName("활성 카테고리 목록을 조회한다")
        void getActiveCategories_thenReturnsActiveCategories() throws Exception {
            // given
            List<CategoryResponse> activeCategories = List.of(
                    createCategoryResponse(1L, "일상", true, CategoryType.RANDOM),
                    createCategoryResponse(2L, "고민상담", true, CategoryType.RANDOM)
            );
            given(categoryService.getActiveCategories()).willReturn(activeCategories);

            // when & then
            mockMvc.perform(get("/api/v1/categories/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("일상"))
                    .andExpect(jsonPath("$.message").value("활성 카테고리 조회 성공"));
        }

        @Test
        @DisplayName("활성 카테고리가 없는 경우 빈 목록을 반환한다")
        void getActiveCategories_whenEmpty_thenReturnsEmptyList() throws Exception {
            // given
            given(categoryService.getActiveCategories()).willReturn(Collections.emptyList());

            // when & then
            mockMvc.perform(get("/api/v1/categories/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }
}
