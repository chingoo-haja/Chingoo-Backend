package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.common.util.NicknameGenerator;
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

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final NicknameGenerator nicknameGenerator;

    @Transactional
    public LoginResponse signUp(SignUpRequest request) {
        log.debug("회원가입 처리 시작 - email: {}", request.email());

        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        try {
            String encodedPassword = passwordEncoder.encode(request.password());

            String randomNickname = nicknameGenerator.generateUniqueNickname(
                    userRepository::existsByNickname
            );

            // 사용자 생성 (닉네임 자동 생성, 성별/생년월일은 임시값)
            User newUser = User.ofLocal(
                    request.email(),
                    encodedPassword,
                    randomNickname,
                    request.realName(),
                    null,
                    LocalDate.of(1900, 1, 1),
                    null,
                    null
            );

            User savedUser = userRepository.save(newUser);

            log.info("회원가입 성공 - userId: {}, email: {}, 자동생성 닉네임: {}",
                    savedUser.getId(), savedUser.getEmail(), savedUser.getNickname());

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

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.debug("로그인 처리 시작 - email: {}", request.email());

        try {
            // 사용자 조회
            User user = userRepository.findByEmailAndProvider(request.email(), "local")
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            // 비밀번호 검증
            if (user.getPassword() == null ||
                    !passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
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
