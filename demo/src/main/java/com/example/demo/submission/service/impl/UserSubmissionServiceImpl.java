package com.example.demo.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.auth.entity.SysUser;
import com.example.demo.auth.mapper.SysUserMapper;
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
import com.example.demo.submission.service.TrackingOwnerService;
import com.example.demo.submission.service.UserSubmissionLogService;
import com.example.demo.submission.service.UserSubmissionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private final SysUserMapper sysUserMapper;
    private final TrackingOwnerService trackingOwnerService;

    @Override
    @Transactional
    public UserSubmission create(UserSubmissionCreateRequest request, String operator, String ownerUsername) {
        String submitter = normalizeUsername(operator);
        String owner = normalizeUsername(ownerUsername);
        String rawContent = request.getTrackingNumber();
        LocalDate orderDate = request.getOrderDate();
        String trackingNumber = resolveTrackingNumber(rawContent, orderDate);
        UserSubmission submission = createSingleSubmission(trackingNumber, submitter, owner, orderDate);
        userSubmissionLogService.record(submitter, rawContent);
        return submission;
    }

    @Override
    @Transactional
    public List<UserSubmission> batchCreate(UserSubmissionBatchRequest request, String operator, String ownerUsername) {
        String submitter = normalizeUsername(operator);
        String owner = normalizeUsername(ownerUsername);
        LocalDate orderDate = request.getOrderDate();
        LinkedHashSet<String> sanitized = request.getTrackingNumbers().stream()
            .map(raw -> resolveTrackingNumber(raw, orderDate))
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sanitized.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少输入一个有效单号");
        }
        userSubmissionLogService.record(submitter, request.getRawContent());
        List<UserSubmission> submissions = new ArrayList<>();
        for (String trackingNumber : sanitized) {
            submissions.add(createSingleSubmission(trackingNumber, submitter, owner, orderDate));
        }
        return submissions;
    }

    @Override
    public IPage<UserSubmission> pageMine(UserSubmissionQueryRequest request, String username) {
        return pageInternal(request, username, false, true);
    }

    @Override
    public IPage<UserSubmission> pageAll(UserSubmissionQueryRequest request) {
        return pageInternal(request, null, true, false);
    }

    private IPage<UserSubmission> pageInternal(UserSubmissionQueryRequest request, String forcedUsername, boolean allowUsernameFilter, boolean queryBySubmitter) {
        long current = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        long size = request.getSize() == null || request.getSize() <= 0 ? 10 : request.getSize();
        Page<UserSubmission> page = Page.of(current, size);
        LambdaQueryWrapper<UserSubmission> wrapper = new LambdaQueryWrapper<>();

        if (forcedUsername != null) {
            // 如果是查询"我的提交"，按提交人字段查询
            if (queryBySubmitter) {
                wrapper.eq(UserSubmission::getUsername, forcedUsername);
            } else {
                // 否则按归属人字段查询
                wrapper.eq(UserSubmission::getOwnerUsername, forcedUsername);
            }
        } else if (allowUsernameFilter && StringUtils.hasText(request.getUsername())) {
            // 管理员视图的归属人筛选
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

    @Override
    public List<String> listOwnerUsernames() {
        // 从 JSON 文件获取所有归属人名称
        return trackingOwnerService.listOwnerNames();
    }

    @Override
    public void deleteOwner(String ownerName) {
        if (!StringUtils.hasText(ownerName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归属人名称不能为空");
        }
        // 查找所有使用该归属人的单号
        Map<String, String> allOwners = trackingOwnerService.getAllOwners();
        List<String> affectedTrackings = allOwners.entrySet().stream()
            .filter(entry -> ownerName.trim().equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // 删除这些单号的归属关系
        for (String trackingNumber : affectedTrackings) {
            trackingOwnerService.removeOwner(trackingNumber);
        }
    }

    private void syncSettlement(String trackingNumber, LocalDate orderDate) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        // 查询所有匹配的订单(包括UNPAID状态的)
        List<OrderRecord> orders;
        if (orderDate != null) {
            // 如果指定了日期，只查询该日期的订单
            LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OrderRecord::getTrackingNumber, trackingNumber.trim())
                .eq(OrderRecord::getOrderDate, orderDate);
            orders = orderRecordMapper.selectList(wrapper);
        } else {
            orders = orderService.findByTracking(List.of(trackingNumber));
        }
        if (orders.isEmpty()) {
            return;
        }
        // 创建待结账记录
        settlementService.createPending(orders, true);
    }

    private void setAmountFromOrder(UserSubmission submission, LocalDate orderDate) {
        if (submission.getTrackingNumber() == null) {
            return;
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getTrackingNumber, submission.getTrackingNumber().trim());
        // 如果指定了日期，按日期筛选
        if (orderDate != null) {
            wrapper.eq(OrderRecord::getOrderDate, orderDate);
        }
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

    private void ensureNotSubmitted(String trackingNumber, LocalDate orderDate) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        LambdaQueryWrapper<UserSubmission> exists = new LambdaQueryWrapper<>();
        exists.eq(UserSubmission::getTrackingNumber, trackingNumber);
        // 如果指定了日期，则只检查同日期的提交
        if (orderDate != null) {
            exists.eq(UserSubmission::getOrderDate, orderDate);
        }
        if (userSubmissionMapper.selectCount(exists) > 0) {
            String msg = orderDate != null
                ? "该单号在 " + orderDate + " 已被提交，请勿重复提交"
                : "该单号已被提交，请勿重复提交";
            throw new BusinessException(ErrorCode.BAD_REQUEST, msg);
        }
    }

    private boolean isSubmitted(String trackingNumber, LocalDate orderDate) {
        LambdaQueryWrapper<UserSubmission> exists = new LambdaQueryWrapper<>();
        exists.eq(UserSubmission::getTrackingNumber, trackingNumber);
        // 如果指定了日期，则检查同日期的提交
        if (orderDate != null) {
            exists.eq(UserSubmission::getOrderDate, orderDate);
        }
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

    private String resolveTrackingNumber(String raw, LocalDate orderDate) {
        String normalized = normalizeTracking(raw);
        // 1) 完全匹配（如果有日期则按日期筛选）
        OrderRecord exact = findOrder(normalized, orderDate);
        if (exact != null && StringUtils.hasText(exact.getTrackingNumber())) {
            return exact.getTrackingNumber().trim();
        }
        // 2) 按前缀模糊匹配 JD 尾缀：PREFIX-1-1
        String prefix = normalized.replaceAll("-+$", "");
        List<OrderRecord> candidates = findOrdersByPrefix(prefix, orderDate);
        if (!candidates.isEmpty()) {
            for (OrderRecord candidate : candidates) {
                String number = candidate.getTrackingNumber();
                if (StringUtils.hasText(number) && !isSubmitted(number.trim(), orderDate)) {
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

    private OrderRecord findOrder(String trackingNumber, LocalDate orderDate) {
        if (!StringUtils.hasText(trackingNumber)) {
            return null;
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getTrackingNumber, trackingNumber.trim());
        // 如果指定了日期，则按日期筛选
        if (orderDate != null) {
            wrapper.eq(OrderRecord::getOrderDate, orderDate);
        }
        wrapper.orderByDesc(OrderRecord::getOrderTime)
            .orderByDesc(OrderRecord::getCreatedAt)
            .last("LIMIT 1");
        return orderRecordMapper.selectOne(wrapper);
    }

    private List<OrderRecord> findOrdersByPrefix(String prefix, LocalDate orderDate) {
        if (!StringUtils.hasText(prefix) || prefix.length() < 6) {
            return List.of();
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(OrderRecord::getTrackingNumber, prefix + "-");
        // 如果指定了日期，则按日期筛选
        if (orderDate != null) {
            wrapper.eq(OrderRecord::getOrderDate, orderDate);
        }
        wrapper.orderByDesc(OrderRecord::getOrderTime)
            .orderByDesc(OrderRecord::getCreatedAt)
            .orderByAsc(OrderRecord::getTrackingNumber)
            .last("LIMIT 20");
        return orderRecordMapper.selectList(wrapper);
    }

    private UserSubmission createSingleSubmission(String trackingNumber, String submitter, String ownerUsername, LocalDate orderDate) {
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

        // Check if tracking number contains Chinese characters
        // Chinese tracking numbers (like "七月", "跑腿") can be submitted multiple times on different dates
        // English/numeric tracking numbers still maintain uniqueness (unless orderDate is specified)
        boolean containsChinese = normalized.matches(".*[\u4e00-\u9fa5]+.*");

        // 对于中文单号，如果指定了日期，则按 单号+日期 组合检查重复
        // 对于非中文单号，如果指定了日期，也按 单号+日期 检查；否则按单号检查
        if (containsChinese) {
            // 中文单号必须指定日期才能检查重复，否则允许重复提交
            if (orderDate != null) {
                ensureNotSubmitted(normalized, orderDate);
            }
        } else {
            // 非中文单号：按 单号+日期（如果有）检查重复
            ensureNotSubmitted(normalized, orderDate);
        }

        // 将归属关系存入 JSON 文件
        trackingOwnerService.setOwner(normalized, ownerUsername.trim());

        UserSubmission submission = new UserSubmission();
        submission.setUsername(submitter.trim());
        submission.setOwnerUsername(ownerUsername.trim());
        submission.setTrackingNumber(normalized);
        submission.setStatus(DEFAULT_STATUS);
        submission.setSubmissionDate(LocalDate.now());
        submission.setOrderDate(orderDate);
        setAmountFromOrder(submission, orderDate);
        userSubmissionMapper.insert(submission);
        syncSettlement(normalized, orderDate);
        return submission;
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交人不能为空");
        }
        return username.trim();
    }
}
