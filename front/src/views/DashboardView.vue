<template>
  <div class="page">
    <div class="hero">
      <div>
        <h2>单号总查询</h2>
        <p class="sub">统一查询所有物流单的录入、打款与收货状态</p>
      </div>
      <el-button v-if="isAdmin" type="primary" @click="goOrders">前往订单管理</el-button>
    </div>

    <el-card ref="profileCard" :class="{ highlight: highlightProfile }">
      <template #header>
        <div class="card-header">
          <span>查询面板</span>
          <el-tag size="small" :type="isAdmin ? 'danger' : 'info'">
            {{ isAdmin ? '管理员' : '用户' }}
          </el-tag>
        </div>
      </template>
      <el-input
        v-model="searchInput"
        type="textarea"
        :rows="4"
        placeholder="支持多个单号，换行 / 逗号 / 分号分隔"
      />
      <div class="actions">
        <el-button type="primary" :loading="searchLoading" @click="handleSearch">查询</el-button>
        <el-button text :disabled="!results.length" @click="clearResults">清空结果</el-button>
      </div>
      <p class="tips">
        如需录入、批量导入或导出，请切换到左侧导航的“物流单号”模块；查询结果仅保留在当前页面。
      </p>
    </el-card>

    <el-card v-if="results.length" class="table-card">
      <el-table :data="results" style="width: 100%">
        <el-table-column prop="orderDate" label="下单日期" width="140" />
        <el-table-column prop="orderTime" label="时间" width="200">
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
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import { searchOrders } from '@/api/orders';
import type { OrderRecord } from '@/types/models';
import { ElMessage } from 'element-plus';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

const searchInput = ref('');
const searchLoading = ref(false);
const results = ref<OrderRecord[]>([]);
const profileCard = ref();
const highlightProfile = ref(false);

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
    ElMessage.warning('请先输入单号');
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

const goOrders = () => {
  router.push('/orders');
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
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.sub {
  margin: 0;
  color: #909399;
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
</style>
