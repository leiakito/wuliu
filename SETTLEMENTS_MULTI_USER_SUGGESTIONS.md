# 结账管理界面 - 多用户权限隔离建议方案

## 📋 需求分析

**目标**：不同用户登录时显示不同的结账管理界面
- ADMIN 用户：查看全部结账记录
- 普通 USER 用户：只查看自己的结账记录（按 `owner_username` 或 `created_by` 过滤）

---

## 🔍 当前系统分析

### 1. 数据库设计现状

#### ✅ 已有的字段支持
```sql
-- settlement_record 表
- owner_username VARCHAR(64)      -- 结账记录的所有者
- confirmed_by VARCHAR(64)         -- 确认人
- created_at DATETIME              -- 创建时间

-- sys_user 表
- role VARCHAR(32)                 -- 用户角色（ADMIN/USER）
- username VARCHAR(64)             -- 用户名
```

#### ⚠️ 需要关注的问题
1. **owner_username 填充不完整**：某些结算记录的 `owner_username` 可能为 NULL
2. **created_by 字段缺失**：`settlement_record` 表没有 `created_by` 字段
3. **权限粒度**：目前只有 ADMIN/USER 两个角色，无法区分不同部门或团队

### 2. 前端权限现状

#### ✅ 已有的权限检查
```javascript
// SettlementsView.vue 中
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

// 按角色显示/隐藏按钮
<el-button v-if="isAdmin" @click="...">批量确认</el-button>
```

#### ⚠️ 缺陷
- 只有 UI 级别的权限控制，没有数据级别的过滤
- 普通用户仍然可以看到所有结账记录（如果直接调用 API）
- 前端权限检查可被绕过

### 3. 后端权限现状

#### ✅ 已有的权限检查
```java
// SettlementController.java
@SaCheckRole("ADMIN")  // 仅 ADMIN 可执行
public ApiResponse<Void> confirm(...) { ... }
```

#### ⚠️ 缺陷
- 数据过滤不完整：`fetchSettlements()` 没有按用户过滤
- 普通用户可以查看所有结账记录
- 批量操作（批量确认、批量删除）没有数据所有权验证

---

## 💡 三大解决方案对比

### 方案一：纯角色权限（RBAC）

#### 实现方式
```
ADMIN 角色 → 查看全部数据
USER 角色  → 查看全部数据（无限制）
```

#### 优点
- 实现简单
- 代码改动最小

#### 缺点
❌ 无法实现"用户只看自己的数据"需求
❌ 安全性低
❌ 不符合业务需求

---

### 方案二：纯数据所有权（ABAC）

#### 实现方式
```
根据 owner_username 或 created_by 过滤数据
ADMIN → 查看全部
USER  → 只看 owner_username = 当前用户 的记录
```

#### 优点
✅ 符合业务需求
✅ 数据隔离清晰
✅ 易于扩展

#### 缺点
❌ 需要修改数据库（添加 `created_by` 字段）
❌ 需要修改 API 查询逻辑
❌ 需要修改前端过滤条件

#### 数据库改动
```sql
-- 1. 添加 created_by 字段到 settlement_record
ALTER TABLE settlement_record 
ADD COLUMN created_by VARCHAR(64) AFTER owner_username;

-- 2. 创建索引优化查询
CREATE INDEX idx_settlement_created_by ON settlement_record(created_by);
CREATE INDEX idx_settlement_owner_created ON settlement_record(owner_username, created_by);
```

---

### 方案三：混合方案（RBAC + ABAC）⭐ 推荐

#### 实现方式
```
ADMIN 角色
  ├─ 查看全部结账记录
  ├─ 可以按 owner_username 筛选
  └─ 可以执行所有操作（确认、删除、导出等）

USER 角色
  ├─ 只查看 owner_username = 当前用户 的记录
  ├─ 不显示"归属用户"筛选项
  └─ 只能查看和导出，不能确认/删除
```

#### 优点
✅ 符合业务需求
✅ 安全性高（前后端双重检查）
✅ 易于维护和扩展
✅ 用户体验好（隐藏不相关的 UI 元素）

#### 缺点
⚠️ 需要修改前后端代码
⚠️ 需要调整数据库索引

---

## 🛠️ 方案三详细实施步骤

### 第一步：数据库调整

#### 1.1 添加 `created_by` 字段（可选但推荐）
```sql
-- 检查字段是否存在，不存在则添加
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'settlement_record'
    AND COLUMN_NAME = 'created_by'
);

SET @sql := IF(@col_exists = 0,
  'ALTER TABLE settlement_record ADD COLUMN created_by VARCHAR(64) AFTER owner_username',
  'DO 0'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

#### 1.2 优化索引（在 performance_optimization.sql 中添加）
```sql
-- 单字段索引
CREATE INDEX idx_settlement_created_by ON settlement_record(created_by);
CREATE INDEX idx_settlement_owner_created ON settlement_record(owner_username, created_by);

-- 复合索引（用于常见查询）
CREATE INDEX idx_settlement_owner_status ON settlement_record(owner_username, status);
CREATE INDEX idx_settlement_created_status ON settlement_record(created_by, status);
```

#### 1.3 数据迁移（可选）
```sql
-- 将 owner_username 回填到 created_by（如果 created_by 为空）
UPDATE settlement_record 
SET created_by = owner_username 
WHERE created_by IS NULL AND owner_username IS NOT NULL;

-- 或者从关联的订单表获取
UPDATE settlement_record sr
SET sr.created_by = (
  SELECT or.created_by FROM order_record or 
  WHERE or.id = sr.order_id
)
WHERE sr.created_by IS NULL;
```

---

### 第二步：后端 API 改造

#### 2.1 修改 SettlementFilterRequest（DTO）
```java
// 添加字段用于权限过滤
public class SettlementFilterRequest {
    // 现有字段...
    
    // 新增：当前登录用户名（由后端注入，前端不传）
    private String currentUsername;
    
    // 新增：当前用户角色（由后端注入，前端不传）
    private String currentRole;
}
```

#### 2.2 修改 SettlementController
```java
@GetMapping
@SaCheckLogin
public ApiResponse<PageResponse<SettlementRecord>> page(SettlementFilterRequest request) {
    // 注入当前用户信息
    String currentUser = StpUtil.getLoginIdAsString();
    String currentRole = StpUtil.getRoleList().get(0);
    
    request.setCurrentUsername(currentUser);
    request.setCurrentRole(currentRole);
    
    IPage<SettlementRecord> page = settlementService.list(request);
    return ApiResponse.ok(PageResponse.from(page));
}
```

#### 2.3 修改 SettlementServiceImpl.buildQueryWrapper()
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
        // ... 其他字段
    }
    
    // 权限过滤：非 ADMIN 用户只能看自己的数据
    if (!"ADMIN".equals(currentRole) && StringUtils.hasText(currentUsername)) {
        wrapper.eq(SettlementRecord::getOwnerUsername, currentUsername);
    }
    
    // 其他过滤条件...
    return wrapper;
}
```

#### 2.4 修改确认/删除操作的权限检查
```java
@PutMapping("/{id}/confirm")
@SaCheckRole("ADMIN")  // 仅 ADMIN 可确认
public ApiResponse<Void> confirm(
    @PathVariable Long id,
    @Valid @RequestBody SettlementConfirmRequest request) {
    settlementService.confirm(id, request, StpUtil.getLoginIdAsString());
    return ApiResponse.ok();
}

// 在 service 中添加所有权验证
public void confirm(Long id, SettlementConfirmRequest request, String operator) {
    SettlementRecord record = settlementRecordMapper.selectById(id);
    if (record == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "待结账数据不存在");
    }
    
    // 验证操作权限（ADMIN 可操作任何记录）
    String role = StpUtil.getRoleList().get(0);
    if (!"ADMIN".equals(role)) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此记录");
    }
    
    // ... 确认逻辑
}
```

---

### 第三步：前端 UI 改造

#### 3.1 修改 SettlementsView.vue 的筛选条件
```javascript
// 在 setup 中添加
const isAdmin = computed(() => auth.user?.role === 'ADMIN');
const currentUsername = computed(() => auth.user?.username);

// 修改过滤表单
<el-form-item label="归属用户" v-if="isAdmin">
  <!-- 仅 ADMIN 显示此筛选项 -->
  <el-select v-model="filters.ownerUsername" ...>
    <!-- 选项 -->
  </el-select>
</el-form-item>

<!-- 普通用户显示提示信息 -->
<el-form-item v-if="!isAdmin">
  <el-alert type="info" :closable="false">
    您正在查看自己的结账记录（{{ currentUsername }}）
  </el-alert>
</el-form-item>
```

#### 3.2 修改表格操作列
```javascript
<el-table-column label="操作" width="160">
  <template #default="{ row }">
    <!-- 仅 ADMIN 可确认/删除 -->
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
    
    <!-- 普通用户只能查看 -->
    <el-button v-if="!isAdmin" link type="info" disabled>
      仅查看
    </el-button>
  </template>
</el-table-column>
```

#### 3.3 修改批量操作按钮
```javascript
<!-- 仅 ADMIN 显示批量操作 -->
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
```

#### 3.4 修改导出功能
```javascript
// 普通用户可以导出自己的数据
const exportData = async () => {
  exporting.value = true;
  try {
    const exportParams: SettlementExportRequest = {
      status: filters.status || undefined,
      // 非 ADMIN 用户自动添加 ownerUsername 过滤
      ownerUsername: !isAdmin.value ? currentUsername.value : (filters.ownerUsername?.trim() || undefined)
    };
    // ... 导出逻辑
  } finally {
    exporting.value = false;
  }
};
```

---

### 第四步：性能优化

#### 4.1 在 performance_optimization.sql 中添加索引
```sql
-- 新增索引支持权限过滤
SET @idx := 'idx_settlement_owner_status';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'settlement_record' AND index_name = @idx);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_settlement_owner_status ON settlement_record(owner_username, status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_owner_time';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'settlement_record' AND index_name = @idx);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_settlement_owner_time ON settlement_record(owner_username, order_time)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_created_status';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'settlement_record' AND index_name = @idx);
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_settlement_created_status ON settlement_record(created_by, status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

#### 4.2 查询优化建议
```sql
-- 查询示例（带权限过滤）
-- ADMIN 查询
EXPLAIN SELECT * FROM settlement_record 
WHERE status = 'PENDING' 
ORDER BY order_time DESC 
LIMIT 20;

-- 普通用户查询
EXPLAIN SELECT * FROM settlement_record 
WHERE owner_username = 'user1' 
  AND status = 'PENDING' 
ORDER BY order_time DESC 
LIMIT 20;
-- 应该使用 idx_settlement_owner_status 索引
```

---

## 📊 实施对比表

| 方面 | 方案一 | 方案二 | 方案三 |
|------|-------|-------|-------|
| **实现难度** | ⭐ 简单 | ⭐⭐ 中等 | ⭐⭐⭐ 较复杂 |
| **安全性** | ❌ 低 | ✅ 高 | ✅✅ 很高 |
| **符合需求** | ❌ 不符合 | ✅ 符合 | ✅✅ 完全符合 |
| **可扩展性** | ⭐ 差 | ⭐⭐ 中等 | ⭐⭐⭐ 优秀 |
| **性能影响** | ✅ 无 | ⚠️ 轻微 | ⚠️ 轻微 |
| **代码改动** | 最小 | 中等 | 较大 |
| **数据库改动** | 无 | 有 | 有 |
| **推荐度** | ❌ 不推荐 | ⭐⭐ 可选 | ⭐⭐⭐ 强烈推荐 |

---

## 🚀 实施优先级建议

### 第一阶段（必须）
1. ✅ 后端 API 添加权限过滤逻辑
2. ✅ 前端隐藏不相关的 UI 元素
3. ✅ 添加权限检查提示

### 第二阶段（推荐）
4. ✅ 数据库添加 `created_by` 字段
5. ✅ 添加相关索引优化查询性能
6. ✅ 完善权限验证逻辑

### 第三阶段（可选）
7. ✅ 添加审计日志记录权限操作
8. ✅ 实现更细粒度的权限控制（如部门级别）
9. ✅ 添加权限管理后台界面

---

## ⚠️ 注意事项

### 1. 数据一致性
- 确保 `owner_username` 字段始终有值
- 定期检查和修复数据不一致问题
- 在创建结账记录时必须设置 `owner_username`

### 2. 向后兼容性
- 旧数据的 `owner_username` 可能为 NULL，需要迁移
- 使用 `COALESCE(owner_username, created_by)` 处理 NULL 值
- 保持 API 版本兼容性

### 3. 性能考虑
- 添加索引后需要运行 `ANALYZE TABLE` 更新统计信息
- 监控查询性能，特别是大数据量情况
- 考虑缓存常用查询结果

### 4. 安全建议
- 前后端都要进行权限检查（不要只依赖前端）
- 定期审计权限相关的操作日志
- 避免在 URL 或请求参数中暴露用户信息

### 5. 测试建议
- 测试 ADMIN 用户能看到所有数据
- 测试普通用户只能看到自己的数据
- 测试跨用户操作是否被正确拒绝
- 测试权限变更后的数据可见性

---

## 📝 总结

**推荐方案**：方案三（混合方案）

**核心改动**：
1. 后端 API 添加权限过滤
2. 前端隐藏不相关的 UI 元素
3. 数据库优化索引

**预期效果**：
- ✅ ADMIN 用户查看全部结账记录
- ✅ 普通用户只查看自己的结账记录
- ✅ 安全性和用户体验都得到提升
- ✅ 系统易于扩展和维护


