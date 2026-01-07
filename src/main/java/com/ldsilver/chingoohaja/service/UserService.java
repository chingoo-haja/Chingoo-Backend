package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.user.request.ProfileImageUploadRequest;
import com.ldsilver.chingoohaja.dto.user.request.ProfileUpdateRequest;
import com.ldsilver.chingoohaja.dto.user.response.ProfileImageUploadResponse;
import com.ldsilver.chingoohaja.dto.user.response.ProfileResponse;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FirebaseStorageService firebaseStorageService;

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

        if (request.hasNicknameChange()) {
            String newNickname = request.getTrimmedNickname();
            if (!user.getNickname().equals(newNickname)) {
                validateNicknameUnique(newNickname, userId);
            }
        }
        updateUserFields(user, request);

        try {
            User updateUser = userRepository.saveAndFlush(user);
            log.debug("사용자 프로필 수정 완료 - userId: {}", userId);
            return ProfileResponse.from(updateUser);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

    }

    @Transactional
    public ProfileImageUploadResponse updateProfileImage(Long userId, ProfileImageUploadRequest request) {
        log.debug("프로필 이미지 업로드 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        MultipartFile imageFile = request.getImage();

        String oldImageUrl = user.getProfileImageUrl();

        String newImageUrl = firebaseStorageService.uploadProfileImage(imageFile, userId);

        user.updateProfileImage(newImageUrl);
        userRepository.save(user);

        if (oldImageUrl != null && !oldImageUrl.contains("default")) {
            firebaseStorageService.deleteFile(oldImageUrl);
        }

        log.debug("프로필 이미지 업로드 완료 - userId: {}", userId);

        return ProfileImageUploadResponse.of(
                newImageUrl,
                imageFile.getOriginalFilename(),
                imageFile.getSize()
        );
    }



    private void updateUserFields(User user, ProfileUpdateRequest request) {
        if (request.hasRealNameChange()) {
            user.updateRealName(request.getTrimmedRealName());
        }
        if (request.hasNicknameChange()) {
            user.updateNickname(request.getTrimmedNickname());
        }
        if (request.hasGenderChange()) {
            user.updateGender(request.getGender());
        }
        if (request.hasBirthChange()) {
            user.updateBirth(request.getBirth());
        }
        if (request.hasPhoneNumberChange()) {
            user.updatePhoneNumber(request.getTrimmedPhoneNumber());
        }
    }

    private void validateNicknameUnique(String nickname, Long currentUserId) {
        userRepository.findByNickname(nickname)
                .filter(user -> !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
                });
    }
}
