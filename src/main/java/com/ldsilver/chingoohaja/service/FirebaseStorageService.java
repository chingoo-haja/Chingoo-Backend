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

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    public String uploadProfileImage(MultipartFile file, Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateImageFile(file);
        return uploadFile(file, "profiles", userId);
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            log.debug("파일 URL이 비어있어 삭제를 건너뜀");
            return;
        }

        try {
            String objectName = extractObjectNameFromUrl(fileUrl);
            if (objectName != null) {
                Bucket bucket = StorageClient.getInstance().bucket();
                if (fileUrl.contains("storage.googleapis.com") || fileUrl.contains("firebasestorage.googleapis.com")) {
                    if (!fileUrl.contains(bucket.getName())) {
                        String safeUrl = fileUrl.replaceAll("(?i)([?&]token=)[^&]+", "$1***");
                        log.debug("버킷 불일치로 삭제 건너뜀 - url: {}, expectedBucket: {}", safeUrl, bucket.getName());
                        return;
                    }
                }

                Blob blob = bucket.get(objectName);
                if (blob == null) {
                    log.debug("Firebase Storage 객체를 찾을 수 없음 - objectName: {}", objectName);
                    return;
                }
                boolean deleted = blob.delete();

                if (deleted) {
                    log.debug("Firebase Storage 파일 삭제 성공 - objectName: {}", objectName);
                } else {
                    log.debug("Firebase Storage 파일 삭제 실패 - objectName: {}", objectName);
                }
            }
        } catch (Exception e) {
            String safeUrl = fileUrl.replaceAll("(?i)([?&]token=)[^&]+", "$1***");
            log.warn("Firebase Storage 파일 삭제 실패 - url: {}", safeUrl, e);
        }
    }

    private String uploadFile(MultipartFile file, String folder, Long userId) {
        try {
            String fileName = generateFileName(file, userId);
            String objectName = folder + "/" + fileName;

            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), objectName);

            String token = UUID.randomUUID().toString();
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .setMetadata(java.util.Map.of("firebaseStorageDownloadTokens", token))
                    .build();

            Blob blob = bucket.getStorage().create(blobInfo, file.getBytes());

            String downloadUrl = String.format(
                    "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s",
                    bucket.getName(),
                    java.net.URLEncoder.encode(objectName, java.nio.charset.StandardCharsets.UTF_8),
                    token
            );

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
            // 1) Firebase REST URL: https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{object}?...
            if (fileUrl.contains("/o/")) {
                String encoded = fileUrl.split("/o/")[1].split("\\?")[0];
                return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
            }
            // 2) GCS URL: https://storage.googleapis.com/{bucket}/{object}?...
            if (fileUrl.contains("storage.googleapis.com")){
                java.net.URI uri = java.net.URI.create(fileUrl);
                String path = uri.getPath(); // /{bucket}/{object...}
                if (path != null && path.startsWith("/")) path = path.substring(1);
                int idx = path.indexOf('/');
                if (idx > 0 && idx < path.length() - 1) {
                    String encoded = path.substring(idx + 1);
                    return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
                    }
            }
        } catch (Exception e) {
            log.warn("URL에서 객체 이름 추출 실패: {}", fileUrl, e);
        }
        return null;
    }
}
