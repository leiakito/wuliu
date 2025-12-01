package com.example.demo.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结算缓存服务
 * 用于缓存热点数据，减少数据库查询压力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String OWNER_CACHE_PREFIX = "settlement:owner:";
    private static final String PRICE_CACHE_PREFIX = "settlement:price:";
    private static final String ORDER_INFO_CACHE_PREFIX = "settlement:order:";

    private static final Duration OWNER_CACHE_TTL = Duration.ofHours(6);
    private static final Duration PRICE_CACHE_TTL = Duration.ofDays(1);
    private static final Duration ORDER_INFO_CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 获取归属用户（从缓存）
     */
    public String getOwnerUsername(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        String key = OWNER_CACHE_PREFIX + trackingNumber;
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("缓存命中 - 归属用户: {}", trackingNumber);
            return (String) value;
        }
        return null;
    }

    /**
     * 批量获取归属用户（从缓存）
     */
    public Map<String, String> getOwnerUsernames(Set<String> trackingNumbers) {
        if (trackingNumbers == null || trackingNumbers.isEmpty()) {
            return Map.of();
        }

        List<String> keys = trackingNumbers.stream()
                .filter(tn -> tn != null && !tn.isBlank())
                .map(tn -> OWNER_CACHE_PREFIX + tn)
                .collect(Collectors.toList());

        if (keys.isEmpty()) {
            return Map.of();
        }

        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return Map.of();
        }

        Map<String, String> result = new java.util.HashMap<>();
        int index = 0;
        for (String trackingNumber : trackingNumbers) {
            if (trackingNumber != null && !trackingNumber.isBlank() && index < values.size()) {
                Object value = values.get(index);
                if (value != null) {
                    result.put(trackingNumber, (String) value);
                }
                index++;
            }
        }

        log.debug("批量缓存命中 - 归属用户: {}/{}", result.size(), trackingNumbers.size());
        return result;
    }

    /**
     * 缓存归属用户
     */
    public void cacheOwnerUsername(String trackingNumber, String owner) {
        if (trackingNumber == null || trackingNumber.isBlank() || owner == null || owner.isBlank()) {
            return;
        }
        String key = OWNER_CACHE_PREFIX + trackingNumber;
        redisTemplate.opsForValue().set(key, owner, OWNER_CACHE_TTL);
        log.debug("缓存写入 - 归属用户: {} -> {}", trackingNumber, owner);
    }

    /**
     * 批量缓存归属用户
     */
    public void cacheOwnerUsernames(Map<String, String> ownerMap) {
        if (ownerMap == null || ownerMap.isEmpty()) {
            return;
        }

        ownerMap.forEach((trackingNumber, owner) -> {
            if (trackingNumber != null && !trackingNumber.isBlank() && owner != null && !owner.isBlank()) {
                String key = OWNER_CACHE_PREFIX + trackingNumber;
                redisTemplate.opsForValue().set(key, owner, OWNER_CACHE_TTL);
            }
        });

        log.debug("批量缓存写入 - 归属用户: {} 条", ownerMap.size());
    }

    /**
     * 获取硬件价格（从缓存）
     */
    public BigDecimal getHardwarePrice(String model, LocalDate date) {
        if (model == null || model.isBlank() || date == null) {
            return null;
        }
        String key = PRICE_CACHE_PREFIX + model + ":" + date;
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("缓存命中 - 硬件价格: {} @ {}", model, date);
            return (BigDecimal) value;
        }
        return null;
    }

    /**
     * 缓存硬件价格
     */
    public void cacheHardwarePrice(String model, LocalDate date, BigDecimal price) {
        if (model == null || model.isBlank() || date == null || price == null) {
            return;
        }
        String key = PRICE_CACHE_PREFIX + model + ":" + date;
        redisTemplate.opsForValue().set(key, price, PRICE_CACHE_TTL);
        log.debug("缓存写入 - 硬件价格: {} @ {} -> {}", model, date, price);
    }

    /**
     * 清除归属用户缓存
     */
    public void evictOwnerCache(String trackingNumber) {
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            String key = OWNER_CACHE_PREFIX + trackingNumber;
            redisTemplate.delete(key);
            log.debug("缓存清除 - 归属用户: {}", trackingNumber);
        }
    }

    /**
     * 清除硬件价格缓存
     */
    public void evictHardwarePrice(String model, LocalDate date) {
        if (model != null && !model.isBlank() && date != null) {
            String key = PRICE_CACHE_PREFIX + model + ":" + date;
            redisTemplate.delete(key);
            log.debug("缓存清除 - 硬件价格: {} @ {}", model, date);
        }
    }

    /**
     * 清除所有归属用户缓存
     */
    public void evictAllOwnerCache() {
        Set<String> keys = redisTemplate.keys(OWNER_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("批量缓存清除 - 归属用户: {} 条", keys.size());
        }
    }

    /**
     * 清除所有硬件价格缓存
     */
    public void evictAllPriceCache() {
        Set<String> keys = redisTemplate.keys(PRICE_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("批量缓存清除 - 硬件价格: {} 条", keys.size());
        }
    }
}
