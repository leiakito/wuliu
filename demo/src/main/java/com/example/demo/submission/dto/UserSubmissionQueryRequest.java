package com.example.demo.submission.dto;

import lombok.Data;

@Data
public class UserSubmissionQueryRequest {

    private Long page;

    private Long size;

    private String status;

    private String username;

    private String trackingNumber;
}
