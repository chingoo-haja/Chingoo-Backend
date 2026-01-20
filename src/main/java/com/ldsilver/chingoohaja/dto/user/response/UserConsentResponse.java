package com.ldsilver.chingoohaja.dto.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.user.UserConsent;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentChannel;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentType;

import java.time.LocalDateTime;

public record UserConsentResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("consent_type") ConsentType consentType,
        @JsonProperty("agreed") Boolean agreed,
        @JsonProperty("version") String version,
        @JsonProperty("agreed_at") LocalDateTime agreedAt,
        @JsonProperty("withdrawn_at") LocalDateTime withdrawnAt,
        @JsonProperty("channel") ConsentChannel channel,
        @JsonProperty("is_active") Boolean isActive
) {
    public static UserConsentResponse from(UserConsent consent) {
        return new UserConsentResponse(
                consent.getId(),
                consent.getConsentType(),
                consent.getAgreed(),
                consent.getVersion(),
                consent.getAgreedAt(),
                consent.getWithdrawnAt(),
                consent.getChannel(),
                consent.isActive()
        );
    }
}