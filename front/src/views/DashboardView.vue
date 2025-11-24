<template>
  <div class="page dashboard-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--primary" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">总订单数</div>
              <div class="stat-value">{{ stats.totalOrders }}</div>
              <div class="stat-trend">
                <el-icon class="trend-up"><TrendCharts /></el-icon>
                <span>较上月 +12%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--success" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="32"><Checked /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">已完成</div>
              <div class="stat-value">{{ stats.completedOrders }}</div>
              <div class="stat-trend">
                <el-icon class="trend-up"><TrendCharts /></el-icon>
                <span>较上月 +8%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--warning" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="32"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">处理中</div>
              <div class="stat-value">{{ stats.pendingOrders }}</div>
              <div class="stat-trend">
                <el-icon class="trend-down"><TrendCharts /></el-icon>
                <span>较上月 -5%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--info" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="32"><Wallet /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">总金额</div>
              <div class="stat-value">¥{{ stats.totalAmount }}</div>
              <div class="stat-trend">
                <el-icon class="trend-up"><TrendCharts /></el-icon>
                <span>较上月 +15%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快速操作 -->
    <el-card v-if="isAdmin" class="quick-actions">
      <template #header>
        <div class="card-header">
          <span>快速操作</span>
        </div>
      </template>
      <div class="action-buttons">
        <el-button type="primary" @click="goOrders">
          <el-icon><Plus /></el-icon>
          新建订单
        </el-button>
        <el-button type="success" @click="goSettlements">
          <el-icon><Money /></el-icon>
          结算管理
        </el-button>
        <el-button type="info" @click="goUsers">
          <el-icon><User /></el-icon>
          用户管理
        </el-button>
      </div>
    </el-card>

    <!-- 查询面板 -->
    <el-card ref="profileCard" :class="{ highlight: highlightProfile }" class="search-panel">
      <template #header>
        <div class="card-header">
          <span>单号查询</span>
          <el-tag size="small" :type="isAdmin ? 'danger' : 'info'">
            {{ isAdmin ? '管理员' : '用户' }}
          </el-tag>
        </div>
      </template>
      <el-input
        v-model="searchInput"
        type="textarea"
        :rows="4"
        placeholder="支持多个单号或 SN，换行 / 逗号 / 分号分隔"
      />
      <div class="actions">
        <el-button type="primary" :loading="searchLoading" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button text :disabled="!results.length" @click="clearResults">
          <el-icon><Delete /></el-icon>
          清空结果
        </el-button>
      </div>
      <p class="tips">
        如需录入、批量导入或导出，请切换到左侧导航的"物流单号"模块；查询结果仅保留在当前页面。
      </p>
    </el-card>

    <!-- 查询结果 -->
    <el-card v-if="results.length" class="table-card">
      <template #header>
        <div class="card-header">
          <span>查询结果 ({{ results.length }} 条)</span>
          <el-button type="primary" size="small" @click="exportResults">
            <el-icon><Download /></el-icon>
            导出结果
          </el-button>
        </div>
      </template>
      <el-table :data="results" style="width: 100%" stripe>
        <el-table-column prop="orderDate" label="下单日期" width="140" />
        <el-table-column prop="orderTime" label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="运单号" width="200" />
        <el-table-column prop="model" label="型号" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="amount" label="金额" width="120">
          <template #default="{ row }">{{ row.amount ?? '-' }} {{ row.currency }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import { searchOrders } from '@/api/orders';
import type { OrderRecord } from '@/types/models';
import { ElMessage } from 'element-plus';
import {
  Document,
  Checked,
  Clock,
  Wallet,
  TrendCharts,
  Plus,
  Money,
  User,
  Search,
  Delete,
  Download
} from '@element-plus/icons-vue';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

const searchInput = ref('');
const searchLoading = ref(false);
const results = ref<OrderRecord[]>([]);
const profileCard = ref();
const highlightProfile = ref(false);

const stats = reactive({
  totalOrders: 1250,
  completedOrders: 980,
  pendingOrders: 270,
  totalAmount: '328,450'
});

const statusDict = [
  { value: 'UNPAID', label: '未打款', tag: 'warning' },
  { value: 'NOT_RECEIVED', label: '未收货', tag: 'info' },
  { value: 'PAID', label: '已打款', tag: 'success' }
];

const statusText = (value?: string) => statusDict.find(item => item.value === value)?.label ?? '未知';
const statusTag = (value?: string) => statusDict.find(item => item.value === value)?.tag ?? 'info';
const formatDateTime = (value?: string) => (value ? value.replace('T', ' ').replace('Z', '') : '-');

const handleSearch = async () => {
  const list = searchInput.value
    .split(/\n|,|;/)
    .map(item => item.trim())
    .filter(Boolean);
  if (!list.length) {
    ElMessage.warning('请先输入单号或 SN');
    return;
  }
  searchLoading.value = true;
  try {
    const data = await searchOrders(list);
    if (!data.length) {
      ElMessage.warning('未查询到对应订单');
      return;
    }
    results.value = data;
  } finally {
    searchLoading.value = false;
  }
};

const clearResults = () => {
  results.value = [];
};

const exportResults = () => {
  ElMessage.success('导出功能开发中');
};

const goOrders = () => {
  router.push('/orders');
};

const goSettlements = () => {
  router.push('/settlements');
};

const goUsers = () => {
  router.push('/users');
};

watch(
  () => route.query.focus,
  async focus => {
    if (focus === 'profile') {
      await nextTick();
      highlightProfile.value = true;
      profileCard.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setTimeout(() => {
        highlightProfile.value = false;
      }, 1500);
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.dashboard-page {
  max-width: 1400px;
  margin: 0 auto;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  transition: all 0.3s ease;
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
}

.stat-card--primary .stat-icon {
  color: #409eff;
  background: linear-gradient(135deg, #e6f4ff 0%, #bae0ff 100%);
}

.stat-card--success .stat-icon {
  color: #67c23a;
  background: linear-gradient(135deg, #f0f9eb 0%, #c2e7b0 100%);
}

.stat-card--warning .stat-icon {
  color: #e6a23c;
  background: linear-gradient(135deg, #fdf6ec 0%, #f5dab1 100%);
}

.stat-card--info .stat-icon {
  color: #909399;
  background: linear-gradient(135deg, #f4f4f5 0%, #d3d4d6 100%);
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #67c23a;
}

.trend-up {
  color: #67c23a;
}

.trend-down {
  color: #f56c6c;
  transform: rotate(180deg);
}

.quick-actions {
  margin-bottom: 20px;
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.search-panel {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.tips {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.table-card {
  margin-top: 16px;
}

.highlight {
  animation: pulse 0.6s ease-in-out 3;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 rgba(64, 158, 255, 0.4);
  }
  50% {
    box-shadow: 0 0 12px rgba(64, 158, 255, 0.8);
  }
  100% {
    box-shadow: 0 0 0 rgba(64, 158, 255, 0.4);
  }
}

@media (max-width: 768px) {
  .stat-value {
    font-size: 24px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}
</style>
