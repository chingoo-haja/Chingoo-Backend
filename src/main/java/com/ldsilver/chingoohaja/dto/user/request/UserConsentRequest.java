package com.ldsilver.chingoohaja.dto.user.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsentRequest {

    @NotNull(message = "필수 동의 여부는 필수입니다.")
    @JsonProperty("required_privacy")
    private Boolean requiredPrivacy;

    @JsonProperty("optional_data_usage")
    private Boolean optionalDataUsage;

    @NotNull(message = "동의 채널은 필수입니다.")
    @JsonProperty("channel")
    private ConsentChannel channel;

    public UserConsentRequest(Boolean requiredPrivacy, Boolean optionalDataUsage, ConsentChannel channel) {
        this.requiredPrivacy = requiredPrivacy;
        this.optionalDataUsage = optionalDataUsage;
        this.channel = channel;
    }

    public static UserConsentRequest of(Boolean requiredPrivacy, Boolean optionalDataUsage, ConsentChannel channel) {
        return new UserConsentRequest(requiredPrivacy, optionalDataUsage, channel);
    }

    public boolean hasOptionalConsent() {
        return optionalDataUsage != null && optionalDataUsage;
    }
}