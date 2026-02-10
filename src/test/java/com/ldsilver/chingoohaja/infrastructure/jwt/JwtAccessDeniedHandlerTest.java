package com.ldsilver.chingoohaja.infrastructure.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAccessDeniedHandler 테스트")
class JwtAccessDeniedHandlerTest {

    private JwtAccessDeniedHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new JwtAccessDeniedHandler(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("접근 거부 시 403 상태 코드와 JSON 에러 응답을 반환한다")
    void givenAccessDenied_whenHandle_thenReturns403WithJson() throws Exception {
        // given
        request.setMethod("GET");
        request.setRequestURI("/api/admin/users");
        AccessDeniedException exception = new AccessDeniedException("Access Denied");

        // when
        handler.handle(request, response, exception);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString()).contains("A002");
    }
}
