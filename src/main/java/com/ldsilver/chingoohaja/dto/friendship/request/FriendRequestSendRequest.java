package com.ldsilver.chingoohaja.dto.friendship.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.UserValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequestSendRequest {
    @NotBlank(message = UserValidationConstants.Nickname.REQUIRED)
    @Size(
            min = UserValidationConstants.Nickname.MIN_LENGTH,
            max = UserValidationConstants.Nickname.MAX_LENGTH,
            message = UserValidationConstants.Nickname.INVALID_LENGTH
    )
    @Pattern(
            regexp = UserValidationConstants.Nickname.PATTERN,
            message = UserValidationConstants.Nickname.INVALID_FORMAT
    )
    @JsonProperty("nickname")
    private String nickname;

    public FriendRequestSendRequest(String nickname) {
        this.nickname = nickname;
    }

    public static FriendRequestSendRequest of(String nickname) {
        return new FriendRequestSendRequest(nickname);
    }

    public String getTrimmedNickname() {
        return nickname != null ? nickname.trim() : null;
    }
}
