package com.tourism.rag.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminSetupRequest {
    /** Must match ADMIN_SETUP_KEY env var */
    @NotBlank
    private String setupKey;
    @NotBlank @Size(min = 2, max = 50)
    private String username;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, max = 100)
    private String password;
}
