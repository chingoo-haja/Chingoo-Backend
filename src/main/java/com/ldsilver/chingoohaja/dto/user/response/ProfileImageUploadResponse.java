package com.ldsilver.chingoohaja.dto.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ProfileImageUploadResponse(
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("original_filename") String originalFilename,
        @JsonProperty("file_size") Long fileSize,
        @JsonProperty("uploaded_at") LocalDateTime uploadedAt
) {
    public static ProfileImageUploadResponse of(
            String profileImageUrl,
            String originalFilename,
            Long fileSize) {
        return new ProfileImageUploadResponse(
                profileImageUrl,
                originalFilename,
                fileSize,
                LocalDateTime.now()
        );
    }

    public String getFormattedFileSize() {
        if (fileSize == null || fileSize <= 0) {
            return "0 B";
        }

        double size = fileSize;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }
}
