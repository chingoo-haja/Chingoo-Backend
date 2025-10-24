package com.ldsilver.chingoohaja.infrastructure.agora;

import com.ldsilver.chingoohaja.config.AgoraProperties;
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

@Slf4j
@Component
public class AgoraRestClient {

    @Qualifier("agoraWebClient")
    private final WebClient webClient;
    private final AgoraProperties agoraProperties;

    public AgoraRestClient(
            @Qualifier("agoraWebClient") WebClient webClient,
            AgoraProperties agoraProperties
    ) {
        this.webClient = webClient;
        this.agoraProperties = agoraProperties;

        log.info("🔧 AgoraRestClient 생성자 호출");
        log.info("  - WebClient hashCode: {}", System.identityHashCode(webClient));
    }

    /**
     * Agora REST API 기본 인증 헤더 생성
     */
    private String createBasicAuthHeader() {
        String credentials = agoraProperties.getCustomerId() + ":" + agoraProperties.getCustomerSecret();
        byte[] encodedCredentials = Base64.getEncoder().encode(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encodedCredentials);
    }

    /**
     * Agora API 연결 테스트
     */
    public Mono<Boolean> testConnection() {
        log.debug("Agora API 연결 테스트 시작");
        log.debug("🔍 WebClient baseUrl: {}",
                webClient.toString());

        try {

            String fullUrl = agoraProperties.getRestApiBaseUrl() + "/dev/v1/projects";
            log.info("🔍 실제 요청 URL: {}", fullUrl);
            log.info("🔍 AgoraProperties.restApiBaseUrl: {}", agoraProperties.getRestApiBaseUrl());

            return webClient
                    .get()
                    .uri("/dev/v1/projects")
                    .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        log.debug("Agora API 연결 테스트 성공");
                        return true;
                    })
                    .onErrorResume(error -> {
                        if (error instanceof WebClientResponseException) {
                            WebClientResponseException webClientError = (WebClientResponseException) error;
                            String body = webClientError.getResponseBodyAsString();
                            body = (body != null && body.length() > 512) ? body.substring(0, 512) + "...(truncated)" : body;
                            log.warn("Agora API 연결 테스트 실패 - url: {}, 상태코드: {}, 응답: {}",
                                    webClientError.getRequest() != null ? webClientError.getRequest().getURI() : "unknown",
                                    webClientError.getStatusCode(),
                                    body);
                        } else {
                            log.error("Agora API 연결 테스트 실패", error);
                        }
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.error("Agora API 연결 테스트 중 예외 발생", e);
            return Mono.just(false);
        }
    }

    /**
     * GET 요청을 위한 WebClient RequestHeadersSpec 생성
     */
    protected WebClient.RequestHeadersSpec<?> createGetRequest() {
        return webClient
                .get()
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * POST 요청을 위한 WebClient RequestBodyUriSpec 생성
     */
    protected WebClient.RequestBodySpec createPostRequest() {
        return webClient
                .post()
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 안전한 로깅을 위한 헬퍼 메서드
     */
    protected String maskSensitiveData(String data) {
        if (data == null || data.length() < 8) {
            return "***";
        }
        return data.substring(0, 4) + "***" + data.substring(data.length() - 4);
    }
}
