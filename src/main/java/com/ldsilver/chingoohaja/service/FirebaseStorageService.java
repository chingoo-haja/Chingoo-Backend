package com.ldsilver.chingoohaja.service;

import com.google.cloud.storage.*;
import com.google.firebase.cloud.StorageClient;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    public String uploadProfileImage(MultipartFile file, Long userId) {
        validateImageFile(file);
        return uploadFile(file, "profiles", userId);
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("firebasestorage.googleapis.com")) {
            log.debug("Firebase Storage 파일이 아니므로 삭제하지 않음 - url: {}", fileUrl);
            return;
        }

        try {
            String objectName = extractObjectNameFromUrl(fileUrl);
            if (objectName != null) {
                Bucket bucket = StorageClient.getInstance().bucket();
                boolean deleted = bucket.get(objectName).delete();

                if (deleted) {
                    log.debug("Firebase Storage 파일 삭제 성공 - objectName: {}", objectName);
                } else {
                    log.debug("Firebase Storage 파일 삭제 실패 - objectName: {}", objectName);
                }
            }
        } catch (Exception e) {
            log.warn("Firebase Storage 파일 삭제 실패 - url: {}", fileUrl, e);
        }
    }

    private String uploadFile(MultipartFile file, String folder, Long userId) {
        try {
            String fileName = generateFileName(file, userId);
            String objectName = folder + "/" + fileName;

            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), objectName);

            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();

            Blob blob = bucket.getStorage().create(blobInfo, file.getBytes());

            String downloadUrl = blob.signUrl(7, TimeUnit.DAYS).toString();

            log.debug("Firebase Storage 업로드 성공 - userId: {}", userId);
            return downloadUrl;

        } catch (IOException e) {
            log.error("파일 읽기 실패 - userId: {}", userId, e);
            throw new CustomException(ErrorCode.FILE_READ_FAILED);
        } catch (StorageException e) {
            log.error("Firebase Storage 업로드 실패 - userId: {}", userId, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("예상치 못한 업로드 오류 - userId: {}", userId, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
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
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_TYPE);
        }
    }

    private String generateFileName(MultipartFile file, Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0,8);
        String fileExtension = getFileExtension(file.getOriginalFilename());
        return String.format("user_%d_%s_%s%s", userId, timestamp, uuid, fileExtension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return ".jpg"; // 기본 확장자
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String extractObjectNameFromUrl(String fileUrl) {
        try {
            if (fileUrl.contains("/o/") && fileUrl.contains("?")) {
                String encoded = fileUrl.split("/o/")[1].split("\\?")[0];
                return java.net.URLDecoder.decode(encoded, "UTF-8");
            }
        } catch (Exception e) {
            log.warn("URL에서 객체 이름 추출 실패: {}", fileUrl, e);
        }
        return null;
    }
}
