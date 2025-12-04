# 多用户权限隔离 - 实施检查清单

## 📋 实施阶段

### ✅ 第一阶段：后端 API 改造（必须）

#### 1.1 修改 SettlementFilterRequest.java
- [ ] 添加 `currentUsername` 字段
- [ ] 添加 `currentRole` 字段
- [ ] 添加 getter/setter 方法

**文件位置**：`demo/src/main/java/com/example/demo/settlement/dto/SettlementFilterRequest.java`

**改动内容**：
```java
// 新增字段
private String currentUsername;  // 当前登录用户
private String currentRole;      // 当前用户角色
```

---

#### 1.2 修改 SettlementController.java
- [ ] 在 `page()` 方法中注入当前用户信息
- [ ] 在 `pageByCursor()` 方法中注入当前用户信息
- [ ] 在 `export()` 方法中注入当前用户信息

**文件位置**：`demo/src/main/java/com/example/demo/settlement/controller/SettlementController.java`

**改动内容**：
```java
@GetMapping
@SaCheckLogin
public ApiResponse<PageResponse<SettlementRecord>> page(SettlementFilterRequest request) {
    String currentUser = StpUtil.getLoginIdAsString();
    String currentRole = StpUtil.getRoleList().isEmpty() ? "USER" : StpUtil.getRoleList().get(0);
    
    request.setCurrentUsername(currentUser);
    request.setCurrentRole(currentRole);
    
    IPage<SettlementRecord> page = settlementService.list(request);
    return ApiResponse.ok(PageResponse.from(page));
}
```

---

#### 1.3 修改 SettlementServiceImpl.java
- [ ] 修改 `buildQueryWrapper()` 方法添加权限过滤
- [ ] 在 `confirm()` 方法中添加权限验证
- [ ] 在 `updateAmount()` 方法中添加权限验证
- [ ] 在 `delete()` 方法中添加权限验证

**文件位置**：`demo/src/main/java/com/example/demo/settlement/service/impl/SettlementServiceImpl.java`

**改动内容**：
```java
private LambdaQueryWrapper<SettlementRecord> buildQueryWrapper(Object request) {
    LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
    
    // 提取用户信息
    String currentUsername = null;
    String currentRole = null;
    
    if (request instanceof SettlementFilterRequest) {
        SettlementFilterRequest r = (SettlementFilterRequest) request;
        currentUsername = r.getCurrentUsername();
        currentRole = r.getCurrentRole();
        // ... 其他字段提取
    } else if (request instanceof SettlementCursorRequest) {
        SettlementCursorRequest r = (SettlementCursorRequest) request;
        currentUsername = r.getCurrentUsername();
        currentRole = r.getCurrentRole();
        // ... 其他字段提取
    }
    
    // 权限过滤：非 ADMIN 用户只能看自己的数据
    if (!"ADMIN".equals(currentRole) && StringUtils.hasText(currentUsername)) {
        wrapper.eq(SettlementRecord::getOwnerUsername, currentUsername);
    }
    
    // ... 其他过滤条件
    return wrapper;
}
```

---

#### 1.4 修改 confirm() 方法权限检查
- [ ] 添加权限验证逻辑
- [ ] 非 ADMIN 用户应该被拒绝

**改动内容**：
```java
@Transactional
public void confirm(Long id, SettlementConfirmRequest request, String operator) {
    SettlementRecord record = settlementRecordMapper.selectById(id);
    if (record == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "待结账数据不存在");
    }
    
    // 权限验证：只有 ADMIN 可以确认
    String role = StpUtil.getRoleList().isEmpty() ? "USER" : StpUtil.getRoleList().get(0);
    if (!"ADMIN".equals(role)) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限确认结账");
    }
    
    // ... 确认逻辑
}
```

---

#### 1.5 修改 delete() 方法权限检查
- [ ] 添加权限验证逻辑
- [ ] 非 ADMIN 用户应该被拒绝

**改动内容**：
```java
@Transactional
public void delete(List<Long> ids) {
    if (CollectionUtils.isEmpty(ids)) {
        return;
    }
    
    // 权限验证
    String role = StpUtil.getRoleList().isEmpty() ? "USER" : StpUtil.getRoleList().get(0);
    if (!"ADMIN".equals(role)) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限删除结账记录");
    }
    
    settlementRecordMapper.deleteBatchIds(ids);
}
```

---

### ✅ 第二阶段：前端 UI 改造（必须）

#### 2.1 修改 SettlementsView.vue
- [ ] 修改筛选表单，隐藏"归属用户"选项（非 ADMIN）
- [ ] 修改操作列，隐藏"确认"和"删除"按钮（非 ADMIN）
- [ ] 修改批量操作按钮，隐藏（非 ADMIN）
- [ ] 添加权限提示信息

**文件位置**：`front/src/views/SettlementsView.vue`

**改动内容**：

```vue
<!-- 1. 隐藏"归属用户"筛选项 -->
<el-form-item label="归属用户" v-if="isAdmin">
  <el-select
    v-model="filters.ownerUsername"
    placeholder="全部"
    clearable
    filterable
    style="width: 200px"
    :loading="userLoading"
  >
    <!-- 选项 -->
  </el-select>
</el-form-item>

<!-- 2. 添加权限提示（非 ADMIN） -->
<el-form-item v-if="!isAdmin" style="flex: 1;">
  <el-alert type="info" :closable="false" show-icon>
    <template #title>
      您正在查看自己的结账记录（{{ auth.user?.username }}）
    </template>
  </el-alert>
</el-form-item>

<!-- 3. 隐藏批量操作按钮 -->
<el-button
  v-if="isAdmin"
  type="success"
  plain
  :disabled="!selectedIds.length"
  @click="openBatchConfirmDialog"
>批量确认</el-button>

<el-button
  v-if="isAdmin"
  type="danger"
  plain
  :disabled="!selectedIds.length"
  @click="handleDelete"
>
  删除所选
</el-button>

<!-- 4. 修改操作列 -->
<el-table-column label="操作" width="160">
  <template #default="{ row }">
    <el-button
      v-if="isAdmin && row.status !== 'CONFIRMED'"
      link
      type="primary"
      @click="openConfirm(row)">
      确认
    </el-button>
    <el-button
      v-if="isAdmin"
      link
      type="danger"
      @click="handleDeleteOne(row)">
      删除
    </el-button>
    
    <!-- 普通用户提示 -->
    <span v-if="!isAdmin" style="color: #909399;">仅查看</span>
  </template>
</el-table-column>
```

---

#### 2.2 修改导出功能
- [ ] 普通用户自动添加 ownerUsername 过滤
- [ ] ADMIN 用户可以导出全部或按条件导出

**改动内容**：
```javascript
const exportData = async () => {
  exporting.value = true;
  try {
    const exportParams: SettlementExportRequest = {
      status: filters.status || undefined,
      // 非 ADMIN 用户自动添加 ownerUsername 过滤
      ownerUsername: !isAdmin.value ? auth.user?.username : (filters.ownerUsername?.trim() || undefined)
    };
    if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
      exportParams.startDate = filters.dateRange[0];
      exportParams.endDate = filters.dateRange[1];
    }
    await downloadExcel(exportParams);
  } finally {
    exporting.value = false;
  }
};
```

---

### ✅ 第三阶段：数据库优化（推荐）

#### 3.1 添加 created_by 字段
- [ ] 在 settlement_record 表添加 created_by 字段
- [ ] 设置为 VARCHAR(64)，允许 NULL

**SQL 脚本**：
```sql
-- 检查字段是否存在
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'settlement_record'
    AND COLUMN_NAME = 'created_by'
);

-- 不存在则添加
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE settlement_record ADD COLUMN created_by VARCHAR(64) AFTER owner_username',
  'DO 0'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

**执行位置**：在 `performance_optimization.sql` 中添加

---

#### 3.2 添加索引
- [ ] 添加 `idx_settlement_owner_status` 索引
- [ ] 添加 `idx_settlement_owner_time` 索引
- [ ] 添加 `idx_settlement_created_status` 索引

**SQL 脚本**：
```sql
-- 在 performance_optimization.sql 中添加

-- 索引 1: owner_username + status
SET @idx := 'idx_settlement_owner_status';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'settlement_record' AND index_name = @idx);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_settlement_owner_status ON settlement_record(owner_username, status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引 2: owner_username + order_time
SET @idx := 'idx_settlement_owner_time';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'settlement_record' AND index_name = @idx);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_settlement_owner_time ON settlement_record(owner_username, order_time)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引 3: created_by + status
SET @idx := 'idx_settlement_created_status';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'settlement_record' AND index_name = @idx);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_settlement_created_status ON settlement_record(created_by, status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

---

#### 3.3 数据迁移（可选）
- [ ] 将 owner_username 回填到 created_by
- [ ] 验证数据完整性

**SQL 脚本**：
```sql
-- 将 owner_username 回填到 created_by
UPDATE settlement_record 
SET created_by = owner_username 
WHERE created_by IS NULL AND owner_username IS NOT NULL;

-- 验证
SELECT COUNT(*) as total,
       SUM(CASE WHEN created_by IS NULL THEN 1 ELSE 0 END) as null_count
FROM settlement_record;
```

---

### ✅ 第四阶段：测试验证（必须）

#### 4.1 后端 API 测试
- [ ] ADMIN 用户查询：能看到全部数据
- [ ] USER 用户查询：只能看到自己的数据
- [ ] USER 用户尝试确认：返回 403 Forbidden
- [ ] USER 用户尝试删除：返回 403 Forbidden

**测试用例**：
```bash
# 1. ADMIN 查询全部
curl -H "Authorization: Bearer <admin_token>" \
  http://localhost:8080/api/settlements?page=1&size=20

# 2. USER 查询自己的
curl -H "Authorization: Bearer <user_token>" \
  http://localhost:8080/api/settlements?page=1&size=20

# 3. USER 尝试确认（应该失败）
curl -X PUT \
  -H "Authorization: Bearer <user_token>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}' \
  http://localhost:8080/api/settlements/1/confirm
```

---

#### 4.2 前端 UI 测试
- [ ] ADMIN 登录：显示所有按钮和筛选项
- [ ] USER 登录：隐藏批量操作、删除按钮
- [ ] USER 登录：隐藏"归属用户"筛选项
- [ ] USER 登录：显示权限提示信息
- [ ] USER 点击操作按钮：无反应（按钮禁用）

**测试步骤**：
1. 以 ADMIN 身份登录
   - 验证显示"批量确认"按钮
   - 验证显示"删除所选"按钮
   - 验证显示"归属用户"筛选项
   - 验证可以执行确认/删除操作

2. 以 USER 身份登录
   - 验证隐藏"批量确认"按钮
   - 验证隐藏"删除所选"按钮
   - 验证隐藏"归属用户"筛选项
   - 验证显示权限提示信息
   - 验证操作列显示"仅查看"
   - 验证只能看到自己的数据

---

#### 4.3 数据库性能测试
- [ ] 查询性能对比（添加索引前后）
- [ ] 大数据量测试（10万+ 记录）

**测试 SQL**：
```sql
-- 查询计划分析（应该使用索引）
EXPLAIN SELECT * FROM settlement_record 
WHERE owner_username = 'user1' 
  AND status = 'PENDING' 
ORDER BY order_time DESC 
LIMIT 20;

-- 性能测试
SELECT SQL_NO_CACHE COUNT(*) FROM settlement_record 
WHERE owner_username = 'user1' 
  AND status = 'PENDING';
```

---

### ✅ 第五阶段：部署上线（可选）

#### 5.1 预发布检查
- [ ] 代码审查完成
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试通过

#### 5.2 灰度发布
- [ ] 先在测试环境验证
- [ ] 再在预发布环境验证
- [ ] 最后在生产环境发布

#### 5.3 发布后监控
- [ ] 监控 API 响应时间
- [ ] 监控数据库查询性能
- [ ] 监控错误日志
- [ ] 收集用户反馈

---

## 📊 改动影响范围

### 后端文件
| 文件 | 改动类型 | 影响范围 |
|------|--------|--------|
| SettlementFilterRequest.java | 新增字段 | 中等 |
| SettlementCursorRequest.java | 新增字段 | 中等 |
| SettlementController.java | 逻辑修改 | 中等 |
| SettlementServiceImpl.java | 逻辑修改 | 大 |
| SettlementService.java | 接口修改 | 小 |

### 前端文件
| 文件 | 改动类型 | 影响范围 |
|------|--------|--------|
| SettlementsView.vue | UI 修改 | 大 |
| auth.ts | 无改动 | 无 |
| settlements.ts | 无改动 | 无 |

### 数据库
| 操作 | 影响范围 | 风险 |
|------|--------|------|
| 添加 created_by 字段 | 低 | 低 |
| 添加索引 | 低 | 低 |
| 数据迁移 | 中 | 中 |

---

## ⏱️ 预计工作量

| 阶段 | 工作量 | 工时 |
|------|-------|------|
| 第一阶段（后端 API） | 中等 | 2-3 小时 |
| 第二阶段（前端 UI） | 中等 | 2-3 小时 |
| 第三阶段（数据库） | 小 | 1 小时 |
| 第四阶段（测试） | 中等 | 2-3 小时 |
| **总计** | | **7-10 小时** |

---

## 🎯 成功标准

- ✅ ADMIN 用户能看到全部结账记录
- ✅ USER 用户只能看到自己的结账记录
- ✅ USER 用户无法执行确认/删除操作
- ✅ 前端隐藏不相关的 UI 元素
- ✅ 后端返回 403 错误拒绝非法操作
- ✅ 查询性能提升 50% 以上
- ✅ 所有测试用例通过


