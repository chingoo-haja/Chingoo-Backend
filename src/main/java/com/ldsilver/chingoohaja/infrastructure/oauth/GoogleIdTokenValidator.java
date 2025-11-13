package com.ldsilver.chingoohaja.infrastructure.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.OAuthProperties;
import com.ldsilver.chingoohaja.dto.oauth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.oauth.response.GoogleApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleIdTokenValidator {

    private final OAuthProperties oAuthProperties;

    /**
     * Google ID Token을 검증하고 사용자 정보를 추출합니다.
     */
    public OAuthUserInfo verifyIdToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(oAuthProperties.getGoogle().getClientId()))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.error("Google ID Token 검증 실패 - 유효하지 않은 토큰");
                throw new CustomException(ErrorCode.INVALID_OAUTH_TOKEN, "유효하지 않은 Google ID Token입니다.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // GoogleApiResponse 형식으로 변환 (verified_email 필드 제외)
            GoogleApiResponse googleResponse = new GoogleApiResponse(
                    payload.getSubject(),           // id
                    payload.getEmail(),              // email
                    (String) payload.get("name"),    // name
                    (String) payload.get("given_name"),  // given_name
                    (String) payload.get("family_name"), // family_name
                    (String) payload.get("picture"),     // picture
                    (String) payload.get("locale")       // locale
            );

            log.debug("Google ID Token 검증 성공 - email: {}, id: {}",
                    googleResponse.email(), googleResponse.id());

            // 기존 fromGoogle 메서드 사용
            return OAuthUserInfo.fromGoogle(googleResponse);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google ID Token 검증 중 오류 발생", e);
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED,
                    "Google 사용자 정보 조회 실패: " + e.getMessage());
        }
    }
}