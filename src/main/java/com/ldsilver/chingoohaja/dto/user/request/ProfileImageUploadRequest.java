package com.ldsilver.chingoohaja.dto.user.request;

import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import static com.ldsilver.chingoohaja.validation.UserValidationConstants.Image.ALLOWED_CONTENT_TYPES;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImageUploadRequest {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; //5MB

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
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    public boolean isFileSizeValid() {
        if (image == null) {
            return false;
        }

        return image.getSize() <= MAX_SIZE_BYTES;
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
