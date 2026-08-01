package com.settleup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/groups/{groupId}/members.
 */
public record AddMemberRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email
) {}
