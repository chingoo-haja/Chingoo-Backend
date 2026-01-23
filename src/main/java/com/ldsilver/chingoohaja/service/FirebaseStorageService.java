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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ldsilver.chingoohaja.validation.UserValidationConstants.Image.ALLOWED_CONTENT_TYPES;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseStorageService {

    public String uploadProfileImage(MultipartFile file, Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateImageFile(file);
        return uploadFile(file, "profiles", userId);
    }

    public void deleteFile(String filePathOrUrl) {
        if (filePathOrUrl == null || filePathOrUrl.isBlank()) {
            log.debug("파일 경로가 비어있어 삭제를 건너뜀");
            return;
        }

        try {
            String objectName;

            // 1. URL인 경우
            if (filePathOrUrl.startsWith("http")) {
                objectName = extractObjectNameFromUrl(filePathOrUrl);

                if (objectName == null) {
                    log.warn("URL에서 객체명 추출 실패 - url: {}", maskUrl(filePathOrUrl));
                    return;
                }

                // 버킷 검증
                Bucket bucket = StorageClient.getInstance().bucket();
                if (filePathOrUrl.contains("storage.googleapis.com")
                        || filePathOrUrl.contains("firebasestorage.googleapis.com")) {
                    if (!filePathOrUrl.contains(bucket.getName())) {
                        log.debug("버킷 불일치로 삭제 건너뜀 - expectedBucket: {}", bucket.getName());
                        return;
                    }
                }
            }
            // 2. gs:// 형식인 경우
            else if (filePathOrUrl.startsWith("gs://")) {
                objectName = filePathOrUrl.substring(filePathOrUrl.indexOf("/", 5) + 1);
            }
            // 3. 경로인 경우 (예: recordings/20260123/1/xxx.m3u8)
            else {
                objectName = filePathOrUrl;
            }


            Bucket bucket = StorageClient.getInstance().bucket();
            Blob blob = bucket.get(objectName);

            if (blob == null) {
                log.debug("파일을 찾을 수 없음 - objectName: {}", objectName);
                return;
            }

            boolean deleted = blob.delete();

            if (deleted) {
                log.info("파일 삭제 성공 - objectName: {}", objectName);
            } else {
                log.warn("파일 삭제 실패 - objectName: {}", objectName);
            }

        } catch (Exception e) {
            log.warn("파일 삭제 중 예외 발생 - path: {}", maskPath(filePathOrUrl), e);
        }
    }

    /**
     * HLS 디렉토리 전체 삭제 (플레이리스트 + 세그먼트)
     */
    public void deleteHlsDirectory(String m3u8Path) {
        log.info("HLS 디렉토리 삭제 시작 - m3u8: {}", m3u8Path);

        try {
            if (m3u8Path.startsWith("gs://")) {
                m3u8Path = m3u8Path.substring(m3u8Path.indexOf("/", 5) + 1);
            }

            String directory = m3u8Path.substring(0, m3u8Path.lastIndexOf('/') + 1);

            Bucket bucket = StorageClient.getInstance().bucket();

            Iterable<Blob> blobs = bucket.list(
                    Storage.BlobListOption.prefix(directory)
            ).iterateAll();

            int deletedCount = 0;
            for (Blob blob : blobs) {
                try {
                    boolean deleted = blob.delete();
                    if (deleted) {
                        deletedCount++;
                        log.debug("파일 삭제 - {}", blob.getName());
                    }
                } catch (Exception e) {
                    log.warn("파일 삭제 실패 - {}", blob.getName(), e);
                }
            }

            log.info("HLS 디렉토리 삭제 완료 - directory: {}, 삭제된 파일: {}개",
                    directory, deletedCount);

        } catch (Exception e) {
            log.error("HLS 디렉토리 삭제 실패 - m3u8: {}", m3u8Path, e);
        }
    }


    /**
     * 바이트 배열을 GCS에 업로드 (WAV 파일용)
     */
    public String uploadRecordingFile(
            byte[] fileData, String filePath, String contentType) {
        log.debug("녹음 파일 업로드 - path: {}, size: {} bytes",
                filePath, fileData.length);

        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), filePath);

            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            bucket.getStorage().create(blobInfo, fileData);

            log.info("녹음 파일 업로드 완료 - path: {}", filePath);

            // GCS 경로 반환
            return String.format("gs://%s/%s", bucket.getName(), filePath);

        } catch (Exception e) {
            log.error("녹음 파일 업로드 실패 - path: {}", filePath, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED,
                    "파일 업로드 실패: " + e.getMessage());
        }
    }

    /**
     * HLS 디렉토리 전체 다운로드 (플레이리스트 + 세그먼트)
     */
    public Path downloadHlsDirectory(String m3u8Path, Path tempDir) throws IOException {
        log.debug("HLS 디렉토리 다운로드 시작 - m3u8: {}", m3u8Path);

        try {
            // gs:// 제거
            if (m3u8Path.startsWith("gs://")) {
                m3u8Path = m3u8Path.substring(m3u8Path.indexOf("/", 5) + 1);
            }

            // 디렉토리 경로 추출 (예: recordings/20260122/1/)
            String directory = m3u8Path.substring(0, m3u8Path.lastIndexOf('/') + 1);

            Bucket bucket = StorageClient.getInstance().bucket();

            // ✅ 1. 플레이리스트 다운로드
            Path localM3u8 = tempDir.resolve("playlist.m3u8");
            Blob m3u8Blob = bucket.get(m3u8Path);

            if (m3u8Blob == null || !m3u8Blob.exists()) {
                throw new CustomException(ErrorCode.FILE_NOT_FOUND,
                        "플레이리스트를 찾을 수 없습니다: " + m3u8Path);
            }

            Files.write(localM3u8, m3u8Blob.getContent());
            log.debug("플레이리스트 다운로드 완료 - {}", localM3u8);

            // ✅ 2. 플레이리스트에서 세그먼트 파일 목록 추출
            List<String> segmentFiles = extractSegmentFiles(localM3u8, directory);
            log.debug("세그먼트 파일 {}개 발견", segmentFiles.size());

            // ✅ 3. 모든 세그먼트 다운로드
            int downloaded = 0;
            for (String segmentPath : segmentFiles) {
                try {
                    Blob segmentBlob = bucket.get(segmentPath);

                    if (segmentBlob != null && segmentBlob.exists()) {
                        // 파일명만 추출
                        String fileName = segmentPath.substring(segmentPath.lastIndexOf('/') + 1);
                        Path localSegment = tempDir.resolve(fileName);

                        Files.write(localSegment, segmentBlob.getContent());
                        downloaded++;

                        log.debug("세그먼트 다운로드 {}/{} - {}",
                                downloaded, segmentFiles.size(), fileName);
                    } else {
                        log.warn("세그먼트를 찾을 수 없음 - {}", segmentPath);
                    }
                } catch (Exception e) {
                    log.error("세그먼트 다운로드 실패 - {}", segmentPath, e);
                }
            }

            log.info("✅ HLS 디렉토리 다운로드 완료 - 플레이리스트: 1개, 세그먼트: {}/{}개",
                    downloaded, segmentFiles.size());

            return localM3u8;

        } catch (Exception e) {
            log.error("❌ HLS 디렉토리 다운로드 실패 - m3u8: {}", m3u8Path, e);
            throw new CustomException(ErrorCode.FILE_DOWNLOAD_FAILED,
                    "HLS 다운로드 실패: " + e.getMessage());
        }
    }

    /**
     * 플레이리스트에서 세그먼트 파일 경로 추출
     */
    private List<String> extractSegmentFiles(Path m3u8File, String directory) throws IOException {
        List<String> segments = new ArrayList<>();
        List<String> lines = Files.readAllLines(m3u8File);

        for (String line : lines) {
            line = line.trim();

            // .ts 파일만 추출
            if (line.endsWith(".ts") && !line.startsWith("#")) {
                // 상대 경로를 절대 경로로 변환
                String segmentPath = directory + line;
                segments.add(segmentPath);
            }
        }

        return segments;
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

            bucket.getStorage().create(blobInfo, file.getBytes());

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
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
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

    private String maskUrl(String url) {
        if (url == null || url.length() < 30) {
            return "***";
        }
        String masked = url.replaceAll("(?i)([?&]token=)[^&]+", "$1***");
        return masked.substring(0, Math.min(50, masked.length())) + "...";
    }

    private String maskPath(String path) {
        if (path == null || path.length() < 30) {
            return path;
        }
        return path.substring(0, 30) + "...";
    }
}
