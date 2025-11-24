package com.example.demo.hardware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.util.ExcelHelper;
import com.example.demo.hardware.dto.HardwarePriceExcelParseResult;
import com.example.demo.hardware.dto.HardwarePriceImportResult;
import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.hardware.mapper.HardwarePriceMapper;
import com.example.demo.hardware.service.HardwarePriceService;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class HardwarePriceServiceImpl implements HardwarePriceService {

    private static final Pattern DATE_IN_FILENAME = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HardwarePriceMapper hardwarePriceMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<HardwarePrice> list(HardwarePriceQuery query) {
        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        if (start != null) {
            wrapper.ge(HardwarePrice::getPriceDate, start);
        }
        if (end != null) {
            wrapper.le(HardwarePrice::getPriceDate, end);
        }
        if (StringUtils.hasText(query.getItemName())) {
            wrapper.like(HardwarePrice::getItemName, query.getItemName().trim());
        }
        wrapper.orderByDesc(HardwarePrice::getPriceDate).orderByAsc(HardwarePrice::getItemName);
        return hardwarePriceMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public HardwarePrice create(HardwarePriceRequest request, String operator) {
        validateUnique(request.getPriceDate(), request.getItemName(), null);
        HardwarePrice price = toEntity(request);
        price.setCreatedBy(operator);
        hardwarePriceMapper.insert(price);
        return price;
    }

    @Override
    @Transactional
    public HardwarePrice update(Long id, HardwarePriceRequest request) {
        HardwarePrice existed = hardwarePriceMapper.selectById(id);
        if (existed == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在");
        }
        validateUnique(request.getPriceDate(), request.getItemName(), id);
        existed.setPriceDate(request.getPriceDate());
        existed.setItemName(normalizeItemName(request.getItemName()));
        existed.setPrice(request.getPrice());
        hardwarePriceMapper.updateById(existed);
        return existed;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        hardwarePriceMapper.deleteById(id);
    }

    @Override
    @Transactional
    public List<HardwarePrice> batchCreate(List<HardwarePriceRequest> requests, String operator) {
        if (CollectionUtils.isEmpty(requests)) {
            return List.of();
        }
        List<HardwarePrice> created = new ArrayList<>();
        for (HardwarePriceRequest request : requests) {
            validateUnique(request.getPriceDate(), request.getItemName(), null);
            HardwarePrice price = toEntity(request);
            price.setCreatedBy(operator);
            hardwarePriceMapper.insert(price);
            created.add(price);
        }
        return created;
    }

    @Override
    public List<HardwarePrice> importExcel(LocalDate priceDate, MultipartFile file, String operator) {
        HardwarePriceImportResult result = doImport(priceDate, file, operator, true, true);
        return result.getRecords();
    }

    @Override
    public List<HardwarePriceImportResult> importExcelBatch(List<MultipartFile> files, String operator) {
        if (CollectionUtils.isEmpty(files)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少上传一个 Excel 文件");
        }
        List<HardwarePriceImportResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                results.add(failureResult(file, "文件为空，已跳过"));
                continue;
            }
            try {
                results.add(doImport(null, file, operator, false, false));
            } catch (BusinessException ex) {
                results.add(failureResult(file, ex.getMessage()));
            } catch (Exception ex) {
                results.add(failureResult(file, "导入失败，请检查文件格式或重试"));
            }
        }
        return results;
    }

    private HardwarePriceImportResult doImport(LocalDate priceDate, MultipartFile file, String operator, boolean rethrowOnError, boolean includeRecords) {
        long start = System.currentTimeMillis();
        validateExcelFile(file);
        LocalDate resolvedDate = resolvePriceDate(priceDate, file);
        try {
            HardwarePriceExcelParseResult parseResult = ExcelHelper.readHardwarePrices(file.getInputStream(), resolvedDate, operator);
            if (CollectionUtils.isEmpty(parseResult.getRows())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 中没有有效数据");
            }
            HardwarePriceImportResult saved = transactionTemplate.execute(status ->
                saveImportedRows(parseResult, resolvedDate, operator)
            );
            if (saved == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导入失败，请稍后重试");
            }
            if (!includeRecords) {
                saved.setRecords(List.of());
            }
            saved.setFileName(extractFileName(file));
            saved.setDurationMillis(System.currentTimeMillis() - start);
            return saved;
        } catch (BusinessException ex) {
            if (rethrowOnError) {
                throw ex;
            }
            HardwarePriceImportResult failure = failureResult(file, ex.getMessage());
            failure.setDurationMillis(System.currentTimeMillis() - start);
            return failure;
        } catch (IOException ex) {
            if (rethrowOnError) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 解析失败");
            }
            HardwarePriceImportResult failure = failureResult(file, "Excel 解析失败");
            failure.setDurationMillis(System.currentTimeMillis() - start);
            return failure;
        }
    }

    private HardwarePriceImportResult saveImportedRows(HardwarePriceExcelParseResult parseResult, LocalDate priceDate, String operator) {
        Map<String, HardwarePrice> latestByItem = new LinkedHashMap<>();
        for (HardwarePrice row : parseResult.getRows()) {
            String normalized = normalizeItemName(row.getItemName());
            if (StringUtils.hasText(normalized)) {
                row.setItemName(normalized);
                row.setPriceDate(priceDate);
                row.setCreatedBy(operator);
                latestByItem.put(normalized, row);
            }
        }
        if (latestByItem.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到有效的型号数据");
        }

        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HardwarePrice::getPriceDate, priceDate);
        Map<String, HardwarePrice> existedMap = hardwarePriceMapper.selectList(wrapper).stream()
            .filter(h -> StringUtils.hasText(h.getItemName()))
            .collect(java.util.stream.Collectors.toMap(
                h -> normalizeItemName(h.getItemName()),
                h -> h,
                (a, b) -> a,
                LinkedHashMap::new
            ));

        List<HardwarePrice> result = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        for (HardwarePrice item : latestByItem.values()) {
            HardwarePrice existed = existedMap.get(item.getItemName());
            if (existed != null) {
                existed.setPrice(item.getPrice());
                existed.setItemName(item.getItemName());
                existed.setCreatedBy(operator);
                hardwarePriceMapper.updateById(existed);
                result.add(existed);
                updated++;
            } else {
                item.setCreatedBy(operator);
                hardwarePriceMapper.insert(item);
                result.add(item);
                inserted++;
            }
        }
        HardwarePriceImportResult importResult = new HardwarePriceImportResult();
        importResult.setPriceDate(priceDate);
        importResult.setRecords(result);
        importResult.setInsertedCount(inserted);
        importResult.setUpdatedCount(updated);
        importResult.setSuccessCount(result.size());
        importResult.setSkippedCount(parseResult.getErrors().size());
        importResult.setTotalRows(parseResult.getTotalRows());
        importResult.getErrors().addAll(parseResult.getErrors());
        importResult.setSuccess(true);
        if (parseResult.getErrors().isEmpty()) {
            importResult.setMessage("导入成功");
        } else {
            importResult.setMessage("导入完成，部分数据已跳过");
        }
        return importResult;
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传 Excel 文件");
        }
        String name = file.getOriginalFilename();
        if (StringUtils.hasText(name)) {
            String lower = name.toLowerCase();
            if (!(lower.endsWith(".xls") || lower.endsWith(".xlsx"))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 .xls 或 .xlsx 文件");
            }
        }
    }

    private LocalDate resolvePriceDate(LocalDate priceDate, MultipartFile file) {
        if (priceDate != null) {
            return priceDate;
        }
        String name = file == null ? null : file.getOriginalFilename();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名为空，无法识别日期");
        }
        Matcher matcher = DATE_IN_FILENAME.matcher(name);
        if (!matcher.find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名需包含日期（yyyy-MM-dd），如 2025-10-10.xlsx");
        }
        String matched = matcher.group(1);
        try {
            return LocalDate.parse(matched, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名中的日期格式有误，示例：2025-10-10.xlsx");
        }
    }

    private HardwarePriceImportResult failureResult(MultipartFile file, String message) {
        HardwarePriceImportResult result = new HardwarePriceImportResult();
        result.setFileName(extractFileName(file));
        result.setSuccess(false);
        result.setMessage(message);
        result.getErrors().add(message);
        return result;
    }

    private String extractFileName(MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        return StringUtils.hasText(name) ? name : "未命名.xlsx";
    }

    private void validateUnique(LocalDate date, String itemName, Long excludeId) {
        String normalized = normalizeItemName(itemName);
        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HardwarePrice::getPriceDate, date)
            .eq(HardwarePrice::getItemName, normalized);
        if (excludeId != null) {
            wrapper.ne(HardwarePrice::getId, excludeId);
        }
        Long count = hardwarePriceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "同一日期的该硬件已存在");
        }
    }

    private HardwarePrice toEntity(HardwarePriceRequest request) {
        HardwarePrice price = new HardwarePrice();
        price.setPriceDate(request.getPriceDate());
        price.setItemName(normalizeItemName(request.getItemName()));
        price.setPrice(request.getPrice());
        return price;
    }

    private String normalizeItemName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw
            .replace('\u00A0', ' ') // 去除不间断空格
            .replaceAll("\\s+", " ");
        return cleaned.trim();
    }
}
