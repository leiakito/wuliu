package com.example.demo.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.log.entity.SysLog;

public interface SysLogService {

    void save(SysLog log);

    IPage<SysLog> page(long page, long size);
}
