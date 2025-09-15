package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.dto.call.request.TokenRequest;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.infrastructure.agora.AgoraTokenGenerator;
import com.ldsilver.chingoohaja.repository.UserRepository;
import io.agora.media.RtcTokenBuilder2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgoraTokenService 테스트")
public class AgoraTokenServiceTest {
    @Mock
    private AgoraTokenGenerator tokenGenerator;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AgoraTokenService agoraTokenService;

    private TokenRequest validTokenRequest;
    private final Long userId = 1L;
    private final String channelName = "test_channel";
    private final String mockRtcToken = "test_rtc_token_12345";

    @BeforeEach
    void setUp() {
        validTokenRequest = TokenRequest.of(channelName, userId);
    }

    @Test
    @DisplayName("RTC Token 생성 성공")
    void generateRtcToken_Success() {
        // Given
        when(userRepository.existsById(userId)).thenReturn(true);
        when(tokenGenerator.generateRtcToken(eq(channelName), eq(userId.intValue()),
                eq(RtcTokenBuilder2.Role.ROLE_PUBLISHER), eq(3600))).thenReturn(mockRtcToken);

        // When
        TokenResponse response = agoraTokenService.generateRtcToken(validTokenRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.rtcToken()).isEqualTo(mockRtcToken);
        assertThat(response.channelName()).isEqualTo(channelName);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo("PUBLISHER");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());

        verify(userRepository).existsById(userId);
        verify(tokenGenerator).generateRtcToken(channelName, userId.intValue(), RtcTokenBuilder2.Role.ROLE_PUBLISHER, 3600);
    }
}
