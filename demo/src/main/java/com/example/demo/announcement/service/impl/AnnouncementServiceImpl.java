package com.example.demo.announcement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.announcement.dto.AnnouncementCreateRequest;
import com.example.demo.announcement.entity.Announcement;
import com.example.demo.announcement.mapper.AnnouncementMapper;
import com.example.demo.announcement.service.AnnouncementService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    @Override
    @Transactional
    public Announcement create(AnnouncementCreateRequest request, String operator) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent().trim());
        announcement.setCreatedBy(operator);
        announcement.setCreatedAt(LocalDateTime.now());
        announcementMapper.insert(announcement);
        return announcement;
    }

    @Override
    public IPage<Announcement> page(int page, int size) {
        Page<Announcement> pager = Page.of(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Announcement::getCreatedAt);
        return announcementMapper.selectPage(pager, wrapper);
    }

    @Override
    public Announcement latest() {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Announcement::getCreatedAt).last("LIMIT 1");
        return announcementMapper.selectOne(wrapper);
    }
}
