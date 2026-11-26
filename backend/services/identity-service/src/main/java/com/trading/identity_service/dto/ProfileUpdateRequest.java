package com.trading.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.ZoneId;

/** PUT replaces all preferences. There is no email, role or user id field: those cannot be changed here. */
public record ProfileUpdateRequest(
        @NotBlank @Pattern(regexp = "LIGHT|DARK|SYSTEM", message = "must be LIGHT, DARK or SYSTEM") String theme,
        @NotBlank @Pattern(regexp = "NSE|BSE|NASDAQ|NYSE", message = "must be NSE, BSE, NASDAQ or NYSE") String defaultExchange,
        @NotBlank @Size(max = 64) String timezone) {

    @JsonIgnore
    @AssertTrue(message = "must be a valid IANA time zone such as Asia/Kolkata")
    public boolean isTimezoneKnown() {
        return timezone == null || ZoneId.getAvailableZoneIds().contains(timezone);
    }
}
