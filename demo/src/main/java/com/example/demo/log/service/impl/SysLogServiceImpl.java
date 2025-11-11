package com.example.demo.log.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.log.entity.SysLog;
import com.example.demo.log.mapper.SysLogMapper;
import com.example.demo.log.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService {

    private final SysLogMapper sysLogMapper;

    @Override
    public void save(SysLog log) {
        sysLogMapper.insert(log);
    }

    @Override
    public Page<SysLog> page(long page, long size) {
        return sysLogMapper.selectPage(Page.of(page, size), null);
    }
}
