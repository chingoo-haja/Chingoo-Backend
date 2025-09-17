package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.AgoraProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgoraCloudRecordingClient {

    @Qualifier("agoraWebClient")
    private final WebClient webClient;
    private final AgoraProperties agoraProperties;

    public Mono<String> acpireResource(String channelName) {
        log.debug("Agora Cloud Recording Resource 획득 시작 - channel: {}", channelName);

        if (!agoraProperties.isCloudRecordingConfigured()) {
            log.error("Agora Cloud Recording이 설정되지 않았습니다.");
            return Mono.error(new CustomException(ErrorCode.OAUTH_CONFIG_ERROR));
        }

        Map<String, Object> clientRequest = Map.of(
                "resourceExpiredHour", 24
        );

        return webClient.post()
                .uri("/v1/apps/{appid}/cloud_recording/acquire", agoraProperties.getAppId())
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(Map.of("cname", channelName, "uid", "0", "clientRequest", clientRequest))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String resourceId = (String) response.get("resourceId");
                    if (resourceId == null || resourceId.trim().isEmpty()) {
                        throw new CustomException(ErrorCode.INVALID_RESOURCE_IN);
                    }
                    log.debug("Resource 획득 성공 - resourceId: {}", maskSensitiveData(resourceId));
                    return resourceId;
                })
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    private String createBasicAuthHeader() {
        String credentials = agoraProperties.getCustomerId() + ":" + agoraProperties.getCustomerSecret();
        byte[] encodedCredentials = Base64.getEncoder().encode(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encodedCredentials);
    }

    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 8) {
            return "***";
        }
        return data.substring(0, 4) + "***" + data.substring(data.length() - 4);
    }

    private CustomException mapWebClientException(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        log.error("Agora API 호출 실패 - 상태코드: {}, 응답: {}", ex.getStatusCode(),
                body.length() > 200 ? body.substring(0, 200) + "..." : body);

        return switch (ex.getStatusCode().value()) {
            case 400 -> new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            case 401 -> new CustomException(ErrorCode.AGORA_UNAUTHORIZED);
            case 403 -> new CustomException(ErrorCode.ACCESS_DENIED);
            case 404 -> new CustomException(ErrorCode.RECORDING_RESOURCE_NOT_FOUND);
            case 429 -> new CustomException(ErrorCode.AGORA_REQUEST_EXCEEDED);
            default -> new CustomException(ErrorCode.AGORA_REQUEST_FAILED);
        };
    }
}
