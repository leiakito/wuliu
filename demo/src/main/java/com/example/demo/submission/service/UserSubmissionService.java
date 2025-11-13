package com.example.demo.submission.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.submission.dto.UserSubmissionCreateRequest;
import com.example.demo.submission.dto.UserSubmissionQueryRequest;
import com.example.demo.submission.entity.UserSubmission;

public interface UserSubmissionService {

    UserSubmission create(UserSubmissionCreateRequest request, String username);

    IPage<UserSubmission> pageMine(UserSubmissionQueryRequest request, String username);

    IPage<UserSubmission> pageAll(UserSubmissionQueryRequest request);
}
