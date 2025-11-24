<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>硬件价格分析</h2>
        <p class="sub">按日期与型号查看价格走势</p>
      </div>
      <div class="actions">
        <el-button @click="loadData">刷新</el-button>
      </div>
    </div>

    <el-card>
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="filters.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item label="型号">
          <el-select
            v-model="filters.itemName"
            placeholder="选择型号"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option v-for="item in modelOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="型号搜索">
          <el-input
            v-model="filters.keyword"
            placeholder="输入型号关键字"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="chart-card">
      <div class="chart-header">
        <div>
          <strong>{{ currentModel || '请选择型号' }}</strong>
          <span class="muted" v-if="series.length">（{{ series.length }} 天数据）</span>
        </div>
        <div class="muted">单位：元</div>
      </div>
      <div v-if="series.length" class="chart-wrap">
        <svg :viewBox="`0 0 ${viewBoxWidth} 220`" preserveAspectRatio="none" class="chart">
          <polyline
            :points="polylinePoints"
            fill="none"
            stroke="#409EFF"
            stroke-width="3"
          />
          <g v-for="point in pointMap" :key="point.x" class="chart-point">
            <circle :cx="point.x" :cy="point.y" r="4" fill="#409EFF" />
            <text :x="point.x" :y="point.y - 10" text-anchor="middle" class="chart-text">{{ point.price }}</text>
          </g>
        </svg>
        <div class="chart-axis">
          <span v-for="item in series" :key="item.priceDate">{{ item.priceDate }}</span>
        </div>
      </div>
      <div v-else class="empty">暂无数据，请调整筛选</div>
    </el-card>

    <el-card class="table-card">
      <el-table :data="series" height="420" v-loading="loading">
        <el-table-column prop="priceDate" label="日期" width="140" />
        <el-table-column prop="itemName" label="型号" />
        <el-table-column prop="price" label="价格" width="140">
          <template #default="{ row }">￥{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人" width="140" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { fetchHardwarePrices } from '@/api/hardware';
import type { HardwarePrice } from '@/types/models';
import { ElMessage } from 'element-plus';

const filters = reactive({
  range: [] as string[],
  itemName: '',
  keyword: ''
});
const loading = ref(false);
const data = ref<HardwarePrice[]>([]);

const modelOptions = computed(() =>
  Array.from(new Set(data.value.map(item => item.itemName))).sort()
);

const filteredList = computed(() => {
  const target = filters.itemName?.trim();
  const keyword = filters.keyword?.trim().toLowerCase();
  let list = data.value;
  if (keyword) {
    list = list.filter(item => item.itemName.toLowerCase().includes(keyword));
  } else if (target) {
    list = list.filter(item => item.itemName === target);
  }
  return list;
});

const series = computed(() => {
  const map = new Map<string, HardwarePrice>();
  filteredList.value.forEach(item => {
    const key = `${item.itemName}__${item.priceDate}`;
    map.set(key, item); // 后面的覆盖前面的，保留最新导入的价格
  });
  const list = Array.from(map.values());
  return [...list].sort((a, b) => a.priceDate.localeCompare(b.priceDate));
});

const viewBoxWidth = computed(() => Math.max(series.value.length * 80, 320));

const pointMap = computed(() => {
  const list = series.value;
  if (!list.length) return [] as { x: number; y: number; price: string }[];
  const prices = list.map(item => Number(item.price) || 0);
  const max = Math.max(...prices, 1);
  const min = Math.min(...prices, 0);
  const span = max - min || 1;
  const gap = viewBoxWidth.value / Math.max(list.length - 1, 1);
  return list.map((item, idx) => {
    const normalized = (Number(item.price) - min) / span;
    const y = 200 - normalized * 160 + 10;
    return { x: idx * gap, y, price: formatPrice(item.price) };
  });
});

const polylinePoints = computed(() => pointMap.value.map(p => `${p.x},${p.y}`).join(' '));

const currentModel = computed(() => filters.itemName || modelOptions.value[0] || '');

const formatPrice = (value?: number | string) => {
  const num = Number(value) || 0;
  return num.toFixed(2);
};

const defaultRange = () => {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 29);
  return [formatDate(start), formatDate(end)];
};

const formatDate = (date: Date) => date.toISOString().slice(0, 10);

const loadData = async () => {
  loading.value = true;
  try {
    const params: any = {};
    if (Array.isArray(filters.range) && filters.range.length === 2) {
      params.startDate = filters.range[0];
      params.endDate = filters.range[1];
    }
    if (filters.itemName) {
      params.itemName = filters.itemName;
    }
    data.value = await fetchHardwarePrices(params);
  } catch (error) {
    console.error(error);
    ElMessage.error('加载价格数据失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.range = defaultRange();
  filters.itemName = '';
  filters.keyword = '';
  loadData();
};

resetFilters();
</script>

<style scoped>
.chart-card {
  margin-top: 16px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.chart-wrap {
  width: 100%;
  overflow-x: auto;
  padding-bottom: 8px;
}

.chart {
  width: 100%;
  min-height: 220px;
}

.chart-axis {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: #666;
}

.chart-text {
  font-size: 12px;
  fill: #666;
}

.empty {
  text-align: center;
  padding: 32px 0;
  color: #888;
}

.table-card {
  margin-top: 16px;
}
</style>
