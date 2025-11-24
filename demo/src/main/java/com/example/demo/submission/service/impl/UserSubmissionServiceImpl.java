package com.example.demo.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.order.service.OrderService;
import com.example.demo.settlement.service.SettlementService;
import com.example.demo.submission.dto.UserSubmissionBatchRequest;
import com.example.demo.submission.dto.UserSubmissionCreateRequest;
import com.example.demo.submission.dto.UserSubmissionQueryRequest;
import com.example.demo.submission.entity.UserSubmission;
import com.example.demo.submission.mapper.UserSubmissionMapper;
import com.example.demo.submission.service.UserSubmissionLogService;
import com.example.demo.submission.service.UserSubmissionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserSubmissionServiceImpl implements UserSubmissionService {

    private static final String DEFAULT_STATUS = "PENDING";

    private final UserSubmissionMapper userSubmissionMapper;
    private final OrderService orderService;
    private final SettlementService settlementService;
    private final OrderRecordMapper orderRecordMapper;
    private final UserSubmissionLogService userSubmissionLogService;

    @Override
    @Transactional
    public UserSubmission create(UserSubmissionCreateRequest request, String operator, String ownerUsername) {
        String submitter = normalizeUsername(operator);
        String owner = normalizeUsername(ownerUsername);
        String rawContent = request.getTrackingNumber();
        String trackingNumber = resolveTrackingNumber(rawContent);
        UserSubmission submission = createSingleSubmission(trackingNumber, submitter, owner);
        userSubmissionLogService.record(submitter, rawContent);
        return submission;
    }

    @Override
    @Transactional
    public List<UserSubmission> batchCreate(UserSubmissionBatchRequest request, String operator, String ownerUsername) {
        String submitter = normalizeUsername(operator);
        String owner = normalizeUsername(ownerUsername);
        LinkedHashSet<String> sanitized = request.getTrackingNumbers().stream()
            .map(this::resolveTrackingNumber)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sanitized.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少输入一个有效单号");
        }
        userSubmissionLogService.record(submitter, request.getRawContent());
        List<UserSubmission> submissions = new ArrayList<>();
        for (String trackingNumber : sanitized) {
            submissions.add(createSingleSubmission(trackingNumber, submitter, owner));
        }
        return submissions;
    }

    @Override
    public IPage<UserSubmission> pageMine(UserSubmissionQueryRequest request, String username) {
        return pageInternal(request, username, false);
    }

    @Override
    public IPage<UserSubmission> pageAll(UserSubmissionQueryRequest request) {
        return pageInternal(request, null, true);
    }

    private IPage<UserSubmission> pageInternal(UserSubmissionQueryRequest request, String forcedUsername, boolean allowUsernameFilter) {
        long current = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        long size = request.getSize() == null || request.getSize() <= 0 ? 10 : request.getSize();
        Page<UserSubmission> page = Page.of(current, size);
        LambdaQueryWrapper<UserSubmission> wrapper = new LambdaQueryWrapper<>();
        if (forcedUsername != null) {
            wrapper.eq(UserSubmission::getOwnerUsername, forcedUsername);
        } else if (allowUsernameFilter && StringUtils.hasText(request.getUsername())) {
            wrapper.eq(UserSubmission::getOwnerUsername, request.getUsername().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(UserSubmission::getStatus, request.getStatus().trim());
        }
        if (StringUtils.hasText(request.getTrackingNumber())) {
            wrapper.like(UserSubmission::getTrackingNumber, request.getTrackingNumber().trim());
        }
        wrapper.orderByDesc(UserSubmission::getSubmissionDate)
            .orderByDesc(UserSubmission::getId);
        IPage<UserSubmission> result = userSubmissionMapper.selectPage(page, wrapper);
        attachOrderDetails(result.getRecords());
        return result;
    }

    private void syncSettlement(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        List<OrderRecord> orders = orderService.findByTracking(List.of(trackingNumber));
        if (orders.isEmpty()) {
            return;
        }
        settlementService.createPending(orders, true);
    }

    private void setAmountFromOrder(UserSubmission submission) {
        if (submission.getTrackingNumber() == null) {
            return;
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getTrackingNumber, submission.getTrackingNumber().trim());
        wrapper.orderByDesc(OrderRecord::getCreatedAt).last("LIMIT 1");
        OrderRecord order = orderRecordMapper.selectOne(wrapper);
        if (order != null && order.getAmount() != null) {
            submission.setAmount(order.getAmount());
        }
    }

    private void attachOrderDetails(List<UserSubmission> submissions) {
        Set<String> numbers = submissions.stream()
            .filter(submission -> StringUtils.hasText(submission.getTrackingNumber()))
            .map(submission -> submission.getTrackingNumber().trim())
            .collect(Collectors.toSet());
        if (numbers.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OrderRecord::getTrackingNumber, numbers);
        List<OrderRecord> orders = orderRecordMapper.selectList(wrapper);
        Map<String, OrderRecord> map = orders.stream()
            .filter(order -> order.getTrackingNumber() != null)
            .collect(Collectors.toMap(order -> order.getTrackingNumber().trim(), order -> order, (a, b) -> a));
        submissions.forEach(submission -> {
            if (!StringUtils.hasText(submission.getTrackingNumber())) {
                return;
            }
            OrderRecord order = map.get(submission.getTrackingNumber().trim());
            if (order != null) {
                submission.setOrder(order);
                if (order.getAmount() != null) {
                    submission.setAmount(order.getAmount());
                }
            }
        });
    }

    private void ensureNotSubmitted(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        LambdaQueryWrapper<UserSubmission> exists = new LambdaQueryWrapper<>();
        exists.eq(UserSubmission::getTrackingNumber, trackingNumber);
        if (userSubmissionMapper.selectCount(exists) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该单号已被提交，请勿重复提交");
        }
    }

    private boolean isSubmitted(String trackingNumber) {
        LambdaQueryWrapper<UserSubmission> exists = new LambdaQueryWrapper<>();
        exists.eq(UserSubmission::getTrackingNumber, trackingNumber);
        return userSubmissionMapper.selectCount(exists) > 0;
    }

    private String normalizeTracking(String input) {
        if (!StringUtils.hasText(input)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单号不能为空");
        }
        String cleaned = input
            .replaceAll("^[='‘’“”\"`\\u200B-\\u200F\\uFEFF]+", "")
            .trim();
        while (cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单号不能为空");
        }
        return cleaned;
    }

    private String resolveTrackingNumber(String raw) {
        String normalized = normalizeTracking(raw);
        // 1) 完全匹配
        OrderRecord exact = findOrder(normalized);
        if (exact != null && StringUtils.hasText(exact.getTrackingNumber())) {
            return exact.getTrackingNumber().trim();
        }
        // 2) 按前缀模糊匹配 JD 尾缀：PREFIX-1-1
        String prefix = normalized.replaceAll("-+$", "");
        List<OrderRecord> candidates = findOrdersByPrefix(prefix);
        if (!candidates.isEmpty()) {
            for (OrderRecord candidate : candidates) {
                String number = candidate.getTrackingNumber();
                if (StringUtils.hasText(number) && !isSubmitted(number.trim())) {
                    return number.trim();
                }
            }
            String first = candidates.get(0).getTrackingNumber();
            if (StringUtils.hasText(first)) {
                return first.trim();
            }
        }
        // 3) 回退到用户输入
        return normalized;
    }

    private OrderRecord findOrder(String trackingNumber) {
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getTrackingNumber, trackingNumber);
        return orderRecordMapper.selectOne(wrapper);
    }

    private List<OrderRecord> findOrdersByPrefix(String prefix) {
        if (!StringUtils.hasText(prefix) || prefix.length() < 6) {
            return List.of();
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(OrderRecord::getTrackingNumber, prefix + "-")
            .orderByDesc(OrderRecord::getOrderTime)
            .orderByDesc(OrderRecord::getCreatedAt)
            .orderByAsc(OrderRecord::getTrackingNumber)
            .last("LIMIT 20");
        return orderRecordMapper.selectList(wrapper);
    }

    private UserSubmission createSingleSubmission(String trackingNumber, String submitter, String ownerUsername) {
        if (!StringUtils.hasText(trackingNumber)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单号不能为空");
        }
        if (!StringUtils.hasText(submitter)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交人不能为空");
        }
        if (!StringUtils.hasText(ownerUsername)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交人不能为空");
        }
        String normalized = trackingNumber.trim();
        ensureNotSubmitted(normalized);
        UserSubmission submission = new UserSubmission();
        submission.setUsername(submitter.trim());
        submission.setOwnerUsername(ownerUsername.trim());
        submission.setTrackingNumber(normalized);
        submission.setStatus(DEFAULT_STATUS);
        submission.setSubmissionDate(LocalDate.now());
        setAmountFromOrder(submission);
        userSubmissionMapper.insert(submission);
        syncSettlement(normalized);
        return submission;
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交人不能为空");
        }
        return username.trim();
    }
}
