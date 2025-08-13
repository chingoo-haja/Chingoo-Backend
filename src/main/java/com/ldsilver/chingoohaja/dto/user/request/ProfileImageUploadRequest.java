package com.ldsilver.chingoohaja.dto.user.request;

import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImageUploadRequest {

    @NotNull(message = UserValidationConstants.Image.REQUIRED)
    private MultipartFile image;

    public ProfileImageUploadRequest(MultipartFile image) {
        this.image = image;
    }

    public static ProfileImageUploadRequest from(MultipartFile image) {
        return new ProfileImageUploadRequest(image);
    }

    public boolean isValidImageFile() {
        if (image == null || image.isEmpty()) {
            return false;
        }

        String contentType = image.getContentType();
        return contentType != null && (
                        contentType.equals("image/jpeg") ||
                        contentType.equals("image/png") ||
                        contentType.equals("image/webp")
                );
    }

    public boolean isFileSizeValid() {
        if (image == null) {
            return false;
        }

        // 5MB 제한
        return image.getSize() <= 5 * 1024 * 1024;
    }

    public String getOriginalFilename() {
        return image != null ? image.getOriginalFilename() : null;
    }

    public String getContentType() {
        return image != null ? image.getContentType() : null;
    }

    public long getSize() {
        return image != null ? image.getSize() : 0;
    }
}
