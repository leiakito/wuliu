package com.example.demo.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

//MyBatis-Plus 在项目启动时会扫描所有继承了 BaseMapper 的接口，
//MyBatis-Plus 就会自动帮你生成常用的 SQL 操作。
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
