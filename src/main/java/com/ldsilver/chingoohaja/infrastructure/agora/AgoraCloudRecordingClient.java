package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.AgoraProperties;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
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
    private final AgoraTokenGenerator agoraTokenGenerator;

    public Mono<String> acquireResource(String channelName) {
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
                        throw new CustomException(ErrorCode.INVALID_RESOURCE_ID);
                    }
                    log.debug("Resource 획득 성공 - resourceId: {}", maskSensitiveData(resourceId));
                    return resourceId;
                })
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    public Mono<String> startRecording(String resourceId, String channelName, RecordingRequest request, String[] fileFormats) {
        log.debug("Agora Cloud Recording 시작 - resourceId: {}, channel: {}",
                maskSensitiveData(resourceId), channelName);

        if (!agoraProperties.isCloudRecordingConfigured()) {
            log.error("Agora Cloud Recording이 설정되지 않았습니다.");
            return Mono.error(new CustomException(ErrorCode.OAUTH_CONFIG_ERROR));
        }

        Map<String, Object> storageConfig = Map.of(
                "vendor", 1, // AWS S3
                "region", agoraProperties.getRecordingRegion(),
                "bucket", agoraProperties.getRecordingStorageBucket(),
                "accessKey", agoraProperties.getRecordingStorageAccessKey(),
                "secretKey", agoraProperties.getRecordingStorageSecretKey(),
                "fileNamePrefix", new String[]{"recordings", "call_" + request.callId()}
        );

        Map<String, Object> recordingConfig = Map.of(
                "maxIdleTime", request.maxIdleTime(),
                "streamTypes", request.getStreamTypes(),
                "channelType", request.getChannelType(),
                "subscribeAudioUids", new String[]{"#allstream#"},
                "subscribeUidGroup", 0
        );

        Map<String, Object> recordingFileConfig = Map.of(
                "avFileType", fileFormats != null ? fileFormats : new String[]{"hls", "mp3"}
        );

        Map<String, Object> clientRequest = Map.of(
                "token", generateRecordingToken(channelName),
                "recordingConfig", recordingConfig,
                "recordingFileConfig", recordingFileConfig
        );

        return webClient.post()
                .uri("/v1/apps/{appid}/cloud_recording/resourceid/{resourceid}/mode/mix/start",
                        agoraProperties.getAppId(), resourceId)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(Map.of("cname", channelName, "uid", "0", "clientRequest", clientRequest))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String sid = (String) response.get("sid");
                    if (sid == null || sid.trim().isEmpty()) {
                        throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "SID를 획득할 수 없습니다.");
                    }
                    log.debug("Recording 시작 성공 - sid: {}", maskSensitiveData(sid));
                    return sid;
                })
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    public Mono<Map<String, Object>> stopRecording(String resourceId, String sid, String channelName) {
        log.debug("Agora Cloud Recording 중지 - resourceId: {}, sid: {}, channel: {}",
                maskSensitiveData(resourceId), maskSensitiveData(sid), channelName);

        return webClient.post()
                .uri("/v1/apps/{appid}/cloud_recording/resourceid/{resourceid}/sid/{sid}/mode/mix/stop",
                        agoraProperties.getAppId(), resourceId, sid)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(Map.of("cname", channelName, "uid", "0", "clientRequest", Map.of()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .defaultIfEmpty(Map.of())
                .doOnSuccess(response -> log.debug("Recording 중지 성공 - resourceId: {}",
                        maskSensitiveData(resourceId)))
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    public Mono<Map<String, Object>> queryRecording(String resourceId, String sid) {
        log.debug("Agora Cloud Recording 상태 조회 - resourceId: {}, sid: {}",
                maskSensitiveData(resourceId), maskSensitiveData(sid));

        return webClient.get()
                .uri("/v1/apps/{appid}/cloud_recording/resourceid/{resourceid}/sid/{sid}/mode/mix/query",
                        agoraProperties.getAppId(), resourceId, sid)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.debug("Recording 상태 조회 성공 - resourceId: {}",
                        maskSensitiveData(resourceId)))
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }



    private String generateRecordingToken(String channelName) {
        return agoraTokenGenerator.generateRtcToken(
                channelName,
                CallValidationConstants.RECORDING_UID_INT,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                CallValidationConstants.RECORDING_TOKEN_TTL_SECONDS
        );
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
