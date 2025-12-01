package com.example.demo.common.util;

import com.example.demo.hardware.dto.HardwarePriceExcelParseResult;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.util.TrackingCategoryUtil;
import com.example.demo.settlement.entity.SettlementRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.hssf.usermodel.HSSFPalette;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.util.CellRangeAddress;

public final class ExcelHelper {

    // 样式采集开关：默认仅采集单元格自身填充色与删除线，忽略条件格式/表格条纹，避免误判导致“未导入也上色”
    private static final boolean ENABLE_CONDITIONAL_BG = false;
    private static final boolean ENABLE_TABLE_STRIPE_BG = false;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern EXCEL_TEXT_PREFIX = Pattern.compile("^[='‘’“”\"\u200B\u200E\uFEFF]+");

    private ExcelHelper() {
    }

    public static HardwarePriceExcelParseResult readHardwarePrices(InputStream inputStream, LocalDate priceDate, String operator) throws IOException {
        HardwarePriceExcelParseResult result = new HardwarePriceExcelParseResult();
        if (priceDate == null) {
            result.addError("未识别到日期");
            return result;
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || rowHasStrikethrough(row)) {
                    continue;
                }
                String itemName = readString(row.getCell(0));
                BigDecimal parsedPrice = parseDecimal(row.getCell(1));
                boolean rowEmpty = (itemName == null || itemName.isBlank()) && parsedPrice == null;
                if (rowEmpty) {
                    continue;
                }
                if ("型号".equalsIgnoreCase(itemName == null ? "" : itemName.trim())) {
                    continue;
                }
                result.setTotalRows(result.getTotalRows() + 1);
                if (itemName == null || itemName.isBlank()) {
                    result.addError("第" + (i + 1) + "行型号为空，已跳过");
                    continue;
                }
                BigDecimal price = parsedPrice; // 保持空值为 null，不再默认 0
                HardwarePrice record = new HardwarePrice();
                record.setPriceDate(priceDate);
                record.setItemName(itemName.trim());
                record.setPrice(price);
                record.setCreatedBy(operator);
                result.getRows().add(record);
            }
        }
        return result;
    }

    public static List<OrderRecord> readOrders(InputStream inputStream, String operator) throws IOException {
        List<OrderRecord> result = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            LocalDateTime lastDateTime = null;
            String lastTracking = null;
            int startRow = detectDataStartRow(sheet);
            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int cellCount = row.getLastCellNum();
                boolean simpleFormat = cellCount <= 5;
                if (simpleFormat) {
                    LocalDateTime dateTime = parseDateTime(row.getCell(0));
                    if (dateTime == null) {
                        dateTime = lastDateTime;
                    } else {
                        lastDateTime = dateTime;
                    }
                    Cell trackingCell = row.getCell(1);
                    String tracking = normalizeTracking(readString(trackingCell));
                    if (tracking == null || tracking.isBlank()) {
                        tracking = lastTracking;
                    } else {
                        lastTracking = tracking;
                    }
                    Cell modelCell = row.getCell(2);
                    Cell snCell = row.getCell(3);
                    Cell remarkCellE = row.getCell(4);
                    String model = readString(modelCell);
                    String sn = readString(snCell);
                    if (sn == null || sn.isBlank() || tracking == null || tracking.isBlank()) {
                        continue;
                    }
                    OrderRecord record = new OrderRecord();
                    record.setOrderTime(dateTime);
                    if (dateTime != null) {
                        record.setOrderDate(dateTime.toLocalDate());
                    }
                    record.setTrackingNumber(tracking);
                    record.setModel(model);
                    record.setSn(sn);
                    // 备注位于 E 列（简单模板下），考虑合并单元格
                    record.setRemark(readMergedAwareString(sheet, i, 4));
                    // 金额不从 Excel 导入（由结账管理写入）
                    record.setAmount(null);
                    record.setStatus("UNPAID");
                    record.setCurrency("CNY");
                    record.setCategory(TrackingCategoryUtil.resolve(tracking));
                    record.setCreatedBy(operator);

                    // 样式采集（简单列：B-E -> tracking/model/sn/remark），不处理金额样式；备注样式考虑合并单元格
                    Cell remarkStyleCell = mergedTopLeftCell(sheet, i, 4);
                    applyCellStyleToRecord(record, trackingCell, modelCell, snCell, null, remarkStyleCell);

                    // 行号基准（用于位置对齐）
                    record.setExcelRowIndex(i - startRow);
                    record.setExcelRowIndex(i - startRow);
                    result.add(record);
                } else {
                    LocalDateTime dateTime = parseDateTime(row.getCell(0));
                    if (dateTime == null) {
                        dateTime = lastDateTime;
                    } else {
                        lastDateTime = dateTime;
                    }
                    Cell trackingCell = row.getCell(1);
                    String tracking = normalizeTracking(readString(trackingCell));
                    if (tracking == null || tracking.isBlank()) {
                        tracking = lastTracking;
                    } else {
                        lastTracking = tracking;
                    }
                    OrderRecord record = new OrderRecord();
                    if (dateTime != null) {
                        record.setOrderTime(dateTime);
                        record.setOrderDate(dateTime.toLocalDate());
                    }
                    record.setTrackingNumber(tracking);
                    Cell modelCell = row.getCell(2);
                    Cell snCell = row.getCell(3);
                    // 备注在 E 和 F 列，合并写入 remark（合并单元格兼容）
                    String remarkE = readMergedAwareString(sheet, i, 4);
                    String remarkF = readMergedAwareString(sheet, i, 5);
                    String mergedRemark = (remarkE == null || remarkE.isBlank()) ? (remarkF == null ? null : remarkF)
                                          : (remarkF == null || remarkF.isBlank() ? remarkE : (remarkE + " " + remarkF));

                    record.setModel(readString(modelCell));
                    record.setSn(readString(snCell));
                    record.setRemark(mergedRemark);
                    // 分类仍按单号自动识别
                    record.setCategory(TrackingCategoryUtil.resolve(tracking));
                    record.setStatus("UNPAID");

                    // 金额不从 Excel 导入（由结账管理写入）
                    record.setAmount(null);
                    record.setCurrency("CNY");
                    record.setCreatedBy(operator);
                    record.setCustomerName(readString(row.getCell(8)));
                    if (record.getTrackingNumber() == null || record.getTrackingNumber().isBlank()) {
                        continue;
                    }
                    if (record.getSn() == null || record.getSn().isBlank()) {
                        continue;
                    }

                    // 样式采集：备注优先取 F 列样式，否则取 E 列；备注样式考虑合并单元格顶格单元
                    Cell remarkStyleCell = mergedTopLeftCell(sheet, i, 5);
                    if (remarkStyleCell == null) remarkStyleCell = mergedTopLeftCell(sheet, i, 4);
                    applyCellStyleToRecord(record, trackingCell, modelCell, snCell, null, remarkStyleCell);

                    record.setExcelRowIndex(i - startRow);
                    result.add(record);
                }
            }
        }
        return result;
    }

public static byte[] writeSettlements(List<SettlementRecord> records) throws IOException {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet("待结账");

        // 表头
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("时间");
        header.createCell(1).setCellValue("订单号");
        header.createCell(2).setCellValue("商品名");
        header.createCell(3).setCellValue("SN/条码");
        header.createCell(4).setCellValue("价格");
        header.createCell(5).setCellValue("备注");
        header.createCell(6).setCellValue(" ");
        header.createCell(7).setCellValue(" ");
        header.createCell(8).setCellValue(" ");
        header.createCell(9).setCellValue("归属人");

        // --- 1. 按“归属人”进行顶级分组 ---
        Map<String, List<SettlementRecord>> byOwner = records.stream()
            .collect(Collectors.groupingBy(r -> safe(r.getOwnerUsername())));

        // --- 2. 按归属人名称排序，确保输出顺序稳定 ---
        List<Map.Entry<String, List<SettlementRecord>>> sortedOwnerGroups = new ArrayList<>(byOwner.entrySet());
        sortedOwnerGroups.sort(Map.Entry.comparingByKey());

        // --- 3. 遍历每个归属人，并处理其下的所有记录 ---
        int rowIndex = 1;
        boolean firstOwner = true;

        for (Map.Entry<String, List<SettlementRecord>> ownerEntry : sortedOwnerGroups) {
            String currentOwner = ownerEntry.getKey();
            List<SettlementRecord> ownerRecords = ownerEntry.getValue();

            // 在不同归属人之间插入3行空白
            if (!firstOwner) {
                for (int i = 0; i < 3; i++) {
                    sheet.createRow(rowIndex++);
                }
            }
            firstOwner = false;

            // --- 3a. 在当前归属人内部，按「时间 + 单号」进行次级分组 ---
            Map<String, List<SettlementRecord>> trackingGroups = new HashMap<>();
            Map<String, LocalDateTime> groupTimeMap = new HashMap<>();
            for (SettlementRecord r : ownerRecords) {
                if (r.getTrackingNumber() == null || r.getTrackingNumber().isBlank()) continue;
                LocalDateTime time = candidateTime(r) != null ? candidateTime(r) : LocalDateTime.MIN;
                String key = buildGroupKey(r.getTrackingNumber().trim(), time);
                trackingGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
                groupTimeMap.putIfAbsent(key, time);
            }

            // --- 3b. 对次级分组按时间排序 ---
            List<Map.Entry<String, List<SettlementRecord>>> sortedTrackingGroups = new ArrayList<>(trackingGroups.entrySet());
            sortedTrackingGroups.sort((a, b) -> {
                LocalDateTime ta = groupTimeMap.getOrDefault(a.getKey(), LocalDateTime.MIN);
                LocalDateTime tb = groupTimeMap.getOrDefault(b.getKey(), LocalDateTime.MIN);
                int cmp = ta.compareTo(tb);
                return cmp != 0 ? cmp : extractTracking(a.getKey()).compareTo(extractTracking(b.getKey()));
            });

            // --- 3c. 输出当前归属人的所有记录 ---
            for (Map.Entry<String, List<SettlementRecord>> trackingEntry : sortedTrackingGroups) {
                List<SettlementRecord> group = trackingEntry.getValue();
                String tracking = extractTracking(trackingEntry.getKey());
                String timeText = formatDateTime(groupTimeMap.getOrDefault(trackingEntry.getKey(), LocalDateTime.MIN));

                for (int i = 0; i < group.size(); i++) {
                    SettlementRecord r = group.get(i);
                    Row row = sheet.createRow(rowIndex++);

                    // 仅在每个单号组的第一行显示时间和单号
                    if (i == 0) {
                        row.createCell(0).setCellValue(timeText);
                        row.createCell(1).setCellValue(tracking);
                    } else {
                        row.createCell(0).setCellValue("");
                        row.createCell(1).setCellValue("");
                    }

                    row.createCell(2).setCellValue(safe(r.getModel()));
                    row.createCell(3).setCellValue(safe(r.getOrderSn()));
                    row.createCell(4).setCellValue(r.getAmount() == null ? 0 : r.getAmount().doubleValue());
                    row.createCell(5).setCellValue(safe(r.getRemark()));
                    row.createCell(6).setCellValue("    ");
                    row.createCell(7).setCellValue("    ");
                    row.createCell(8).setCellValue("    ");
                    row.createCell(9).setCellValue(currentOwner);
                }
            }
        }
        // 自动列宽
        for (int c = 0; c <= 9; c++) sheet.autoSizeColumn(c);

        workbook.write(baos);
        return baos.toByteArray();
    }
    }

    // 找到组内最早时间，允许任意年份；为空则返回 MIN 以便排序时置前并显示为空
    private static LocalDateTime earliestValidTime(List<SettlementRecord> list) {
        return list.stream()
            .map(ExcelHelper::candidateTime)
            .filter(Objects::nonNull)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.MIN);
    }

    private static String formatDateTime(LocalDateTime time) {
        if (time == null || LocalDateTime.MIN.equals(time)) {
            return "";
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    }

    /**
     * 为导出排序/展示提供候选时间：
     * 优先 orderTime，其次 confirmedAt、createdAt、updatedAt，最后 payableAt 当天 00:00。
     */
    private static LocalDateTime candidateTime(SettlementRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getOrderTime() != null) {
            return record.getOrderTime();
        }
        if (record.getConfirmedAt() != null) {
            return record.getConfirmedAt();
        }
        if (record.getCreatedAt() != null) {
            return record.getCreatedAt();
        }
        if (record.getUpdatedAt() != null) {
            return record.getUpdatedAt();
        }
        if (record.getPayableAt() != null) {
            return record.getPayableAt().atStartOfDay();
        }
        return null;
    }

    private static String buildGroupKey(String tracking, LocalDateTime time) {
        return tracking + "|" + (time == null ? "" : time.toString());
    }

    private static String extractTracking(String groupKey) {
        int idx = groupKey.indexOf('|');
        return idx >= 0 ? groupKey.substring(0, idx) : groupKey;
    }

    private static String safe(String input) {
        return input == null ? "" : input;
    }

    private static String readString(Cell cell) {
        return readString(cell, null);
    }

    private static String readString(Cell cell, String defaultValue) {
        if (cell == null || isErrorCell(cell)) {
            return defaultValue;
        }
        if (isNumericCell(cell)) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        String value = cleanExcelText(cell.getStringCellValue());
        return value == null || value.isBlank() ? defaultValue : value;
    }

    // 解析 Excel 单元格的日期/时间，支持数值日期与常见文本格式
    private static LocalDateTime parseDateTime(Cell cell) {
        if (cell == null || isErrorCell(cell)) return null;
        try {
            if (isNumericCell(cell)) {
                java.util.Date d = cell.getDateCellValue();
                if (d != null) {
                    return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
                }
            }
        } catch (Throwable ignored) {}
        String text = readString(cell, null);
        if (text == null || text.isBlank()) return null;
        String normalized = text.trim()
            .replace('年','-').replace("月","-").replace("日"," ")
            .replace('/', '-')
            .replace('.', '-')
            .replace("T", " ")
            .replaceAll("\\s+", " ");
        String[] patterns = new String[] {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yy-MM-dd HH:mm:ss",
            "yy-MM-dd HH:mm",
            "yy-MM-dd"
        };
        for (String p : patterns) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(p);
                if (p.endsWith("dd")) {
                    return LocalDate.parse(normalized, f).atStartOfDay();
                } else {
                    return LocalDateTime.parse(normalized, f);
                }
            } catch (Exception ignored) {}
        }
        // 尝试 ISO 解析
        try { return LocalDateTime.parse(normalized); } catch (Exception ignored) {}
        try { return LocalDate.parse(normalized).atStartOfDay(); } catch (Exception ignored) {}
        return null;
    }

    // 规范化运单号文本（去除 Excel 前缀/空白/末尾 - 等）
    private static String normalizeTracking(String s) {
        if (s == null) return null;
        String v = cleanExcelText(s);
        if (v == null) return null;
        v = v.trim();
        // 去除末尾的一个或多个 - 符号
        v = v.replaceAll("-+$", "");
        return v.isEmpty() ? null : v;
    }

    /**
     * 当单元格为空时，若处于合并单元格区域，则返回该区域左上角单元格的文本。
     */
    private static String readMergedAwareString(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = (row == null) ? null : row.getCell(colIndex);
        String v = readString(cell, null);
        if (v != null && !v.isBlank()) return v;
        // 查找合并区域
        int count = sheet.getNumMergedRegions();
        for (int i = 0; i < count; i++) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r != null && r.isInRange(rowIndex, colIndex)) {
                Row top = sheet.getRow(r.getFirstRow());
                Cell tl = (top == null) ? null : top.getCell(r.getFirstColumn());
                String base = readString(tl, null);
                if (base != null && !base.isBlank()) return base;
            }
        }
        return v;
    }

    /**
     * 返回合并区域的左上角单元格（若非合并则返回自身单元格），用于读取样式。
     */
    private static Cell mergedTopLeftCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = (row == null) ? null : row.getCell(colIndex);
        int count = sheet.getNumMergedRegions();
        for (int i = 0; i < count; i++) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r != null && r.isInRange(rowIndex, colIndex)) {
                Row top = sheet.getRow(r.getFirstRow());
                return (top == null) ? cell : top.getCell(r.getFirstColumn());
            }
        }
        return cell;
    }

    private static String cleanExcelText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u200E", "")
            .replace("\u202A", "")
            .replace("\u202B", "")
            .replace("\u202C", "")
            .replace("\u202D", "")
            .replace("\u202E", "");
        // 去掉 Excel 防公式前缀
        normalized = EXCEL_TEXT_PREFIX.matcher(normalized).replaceFirst("");
        return normalized;
    }

    private static boolean rowHasStrikethrough(Row row) {
        if (row == null) return false;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell == null) continue;
            try {
                CellStyle style = cell.getCellStyle();
                if (style == null) continue;
                Font f = cell.getSheet().getWorkbook().getFontAt(style.getFontIndex());
                if (f != null && f.getStrikeout()) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static BigDecimal parseDecimal(Cell cell) {
        if (cell == null || isErrorCell(cell)) {
            return null;
        }
        if (isNumericCell(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        try {
            return new BigDecimal(cell.getStringCellValue());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void applyCellStyleToRecord(OrderRecord record, Cell trackingCell, Cell modelCell, Cell snCell, Cell amountCell, Cell remarkCell) {
        if (trackingCell != null) {
            record.setTrackingBgColor(getEffectiveBgHex(trackingCell));
            record.setTrackingFontColor(getFontHex(trackingCell));
            record.setTrackingStrike(isStrike(trackingCell));
        }
        if (modelCell != null) {
            record.setModelBgColor(getEffectiveBgHex(modelCell));
            record.setModelFontColor(getFontHex(modelCell));
            record.setModelStrike(isStrike(modelCell));
        }
        if (snCell != null) {
            record.setSnBgColor(getEffectiveBgHex(snCell));
            record.setSnFontColor(getFontHex(snCell));
            record.setSnStrike(isStrike(snCell));
        }
        if (amountCell != null) {
            record.setAmountBgColor(getEffectiveBgHex(amountCell));
            record.setAmountFontColor(getFontHex(amountCell));
            record.setAmountStrike(isStrike(amountCell));
        }
        if (remarkCell != null) {
            record.setRemarkBgColor(getEffectiveBgHex(remarkCell));
            record.setRemarkFontColor(getFontHex(remarkCell));
            record.setRemarkStrike(isStrike(remarkCell));
        }
    }

    /**
     * 获取单元格的有效背景色：优先 单元格填充 -> 条件格式 -> 表格条纹
     */
    private static String getEffectiveBgHex(Cell cell) {
        String direct = getBgHex(cell);
        if (direct != null && !direct.isBlank()) return direct;
        if (ENABLE_CONDITIONAL_BG) {
            String fromCf = getConditionalBgHex(cell);
            if (fromCf != null && !fromCf.isBlank()) return fromCf;
        }
        if (ENABLE_TABLE_STRIPE_BG) {
            String fromTable = getTableStripeBgHex(cell);
            if (fromTable != null && !fromTable.isBlank()) return fromTable;
        }
        return null;
    }

    private static String getConditionalBgHex(Cell cell) {
        try {
            Sheet sheet = cell.getSheet();
            org.apache.poi.ss.usermodel.SheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();
            if (scf == null) return null;
            int r = cell.getRowIndex();
            int c = cell.getColumnIndex();
            for (int i = 0; i < scf.getNumConditionalFormattings(); i++) {
                org.apache.poi.ss.usermodel.ConditionalFormatting cf = scf.getConditionalFormattingAt(i);
                if (cf == null) continue;
                org.apache.poi.ss.util.CellRangeAddress[] ranges = cf.getFormattingRanges();
                boolean inRange = false;
                if (ranges != null) {
                    for (org.apache.poi.ss.util.CellRangeAddress range : ranges) {
                        if (range == null) continue;
                        if (r >= range.getFirstRow() && r <= range.getLastRow()
                                && c >= range.getFirstColumn() && c <= range.getLastColumn()) {
                            inRange = true; break;
                        }
                    }
                }
                if (!inRange) continue;
                for (int j = 0; j < cf.getNumberOfRules(); j++) {
                    org.apache.poi.ss.usermodel.ConditionalFormattingRule rule = cf.getRule(j);
                    if (rule == null) continue;
                    org.apache.poi.ss.usermodel.PatternFormatting pf = rule.getPatternFormatting();
                    if (pf == null) continue;
                    // XSSF: 直接拿颜色对象
                    try {
                        org.apache.poi.ss.usermodel.Color bg = pf.getFillBackgroundColorColor();
                        String hex = colorToHex(bg, sheet.getWorkbook());
                        if (hex != null) return hex;
                        org.apache.poi.ss.usermodel.Color fg = pf.getFillForegroundColorColor();
                        hex = colorToHex(fg, sheet.getWorkbook());
                        if (hex != null) return hex;
                    } catch (Throwable ignore) {}
                    // HSSF: 使用索引
                    short bgIdx = pf.getFillBackgroundColor();
                    String hex = toHexHSSF(bgIdx, sheet.getWorkbook());
                    if (hex != null) return hex;
                    short fgIdx = pf.getFillForegroundColor();
                    hex = toHexHSSF(fgIdx, sheet.getWorkbook());
                    if (hex != null) return hex;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String colorToHex(org.apache.poi.ss.usermodel.Color color, Workbook wb) {
        if (color == null) return null;
        try {
            if (color instanceof XSSFColor xc) {
                return toHexXSSF(xc);
            }
            if (color instanceof HSSFColor hc) {
                short[] t = hc.getTriplet();
                if (t != null && t.length == 3) {
                    return String.format("#%02X%02%02X%02X", t[0], t[1], t[2]);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getTableStripeBgHex(Cell cell) {
        try {
            Sheet sheet = cell.getSheet();
            if (!(sheet instanceof org.apache.poi.xssf.usermodel.XSSFSheet xs)) return null;
            java.util.List<org.apache.poi.xssf.usermodel.XSSFTable> tables = xs.getTables();
            if (tables == null || tables.isEmpty()) return null;
            int r = cell.getRowIndex();
            int c = cell.getColumnIndex();
            for (org.apache.poi.xssf.usermodel.XSSFTable t : tables) {
                int sr, er, sc, ec;
                try {
                    sr = t.getStartRowIndex();
                    er = t.getEndRowIndex();
                    sc = t.getStartColIndex();
                    ec = t.getEndColIndex();
                } catch (Throwable ex) {
                    // 兼容旧版本，通过引用解析
                    org.apache.poi.ss.util.CellReference start = t.getStartCellReference();
                    org.apache.poi.ss.util.CellReference end = t.getEndCellReference();
                    if (start == null || end == null) continue;
                    sr = start.getRow(); er = end.getRow();
                    sc = start.getCol(); ec = end.getCol();
                }
                if (r < sr || r > er || c < sc || c > ec) continue;
                // 命中表格区域，若开启行条纹，则奇偶行返回带状底色
                try {
                    org.apache.poi.xssf.usermodel.XSSFTableStyleInfo style = (org.apache.poi.xssf.usermodel.XSSFTableStyleInfo) t.getStyle();
                    if (style != null && style.isShowRowStripes()) {
                        boolean highlightBand = ((r - sr) % 2) == 1; // Excel 默认第二条带有底色
                        if (highlightBand) {
                            // 近似一个常见的行条纹底色（不同内置样式会有差异，这里取较通用的浅蓝）
                            return "#C9DAF8";
                        }
                    }
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getBgHex(Cell cell) {
        try {
            if (cell == null) return null;
            CellStyle style = cell.getCellStyle();
            if (style == null) return null;

            // 无填充时返回空（避免误判）
            try {
                FillPatternType p = style.getFillPattern();
                if (p == null || p == FillPatternType.NO_FILL) {
                    // 仍允许继续尝试取颜色（有些文件可能设置了颜色但模式为NONE），若取不到则返回空
                }
            } catch (Throwable ignored) {}

            if (style instanceof XSSFCellStyle xs) {
                // XSSF：优先前景色，其次背景色；支持主题色/tint
                XSSFColor c = xs.getFillForegroundXSSFColor();
                if (c == null) c = xs.getFillBackgroundColorColor();
                return toHexXSSF(c);
            } else if (style instanceof HSSFCellStyle hs) {
                // HSSF：使用索引色+调色板
                short idx = hs.getFillForegroundColor();
                if (idx == HSSFColor.HSSFColorPredefined.AUTOMATIC.getIndex()) {
                    idx = hs.getFillBackgroundColor();
                }
                return toHexHSSF(idx, cell.getSheet().getWorkbook());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String getFontHex(Cell cell) {
        try {
            if (cell == null) return null;
            CellStyle style = cell.getCellStyle();
            if (style == null) return null;
            Font f = cell.getSheet().getWorkbook().getFontAt(style.getFontIndex());
            if (f instanceof XSSFFont xf) {
                XSSFColor c = xf.getXSSFColor();
                return toHexXSSF(c);
            } else if (f instanceof HSSFFont hf) {
                short idx = hf.getColor();
                return toHexHSSF(idx, cell.getSheet().getWorkbook());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isStrike(Cell cell) {
        try {
            CellStyle style = cell.getCellStyle();
            if (style == null) return false;
            Font f = cell.getSheet().getWorkbook().getFontAt(style.getFontIndex());
            return f != null && f.getStrikeout();
        } catch (Throwable ignored) {}
        return false;
    }

    private static String toHexXSSF(XSSFColor color) {
        if (color == null) return null;
        byte[] rgb = color.getRGBWithTint();
        if (rgb == null) rgb = color.getRGB();
        if (rgb == null) return null;
        int r = rgb[0] & 0xFF;
        int g = rgb[1] & 0xFF;
        int b = rgb[2] & 0xFF;
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static String toHexHSSF(short idx, Workbook wb) {
        try {
            if (!(wb instanceof HSSFWorkbook)) return null;
            HSSFWorkbook hwb = (HSSFWorkbook) wb;
            HSSFPalette palette = hwb.getCustomPalette();
            HSSFColor color = palette.getColor(idx);
            if (color == null) {
                for (HSSFColor.HSSFColorPredefined p : HSSFColor.HSSFColorPredefined.values()) {
                    if (p.getIndex() == idx) {
                        short[] t = p.getTriplet();
                        return String.format("#%02X%02X%02X", t[0], t[1], t[2]);
                    }
                }
                return null;
            }
            short[] t = color.getTriplet();
            return String.format("#%02X%02X%02X", t[0], t[1], t[2]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int detectDataStartRow(Sheet sheet) {
        if (sheet == null) return 0;
        Row r0 = sheet.getRow(0);
        if (r0 == null) return 0;
        int last = r0.getLastCellNum();
        if (last <= 0) return 0;
        int headerScore = 0;
        int limit = Math.min(last, 10);
        for (int c = 0; c < limit; c++) {
            Cell cell = r0.getCell(c);
            String text = readString(cell, null);
            if (text == null) continue;
            String t = text.trim().toLowerCase(Locale.ROOT);
            if (t.equals("日期") || t.equals("时间") || t.equals("下单日期") || t.equals("下单时间")
                || t.equals("单号") || t.equals("运单号") || t.equals("型号") || t.equals("sn")
                || t.equals("备注") || t.equals("金额") || t.equals("状态") || t.equals("物流") || t.equals("分类")) {
                headerScore++;
            }
        }
        // 如果首行至少包含两个典型表头，则认为第1行为表头，从第2行开始读；否则从第1行开始读
        return headerScore >= 2 ? 1 : 0;
    }

    private static boolean isNumericCell(Cell cell) {
        return cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
            || (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
            && cell.getCachedFormulaResultType() == org.apache.poi.ss.usermodel.CellType.NUMERIC);
    }

    private static boolean isErrorCell(Cell cell) {
        if (cell == null) {
            return false;
        }
        return cell.getCellType() == org.apache.poi.ss.usermodel.CellType.ERROR
            || (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
            && cell.getCachedFormulaResultType() == org.apache.poi.ss.usermodel.CellType.ERROR);
    }
}