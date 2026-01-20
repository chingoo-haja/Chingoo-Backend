package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.UserConsent;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentType;
import com.ldsilver.chingoohaja.dto.user.request.ConsentWithdrawRequest;
import com.ldsilver.chingoohaja.dto.user.request.UserConsentRequest;
import com.ldsilver.chingoohaja.dto.user.response.UserConsentResponse;
import com.ldsilver.chingoohaja.dto.user.response.UserConsentsResponse;
import com.ldsilver.chingoohaja.repository.UserConsentRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserConsentService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;

    @Value("${app.consent.current-version:2026-01-v1}")
    private String currentConsentVersion;

    /**
     * 회원가입 시 동의 정보 저장
     */
    @Transactional
    public void saveConsents(Long userId, UserConsentRequest request) {
        log.debug("동의 정보 저장 시작 - userId: {}, channel: {}", userId, request.getChannel());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 필수 동의 검증
        if (request.getRequiredPrivacy() == null || !request.getRequiredPrivacy()) {
            throw new CustomException(ErrorCode.CONSENT_REQUIRED);
        }

        // 필수 동의 저장
        UserConsent requiredConsent = UserConsent.of(
                user,
                ConsentType.REQUIRED_PRIVACY,
                true,
                currentConsentVersion,
                request.getChannel()
        );
        userConsentRepository.save(requiredConsent);

        // 선택 동의 저장
        if (request.getOptionalDataUsage() != null) {
            UserConsent optionalConsent = UserConsent.of(
                    user,
                    ConsentType.OPTIONAL_DATA_USAGE,
                    request.getOptionalDataUsage(),
                    currentConsentVersion,
                    request.getChannel()
            );
            userConsentRepository.save(optionalConsent);
            log.debug("선택 동의 저장 완료 - userId: {}, agreed: {}",
                    userId, request.getOptionalDataUsage());
        }

        log.info("동의 정보 저장 완료 - userId: {}, 필수: {}, 선택: {}",
                userId, true, request.hasOptionalConsent());
    }

    /**
     * 사용자의 모든 동의 정보 조회
     */
    @Transactional(readOnly = true)
    public UserConsentsResponse getUserConsents(Long userId) {
        log.debug("동의 정보 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<UserConsent> consents = userConsentRepository.findByUserOrderByAgreedAtDesc(user);

        boolean hasRequiredConsent = userConsentRepository.hasActiveConsent(
                user, ConsentType.REQUIRED_PRIVACY);
        boolean hasOptionalConsent = userConsentRepository.hasActiveConsent(
                user, ConsentType.OPTIONAL_DATA_USAGE);

        List<UserConsentResponse> consentResponses = consents.stream()
                .map(UserConsentResponse::from)
                .toList();

        return UserConsentsResponse.of(
                consentResponses,
                hasRequiredConsent,
                hasOptionalConsent,
                currentConsentVersion
        );
    }

    /**
     * 선택 동의 철회
     */
    @Transactional
    public UserConsentResponse withdrawConsent(Long userId, ConsentWithdrawRequest request) {
        log.debug("동의 철회 시작 - userId: {}, consentType: {}", userId, request.getConsentType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 필수 동의는 철회 불가
        if (request.getConsentType() == ConsentType.REQUIRED_PRIVACY) {
            throw new CustomException(ErrorCode.CONSENT_REQUIRED, "필수 동의는 철회할 수 없습니다.");
        }

        // 활성 동의 찾기
        UserConsent consent = userConsentRepository.findActiveConsentByUserAndType(
                        user, request.getConsentType())
                .orElseThrow(() -> new CustomException(ErrorCode.CONSENT_NOT_FOUND));

        // 이미 철회된 경우
        if (consent.isWithdrawn()) {
            throw new CustomException(ErrorCode.CONSENT_ALREADY_WITHDRAWN);
        }

        // 철회 처리
        consent.withdraw();
        UserConsent savedConsent = userConsentRepository.save(consent);

        log.info("동의 철회 완료 - userId: {}, consentType: {}", userId, request.getConsentType());

        return UserConsentResponse.from(savedConsent);
    }

    /**
     * 특정 타입의 활성 동의 확인
     */
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(Long userId, ConsentType consentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return userConsentRepository.hasActiveConsent(user, consentType);
    }
}