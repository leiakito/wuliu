package com.example.demo.submission.service;

import java.util.List;
import java.util.Map;

/**
 * 物流单号归属关系管理服务
 * 用于管理单号与归属用户的映射关系（存储在 JSON 文件中）
 */
public interface TrackingOwnerService {

    /**
     * 设置单号的归属用户
     */
    void setOwner(String trackingNumber, String ownerName);

    /**
     * 批量设置单号的归属用户
     */
    void setOwners(List<String> trackingNumbers, String ownerName);

    /**
     * 获取单号的归属用户
     */
    String getOwner(String trackingNumber);

    /**
     * 获取所有归属关系
     */
    Map<String, String> getAllOwners();

    /**
     * 删除单号的归属关系
     */
    void removeOwner(String trackingNumber);

    /**
     * 获取所有归属用户名列表（去重）
     */
    List<String> listOwnerNames();
}
