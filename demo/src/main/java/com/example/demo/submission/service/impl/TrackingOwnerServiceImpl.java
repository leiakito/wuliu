package com.example.demo.submission.service.impl;

import com.example.demo.submission.service.TrackingOwnerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 物流单号归属关系管理服务实现
 * 使用 JSON 文件存储单号与归属用户的映射关系
 */
@Slf4j
@Service
public class TrackingOwnerServiceImpl implements TrackingOwnerService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> ownerMap = new ConcurrentHashMap<>();

    @Value("${tracking.owner.file:data/tracking-owners.json}")
    private String dataFilePath;

    private File dataFile;

    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(dataFilePath);
            // 确保父目录存在
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            dataFile = path.toFile();

            // 如果文件存在，加载数据
            if (dataFile.exists()) {
                loadFromFile();
            } else {
                // 创建空文件
                saveToFile();
                log.info("创建单号归属关系文件: {}", dataFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("初始化单号归属关系文件失败", e);
        }
    }

    @Override
    public void setOwner(String trackingNumber, String ownerName) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        String normalized = trackingNumber.trim();
        if (StringUtils.hasText(ownerName)) {
            ownerMap.put(normalized, ownerName.trim());
        } else {
            ownerMap.remove(normalized);
        }
        saveToFile();
    }

    @Override
    public void setOwners(List<String> trackingNumbers, String ownerName) {
        if (trackingNumbers == null || trackingNumbers.isEmpty()) {
            return;
        }
        for (String trackingNumber : trackingNumbers) {
            if (!StringUtils.hasText(trackingNumber)) {
                continue;
            }
            String normalized = trackingNumber.trim();
            if (StringUtils.hasText(ownerName)) {
                ownerMap.put(normalized, ownerName.trim());
            } else {
                ownerMap.remove(normalized);
            }
        }
        saveToFile();
    }

    @Override
    public String getOwner(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return null;
        }
        return ownerMap.get(trackingNumber.trim());
    }

    @Override
    public Map<String, String> getAllOwners() {
        return new HashMap<>(ownerMap);
    }

    @Override
    public void removeOwner(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        ownerMap.remove(trackingNumber.trim());
        saveToFile();
    }

    @Override
    public List<String> listOwnerNames() {
        return ownerMap.values().stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
    }

    private void loadFromFile() {
        try {
            if (dataFile.exists() && dataFile.length() > 0) {
                Map<String, String> loaded = objectMapper.readValue(dataFile,
                    new TypeReference<Map<String, String>>() {});
                ownerMap.clear();
                ownerMap.putAll(loaded);
                log.info("加载单号归属关系: {} 条记录", ownerMap.size());
            }
        } catch (IOException e) {
            log.error("加载单号归属关系文件失败", e);
        }
    }

    private synchronized void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(dataFile, ownerMap);
        } catch (IOException e) {
            log.error("保存单号归属关系文件失败", e);
        }
    }
}
