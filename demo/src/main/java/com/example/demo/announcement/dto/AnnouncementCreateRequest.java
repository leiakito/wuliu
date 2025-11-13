package com.example.demo.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnouncementCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String title;

    @NotBlank
    private String content;
}
