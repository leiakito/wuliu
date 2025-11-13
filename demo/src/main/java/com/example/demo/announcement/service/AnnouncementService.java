package com.example.demo.announcement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.announcement.dto.AnnouncementCreateRequest;
import com.example.demo.announcement.entity.Announcement;

public interface AnnouncementService {

    Announcement create(AnnouncementCreateRequest request, String operator);

    IPage<Announcement> page(int page, int size);

    Announcement latest();
}
