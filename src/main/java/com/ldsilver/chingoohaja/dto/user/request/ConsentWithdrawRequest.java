package com.ldsilver.chingoohaja.dto.user.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.user.enums.ConsentType;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentWithdrawRequest {

    @NotNull(message = "동의 타입은 필수입니다.")
    @JsonProperty("consent_type")
    private ConsentType consentType;

    public ConsentWithdrawRequest(ConsentType consentType) {
        this.consentType = consentType;
    }

    public static ConsentWithdrawRequest of(ConsentType consentType) {
        return new ConsentWithdrawRequest(consentType);
    }
}