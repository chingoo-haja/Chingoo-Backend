package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.user.request.ProfileUpdateRequest;
import com.ldsilver.chingoohaja.dto.user.response.ProfileResponse;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads/profiles";
    private static final String BASE_URL = "http://localhost:8080/api/v1/files/profiles";

    @Transactional(readOnly = true)
    public ProfileResponse getUserProfile(Long userId) {
        log.debug("사용자 프로필 조회 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return ProfileResponse.from(user);
    }

    @Transactional
    public ProfileResponse updateUserProfile(Long userId, ProfileUpdateRequest request) {
        log.debug("사용자 프로필 수정 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (request.hasNicknameChange() && !user.getNickname().equals(request.getNickname())) {
            validateNicknameUnique(request.getNickname(), userId);
        }

        user.updateProfile(
                request.getRealName(),
                request.getNickname(),
                request.getGender(),
                request.getBirth(),
                user.getProfileImageUrl()
        );

        User updateUser = userRepository.save(user);

        return ProfileResponse.from(updateUser);
    }

    private void validateNicknameUnique(String nickname, Long currentUserId) {
        boolean exists = userRepository.existsByNickname(nickname);
        if (exists) {
            userRepository.findById(currentUserId)
                    .filter(user -> !user.getNickname().equals(nickname))
                    .ifPresent(user -> {
                        throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
                    });
        }
    }
}
