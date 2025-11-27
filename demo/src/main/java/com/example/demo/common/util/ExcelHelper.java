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
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelHelper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern EXCEL_TEXT_PREFIX = Pattern.compile("^[='‘’“”\"\\u200B\\u200E\\uFEFF]+");

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
                BigDecimal price = parsedPrice == null ? BigDecimal.ZERO : parsedPrice;
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
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || rowHasStrikethrough(row)) {
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
                    String tracking = normalizeTracking(readString(row.getCell(1)));
                    if (tracking == null || tracking.isBlank()) {
                        tracking = lastTracking;
                    } else {
                        lastTracking = tracking;
                    }
                    String model = readString(row.getCell(2));
                    String sn = readString(row.getCell(3));
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
                    record.setRemark(readString(row.getCell(4)));
                    record.setStatus("UNPAID");
                    record.setCurrency("CNY");
                    record.setCategory(TrackingCategoryUtil.resolve(tracking));
                    record.setCreatedBy(operator);
                    result.add(record);
                } else {
                    LocalDateTime dateTime = parseDateTime(row.getCell(0));
                    if (dateTime == null) {
                        dateTime = lastDateTime;
                    } else {
                        lastDateTime = dateTime;
                    }
                    String tracking = normalizeTracking(readString(row.getCell(1)));
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
                    record.setModel(readString(row.getCell(2)));
                    record.setSn(readString(row.getCell(3)));
                    record.setRemark(readString(row.getCell(4)));
                    record.setCategory(readString(row.getCell(5)));
                    record.setStatus("UNPAID");
                    record.setAmount(parseDecimal(row.getCell(6)));
                    record.setCurrency("CNY");
                    record.setCategory(TrackingCategoryUtil.resolve(tracking));
                    record.setCreatedBy(operator);
                    record.setCustomerName(readString(row.getCell(8)));
                    if (record.getTrackingNumber() == null || record.getTrackingNumber().isBlank()) {
                        continue;
                    }
                    if (record.getSn() == null || record.getSn().isBlank()) {
                        continue;
                    }
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

        // --- 1. 按「时间 + 单号」分组 ---
        Map<String, List<SettlementRecord>> grouped = new HashMap<>();
        Map<String, LocalDateTime> groupTimeMap = new HashMap<>();
        for (SettlementRecord r : records) {
            if (r.getTrackingNumber() == null || r.getTrackingNumber().isBlank()) {
                continue;
            }
            LocalDateTime time = candidateTime(r);
            if (time == null) {
                time = LocalDateTime.MIN;
            }
            String key = buildGroupKey(r.getTrackingNumber().trim(), time);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            groupTimeMap.putIfAbsent(key, time);
        }

        // --- 2. 排序：时间升序，同一时间按单号排序 ---
        List<Map.Entry<String, List<SettlementRecord>>> orderedGroups = new ArrayList<>(grouped.entrySet());
        orderedGroups.sort((a, b) -> {
            LocalDateTime ta = groupTimeMap.getOrDefault(a.getKey(), LocalDateTime.MIN);
            LocalDateTime tb = groupTimeMap.getOrDefault(b.getKey(), LocalDateTime.MIN);
            int cmp = ta.compareTo(tb);
            if (cmp != 0) {
                return cmp;
            }
            return extractTracking(a.getKey()).compareTo(extractTracking(b.getKey()));
        });

        // --- 3. 输出 ---
        int rowIndex = 1;
        String lastOwnerWritten = null;

        for (Map.Entry<String, List<SettlementRecord>> e : orderedGroups) {

            List<SettlementRecord> group = e.getValue();
            String tracking = extractTracking(e.getKey());
            String timeText = formatDateTime(groupTimeMap.getOrDefault(e.getKey(), LocalDateTime.MIN));

            // ====== 新增：按归属人分组 ======
            Map<String, List<SettlementRecord>> byOwner = group.stream()
                    .collect(Collectors.groupingBy(r -> safe(r.getOwnerUsername())));

            // 按归属人名称排序（为了展示稳定）
            List<Map.Entry<String, List<SettlementRecord>>> ownerGroups =
                    new ArrayList<>(byOwner.entrySet());
            ownerGroups.sort(Map.Entry.comparingByKey());

            boolean firstRowInGroup = true;
            for (int ownerIdx = 0; ownerIdx < ownerGroups.size(); ownerIdx++) {
                Map.Entry<String, List<SettlementRecord>> og = ownerGroups.get(ownerIdx);
                List<SettlementRecord> ownerRecords = og.getValue();
                String currentOwner = safe(og.getKey());

                // 不同归属人（跨单号也算）之间插入3行空白
                if (lastOwnerWritten != null && !currentOwner.equals(lastOwnerWritten)) {
                    for (int i = 0; i < 3; i++) {
                        sheet.createRow(rowIndex++);
                    }
                }
                lastOwnerWritten = currentOwner;

                // 输出该归属人的所有商品
                for (int i = 0; i < ownerRecords.size(); i++) {

                    SettlementRecord r = ownerRecords.get(i);

                    Row row = sheet.createRow(rowIndex++);

                    // 单号组内首次写入时间/订单号（仅第一行显示）
                    String displayTime = firstRowInGroup ? timeText : "";
                    String displayTracking = firstRowInGroup ? tracking : "";
                    firstRowInGroup = false;

                    row.createCell(0).setCellValue(displayTime);
                    row.createCell(1).setCellValue(displayTracking);
                    row.createCell(2).setCellValue(safe(r.getModel()));
                    row.createCell(3).setCellValue(safe(r.getOrderSn()));
                    row.createCell(4).setCellValue(r.getAmount() == null ? 0 : r.getAmount().doubleValue());
                    row.createCell(5).setCellValue(safe(r.getRemark()));

                    // 6, 7, 8 空白列
                    row.createCell(6).setCellValue("    ");
                    row.createCell(7).setCellValue("    ");
                    row.createCell(8).setCellValue("    ");

                    // 归属人放到第 9 列
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
            .replace("\u202E", "")
            .trim();
        normalized = EXCEL_TEXT_PREFIX.matcher(normalized).replaceFirst("");
        return normalized;
    }

    private static String normalizeTracking(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        while (trimmed.endsWith("-")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean rowHasStrikethrough(Row row) {
        if (row == null) {
            return false;
        }
        Workbook workbook = row.getSheet().getWorkbook();
        int first = row.getFirstCellNum();
        int last = row.getLastCellNum();
        for (int c = first; c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                continue;
            }
            if (cell.getCellStyle() == null) {
                continue;
            }
            short fontIndex = (short) cell.getCellStyle().getFontIndex();
            Font font = workbook.getFontAt(fontIndex);
            if (font != null && font.getStrikeout()) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate parseDate(Cell cell) {
        if (cell == null || isErrorCell(cell)) {
            return null;
        }
        if (isNumericCell(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = cell.getStringCellValue();
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text.trim(), DATE_FORMATTER);
    }

    private static LocalDateTime parseDateTime(Cell cell) {
        if (cell == null || isErrorCell(cell)) {
            return null;
        }
        if (isNumericCell(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        String text = cell.getStringCellValue();
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace('.', '-').replace('/', '-');
        try {
            return LocalDateTime.parse(normalized.replace(" ", "T"));
        } catch (Exception ex) {
            try {
                return LocalDate.parse(normalized, DATE_FORMATTER).atStartOfDay();
            } catch (Exception inner) {
                return null;
            }
        }
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
