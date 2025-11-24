package com.example.demo.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

//MyBatis-Plus 在项目启动时会扫描所有继承了 BaseMapper 的接口，
//MyBatis-Plus 就会自动帮你生成常用的 SQL 操作。
/**
 * 方法
 * 自动生成的 SQL
 * selectById(id)
 * SELECT * FROM sys_user WHERE id = ?
 * insert(entity)
 * INSERT INTO sys_user (...) VALUES (...)
 * updateById(entity)
 * UPDATE sys_user SET ... WHERE id = ?
 * deleteById(id)
 * DELETE FROM sys_user WHERE id = ?
 * selectList(wrapper)
 * 动态 SQL 条件查询
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
