package com.example.demo.submission.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.submission.dto.UserSubmissionBatchRequest;
import com.example.demo.submission.dto.UserSubmissionCreateRequest;
import com.example.demo.submission.dto.UserSubmissionQueryRequest;
import com.example.demo.submission.entity.UserSubmission;
import java.util.List;

public interface UserSubmissionService {

    UserSubmission create(UserSubmissionCreateRequest request, String operator, String ownerUsername);

    List<UserSubmission> batchCreate(UserSubmissionBatchRequest request, String operator, String ownerUsername);

    IPage<UserSubmission> pageMine(UserSubmissionQueryRequest request, String username);

    IPage<UserSubmission> pageAll(UserSubmissionQueryRequest request);

    /**
     * 列出所有可能的归属用户（系统账号与历史提交中的归属人/提交人），去重排序。
     */
    List<String> listOwnerUsernames();
}
