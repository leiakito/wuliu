package com.example.demo.submission.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.submission.dto.SubmissionLogQueryRequest;
import com.example.demo.submission.entity.UserSubmissionLog;

public interface UserSubmissionLogService {

    void record(String username, String content);

    IPage<UserSubmissionLog> page(SubmissionLogQueryRequest request);
}
