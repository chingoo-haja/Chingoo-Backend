package com.ldsilver.chingoohaja.dto.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserConsentsResponse(
        @JsonProperty("consents") List<UserConsentResponse> consents,
        @JsonProperty("has_required_consent") Boolean hasRequiredConsent,
        @JsonProperty("has_optional_consent") Boolean hasOptionalConsent,
        @JsonProperty("current_version") String currentVersion
) {
    public static UserConsentsResponse of(
            List<UserConsentResponse> consents,
            Boolean hasRequiredConsent,
            Boolean hasOptionalConsent,
            String currentVersion
    ) {
        return new UserConsentsResponse(
                consents,
                hasRequiredConsent,
                hasOptionalConsent,
                currentVersion
        );
    }
}