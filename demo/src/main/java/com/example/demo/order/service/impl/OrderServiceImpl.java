package com.example.demo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.util.ExcelHelper;
import com.example.demo.order.dto.BatchFetchRequest;
import com.example.demo.order.dto.OrderAmountRequest;
import com.example.demo.order.dto.OrderCategoryStats;
import com.example.demo.order.dto.OrderCreateRequest;
import com.example.demo.order.dto.OrderFilterRequest;
import com.example.demo.order.dto.OrderUpdateRequest;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.entity.OrderCellStyle;
import com.example.demo.order.mapper.OrderCellStyleMapper;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.order.util.TrackingCategoryUtil;
import com.example.demo.order.service.OrderService;
import com.example.demo.settlement.service.SettlementService;
import com.example.demo.submission.entity.UserSubmission;
import com.example.demo.submission.mapper.UserSubmissionMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    // 会话级：上一次导入的样式与内容快照（仅用于与本次导入对比，不使用数据库样式）
    private static final Map<String, Map<String, CellStyleSnap>> LAST_IMPORT_SNAPSHOT = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> LAST_VALUE_SNAPSHOT = new ConcurrentHashMap<>();
    // 位置对齐（行号）
    private static final Map<Integer, Map<String, CellStyleSnap>> LAST_STYLE_BY_ROW = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, String>> LAST_VALUE_BY_ROW = new ConcurrentHashMap<>();
    // tracking 兜底
    private static final Map<String, Map<String, CellStyleSnap>> LAST_STYLE_BY_TRACKING = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> LAST_VALUE_BY_TRACKING = new ConcurrentHashMap<>();

    @Data
    private static class CellStyleSnap {
        private String bg;
        private String font;
        private Boolean strike;
        CellStyleSnap() {}
        CellStyleSnap(String bg, String font, Boolean strike) { this.bg = bg; this.font = font; this.strike = strike; }
    }

    private String styleKey(OrderRecord r) {
        return ((r.getTrackingNumber() == null ? "" : r.getTrackingNumber()).toUpperCase(Locale.ROOT)) + "#" +
               ((r.getSn() == null ? "" : r.getSn()).toUpperCase(Locale.ROOT));
    }

    private Map<String, CellStyleSnap> buildStyleMap(OrderRecord r) {
        Map<String, CellStyleSnap> m = new HashMap<>();
        m.put("tracking", new CellStyleSnap(norm(r.getTrackingBgColor()), norm(r.getTrackingFontColor()), bool(r.getTrackingStrike())));
        m.put("model",    new CellStyleSnap(norm(r.getModelBgColor()),    norm(r.getModelFontColor()),    bool(r.getModelStrike())));
        m.put("sn",       new CellStyleSnap(norm(r.getSnBgColor()),       norm(r.getSnFontColor()),       bool(r.getSnStrike())));
        m.put("remark",   new CellStyleSnap(norm(r.getRemarkBgColor()),   norm(r.getRemarkFontColor()),   bool(r.getRemarkStrike())));
        m.put("amount",   new CellStyleSnap(norm(r.getAmountBgColor()),   norm(r.getAmountFontColor()),   bool(r.getAmountStrike())));
        return m;
    }

    private Map<String, CellStyleSnap> buildStyleMapFromDb(OrderRecord dbRecord, List<OrderCellStyle> dbStyles) {
        Map<String, OrderCellStyle> styleMap = new HashMap<>();
        if (dbStyles != null) {
            for (OrderCellStyle s : dbStyles) {
                styleMap.put(s.getField(), s);
            }
        }

        Map<String, CellStyleSnap> m = new HashMap<>();

        // 从持久化的样式表读取，如果没有则使用默认值
        OrderCellStyle trackingStyle = styleMap.get("tracking");
        m.put("tracking", new CellStyleSnap(
            trackingStyle != null ? norm(trackingStyle.getBgColor()) : norm(null),
            trackingStyle != null ? norm(trackingStyle.getFontColor()) : norm(null),
            trackingStyle != null ? bool(trackingStyle.getStrike()) : Boolean.FALSE
        ));

        OrderCellStyle modelStyle = styleMap.get("model");
        m.put("model", new CellStyleSnap(
            modelStyle != null ? norm(modelStyle.getBgColor()) : norm(null),
            modelStyle != null ? norm(modelStyle.getFontColor()) : norm(null),
            modelStyle != null ? bool(modelStyle.getStrike()) : Boolean.FALSE
        ));

        OrderCellStyle snStyle = styleMap.get("sn");
        m.put("sn", new CellStyleSnap(
            snStyle != null ? norm(snStyle.getBgColor()) : norm(null),
            snStyle != null ? norm(snStyle.getFontColor()) : norm(null),
            snStyle != null ? bool(snStyle.getStrike()) : Boolean.FALSE
        ));

        OrderCellStyle remarkStyle = styleMap.get("remark");
        m.put("remark", new CellStyleSnap(
            remarkStyle != null ? norm(remarkStyle.getBgColor()) : norm(null),
            remarkStyle != null ? norm(remarkStyle.getFontColor()) : norm(null),
            remarkStyle != null ? bool(remarkStyle.getStrike()) : Boolean.FALSE
        ));

        OrderCellStyle amountStyle = styleMap.get("amount");
        m.put("amount", new CellStyleSnap(
            amountStyle != null ? norm(amountStyle.getBgColor()) : norm(null),
            amountStyle != null ? norm(amountStyle.getFontColor()) : norm(null),
            amountStyle != null ? bool(amountStyle.getStrike()) : Boolean.FALSE
        ));

        return m;
    }

    private String norm(String c) {
        // null / 空字符串 / #FFFFFF / #FFF 统一视为白色，不提示变化
        if (c == null) return "#FFFFFF";
        String s = c.trim();
        if (s.isEmpty()) return "#FFFFFF";
        s = s.toUpperCase(Locale.ROOT);
        if ("#FFF".equals(s)) return "#FFFFFF";
        if ("#FFFFFF".equals(s)) return "#FFFFFF";
        return s;
    }

    // 与上一次导入快照对比（混合对齐：先 key(运单号+SN)，未命中则按行号），
    // 只返回一条记录级提示（选择第一个发生变化的列）；比较“格式变化 或 内容变化”。
    private Optional<Map<String, Object>> compareWithSessionSnapshot(OrderRecord r) {
        String key = styleKey(r);
        Map<String, CellStyleSnap> curStyle = buildStyleMap(r);
        Map<String, String> curValue = buildValueMap(r);

        List<String> order = Arrays.asList("tracking","model","sn","remark","amount");

        Map<String, CellStyleSnap> prevStyle = LAST_IMPORT_SNAPSHOT.get(key);
        Map<String, String> prevValue = LAST_VALUE_SNAPSHOT.get(key);
        boolean keyMatched = prevStyle != null && prevValue != null;

        if (!keyMatched && r.getExcelRowIndex() != null) {
            // 先尝试精确行号
            prevStyle = LAST_STYLE_BY_ROW.get(r.getExcelRowIndex());
            prevValue = LAST_VALUE_BY_ROW.get(r.getExcelRowIndex());
            keyMatched = prevStyle != null && prevValue != null;
            // 再尝试邻近行窗口（±2），抵抗小幅漂移
            if (!keyMatched) {
                for (int d = 1; d <= 2; d++) {
                    int up = r.getExcelRowIndex() - d;
                    int down = r.getExcelRowIndex() + d;
                    if (!keyMatched && up >= 0) {
                        prevStyle = LAST_STYLE_BY_ROW.get(up);
                        prevValue = LAST_VALUE_BY_ROW.get(up);
                        keyMatched = prevStyle != null && prevValue != null;
                    }
                    if (!keyMatched && down >= 0) {
                        prevStyle = LAST_STYLE_BY_ROW.get(down);
                        prevValue = LAST_VALUE_BY_ROW.get(down);
                        keyMatched = prevStyle != null && prevValue != null;
                    }
                    if (keyMatched) break;
                }
            }
        }
        if (!keyMatched) {
            String tKey = (r.getTrackingNumber() == null ? "" : r.getTrackingNumber().toUpperCase(Locale.ROOT));
            prevStyle = LAST_STYLE_BY_TRACKING.get(tKey);
            prevValue = LAST_VALUE_BY_TRACKING.get(tKey);
        }

        if (prevStyle == null || prevValue == null || prevStyle.isEmpty()) {
            // 首次：建立基线，不提示
            LAST_IMPORT_SNAPSHOT.put(key, curStyle);
            LAST_VALUE_SNAPSHOT.put(key, curValue);
            if (r.getExcelRowIndex() != null) {
                LAST_STYLE_BY_ROW.put(r.getExcelRowIndex(), curStyle);
                LAST_VALUE_BY_ROW.put(r.getExcelRowIndex(), curValue);
            }
            String tKey = (r.getTrackingNumber() == null ? "" : r.getTrackingNumber().toUpperCase(Locale.ROOT));
            LAST_STYLE_BY_TRACKING.put(tKey, curStyle);
            LAST_VALUE_BY_TRACKING.put(tKey, curValue);
            return Optional.empty();
        }

        String changedField = null;
        for (String f : order) {
            CellStyleSnap a = prevStyle.get(f);
            CellStyleSnap b = curStyle.get(f);
            boolean styleChanged = !Objects.equals(a == null ? null : a.getBg(),   b == null ? null : b.getBg())
                                || !Objects.equals(a == null ? null : a.getFont(), b == null ? null : b.getFont())
                                || !Objects.equals(a == null ? Boolean.FALSE : a.getStrike(), b == null ? Boolean.FALSE : b.getStrike());
            String va = prevValue.get(f);
            String vb = curValue.get(f);
            boolean valueChanged = !Objects.equals(va, vb);
            if (styleChanged || valueChanged) { changedField = f; break; }
        }

        // 更新基线为本次
        LAST_IMPORT_SNAPSHOT.put(key, curStyle);
        LAST_VALUE_SNAPSHOT.put(key, curValue);
        if (r.getExcelRowIndex() != null) {
            LAST_STYLE_BY_ROW.put(r.getExcelRowIndex(), curStyle);
            LAST_VALUE_BY_ROW.put(r.getExcelRowIndex(), curValue);
        }
        // 同步 tracking 兜底映射（避免下一次按 tracking 兜底对齐缺失）
        String tKey2 = (r.getTrackingNumber() == null ? "" : r.getTrackingNumber().toUpperCase(Locale.ROOT));
        LAST_STYLE_BY_TRACKING.put(tKey2, curStyle);
        LAST_VALUE_BY_TRACKING.put(tKey2, curValue);

        if (changedField == null) return Optional.empty();
        Map<String, Object> row = new HashMap<>();
        row.put("trackingNumber", r.getTrackingNumber());
        row.put("sn", r.getSn());
        row.put("field", changedField);
        // 样式 from/to
        CellStyleSnap a = prevStyle.get(changedField);
        CellStyleSnap b = curStyle.get(changedField);
        row.put("fromBg", a == null ? null : a.getBg());
        row.put("toBg",   b == null ? null : b.getBg());
        row.put("fromFont", a == null ? null : a.getFont());
        row.put("toFont",   b == null ? null : b.getFont());
        row.put("fromStrike", a == null ? Boolean.FALSE : a.getStrike());
        row.put("toStrike",   b == null ? Boolean.FALSE : b.getStrike());
        // 内容 from/to
        row.put("fromText", prevValue.get(changedField));
        row.put("toText",   curValue.get(changedField));
        return Optional.of(row);
    }

    private Map<String, String> buildValueMap(OrderRecord r) {
        Map<String, String> m = new HashMap<>();
        m.put("tracking", safeStr(r.getTrackingNumber()));
        m.put("model",    safeStr(r.getModel()));
        m.put("sn",       safeStr(r.getSn()));
        m.put("remark",   safeStr(r.getRemark()));
        m.put("amount",   r.getAmount() == null ? "" : r.getAmount().stripTrailingZeros().toPlainString());
        return m;
    }

    private String safeStr(String s) { return s == null ? "" : s.trim(); }

    /**
     * 基于 Excel 行号检测是否发生变化（仅行号对齐，不使用SN/运单号）
     * 比较内容与样式（颜色/加删除线）。
     * 同时更新该行的基线快照。
     * 返回：true 表示发生变化；false 表示未变化。
     */
    private boolean isRowChangedAndUpdateBaseline(OrderRecord r) {
        Integer row = r.getExcelRowIndex();
        Map<String, CellStyleSnap> curStyle = buildStyleMap(r);
        Map<String, String> curValue = buildValueMap(r);
        // 没有行号时，视为发生变化（无法判断），同时不写入行号快照
        if (row == null) {
            return true;
        }
        Map<String, CellStyleSnap> prevStyle = LAST_STYLE_BY_ROW.get(row);
        Map<String, String> prevValue = LAST_VALUE_BY_ROW.get(row);
        boolean changed = false;
        if (prevStyle == null || prevValue == null) {
            changed = true; // 首次出现，视为变化
        } else {
            // 字段顺序与 buildMap 保持一致
            List<String> order = Arrays.asList("tracking","model","sn","remark","amount");
            for (String f : order) {
                CellStyleSnap a = prevStyle.get(f);
                CellStyleSnap b = curStyle.get(f);
                boolean styleChanged = !Objects.equals(a == null ? null : a.getBg(),   b == null ? null : b.getBg())
                                    || !Objects.equals(a == null ? null : a.getFont(), b == null ? null : b.getFont())
                                    || !Objects.equals(a == null ? Boolean.FALSE : a.getStrike(), b == null ? Boolean.FALSE : b.getStrike());
                String va = prevValue.get(f);
                String vb = curValue.get(f);
                boolean valueChanged = !Objects.equals(va, vb);
                if (styleChanged || valueChanged) { changed = true; break; }
            }
        }
        // 更新该行的基线
        LAST_STYLE_BY_ROW.put(row, curStyle);
        LAST_VALUE_BY_ROW.put(row, curValue);
        return changed;
    }

    private boolean isChangedAndUpdateBaseline(OrderRecord r) {
        // 1. 首先查询数据库中是否存在相同运单号+SN 的最新记录
        OrderRecord dbLatest = null;
        List<OrderCellStyle> dbStyles = null;

        // 只要有运单号或SN其中之一，就尝试查询数据库
        if (StringUtils.hasText(r.getTrackingNumber()) || StringUtils.hasText(r.getSn())) {
            LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();

            // 构建查询条件：运单号和SN都要匹配（包括 null 的情况）
            if (StringUtils.hasText(r.getTrackingNumber())) {
                wrapper.eq(OrderRecord::getTrackingNumber, r.getTrackingNumber());
            } else {
                wrapper.isNull(OrderRecord::getTrackingNumber);
            }

            if (StringUtils.hasText(r.getSn())) {
                wrapper.eq(OrderRecord::getSn, r.getSn());
            } else {
                wrapper.isNull(OrderRecord::getSn);
            }

            wrapper.orderByDesc(OrderRecord::getId).last("LIMIT 1");
            dbLatest = orderRecordMapper.selectOne(wrapper);

            if (dbLatest != null) {
                // 查询该记录的样式
                dbStyles = orderCellStyleMapper.selectList(
                    new QueryWrapper<OrderCellStyle>().lambda()
                        .eq(OrderCellStyle::getOrderId, dbLatest.getId())
                );
            }
        }

        // 2. 如果数据库中存在记录，进行比较
        if (dbLatest != null) {
            Map<String, CellStyleSnap> curStyle = buildStyleMap(r);
            Map<String, String> curValue = buildValueMap(r);

            // 从数据库记录构建样式和内容
            Map<String, CellStyleSnap> dbStyleMap = buildStyleMapFromDb(dbLatest, dbStyles);
            Map<String, String> dbValueMap = buildValueMap(dbLatest);

            List<String> order = Arrays.asList("tracking","model","sn","remark","amount");
            boolean changed = false;

            for (String f : order) {
                CellStyleSnap a = dbStyleMap.get(f);
                CellStyleSnap b = curStyle.get(f);
                boolean styleChanged = !Objects.equals(a == null ? null : a.getBg(),   b == null ? null : b.getBg())
                                    || !Objects.equals(a == null ? null : a.getFont(), b == null ? null : b.getFont())
                                    || !Objects.equals(a == null ? Boolean.FALSE : a.getStrike(), b == null ? Boolean.FALSE : b.getStrike());
                String va = dbValueMap.get(f);
                String vb = curValue.get(f);
                boolean valueChanged = !Objects.equals(va, vb);
                if (styleChanged || valueChanged) {
                    changed = true;
                    break;
                }
            }

            // 仍然更新内存快照（用于会话内的快速比较）
            String key = styleKey(r);
            LAST_IMPORT_SNAPSHOT.put(key, curStyle);
            LAST_VALUE_SNAPSHOT.put(key, curValue);
            if (r.getExcelRowIndex() != null) {
                LAST_STYLE_BY_ROW.put(r.getExcelRowIndex(), curStyle);
                LAST_VALUE_BY_ROW.put(r.getExcelRowIndex(), curValue);
            }
            String tKey = (r.getTrackingNumber() == null ? "" : r.getTrackingNumber().toUpperCase(Locale.ROOT));
            LAST_STYLE_BY_TRACKING.put(tKey, curStyle);
            LAST_VALUE_BY_TRACKING.put(tKey, curValue);

            return changed;
        }

        // 3. 数据库中不存在，视为首次出现，返回 true（有变化）
        String key = styleKey(r);
        Map<String, CellStyleSnap> curStyle = buildStyleMap(r);
        Map<String, String> curValue = buildValueMap(r);

        // 更新内存快照
        LAST_IMPORT_SNAPSHOT.put(key, curStyle);
        LAST_VALUE_SNAPSHOT.put(key, curValue);
        if (r.getExcelRowIndex() != null) {
            LAST_STYLE_BY_ROW.put(r.getExcelRowIndex(), curStyle);
            LAST_VALUE_BY_ROW.put(r.getExcelRowIndex(), curValue);
        }
        String tKey = (r.getTrackingNumber() == null ? "" : r.getTrackingNumber().toUpperCase(Locale.ROOT));
        LAST_STYLE_BY_TRACKING.put(tKey, curStyle);
        LAST_VALUE_BY_TRACKING.put(tKey, curValue);

        return true;
    }

    private final OrderRecordMapper orderRecordMapper;
    private final SettlementService settlementService;
    private final UserSubmissionMapper userSubmissionMapper;
    private final OrderCellStyleMapper orderCellStyleMapper;

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public Map<String, Object> importOrders(MultipartFile file, String operator) {
        try {
            List<OrderRecord> records = ExcelHelper.readOrders(file.getInputStream(), operator);
            Map<String, List<String>> duplicateSnInFile = findDuplicateSnInList(records);
            List<OrderRecord> needSettlement = new ArrayList<>();
            // 收集样式变更

            int skippedUnchanged = 0;
            List<Integer> skippedRows = new ArrayList<>();
            List<OrderRecord> changedRecords = new ArrayList<>();
            for (OrderRecord record : records) {
                record.setImported(Boolean.TRUE);
                if (record.getTrackingNumber() != null) {
                    record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
                }
                // 变更检测（优先使用“运单号+SN”对齐；未命中再按行号以及 tracking 兜底）
                // 内容/格式/颜色 任一变化才写库；首次见到视为变化
                boolean changed = isChangedAndUpdateBaseline(record);
                if (!changed) {
                    skippedUnchanged++;
                    if (record.getExcelRowIndex() != null) skippedRows.add(record.getExcelRowIndex());
                    // 未变化：不插入、不更新样式、不生成结算待处理
                    continue;
                }
                // 直接插入，不做唯一性检查，允许重复数据
                insertDirectly(record);
                // 持久化最新样式（B~F列）供刷新后展示
                persistOrderStyles(record);
                changedRecords.add(record);
                if (hasSubmission(record.getTrackingNumber())) {
                    needSettlement.add(record);
                }
            }
            if (!needSettlement.isEmpty()) {
                settlementService.createPending(needSettlement, true);
            }
            Map<String, Object> report = new HashMap<>();
            if (!duplicateSnInFile.isEmpty()) {
                report.put("duplicateSn", duplicateSnInFile.keySet());
                report.put("duplicateSnDetail", duplicateSnInFile);
            }
            // 统计信息：跳过未变化的行
            report.put("skippedUnchanged", skippedUnchanged);
            report.put("skippedRows", skippedRows);
            report.put("importedCount", changedRecords.size());
            // 返回样式信息（仅变化的用于即时展示）
            List<Map<String, Object>> styles = changedRecords.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("trackingNumber", r.getTrackingNumber());
                m.put("sn", r.getSn());
                m.put("trackingBgColor", r.getTrackingBgColor());
                m.put("trackingFontColor", r.getTrackingFontColor());
                m.put("trackingStrike", r.getTrackingStrike());
                m.put("modelBgColor", r.getModelBgColor());
                m.put("modelFontColor", r.getModelFontColor());
                m.put("modelStrike", r.getModelStrike());
                m.put("snBgColor", r.getSnBgColor());
                m.put("snFontColor", r.getSnFontColor());
                m.put("snStrike", r.getSnStrike());
                m.put("amountBgColor", r.getAmountBgColor());
                m.put("amountFontColor", r.getAmountFontColor());
                m.put("amountStrike", r.getAmountStrike());
                m.put("remarkBgColor", r.getRemarkBgColor());
                m.put("remarkFontColor", r.getRemarkFontColor());
                m.put("remarkStrike", r.getRemarkStrike());
                return m;
            }).toList();
            report.put("styles", styles);

            return report;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 解析失败");
        }
    }

    @Override
    @Cacheable(value = "orders",
                   key = "'page1:' + #request.size + ':' + #request.startDate + ':' + #request.endDate + ':' + #request.category + ':' + #request.status + ':' + #request.keyword + ':' + #request.ownerUsername + ':' + #request.sortBy + ':' + #request.sortOrder", 
                   condition = "#request.page == 1")
    public IPage<OrderRecord> query(OrderFilterRequest request) {
        Page<OrderRecord> page = Page.of(request.getPage(), request.getSize());
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();

        if (request.getStartDate() != null) {
            wrapper.ge(OrderRecord::getOrderDate, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(OrderRecord::getOrderDate, request.getEndDate());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            wrapper.eq(OrderRecord::getCategory, request.getCategory());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(OrderRecord::getStatus, request.getStatus());
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            // 使用全文索引进行关键字搜索，性能远高于 LIKE
            String keyword = request.getKeyword();
            // 在布尔模式下，+ 表示必须包含，* 是通配符
            // 这里简单处理，在每个词后加 * 实现前缀匹配
            String booleanModeKeyword = Arrays.stream(keyword.split("\\s+"))
                .filter(s -> !s.isEmpty())
                .map(s -> "+" + s + "*")
                .collect(Collectors.joining(" "));

            wrapper.apply("MATCH(tracking_number, sn, model) AGAINST({0} IN BOOLEAN MODE)", booleanModeKeyword);
        }

        // 归属用户筛选（基于 user_submission 最新记录的 ownerUsername/username）
        if (StringUtils.hasText(request.getOwnerUsername())) {
            String targetOwner = request.getOwnerUsername().trim();
            // 1) 找到该用户相关的提交记录（作为 owner 或 submitter）
            LambdaQueryWrapper<UserSubmission> first = new LambdaQueryWrapper<>();
            first.select(UserSubmission::getTrackingNumber)
                .and(w -> w.eq(UserSubmission::getOwnerUsername, targetOwner).or().eq(UserSubmission::getUsername, targetOwner));
            List<UserSubmission> ownerRelated = userSubmissionMapper.selectList(first);
            Set<String> relatedTns = ownerRelated.stream()
                .map(UserSubmission::getTrackingNumber)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

            if (relatedTns.isEmpty()) {
                // 无匹配，直接返回空
                return Page.of(request.getPage(), request.getSize());
            }

            // 2) 对这些运单号查询其最新的提交记录
            LambdaQueryWrapper<UserSubmission> latestQ = new LambdaQueryWrapper<>();
            latestQ.in(UserSubmission::getTrackingNumber, relatedTns)
                .select(UserSubmission::getTrackingNumber, UserSubmission::getOwnerUsername, UserSubmission::getUsername, UserSubmission::getCreatedAt);
            List<UserSubmission> latestCandidates = userSubmissionMapper.selectList(latestQ);

            Map<String, UserSubmission> latestMap = new HashMap<>();
            for (UserSubmission sub : latestCandidates) {
                String tn = sub.getTrackingNumber();
                if (!StringUtils.hasText(tn)) continue;
                UserSubmission prev = latestMap.get(tn);
                if (prev == null || (sub.getCreatedAt() != null && (prev.getCreatedAt() == null || sub.getCreatedAt().isAfter(prev.getCreatedAt())))) {
                    latestMap.put(tn, sub);
                }
            }

            // 3) 仅保留“最新记录归属人 == 目标用户”的运单号
            Set<String> finalTns = latestMap.entrySet().stream()
                .filter(e -> {
                    UserSubmission s = e.getValue();
                    String owner = StringUtils.hasText(s.getOwnerUsername()) ? s.getOwnerUsername().trim() : s.getUsername();
                    return targetOwner.equals(owner);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

            if (finalTns.isEmpty()) {
                return Page.of(request.getPage(), request.getSize());
            }

            wrapper.in(OrderRecord::getTrackingNumber, finalTns);
        }

        // 动态排序处理
        if (StringUtils.hasText(request.getSortBy()) && StringUtils.hasText(request.getSortOrder())) {
            boolean isAsc = "asc".equalsIgnoreCase(request.getSortOrder());
            // 映射前端字段名到数据库字段
            switch (request.getSortBy().toLowerCase()) {
                case "status":
                    if (isAsc) wrapper.orderByAsc(OrderRecord::getStatus);
                    else wrapper.orderByDesc(OrderRecord::getStatus);
                    break;
                case "orderdate":
                    if (isAsc) wrapper.orderByAsc(OrderRecord::getOrderDate);
                    else wrapper.orderByDesc(OrderRecord::getOrderDate);
                    break;
                case "ordertime":
                    if (isAsc) wrapper.orderByAsc(OrderRecord::getOrderTime);
                    else wrapper.orderByDesc(OrderRecord::getOrderTime);
                    break;
                case "trackingnumber":
                    if (isAsc) wrapper.orderByAsc(OrderRecord::getTrackingNumber);
                    else wrapper.orderByDesc(OrderRecord::getTrackingNumber);
                    break;
                case "model":
                    if (isAsc) wrapper.orderByAsc(OrderRecord::getModel);
                    else wrapper.orderByDesc(OrderRecord::getModel);
                    break;
                case "sn":
                    if (isAsc) wrapper.orderByAsc(OrderRecord::getSn);
                    else wrapper.orderByDesc(OrderRecord::getSn);
                    break;
                default:
                    // 默认按日期降序
                    wrapper.orderByDesc(OrderRecord::getOrderDate);
            }
        } else {
            // 没有指定排序时，默认按日期降序
            wrapper.orderByDesc(OrderRecord::getOrderDate);
        }

        IPage<OrderRecord> result = orderRecordMapper.selectPage(page, wrapper);

        // 关联查询归属用户信息
        attachOwnerInfo(result.getRecords());
        // 回填持久化样式
        attachStyles(result.getRecords());

        return result;
    }

    /**
     * 为订单列表关联归属用户信息
     * 规则：优先取 user_submission.ownerUsername（管理员可代提交的目标用户）；
     * 若为空则回退为 username。若存在多条提交，按 createdAt 最新一条为准。
     */
    private void attachOwnerInfo(List<OrderRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        // 收集所有运单号
        Set<String> trackingNumbers = records.stream()
            .map(OrderRecord::getTrackingNumber)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        if (trackingNumbers.isEmpty()) {
            return;
        }

        // 批量查询用户提交记录，获取归属用户
        LambdaQueryWrapper<UserSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserSubmission::getTrackingNumber, trackingNumbers)
            .select(UserSubmission::getTrackingNumber, UserSubmission::getOwnerUsername, UserSubmission::getUsername, UserSubmission::getCreatedAt);
        List<UserSubmission> submissions = userSubmissionMapper.selectList(wrapper);

        // 按 trackingNumber 分组，选择最新一条记录的 ownerUsername（为空则用 username）
        Map<String, String> ownerMap = new HashMap<>();
        Map<String, UserSubmission> latestMap = new HashMap<>();
        for (UserSubmission sub : submissions) {
            String tn = sub.getTrackingNumber();
            if (!StringUtils.hasText(tn)) continue;
            UserSubmission prev = latestMap.get(tn);
            if (prev == null || (sub.getCreatedAt() != null && (prev.getCreatedAt() == null || sub.getCreatedAt().isAfter(prev.getCreatedAt())))) {
                latestMap.put(tn, sub);
            }
        }
        latestMap.forEach((tn, sub) -> {
            String owner = StringUtils.hasText(sub.getOwnerUsername()) ? sub.getOwnerUsername().trim() : sub.getUsername();
            if (StringUtils.hasText(owner)) {
                ownerMap.put(tn, owner);
            }
        });

        // 为每条订单设置归属用户
        records.forEach(record -> {
            if (StringUtils.hasText(record.getTrackingNumber())) {
                record.setOwnerUsername(ownerMap.get(record.getTrackingNumber()));
            }
        });
    }

    private void persistOrderStyles(OrderRecord r) {
        if (r.getId() == null) return;
        // 读取旧样式，按字段合并：仅覆盖“新传入的非空值/显式为 true 的删除线”，其余沿用旧值
        List<OrderCellStyle> old = orderCellStyleMapper.selectList(new QueryWrapper<OrderCellStyle>().lambda().eq(OrderCellStyle::getOrderId, r.getId()));
        Map<String, OrderCellStyle> oldMap = new HashMap<>();
        for (OrderCellStyle s : old) oldMap.put(s.getField(), s);

        mergeStyle(r.getId(), "tracking", oldMap.get("tracking"), r.getTrackingBgColor(), r.getTrackingFontColor(), r.getTrackingStrike());
        mergeStyle(r.getId(), "model",    oldMap.get("model"),    r.getModelBgColor(),    r.getModelFontColor(),    r.getModelStrike());
        mergeStyle(r.getId(), "sn",       oldMap.get("sn"),       r.getSnBgColor(),       r.getSnFontColor(),       r.getSnStrike());
        mergeStyle(r.getId(), "amount",   oldMap.get("amount"),   r.getAmountBgColor(),   r.getAmountFontColor(),   r.getAmountStrike());
        mergeStyle(r.getId(), "remark",   oldMap.get("remark"),   r.getRemarkBgColor(),   r.getRemarkFontColor(),   r.getRemarkStrike());
    }

    // 合并策略：
    // - bg/fg：新值非空则覆盖；否则保留旧值
    // - strike：只有新值为 true 才覆盖为 true；否则保留旧值（避免无意清空旧的删除线）
    // - 如果最终三项都为空/false，则：若原本有记录则保留不变；若原本没有记录则不写入
    private void mergeStyle(Long orderId, String field, OrderCellStyle old,
                            String newBg, String newFg, Boolean newStrike) {
        String bg = (newBg != null && !newBg.isBlank()) ? newBg : (old == null ? null : old.getBgColor());
        String fg = (newFg != null && !newFg.isBlank()) ? newFg : (old == null ? null : old.getFontColor());
        Boolean strike = (newStrike != null && newStrike) ? Boolean.TRUE : (old == null ? Boolean.FALSE : old.getStrike());

        boolean has = (bg != null && !bg.isBlank()) || (fg != null && !fg.isBlank()) || Boolean.TRUE.equals(strike);
        if (!has) {
            // 没有任何样式：如果原来有记录，保持不动（不删除）；如果没有，什么也不做
            return;
        }
        if (old == null) {
            OrderCellStyle s = new OrderCellStyle();
            s.setOrderId(orderId);
            s.setField(field);
            s.setBgColor(bg);
            s.setFontColor(fg);
            s.setStrike(Boolean.TRUE.equals(strike));
            orderCellStyleMapper.insert(s);
        } else {
            boolean changed = !Objects.equals(bg, old.getBgColor())
                           || !Objects.equals(fg, old.getFontColor())
                           || !Objects.equals(Boolean.TRUE.equals(strike), Boolean.TRUE.equals(old.getStrike()));
            if (changed) {
                old.setBgColor(bg);
                old.setFontColor(fg);
                old.setStrike(Boolean.TRUE.equals(strike));
                orderCellStyleMapper.updateById(old);
            }
        }
    }

    private void attachStyles(List<OrderRecord> list) {
        if (CollectionUtils.isEmpty(list)) return;
        List<Long> ids = list.stream().map(OrderRecord::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        List<OrderCellStyle> rows = orderCellStyleMapper.selectList(new QueryWrapper<OrderCellStyle>().lambda().in(OrderCellStyle::getOrderId, ids));
        Map<Long, List<OrderCellStyle>> byOrder = rows.stream().collect(Collectors.groupingBy(OrderCellStyle::getOrderId));
        list.forEach(r -> {
            List<OrderCellStyle> styles = byOrder.getOrDefault(r.getId(), List.of());
            for (OrderCellStyle s : styles) {
                switch (s.getField()) {
                    case "tracking":
                        r.setTrackingBgColor(s.getBgColor());
                        r.setTrackingFontColor(s.getFontColor());
                        r.setTrackingStrike(Boolean.TRUE.equals(s.getStrike()));
                        break;
                    case "model":
                        r.setModelBgColor(s.getBgColor());
                        r.setModelFontColor(s.getFontColor());
                        r.setModelStrike(Boolean.TRUE.equals(s.getStrike()));
                        break;
                    case "sn":
                        r.setSnBgColor(s.getBgColor());
                        r.setSnFontColor(s.getFontColor());
                        r.setSnStrike(Boolean.TRUE.equals(s.getStrike()));
                        break;
                    case "amount":
                        r.setAmountBgColor(s.getBgColor());
                        r.setAmountFontColor(s.getFontColor());
                        r.setAmountStrike(Boolean.TRUE.equals(s.getStrike()));
                        break;
                    case "remark":
                        r.setRemarkBgColor(s.getBgColor());
                        r.setRemarkFontColor(s.getFontColor());
                        r.setRemarkStrike(Boolean.TRUE.equals(s.getStrike()));
                        break;
                }
            }
        });
    }

    private List<Map<String, Object>> compareStyles(OrderRecord r, List<OrderCellStyle> oldStyles) {
        // 如果没有历史样式（新建），不提示变动
        if (oldStyles == null || oldStyles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, OrderCellStyle> oldMap = new HashMap<>();
        for (OrderCellStyle s : oldStyles) {
            oldMap.put(s.getField(), s);
        }
        // 新样式来自本次解析的 transient 字段
        List<Map<String, Object>> diffs = new ArrayList<>();
        compareOne(diffs, r, oldMap.get("tracking"), "tracking",
            r.getTrackingBgColor(), r.getTrackingFontColor(), r.getTrackingStrike());
        compareOne(diffs, r, oldMap.get("model"), "model",
            r.getModelBgColor(), r.getModelFontColor(), r.getModelStrike());
        compareOne(diffs, r, oldMap.get("sn"), "sn",
            r.getSnBgColor(), r.getSnFontColor(), r.getSnStrike());
        compareOne(diffs, r, oldMap.get("remark"), "remark",
            r.getRemarkBgColor(), r.getRemarkFontColor(), r.getRemarkStrike());
        compareOne(diffs, r, oldMap.get("amount"), "amount",
            r.getAmountBgColor(), r.getAmountFontColor(), r.getAmountStrike());
        return diffs;
    }

    private void compareOne(List<Map<String, Object>> out, OrderRecord r, OrderCellStyle oldS,
                             String field, String newBg, String newFg, Boolean newStrike) {
        String oldBg = oldS == null ? null : oldS.getBgColor();
        String oldFg = oldS == null ? null : oldS.getFontColor();
        Boolean oldSt = oldS == null ? null : oldS.getStrike();
        boolean changed = false;
        if (!Objects.equals(normalizeColor(oldBg), normalizeColor(newBg))) changed = true;
        if (!Objects.equals(normalizeColor(oldFg), normalizeColor(newFg))) changed = true;
        if (!Objects.equals(bool(oldSt), bool(newStrike))) changed = true;
        if (changed) {
            Map<String, Object> m = new HashMap<>();
            m.put("trackingNumber", r.getTrackingNumber());
            m.put("sn", r.getSn());
            m.put("field", field);
            m.put("fromBg", normalizeColor(oldBg));
            m.put("toBg", normalizeColor(newBg));
            m.put("fromFont", normalizeColor(oldFg));
            m.put("toFont", normalizeColor(newFg));
            m.put("fromStrike", bool(oldSt));
            m.put("toStrike", bool(newStrike));
            out.add(m);
        }
    }

    private String normalizeColor(String c) {
        if (c == null) return null;
        String s = c.trim();
        if (s.isEmpty()) return null;
        // 统一大写#RRGGBB
        if (s.startsWith("#") && s.length() == 7) return s.toUpperCase(Locale.ROOT);
        return s.toUpperCase(Locale.ROOT);
    }

    private Boolean bool(Boolean b) { return b != null && b; }

    private Map<String, List<String>> findDuplicateSnInList(List<OrderRecord> records) {
        Map<String, List<String>> map = new HashMap<>();
        records.stream()
            .filter(r -> StringUtils.hasText(r.getSn()))
            .forEach(r -> {
                String sn = r.getSn().trim();
                map.computeIfAbsent(sn, key -> new ArrayList<>()).add(
                    StringUtils.hasText(r.getTrackingNumber()) ? r.getTrackingNumber().trim() : "(无单号)"
                );
            });
        return map.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderRecord create(OrderCreateRequest request, String operator) {
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getSn, request.getSn());
        OrderRecord existed = orderRecordMapper.selectOne(wrapper);
        if (existed != null) {
            throw new BusinessException(ErrorCode.DUPLICATE, "单号已存在，请勿重复提交");
        }

        OrderRecord record = new OrderRecord();
        record.setOrderDate(request.getOrderDate());
        if (record.getOrderDate() != null && request.getOrderTime() == null) {
            record.setOrderTime(request.getOrderDate().atStartOfDay());
        } else {
            record.setOrderTime(request.getOrderTime());
        }
        if (record.getOrderTime() == null) {
            record.setOrderTime(LocalDateTime.now());
        }
        if (record.getOrderDate() == null && record.getOrderTime() != null) {
            record.setOrderDate(record.getOrderTime().toLocalDate());
        }
        record.setTrackingNumber(request.getTrackingNumber());
        record.setModel(request.getModel());
        record.setSn(request.getSn());
        record.setRemark(request.getRemark());
        record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
        record.setAmount(request.getAmount());
        record.setCurrency("CNY");
        record.setStatus("UNPAID");
        record.setCreatedBy(operator);
        record.setImported(Boolean.TRUE);
        orderRecordMapper.insert(record);
        if (hasSubmission(record.getTrackingNumber())) {
            settlementService.createPending(List.of(record), true);
        }
        return record;
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public void updateStatus(Long id, String status) {
        OrderRecord record = orderRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        record.setStatus(status);
        orderRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public void updateAmount(Long id, OrderAmountRequest request) {
        OrderRecord record = orderRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        record.setAmount(request.getAmount());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            record.setCurrency(request.getCurrency());
        }
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }
        orderRecordMapper.updateById(record);
    }

    @Override
    public List<OrderRecord> findByTracking(List<String> trackingNumbers) {
        if (CollectionUtils.isEmpty(trackingNumbers)) {
            return List.of();
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        var unique = trackingNumbers.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(str -> !str.isEmpty())
            .collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return List.of();
        }
        wrapper.in(OrderRecord::getTrackingNumber, unique);
        return orderRecordMapper.selectList(wrapper);
    }

    @Override
    public List<OrderRecord> search(List<String> keywords) {
        if (CollectionUtils.isEmpty(keywords)) {
            return List.of();
        }
        //// 去掉 null / "" / "   "  去空格 再次过滤空字符串 去重
        var unique = keywords.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .filter(str -> !str.isEmpty())
            .collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return List.of();
        }
        List<String> prefixes = unique.stream()
            .map(String::trim)
            .map(str -> str.replaceAll("-+$", ""))
            .filter(str -> str.length() >= 6)
            .filter(str -> !str.contains("-"))
            .toList();
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.in(OrderRecord::getTrackingNumber, unique)
            .or()
            .in(OrderRecord::getSn, unique));
        if (!prefixes.isEmpty()) {
            wrapper.or(w -> {
                for (int i = 0; i < prefixes.size(); i++) {
                    w.likeRight(OrderRecord::getTrackingNumber, prefixes.get(i) + "-");
                    if (i < prefixes.size() - 1) {
                        w.or();
                    }
                }
            });
        }
        //等价 AND (
        //  tracking_number IN (?,?,?,?)
        //  OR sn IN (?,?,?,?)
        //  OR tracking_number LIKE 'PREFIX-%' ...
        //)
        List<OrderRecord> records = orderRecordMapper.selectList(wrapper);

        // 关联查询归属用户信息
        attachOwnerInfo(records);
        // 回填持久化样式
        attachStyles(records);

        return records;
    }

    @Override
    @CacheEvict(value = {"orders", "orderDetail"}, allEntries = true)
    @Transactional
    public List<OrderRecord> syncFromThirdParty(BatchFetchRequest request, String operator) {
        if (request == null || CollectionUtils.isEmpty(request.getTrackingNumbers())) {
            return List.of();
        }
        List<OrderRecord> existing = findByTracking(request.getTrackingNumbers());
        List<String> existNumbers = existing.stream()
            .map(OrderRecord::getTrackingNumber)
            .collect(Collectors.toList());

        List<OrderRecord> created = new ArrayList<>();
        for (String tracking : request.getTrackingNumbers()) {
            if (existNumbers.contains(tracking)) {
                continue;
            }
            OrderRecord record = new OrderRecord();
            record.setTrackingNumber(tracking);
            record.setOrderDate(LocalDate.now());
            record.setOrderTime(LocalDateTime.now());
            record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
            record.setStatus("UNPAID");
            BigDecimal autoAmount = request.getManualAmount() != null ? request.getManualAmount() : BigDecimal.ZERO;
            record.setAmount(autoAmount);
            record.setCurrency("CNY");
            record.setRemark("自动抓取生成");
            record.setCreatedBy(operator);
            record.setSn(tracking);
            record.setImported(Boolean.TRUE);
            orderRecordMapper.insert(record);
            existNumbers.add(tracking);
            created.add(record);
        }
        if (!created.isEmpty()) {
            settlementService.createPending(created, true);
        }
        return created;
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderRecord update(Long id, OrderUpdateRequest request) {
        OrderRecord record = orderRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (StringUtils.hasText(request.getTrackingNumber())) {
            record.setTrackingNumber(request.getTrackingNumber().trim());
            record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
        }
        if (StringUtils.hasText(request.getModel())) {
            record.setModel(request.getModel().trim());
        }
        if (StringUtils.hasText(request.getSn())) {
            record.setSn(request.getSn().trim());
        }
        if (request.getAmount() != null) {
            record.setAmount(request.getAmount());
        }
        if (StringUtils.hasText(request.getStatus())) {
            record.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }
        record.setCurrency("CNY");
        orderRecordMapper.updateById(record);
        settlementService.syncFromOrder(record);
        return record;
    }

    @Override
    public List<OrderCategoryStats> listCategoryStats(OrderFilterRequest request) {
        QueryWrapper<OrderRecord> wrapper = new QueryWrapper<>();
        if (request.getStartDate() != null) {
            wrapper.ge("order_date", request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le("order_date", request.getEndDate());
        }
        if (StringUtils.hasText(request.getCategory())) {
            wrapper.eq("category", request.getCategory());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq("status", request.getStatus());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w.like("tracking_number", request.getKeyword())
                .or().like("sn", request.getKeyword())
                .or().like("model", request.getKeyword()));
        }
        wrapper.select("COALESCE(category, '未分配') AS category_name", "COUNT(*) AS total_count");
        wrapper.groupBy("COALESCE(category, '未分配')");
        List<Map<String, Object>> rows = orderRecordMapper.selectMaps(wrapper);
        return rows.stream().map(row -> {
            OrderCategoryStats stats = new OrderCategoryStats();
            Object nameObj = row.get("category_name");
            stats.setCategory(nameObj == null ? "未分配" : nameObj.toString());
            Object countObj = row.get("total_count");
            stats.setCount(countObj == null ? 0 : ((Number) countObj).longValue());
            return stats;
        }).collect(Collectors.toList());
    }

    /**
     * 直接插入订单记录，不做任何唯一性检查
     * 允许相同 SN 或 trackingNumber 的多条记录存在
     * 如果数据库仍有唯一约束导致插入失败，会捕获异常并继续处理
     */
    private void insertDirectly(OrderRecord incoming) {
        // 处理日期和时间
        if (incoming.getOrderDate() == null && incoming.getOrderTime() != null) {
            incoming.setOrderDate(incoming.getOrderTime().toLocalDate());
        }
        if (incoming.getOrderTime() == null && incoming.getOrderDate() != null) {
            incoming.setOrderTime(incoming.getOrderDate().atStartOfDay());
        }

        // 截断过长的字段，防止数据库错误
        if (StringUtils.hasText(incoming.getModel()) && incoming.getModel().length() > 50) {
            incoming.setModel(incoming.getModel().substring(0, 50));
        }
        if (StringUtils.hasText(incoming.getTrackingNumber()) && incoming.getTrackingNumber().length() > 64) {
            incoming.setTrackingNumber(incoming.getTrackingNumber().substring(0, 64));
        }
        if (StringUtils.hasText(incoming.getRemark()) && incoming.getRemark().length() > 255) {
            incoming.setRemark(incoming.getRemark().substring(0, 255));
        }

        // 设置默认值
        if (incoming.getStatus() == null) {
            incoming.setStatus("UNPAID");
        }
        if (incoming.getCurrency() == null) {
            incoming.setCurrency("CNY");
        }
        if (incoming.getImported() == null) {
            incoming.setImported(Boolean.TRUE);
        }

        // 直接插入，不做任何去重检查
        // 如果数据库仍有唯一约束，捕获异常并继续处理（不中断导入流程）
        try {
            orderRecordMapper.insert(incoming);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 如果数据库仍有唯一约束导致插入失败，记录日志但继续处理
            // 建议执行 remove_unique_constraint.sql 迁移脚本删除唯一约束
            System.err.println("警告: 插入记录失败（可能因唯一约束），跳过该记录: " + 
                "trackingNumber=" + incoming.getTrackingNumber() + 
                ", sn=" + incoming.getSn() + 
                ", 错误: " + e.getMessage());
            // 不抛出异常，继续处理下一条记录
        }
    }

    /**
     * 根据 SN 和 trackingNumber 进行 upsert（保留此方法供其他场景使用，但导入不再使用）
     * @deprecated 导入功能已改为直接插入，不再使用此方法
     */
    @Deprecated
    private void upsertBySn(OrderRecord incoming) {
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getSn, incoming.getSn())
               .eq(StringUtils.hasText(incoming.getTrackingNumber()), OrderRecord::getTrackingNumber, incoming.getTrackingNumber());
        OrderRecord existed = orderRecordMapper.selectOne(wrapper);
        if (incoming.getOrderDate() == null && incoming.getOrderTime() != null) {
            incoming.setOrderDate(incoming.getOrderTime().toLocalDate());
        }
        if (incoming.getOrderTime() == null && incoming.getOrderDate() != null) {
            incoming.setOrderTime(incoming.getOrderDate().atStartOfDay());
        }

        // 截断过长的字段，防止数据库错误
        if (StringUtils.hasText(incoming.getModel()) && incoming.getModel().length() > 50) {
            incoming.setModel(incoming.getModel().substring(0, 50));
        }
        if (StringUtils.hasText(incoming.getTrackingNumber()) && incoming.getTrackingNumber().length() > 64) {
            incoming.setTrackingNumber(incoming.getTrackingNumber().substring(0, 64));
        }
        if (StringUtils.hasText(incoming.getRemark()) && incoming.getRemark().length() > 255) {
            incoming.setRemark(incoming.getRemark().substring(0, 255));
        }

        incoming.setImported(Boolean.TRUE);
        if (existed == null) {
            orderRecordMapper.insert(incoming);
        } else {
            incoming.setId(existed.getId());
            orderRecordMapper.updateById(incoming);
        }
    }

    private boolean hasSubmission(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return false;
        }
        LambdaQueryWrapper<UserSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubmission::getTrackingNumber, trackingNumber.trim())
            .ne(UserSubmission::getStatus, "COMPLETED");
        return userSubmissionMapper.selectCount(wrapper) > 0;
    }
}

