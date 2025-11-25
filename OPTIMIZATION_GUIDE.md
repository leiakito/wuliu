# 进一步优化建议

基于当前代码分析，我提供以下多个方向的优化方案：

## 1. 数据库层优化

### 1.1 使用 Redis 缓存热点数据

**问题分析：**
- `attachOrderInfo()` 方法每次查询都要关联 `order_record` 和 `user_submission` 表
- 归属用户 (`ownerUsername`) 查询频繁且数据变化不频繁
- 硬件价格 (`hardware_price`) 查询重复度高

**优化方案：**
```java
@Service
@RequiredArgsConstructor
public class SettlementCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String OWNER_CACHE_PREFIX = "settlement:owner:";
    private static final String PRICE_CACHE_PREFIX = "settlement:price:";
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    public String getOwnerUsername(String trackingNumber) {
        String key = OWNER_CACHE_PREFIX + trackingNumber;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void cacheOwnerUsername(String trackingNumber, String owner) {
        String key = OWNER_CACHE_PREFIX + trackingNumber;
        redisTemplate.opsForValue().set(key, owner, CACHE_TTL);
    }

    public BigDecimal getHardwarePrice(String model, LocalDate date) {
        String key = PRICE_CACHE_PREFIX + model + ":" + date;
        return (BigDecimal) redisTemplate.opsForValue().get(key);
    }

    public void cacheHardwarePrice(String model, LocalDate date, BigDecimal price) {
        String key = PRICE_CACHE_PREFIX + model + ":" + date;
        redisTemplate.opsForValue().set(key, price, Duration.ofDays(1));
    }
}
```

**预期收益：**
- 减少 50-70% 的关联查询
- 查询响应时间从 200-500ms 降至 50-100ms

### 1.2 批量查询优化

**当前问题：**
`attachOrderInfo()` 中使用 `selectBatchIds()` 可能触发多次数据库往返

**优化方案：**
```java
// 使用 MyBatis-Plus 的 in 查询 + 手动指定字段，减少数据传输
private void attachOrderInfo(List<SettlementRecord> records) {
    if (CollectionUtils.isEmpty(records)) {
        return;
    }

    List<Long> orderIds = records.stream()
        .map(SettlementRecord::getOrderId)
        .filter(id -> id != null && id > 0)
        .distinct()  // 去重避免重复查询
        .collect(Collectors.toList());

    if (orderIds.isEmpty()) {
        return;
    }

    // 只查询需要的字段，减少数据传输
    LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
    wrapper.select(OrderRecord::getId, OrderRecord::getStatus,
                   OrderRecord::getAmount, OrderRecord::getCreatedBy,
                   OrderRecord::getOrderTime, OrderRecord::getSn,
                   OrderRecord::getModel)
           .in(OrderRecord::getId, orderIds);

    List<OrderRecord> orders = orderRecordMapper.selectList(wrapper);
    Map<Long, OrderRecord> orderMap = orders.stream()
        .collect(Collectors.toMap(OrderRecord::getId, o -> o));

    // 后续逻辑...
}
```

### 1.3 添加更多数据库索引

除了已有的索引，还可以添加：

```sql
-- 复合索引优化 keyword 关键字查询
CREATE INDEX idx_settlement_tracking_model ON settlement_record(tracking_number, model);

-- 优化日期范围查询
CREATE INDEX idx_settlement_payable_status ON settlement_record(payable_at, status);

-- 优化 order_record 的 SN 查询（如果数据量大）
CREATE INDEX idx_order_sn_upper ON order_record((UPPER(sn)));

-- 全文索引支持更复杂的搜索（MySQL 5.7+）
ALTER TABLE settlement_record ADD FULLTEXT INDEX ft_idx_model(model);
```

## 2. 后端代码优化

### 2.1 异步处理非关键操作

**当前问题：**
`attachOrderInfo()` 中的 `resolveHardwarePrice()` 会同步更新订单，阻塞查询响应

**优化方案：**
```java
@Async
public CompletableFuture<Void> asyncUpdateHardwarePrice(OrderRecord order, SettlementRecord record) {
    BigDecimal price = resolveHardwarePrice(order);
    if (price != null) {
        record.setAmount(price);
        record.setOrderAmount(price);
        order.setAmount(price);
        orderRecordMapper.updateById(order);
        settlementRecordMapper.updateById(record);
    }
    return CompletableFuture.completedFuture(null);
}

// 在 attachOrderInfo 中使用
if ((record.getManualInput() == null || Boolean.FALSE.equals(record.getManualInput()))
    && (record.getAmount() == null || BigDecimal.ZERO.compareTo(record.getAmount()) == 0)) {
    asyncUpdateHardwarePrice(order, record); // 异步更新，不阻塞
}
```

### 2.2 使用数据库视图简化查询

创建视图预关联数据：

```sql
CREATE VIEW settlement_with_order AS
SELECT
    s.*,
    o.status AS order_status,
    o.amount AS order_amount,
    o.created_by AS order_created_by,
    o.sn AS order_sn
FROM settlement_record s
LEFT JOIN order_record o ON s.order_id = o.id
WHERE s.deleted = 0;
```

然后创建对应的实体类直接查询视图，避免 Java 代码中的关联逻辑。

### 2.3 分页查询优化 - 游标分页

**当前问题：**
使用 `OFFSET` 分页，当跳到后面页码时性能差（如第 43 页）

**优化方案：**
使用基于 ID 的游标分页：

```java
public IPage<SettlementRecord> listByCursor(SettlementFilterRequest request, Long lastId) {
    LambdaQueryWrapper<SettlementRecord> wrapper = buildWrapper(request);

    if (lastId != null && lastId > 0) {
        wrapper.lt(SettlementRecord::getId, lastId); // 游标条件
    }

    wrapper.orderByDesc(SettlementRecord::getId);
    wrapper.last("LIMIT " + request.getSize());

    List<SettlementRecord> records = settlementRecordMapper.selectList(wrapper);
    // ... 构造分页结果
}
```

**前端配合：**
```typescript
// 记录上一页的最后一条记录 ID
const lastRecordId = ref<number | null>(null);

const loadNextPage = async () => {
  if (records.value.length > 0) {
    lastRecordId.value = records.value[records.value.length - 1].id;
  }
  const data = await fetchSettlementsByCursor({ ...params, lastId: lastRecordId.value });
  // ...
};
```

## 3. 前端优化

### 3.1 虚拟滚动替代传统分页

**当前问题：**
用户需要点击多次翻页才能找到深层数据（如第 43 页）

**优化方案：**
使用虚拟滚动组件，支持无限加载：

```vue
<template>
  <el-table-v2
    :columns="columns"
    :data="records"
    :width="tableWidth"
    :height="600"
    :estimated-row-height="50"
    @end-reached="loadMore"
  />
</template>

<script setup lang="ts">
import { ElTableV2 } from 'element-plus';

const loadMore = async () => {
  if (loading.value || !hasMore.value) return;
  filters.page += 1;
  const data = await fetchSettlements(params.value);
  records.value.push(...data.records); // 追加数据
  hasMore.value = records.value.length < data.total;
};
</script>
```

### 3.2 防抖优化 - 使用 VueUse

**当前优化：**
已移除自动搜索，但可以为用户输入添加更智能的提示

```typescript
import { useDebounceFn } from '@vueuse/core';

const debouncedSearch = useDebounceFn(() => {
  loadData();
}, 300);

// 可选：显示输入提示，不触发查询
const showSearchPreview = useDebounceFn((keyword: string) => {
  // 调用轻量级接口获取建议
  suggestKeywords(keyword);
}, 200);
```

### 3.3 本地缓存查询结果

```typescript
import { useLocalStorage } from '@vueuse/core';

// 缓存最近查询结果
const queryCache = useLocalStorage<Map<string, PageResponse<SettlementRecord>>>(
  'settlement-query-cache',
  new Map()
);

const loadData = async () => {
  const cacheKey = JSON.stringify(params.value);
  const cached = queryCache.value.get(cacheKey);

  if (cached && Date.now() - cached.timestamp < 60000) {
    // 1分钟内的缓存直接使用
    records.value = cached.records;
    total.value = cached.total;
    return;
  }

  const data = await fetchSettlements(params.value);
  queryCache.value.set(cacheKey, { ...data, timestamp: Date.now() });
  records.value = data.records;
  total.value = data.total;
};
```

### 3.4 表格渲染优化

```vue
<template>
  <el-table
    :data="records"
    v-loading="loading"
    lazy
    :row-key="row => row.id"
    :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
  >
    <!-- 使用 v-memo 优化大列表渲染 -->
    <el-table-column label="SN" v-memo="[record.orderSn]">
      <template #default="{ row }">
        {{ row.orderSn }}
      </template>
    </el-table-column>
  </el-table>
</template>
```

## 4. 架构优化

### 4.1 读写分离

**适用场景：**
如果系统读多写少（查询量 >> 更新量）

**方案：**
```yaml
# application.yml
spring:
  datasource:
    master:
      url: jdbc:mysql://master-db:3306/logistics
    slave:
      url: jdbc:mysql://slave-db:3306/logistics
```

```java
@Configuration
public class DataSourceConfig {
    @Bean
    @Primary
    public DataSource routingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource());
        targetDataSources.put("slave", slaveDataSource());

        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource());
        return routingDataSource;
    }
}
```

### 4.2 ElasticSearch 全文搜索

**适用场景：**
如果需要复杂的关键字搜索、多字段匹配

**方案：**
```java
@Document(indexName = "settlements")
public class SettlementDocument {
    @Field(type = FieldType.Long)
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String trackingNumber;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String model;

    @Field(type = FieldType.Keyword)
    private String orderSn;

    // 支持拼音搜索、同义词等高级功能
}
```

### 4.3 API 响应压缩

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }

    @Bean
    public FilterRegistrationBean<GzipFilter> gzipFilter() {
        FilterRegistrationBean<GzipFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GzipFilter());
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
```

前端配合：
```typescript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Accept-Encoding': 'gzip, deflate'
  }
});
```

## 5. 监控与分析

### 5.1 添加慢查询日志

```java
@Aspect
@Component
public class QueryPerformanceAspect {
    private static final Logger log = LoggerFactory.getLogger(QueryPerformanceAspect.class);

    @Around("execution(* com.example.demo.settlement.service..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - start;

        if (executionTime > 1000) { // 超过1秒记录
            log.warn("慢查询 - {}.{} 耗时: {}ms",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                executionTime);
        }
        return result;
    }
}
```

### 5.2 MySQL 慢查询分析

```sql
-- 启用慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1; -- 超过1秒的查询

-- 查看慢查询
SELECT * FROM mysql.slow_log
WHERE query_time > 1
ORDER BY query_time DESC
LIMIT 10;

-- 使用 EXPLAIN 分析查询计划
EXPLAIN SELECT * FROM settlement_record
WHERE order_id IN (SELECT id FROM order_record WHERE UPPER(sn) = 'XXX');
```

## 6. 优先级建议

### 高优先级（立即实施）：
1. ✅ 数据库索引（已完成）
2. **批量查询字段优化** - 减少数据传输量
3. **分页查询深度优化** - 考虑游标分页或限制最大页数

### 中优先级（1-2周内）：
4. **Redis 缓存热点数据** - 归属用户和硬件价格
5. **异步处理非关键更新** - 硬件价格更新
6. **前端虚拟滚动** - 改善大数据量浏览体验

### 低优先级（长期规划）：
7. **读写分离** - 数据量达到百万级时考虑
8. **ElasticSearch** - 需要复杂搜索时考虑
9. **数据库视图** - 简化查询逻辑

## 7. 性能目标

### 当前性能基线（预估）：
- SN 精确查询：200-500ms（已优化索引后预计 50-150ms）
- 分页查询（前 10 页）：100-300ms（已优化索引后预计 30-100ms）
- 分页查询（第 40+ 页）：500-1500ms（需优化）
- 批量设置价格（100条）：1-3s（可优化至 0.5-1s）

### 优化后目标：
- SN 精确查询：< 100ms
- 分页查询（任意页）：< 200ms
- 批量设置价格（100条）：< 1s
- 首屏加载时间：< 500ms

## 8. 下一步行动

建议按以下顺序执行：

1. **执行数据库索引脚本**（立即）
2. **监控生产环境性能**（1-3天收集数据）
3. **实施批量查询优化**（代码改动小，收益明显）
4. **评估是否需要 Redis 缓存**（基于监控数据决定）
5. **前端体验优化**（虚拟滚动、本地缓存）

每次优化后都应该：
- 使用 `EXPLAIN` 验证 SQL 执行计划
- 记录优化前后的响应时间
- 进行压力测试确保稳定性
