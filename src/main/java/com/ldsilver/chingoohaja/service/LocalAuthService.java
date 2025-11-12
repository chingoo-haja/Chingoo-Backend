package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.auth.request.LoginRequest;
import com.ldsilver.chingoohaja.dto.auth.request.SignUpRequest;
import com.ldsilver.chingoohaja.dto.auth.response.LoginResponse;
import com.ldsilver.chingoohaja.dto.oauth.response.SocialLoginResponse;
import com.ldsilver.chingoohaja.dto.oauth.response.TokenResponse;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public LoginResponse signUp(SignUpRequest request) {
        log.debug("회원가입 처리 시작 - email: {}", request.getTrimmedEmail());

        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getTrimmedEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 닉네임 중복 체크
        if (userRepository.existsByNickname(request.getTrimmedNickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        try {
            // 비밀번호 암호화
            String encodedPassword = passwordEncoder.encode(request.getPassword());

            // 사용자 생성
            User newUser = User.ofLocal(
                    request.getTrimmedEmail(),
                    encodedPassword,
                    request.getTrimmedNickname(),
                    request.getTrimmedRealName(),
                    request.getGender(),
                    request.getBirth(),
                    null  // 프로필 이미지는 나중에 업로드 가능
            );

            User savedUser = userRepository.save(newUser);

            log.info("회원가입 성공 - userId: {}, email: {}", savedUser.getId(), savedUser.getEmail());

            // 토큰 생성 및 로그인 처리
            TokenResponse tokenResponse = tokenService.generateTokens(
                    savedUser.getId(),
                    "Unknown Device"
            );

            SocialLoginResponse.UserInfo userInfo = SocialLoginResponse.UserInfo.from(savedUser, true);

            return LoginResponse.of(
                    tokenResponse.accessToken(),
                    tokenResponse.refreshToken(),
                    tokenResponse.expiresIn(),
                    userInfo
            );

        } catch (DataIntegrityViolationException e) {
            log.error("회원가입 실패 - 데이터 무결성 위반: {}", e.getMessage());
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        } catch (Exception e) {
            log.error("회원가입 중 예상치 못한 오류 발생", e);
            throw new CustomException(ErrorCode.USER_CREATION_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.debug("로그인 처리 시작 - email: {}", request.getTrimmedEmail());

        try {
            // 사용자 조회
            User user = userRepository.findByEmailAndProvider(request.getTrimmedEmail(), "local")
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            // 비밀번호 검증
            if (user.getPassword() == null ||
                    !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_TOKEN, "이메일 또는 비밀번호가 올바르지 않습니다.");
            }

            log.info("로그인 성공 - userId: {}, email: {}", user.getId(), user.getEmail());

            // 토큰 생성
            TokenResponse tokenResponse = tokenService.generateTokens(
                    user.getId(),
                    request.getSafeDeviceInfo()
            );

            SocialLoginResponse.UserInfo userInfo = SocialLoginResponse.UserInfo.from(user, false);

            return LoginResponse.of(
                    tokenResponse.accessToken(),
                    tokenResponse.refreshToken(),
                    tokenResponse.expiresIn(),
                    userInfo
            );

        } catch (CustomException e) {
            log.error("로그인 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("로그인 중 예상치 못한 오류 발생", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
