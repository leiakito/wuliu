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
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("时间");
            header.createCell(1).setCellValue("订单号");
            header.createCell(2).setCellValue("型号");
            header.createCell(3).setCellValue("SN码");
            header.createCell(4).setCellValue("价格");
            header.createCell(5).setCellValue("备注");
            header.createCell(6).setCellValue("订单状态");
            header.createCell(7).setCellValue("归属用户");
            header.createCell(8).setCellValue("批次");

            Map<String, List<SettlementRecord>> grouped = records.stream()
                .filter(r -> r.getTrackingNumber() != null && !r.getTrackingNumber().isBlank())
                .collect(Collectors.groupingBy(r -> r.getTrackingNumber().trim()));

            int rowIndex = 1;
            for (Map.Entry<String, List<SettlementRecord>> entry : grouped.entrySet()) {
                List<SettlementRecord> group = entry.getValue();
                for (int i = 0; i < group.size(); i++) {
                    SettlementRecord record = group.get(i);
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(formatDateTime(record.getOrderTime()));
                    row.createCell(1).setCellValue(i == 0 ? entry.getKey() : "");
                    row.createCell(2).setCellValue(safe(record.getModel()));
                    row.createCell(3).setCellValue(safe(record.getOrderSn()));
                    row.createCell(4).setCellValue(record.getAmount() == null ? 0 : record.getAmount().doubleValue());
                    row.createCell(5).setCellValue(safe(record.getRemark()));
                    row.createCell(6).setCellValue(safe(record.getStatus()));
                    row.createCell(7).setCellValue(safe(record.getOwnerUsername()));
                    row.createCell(8).setCellValue(safe(record.getSettleBatch()));
                }
            }
            for (int c = 0; c <= 8; c++) {
                sheet.autoSizeColumn(c);
            }
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private static String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.toString().replace('T', ' ');
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
