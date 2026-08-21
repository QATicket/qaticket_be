package com.qms.qms.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFactoryRequest(
        @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String address
) {
}
