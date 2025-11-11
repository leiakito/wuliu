package com.example.demo.common.util;

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
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelHelper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExcelHelper() {
    }

    public static List<OrderRecord> readOrders(InputStream inputStream, String operator) throws IOException {
        List<OrderRecord> result = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            LocalDateTime lastDateTime = null;
            String lastTracking = null;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
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
            header.createCell(0).setCellValue("单号");
            header.createCell(1).setCellValue("金额");
            header.createCell(2).setCellValue("币种");
            header.createCell(3).setCellValue("状态");
            header.createCell(4).setCellValue("批次");
            header.createCell(5).setCellValue("应付日期");
            header.createCell(6).setCellValue("备注");

            for (int i = 0; i < records.size(); i++) {
                SettlementRecord record = records.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(safe(record.getTrackingNumber()));
                row.createCell(1).setCellValue(record.getAmount() == null ? 0 : record.getAmount().doubleValue());
                row.createCell(2).setCellValue(safe(record.getCurrency()));
                row.createCell(3).setCellValue(safe(record.getStatus()));
                row.createCell(4).setCellValue(safe(record.getSettleBatch()));
                row.createCell(5).setCellValue(record.getPayableAt() == null ? "" : record.getPayableAt().toString());
                row.createCell(6).setCellValue(safe(record.getRemark()));
            }
            for (int c = 0; c <= 6; c++) {
                sheet.autoSizeColumn(c);
            }
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private static String safe(String input) {
        return input == null ? "" : input;
    }

    private static String readString(Cell cell) {
        return readString(cell, null);
    }

    private static String readString(Cell cell, String defaultValue) {
        if (cell == null) {
            return defaultValue;
        }
        if (isNumericCell(cell)) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        String value = cell.getStringCellValue();
        return value == null || value.isBlank() ? defaultValue : value.trim();
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

    private static LocalDate parseDate(Cell cell) {
        if (cell == null) {
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
        if (cell == null) {
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
        if (cell == null) {
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
}
