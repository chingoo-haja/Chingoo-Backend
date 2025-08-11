package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.common.util.NicknameGenerator;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.auth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.auth.request.LogoutRequest;
import com.ldsilver.chingoohaja.dto.auth.request.RefreshTokenRequest;
import com.ldsilver.chingoohaja.dto.auth.request.SocialLoginRequest;
import com.ldsilver.chingoohaja.dto.auth.response.SocialLoginResponse;
import com.ldsilver.chingoohaja.dto.auth.response.TokenResponse;
import com.ldsilver.chingoohaja.dto.auth.response.TokenValidationResponse;
import com.ldsilver.chingoohaja.dto.auth.response.UserMeResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;
import com.ldsilver.chingoohaja.infrastructure.oauth.OAuthClient;
import com.ldsilver.chingoohaja.infrastructure.oauth.OAuthClientFactory;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.repository.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuthClientFactory oAuthClientFactory;
    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final TokenService tokenService;
    private final TokenCacheService tokenCacheService;
    private final NicknameGenerator nicknameGenerator;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SocialLoginResponse socialLogin(String provider, SocialLoginRequest request) {
        log.debug("소셜 로그인 처리 시작 - provider: {}, state: {}", provider, request.getState());

        try {
            OAuthUserInfo oAuthUserInfo = getOAuthUserInfo(provider, request);

            UserLoginResult userLoginResult = findOrCreateUser(oAuthUserInfo);

            TokenResponse tokenResponse = tokenService.generateTokens(
                    userLoginResult.user().getId(),
                    request.getSafeDeviceInfo()
            );

            SocialLoginResponse.UserInfo userInfo = SocialLoginResponse.UserInfo.from(
                    userLoginResult.user(),
                    userLoginResult.isNewUser()
            );

            log.info("소셜 로그인 성공 - userId: {}, provider: {}, isNewUser: {}",
                    userLoginResult.user().getId(), provider, userLoginResult.isNewUser());

            return SocialLoginResponse.of(
                    tokenResponse.accessToken(),
                    tokenResponse.refreshToken(),
                    tokenResponse.expiresIn(),
                    userInfo
            );
        } catch (CustomException e) {
            log.error("소셜 로그인 실패 - provider: {}, error: {}", provider, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("소셜 로그인 중 예상치 못한 오류 발생 - provider: {}", provider, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "로그인 처리 중 오류가 발생했습니다.");
        }
    }


    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        log.debug("토큰 갱신 처리 시작");

        try {
            String refreshToken = request.refreshToken();

            if (!jwtTokenProvider.isTokenValid(refreshToken)) {
                throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
                throw new CustomException(ErrorCode.IS_NOT_REFRESH_TOKEN);
            }

            var userToken = userTokenRepository.findByRefreshTokenAndIsActiveTrue(refreshToken)
                    .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

            User user = userToken.getUser();

            String newAccessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(),
                    user.getEmail(),
                    user.getUserType().name()
            );

            log.info("토크 갱신 성공 - userId: {}", user.getId());

            return TokenResponse.forRefresh(
                    newAccessToken,
                    refreshToken,
                    jwtTokenProvider.getTimeUntilExpiration(newAccessToken)
            );
        } catch (CustomException e) {
            log.error("토큰 갱신 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("토큰 갱신 중 예상치 못한 오류", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }


    @Transactional
    public void logout(String accessToken, LogoutRequest request) {
        log.debug("로그아웃 처리 시작 - logoutAll: {}", request.isLogoutAll());

        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            if (request.isLogoutAll()) {
                logoutAllDeivces(user);
            } else {
                logoutCurrentDevice(request.refreshToken());
            }

            long remainingTime = jwtTokenProvider.getTimeUntilExpiration(accessToken);
            if (remainingTime > 0) {
                tokenCacheService.addToBlacklist(
                        accessToken,
                        Duration.ofMillis(remainingTime)
                );
            }

            log.debug("로그아웃 성공 - userId: {}, logoutAll: {}", user, request.isLogoutAll());
         } catch (CustomException e) {
            log.error("로그아웃 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("로그아웃 중 예상치 못한 오류", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }


    public TokenValidationResponse validateToken(String accessToken) {
        log.debug("토큰 검증 시작");

        try {
            if (!jwtTokenProvider.isTokenValid(accessToken)) {
                return TokenValidationResponse.invalid();
            }

            if (tokenCacheService.isTokenBlacklisted(accessToken)) {
                return TokenValidationResponse.invalid();
            }

            return TokenValidationResponse.fromValidToken(jwtTokenProvider, accessToken);
        } catch (Exception e) {
            log.debug("토큰 검증 실패: {}", e.getMessage());
            return TokenValidationResponse.invalid();
        }
    }


    public UserMeResponse getMyInfo(String accessToken) {
        log.debug("사용자 정보 조회 시작");

        try {
            if (!jwtTokenProvider.isTokenValid(accessToken) || tokenCacheService.isTokenBlacklisted(accessToken)) {
                throw new CustomException(ErrorCode.INVALID_TOKEN);
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            return UserMeResponse.from(user);

        } catch (CustomException e) {
            log.error("사용자 정보 조회 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("사용자 정보 조회 중 예상치 못한 예외 발생", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void logoutAllDeivces(User user) {
        userTokenRepository.deactivateAllTokensByUser(user);
        tokenCacheService.deleteAllUserTokens(user.getId());
        log.debug("모든 디바이스에서 로그아웃 완료 - userId: {}", user.getId());
    }

    private void logoutCurrentDevice(String refreshToken) {
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            userTokenRepository.deactivateTokenByRefreshToken(refreshToken);
            tokenCacheService.deleteRefreshToken(refreshToken);
            log.debug("현재 디바이스 로그아웃 완료");
        }
    }

    private OAuthUserInfo getOAuthUserInfo(String provider, SocialLoginRequest request) {
        OAuthClient oAuthClient = oAuthClientFactory.getClient(provider);

        TokenResponse tokenResponse = oAuthClient.exchangeCodeForToken(
                request.getCode(),
                request.getCodeVerifier()
        );

        OAuthUserInfo userInfo = oAuthClient.getUserInfo(tokenResponse.accessToken());

        validateOAuthUserInfo(userInfo);

        return userInfo;
    }

    private UserLoginResult findOrCreateUser(OAuthUserInfo oAuthUserInfo) {
        Optional<User> existingUser = findExistingUser(oAuthUserInfo);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            updateUserProfileIfNeeded(user, oAuthUserInfo);
            return new UserLoginResult(user, false);
        } else {
            User newUser = createNewUser(oAuthUserInfo);
            return new UserLoginResult(newUser, true);
        }
    }

    private Optional<User> findExistingUser(OAuthUserInfo oAuthUserInfo) {
        return userRepository.findByEmailAndProvider(
                oAuthUserInfo.email(),
                oAuthUserInfo.provider()
        );
    }

    private User createNewUser(OAuthUserInfo oAuthUserInfo) {
        log.debug("신규 사용자 생성 시작 - email: {}, provider: {}", oAuthUserInfo.email(), oAuthUserInfo.provider());

        String uniqueNickname = generateUniqueNickname();

        String profileImageUrl = getProfileImageUrl(oAuthUserInfo);

        User newUser = User.of(
                oAuthUserInfo.email(),
                uniqueNickname,
                oAuthUserInfo.name() != null ? oAuthUserInfo.name() : uniqueNickname,
                determineGender(oAuthUserInfo.gender()),
                determineBirthDate(),
                UserType.USER,
                profileImageUrl,
                oAuthUserInfo.provider(),
                oAuthUserInfo.providerId()
        );

        User savedUser = userRepository.save(newUser);

        log.info("신규 사용자 생성 완료 - userId: {}, email: {}, nickname: {}",
                savedUser.getId(), savedUser.getEmail(), savedUser.getNickname());

        return savedUser;
    }

    private void updateUserProfileIfNeeded(User user, OAuthUserInfo oAuthUserInfo) {
        boolean needsUpdate = false;

        String newProfileImage = getProfileImageUrl(oAuthUserInfo);
        if (newProfileImage != null && !newProfileImage.equals(user.getProfileImageUrl())) {
            // user.updateProfileImage(newProfileImage);
            needsUpdate = true;
        }
        if (needsUpdate) {
            userRepository.save(user);
            log.debug("사용자 프로필 정보 업데이트 - userId: {}", user.getId());
        }
    }

    private String getProfileImageUrl(OAuthUserInfo oAuthUserInfo) {
        if (oAuthUserInfo.profileImageUrl() != null && !oAuthUserInfo.profileImageUrl().trim().isEmpty()) {
            return oAuthUserInfo.profileImageUrl();
        }
        return determineDefaultProfileImage(oAuthUserInfo.gender());
    }

    private String generateUniqueNickname() {
        return nicknameGenerator.generateUniqueNickname(
                userRepository::existsByNickname
        );
    }

    private String determineDefaultProfileImage(Gender gender) {
        // TODO: 디폴트 프로필 이미지 주소 수정
        if (gender == Gender.FEMALE) {
            return "https://example.com/default-profile-female.png";
        } else {
            return "https://example.com/default-profile-male.png";
        }
    }

    private Gender determineGender(Gender oAuthGender) {
        if (oAuthGender != null) {
            return oAuthGender;
        }
        // OAuth에서 성별 정보를 제공하지 않는 경우 기본값
        // 실제 서비스에서는 사용자에게 추가 정보 입력을 요청할 수 있음
        return Gender.MALE;
    }

    private LocalDate determineBirthDate() {
        // OAuth에서 생년월일 정보를 제공하지 않는 경우 기본값
        // 실제 서비스에서는 사용자에게 추가 정보 입력을 요청해야 함
        return LocalDate.of(1990, 1, 1);
    }


    private void validateOAuthUserInfo(OAuthUserInfo userInfo) {
        if (userInfo.email() == null || userInfo.email().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED,
                    "이메일 정보가 없습니다. OAuth 공급자에서 이메일 제공 동의가 필요합니다.");
        }

        if (userInfo.providerId() == null || userInfo.providerId().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED,
                    "Provider ID가 없습니다.");
        }

        if (userInfo.provider() == null || userInfo.provider().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED,
                    "Provider 정보가 없습니다.");
        }
    }


    /**
     * 사용자 로그인 결과를 담는 내부 record
     */
    private record UserLoginResult(User user, boolean isNewUser) {}
}
