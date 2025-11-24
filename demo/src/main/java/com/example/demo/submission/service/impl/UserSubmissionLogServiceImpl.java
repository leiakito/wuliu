package com.example.demo.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.submission.dto.SubmissionLogQueryRequest;
import com.example.demo.submission.entity.UserSubmissionLog;
import com.example.demo.submission.mapper.UserSubmissionLogMapper;
import com.example.demo.submission.service.UserSubmissionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserSubmissionLogServiceImpl implements UserSubmissionLogService {

    private final UserSubmissionLogMapper userSubmissionLogMapper;

    @Override
    @Transactional
    public void record(String username, String content) {
        if (!StringUtils.hasText(username) && !StringUtils.hasText(content)) {
            return;
        }
        UserSubmissionLog log = new UserSubmissionLog();
        log.setUsername(username);
        log.setContent(content);
        userSubmissionLogMapper.insert(log);
    }

    @Override
    public IPage<UserSubmissionLog> page(SubmissionLogQueryRequest request) {
        long current = request.getPage() <= 0 ? 1 : request.getPage();
        long size = request.getSize() <= 0 ? 20 : request.getSize();
        Page<UserSubmissionLog> page = Page.of(current, size);
        LambdaQueryWrapper<UserSubmissionLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getUsername())) {
            wrapper.eq(UserSubmissionLog::getUsername, request.getUsername().trim());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.like(UserSubmissionLog::getContent, request.getKeyword().trim());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(UserSubmissionLog::getCreatedAt, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(UserSubmissionLog::getCreatedAt, request.getEndTime());
        }
        wrapper.orderByDesc(UserSubmissionLog::getCreatedAt);
        return userSubmissionLogMapper.selectPage(page, wrapper);
    }
}
