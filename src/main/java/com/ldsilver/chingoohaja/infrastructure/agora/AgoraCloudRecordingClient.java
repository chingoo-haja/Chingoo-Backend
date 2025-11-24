package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.AgoraProperties;
import com.ldsilver.chingoohaja.dto.call.request.RecordingRequest;
import com.ldsilver.chingoohaja.validation.CallValidationConstants;
import io.agora.media.RtcTokenBuilder2;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AgoraCloudRecordingClient {

    @Qualifier("agoraWebClient")
    private final WebClient webClient;
    private final AgoraProperties agoraProperties;
    private final AgoraTokenGenerator agoraTokenGenerator;

    public AgoraCloudRecordingClient(
            @Qualifier("agoraWebClient") WebClient webClient,
            AgoraProperties agoraProperties,
            AgoraTokenGenerator agoraTokenGenerator
    ) {
        this.webClient = webClient;
        this.agoraProperties = agoraProperties;
        this.agoraTokenGenerator = agoraTokenGenerator;

        log.info("🔧 AgoraCloudRecordingClient 생성자 호출");
        log.info("  - WebClient hashCode: {}", System.identityHashCode(webClient));
    }


    public Mono<String> acquireResource(String channelName) {
        log.debug("Agora Cloud Recording Resource 획득 시작 - channel: {}", channelName);

        if (!agoraProperties.isCloudRecordingConfigured()) {
            log.error("Agora Cloud Recording이 설정되지 않았습니다.");
            return Mono.error(new CustomException(ErrorCode.OAUTH_CONFIG_ERROR));
        }

        Map<String, Object> requestBody = Map.of(
                "cname", channelName,
                "uid", CallValidationConstants.RECORDING_API_UID,
                "clientRequest", Map.of(
                        "resourceExpiredHour", 24,
                        "scene", 0
                )
        );

        return webClient.post()
                .uri("/v1/apps/{appid}/cloud_recording/acquire",
                        agoraProperties.getAppId())
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
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
                .doOnError(error -> log.error("❌ Resource 획득 실패", error))
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    public Mono<String> startRecording(String resourceId, String channelName, RecordingRequest request) {
        log.debug("개별 오디오 전용 Agora Cloud Recording 시작 - resourceId: {}, channel: {}",
                maskSensitiveData(resourceId), channelName);

        log.info("=" .repeat(80));
        log.info("🎙️ 녹음 시작 요청 - FULL LOG");
        log.info("=" .repeat(80));

        if (!agoraProperties.isCloudRecordingConfigured()) {
            log.error("Agora Cloud Recording이 설정되지 않았습니다.");
            return Mono.error(new CustomException(ErrorCode.OAUTH_CONFIG_ERROR));
        }


        Map<String, Object> recordingConfig = new HashMap<>();
        recordingConfig.put("maxIdleTime", request.maxIdleTime());
        recordingConfig.put("streamTypes", 0); // 0 = audio only
        recordingConfig.put("streamMode", "standard");
        recordingConfig.put("channelType", 0); // 0 = communication
        recordingConfig.put("subscribeAudioUids", List.of("#allstream#"));
        recordingConfig.put("subscribeVideoUids", List.of());
        recordingConfig.put("subscribeUidGroup", 0);

        Map<String, Object> recordingFileConfig = Map.of(
                "avFileType", List.of("hls")
        );

        Map<String, Object> clientRequest = new HashMap<>();
        clientRequest.put("token", generateRecordingToken(channelName));
        clientRequest.put("recordingConfig", recordingConfig);
        clientRequest.put("recordingFileConfig", recordingFileConfig);

        Map<String, Object> storageConfig = createStorageConfig(request);
        clientRequest.put("storageConfig", storageConfig);


        Map<String, Object> requestBody = Map.of(
                "cname", channelName,
                "uid", CallValidationConstants.RECORDING_API_UID, // "0"
                "clientRequest", clientRequest
        );

        // ✅ 전체 요청 본문 로그 (민감 정보는 마스킹)
        log.info("📤 요청 URL: /v1/apps/{}/cloud_recording/resourceid/{}/mode/individual/start",
                maskSensitiveData(agoraProperties.getAppId()), maskSensitiveData(resourceId));
        log.info("📦 요청 본문:");
        log.info("  cname: {}", channelName);
        log.info("  uid: {}", CallValidationConstants.RECORDING_API_UID);
        log.info("  clientRequest:");
        log.info("    token: {}***", clientRequest.get("token").toString().substring(0, 20));
        log.info("    recordingConfig: {}", recordingConfig);
        log.info("    recordingFileConfig: {}", recordingFileConfig);
        log.info("    storageConfig:");
        log.info("      vendor: {}", storageConfig.get("vendor"));
        log.info("      region: {}", storageConfig.get("region"));
        log.info("      bucket: {}", storageConfig.get("bucket"));
        log.info("      accessKey: {}***", storageConfig.get("accessKey").toString().substring(0, 10));
        log.info("      secretKey: ***");
        log.info("=" .repeat(80));



        return webClient.post()
                .uri("/v1/apps/{appid}/cloud_recording/resourceid/{resourceid}/mode/individual/start",
                        agoraProperties.getAppId(), resourceId)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String sid = (String) response.get("sid");
                    if (sid == null || sid.trim().isEmpty()) {
                        throw new CustomException(ErrorCode.CALL_SESSION_ERROR, "SID를 획득할 수 없습니다.");
                    }
                    log.info("Recording 시작 성공 - sid: {}", maskSensitiveData(sid));
                    return sid;
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException webEx) {
                        log.error("❌ Recording 시작 실패 - Status: {}, Body: {}",
                                webEx.getStatusCode(),
                                webEx.getResponseBodyAsString());
                    } else {
                        log.error("❌ Recording 시작 실패", error);
                    }
                })
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    public Mono<Map<String, Object>> stopRecording(String resourceId, String sid, String channelName) {
        log.debug("Agora Cloud Recording 중지 - resourceId: {}, sid: {}, channel: {}",
                maskSensitiveData(resourceId), maskSensitiveData(sid), channelName);

        Map<String, Object> requestBody = Map.of(
                "cname", channelName,
                "uid", CallValidationConstants.RECORDING_API_UID, // "0"
                "clientRequest", Map.of()
        );

        //   https://api.agora.io/v1/apps/<yourappid>/cloud_recording/resourceid/<resourceid>/sid/<sid>/mode/individual/stop
        return webClient.post()
                .uri("/v1/apps/{appid}/cloud_recording/resourceid/{resourceid}/sid/{sid}/mode/individual/stop",
                        agoraProperties.getAppId(), resourceId, sid)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .defaultIfEmpty(Map.of())
                .doOnSuccess(response -> log.debug("Recording 중지 성공 - resourceId: {}",
                        maskSensitiveData(resourceId)))
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.warn("⚠️ Recording worker를 찾을 수 없음 (404) - resourceId: {}, reason: 자동 종료되었거나 아직 시작 안 됨",
                            maskSensitiveData(resourceId));
                    return Mono.just(Map.of(
                            "code", 404,
                            "reason", "already_stopped_or_not_started",
                            "resourceId", resourceId
                    ));
                })
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    public Mono<Map<String, Object>> queryRecording(String resourceId, String sid) {
        log.debug("Agora Cloud Recording 상태 조회 - resourceId: {}, sid: {}",
                maskSensitiveData(resourceId), maskSensitiveData(sid));

        return webClient.get()
                .uri("/v1/apps/{appid}/cloud_recording/resourceid/{resourceid}/sid/{sid}/mode/individual/query",
                        agoraProperties.getAppId(), resourceId, sid)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(response -> log.debug("Recording 상태 조회 성공 - resourceId: {}",
                        maskSensitiveData(resourceId)))
                .doOnError(error -> log.error("❌ Recording 상태 조회 실패", error))
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }



    private String generateRecordingToken(String channelName) {
        return agoraTokenGenerator.generateRtcToken(
                channelName,
                CallValidationConstants.RECORDING_BOT_UID,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                CallValidationConstants.RECORDING_TOKEN_TTL_SECONDS
        );
    }

    private Map<String, Object> createStorageConfig(RecordingRequest request) {
        int vendorCode = Integer.parseInt(agoraProperties.getRecordingStorageVendor());
        String regionStr = agoraProperties.getRecordingRegion();
        String bucket = agoraProperties.getRecordingStorageBucket();
        String accessKey = agoraProperties.getRecordingStorageAccessKey();
        String secretKey = agoraProperties.getRecordingStorageSecretKey();

        // ✅ 검증
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalStateException("Storage bucket이 설정되지 않았습니다!");
        }
        if (accessKey == null || accessKey.isEmpty()) {
            throw new IllegalStateException("Storage accessKey가 설정되지 않았습니다!");
        }
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("Storage secretKey가 설정되지 않았습니다!");
        }

        Map<String, Object> config = new HashMap<>();
        config.put("vendor", vendorCode);

        // GCS Multi-region 처리
        if (vendorCode == 6) {
            // "0", "us", "US" 같은 값들은 그대로 문자열로
            if ("0".equals(regionStr) || "US".equalsIgnoreCase(regionStr)) {
                config.put("region", 0); // 소문자 "us"
            } else {
                config.put("region", regionStr);
            }
        } else {
            try {
                config.put("region", Integer.parseInt(regionStr));
            } catch (NumberFormatException e) {
                config.put("region", 0);
            }
        }

        config.put("bucket", bucket);
        config.put("accessKey", accessKey);
        config.put("secretKey", secretKey);

        LocalDate today = LocalDate.now();
        String dateFolder = today.format(DateTimeFormatter.BASIC_ISO_DATE);

        config.put("fileNamePrefix", List.of(
                "recordings",                       //recordings/
                dateFolder,                         //recordings/20250123
                String.valueOf(request.callId())    //recordings/20250123/1/
        ));

        log.info("📦 StorageConfig - vendor: {}, region: '{}', bucket: {}",
                vendorCode, config.get("region"), agoraProperties.getRecordingStorageBucket());

        return config;
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
