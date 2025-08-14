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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

        if (request.hasNicknameChange()) {
            String newNickname = request.getTrimmedNickname();
            if (!user.getNickname().equals(newNickname)) {
                validateNicknameUnique(newNickname, userId);
            }
        }
        updateUserFields(user, request);

        log.debug("사용자 프로필 수정 완료 - userId: {}", userId);
        User updateUser = userRepository.save(user);

        return ProfileResponse.from(updateUser);
    }

    @Transactional
    public ProfileImageUploadResponse updateProfileImage(Long userId, ProfileImageUploadRequest request) {
        log.debug("프로필 이미지 업로드 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        MultipartFile imageFile = request.getImage();

        validateImageFile(imageFile);

        try {
            String savedFileName = saveImageFile(imageFile, userId);
            String profileImageUrl = BASE_URL + "/" + savedFileName;

            user.updateProfileImage(profileImageUrl);
            userRepository.save(user);

            return ProfileImageUploadResponse.of(
                    profileImageUrl,
                    imageFile.getOriginalFilename(),
                    imageFile.getSize()
            );
        } catch (IOException e) {
            log.error("프로필 이미지 저장 실패 - userId: {}", userId, e);
            throw new CustomException(ErrorCode.IMAGE_SAVE_FAILED);
        }
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

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.IMAGE_FILE_EMPTY);
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new CustomException(ErrorCode.IMAGE_FILE_OVERSIZED);
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/jpeg") &&
                        !contentType.equals("image/png") &&
                        !contentType.equals("image/webp"))) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE_NAME);
        }
    }

    private String saveImageFile(MultipartFile file, Long userId) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0,8);
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String fileName = String.format("user_%d_%s_%s%s", userId, timestamp, uuid, fileExtension);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return fileName;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
