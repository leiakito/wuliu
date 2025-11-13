package com.example.demo.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserSubmissionCreateRequest {

    @NotBlank
    private String trackingNumber;
}
