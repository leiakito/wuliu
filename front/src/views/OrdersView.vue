<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>物流单号</h2>
        <p class="sub">管理员可录入与维护，普通用户仅可查询并跟踪状态</p>
      </div>
      <div v-if="isAdmin" class="actions">
        <input ref="fileInput" type="file" accept=".xls,.xlsx" hidden @change="handleFileChange" />
        <el-button @click="triggerImport">批量导入</el-button>
        <el-button type="primary" @click="openCreateDrawer">新增单号</el-button>
      </div>
    </div>

    <el-card v-if="isAdmin">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="日期">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="filters.category" placeholder="如 手机" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="单号/型号/客户" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-else class="user-search-card">
      <template #header>
        <div class="settle-bar">
          <span>订单状态查询</span>
          <small class="muted">输入单号即可查询是否结账/录入</small>
        </div>
      </template>
      <el-input
        v-model="userSearchInput"
        type="textarea"
        :rows="4"
        placeholder="支持多个单号，使用换行/逗号/分号分隔"
      />
      <div class="user-search-actions">
        <el-button type="primary" :loading="userSearchLoading" @click="handleUserSearch">查询状态</el-button>
        <el-button text :disabled="!userOrders.length" @click="clearUserResults">清空记录</el-button>
        <el-button text :disabled="!userOrders.length" @click="exportUserOrders">导出 Excel</el-button>
      </div>
    </el-card>

    <el-card v-if="isAdmin" class="batch-card">
      <template #header>
        <div class="settle-bar">
          <span>批量抓取</span>
          <el-button type="success" :loading="batchLoading" @click="handleBatchFetch">抓取并保存</el-button>
        </div>
      </template>
      <div class="batch-grid">
        <el-input
          v-model="batchNumbers"
          type="textarea"
          :rows="4"
          placeholder="每行一个单号"
        />
        <el-input-number
          v-model="manualAmount"
          :min="0"
          :step="10"
          controls-position="right"
          label="统一金额 (可选)"
          placeholder="统一金额 (可选)"
        />
      </div>
    </el-card>

    <div class="quick-tools">
      <div class="quick-filter-row">
        <span class="label">状态：</span>
        <el-check-tag :checked="quickStatus === ''" @click="setStatusFilter('')">全部</el-check-tag>
        <el-check-tag
          v-for="item in statusOptions"
          :key="item.value"
          :checked="quickStatus === item.value"
          @click="setStatusFilter(item.value)"
        >
          {{ item.label }}
        </el-check-tag>
      </div>
      <div class="quick-filter-row">
        <span class="label">物流公司：</span>
        <el-check-tag :checked="quickCategory === ''" @click="setCategoryFilter('')">全部</el-check-tag>
        <el-check-tag
          v-for="category in categoryChips"
          :key="category.name"
          :checked="quickCategory === category.name"
          @click="setCategoryFilter(category.name)"
        >
          {{ category.name }}（{{ category.count }}）
        </el-check-tag>
      </div>
    </div>

    <el-card class="table-card">
      <el-table :data="filteredTableData" v-loading="tableLoading" style="width: 100%">
        <el-table-column prop="orderDate" label="下单日期" width="120" />
        <el-table-column prop="orderTime" label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="运单号" width="160" />
        <el-table-column prop="model" label="型号" />
        <el-table-column prop="sn" label="SN" width="180">
          <template #default="{ row }">
            <span :class="['sn-text', { 'sn-duplicate': isSnDuplicate(row.sn) }]">{{ row.sn }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="amount" label="金额" width="160">
          <template #default="{ row }">
            <template v-if="isAdmin">
              <div class="amount-cell">
                <span class="currency-label">￥</span>
                <el-input-number
                  v-model="row.amount"
                  :min="0"
                  :step="10"
                  size="small"
                  controls-position="right"
                  @change="(value, oldValue) => changeAmount(row, value as number, oldValue as number)"
                />
              </div>
            </template>
            <template v-else>
              {{ formatAmountValue(row.amount) }}
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="160">
          <template #default="{ row }">
            <template v-if="isAdmin">
              <el-select
                :model-value="row.status"
                size="small"
                placeholder="状态"
                @change="status => changeStatus(row, status as string)"
              >
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>
            <template v-else>
              <el-tag :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column v-if="isAdmin" label="导入状态" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.imported" type="success">已录入系统</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="创建人" prop="createdBy" width="120" />
        <el-table-column v-if="isAdmin" label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="isAdmin"
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        background
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-drawer v-if="isAdmin" v-model="createVisible" title="新增物流单" size="30%" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="日期" prop="orderDate">
          <el-date-picker v-model="createForm.orderDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="单号" prop="trackingNumber">
          <el-input v-model="createForm.trackingNumber" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="createForm.model" />
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="createForm.sn" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-if="isAdmin" v-model="editDialog.visible" title="编辑物流单号" width="520px">
      <el-form label-width="90px">
        <el-form-item label="运单号">
          <el-input v-model="editDialog.form.trackingNumber" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="editDialog.form.model" />
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="editDialog.form.sn" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="editDialog.form.amount" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editDialog.form.status" placeholder="请选择">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.loading" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { fetchOrders, createOrder, importOrders, fetchByTracking, updateOrderStatus, searchOrders, updateOrderAmount, fetchCategoryStats } from '@/api/orders';
import type { OrderCategoryStats, OrderCreateRequest, OrderRecord, OrderUpdateRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

interface FilterModel {
  dateRange: string[];
  category: string;
  status: string;
  keyword: string;
  page: number;
  size: number;
}

const statusOptions = [
  { label: '未打款', value: 'UNPAID', tag: 'warning' },
  { label: '未收货', value: 'NOT_RECEIVED', tag: 'info' },
  { label: '已打款', value: 'PAID', tag: 'success' }
] as const;

const filters = reactive<FilterModel>({
  dateRange: [],
  category: '',
  status: '',
  keyword: '',
  page: 1,
  size: 20
});

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');
const orders = ref<OrderRecord[]>([]);
const userOrders = ref<OrderRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const userSearchInput = ref('');
const userSearchLoading = ref(false);
const tableData = computed(() => (isAdmin.value ? orders.value : userOrders.value));
const tableLoading = computed(() => (isAdmin.value ? loading.value : userSearchLoading.value));
const USER_HISTORY_KEY = 'user-order-history';
const quickStatus = ref('');
const quickCategory = ref('');
const categoryStats = ref<OrderCategoryStats[]>([]);
const duplicateSnSet = computed(() => {
  const counts = new Map<string, number>();
  tableData.value.forEach(order => {
    if (order.sn) {
      counts.set(order.sn, (counts.get(order.sn) ?? 0) + 1);
    }
  });
  return new Set(Array.from(counts.entries()).filter(([, count]) => count > 1).map(([sn]) => sn));
});
const filteredTableData = computed(() =>
  tableData.value.filter(order => {
    const statusMatch = !quickStatus.value || order.status === quickStatus.value;
    const categoryName = order.category && order.category.trim().length > 0 ? order.category : '未分配';
    const categoryMatch = !quickCategory.value || categoryName === quickCategory.value;
    return statusMatch && categoryMatch;
  })
);
const categoryChips = computed(() =>
  categoryStats.value.map(item => ({
    name: item.category || '未分配',
    count: item.count
  }))
);

const isSnDuplicate = (sn?: string) => {
  if (!sn) return false;
  return duplicateSnSet.value.has(sn);
};

const batchNumbers = ref('');
const manualAmount = ref<number | null>(null);
const batchLoading = ref(false);

const createVisible = ref(false);
const createLoading = ref(false);
const createFormRef = ref<FormInstance>();
const createForm = reactive<OrderCreateRequest>({
  orderDate: '',
  trackingNumber: '',
  model: '',
  sn: '',
  remark: '',
  currency: 'CNY',
  orderTime: undefined
});

const createRules: FormRules<OrderCreateRequest> = {
  trackingNumber: [{ required: true, message: '请输入单号', trigger: 'blur' }],
  sn: [{ required: true, message: '请输入 SN', trigger: 'blur' }]
};

const fileInput = ref<HTMLInputElement>();

const editDialog = reactive({
  visible: false,
  loading: false,
  targetId: 0,
  form: { trackingNumber: '', model: '', sn: '', amount: 0, status: '', remark: '' } as OrderUpdateRequest & { amount?: number }
});

const statusLabel = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return match ? match.label : '未知状态';
};

const statusTagType = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return (match?.tag as string) ?? 'info';
};
const formatAmountValue = (value?: number) => {
  if (value === undefined || value === null) {
    return '-';
  }
  return `￥${value.toFixed(2)}`;
};

const setStatusFilter = async (value: string) => {
  quickStatus.value = quickStatus.value === value ? '' : value;
  if (isAdmin.value) {
    filters.status = quickStatus.value;
    filters.page = 1;
    await loadOrders();
  }
};

const setCategoryFilter = async (value: string) => {
  quickCategory.value = quickCategory.value === value ? '' : value;
  if (isAdmin.value) {
    filters.category = quickCategory.value;
    filters.page = 1;
    await loadOrders();
  }
};

const queryParams = computed(() => {
  const params: any = {
    page: filters.page,
    size: filters.size,
    keyword: filters.keyword || undefined,
    category: filters.category || undefined,
    status: filters.status || undefined
  };
  if (filters.dateRange.length === 2) {
    params.startDate = filters.dateRange[0];
    params.endDate = filters.dateRange[1];
  }
  return params;
});

const buildFilterPayload = () => {
  const params: any = {
    keyword: filters.keyword || undefined,
    category: filters.category || undefined,
    status: filters.status || undefined
  };
  if (filters.dateRange.length === 2) {
    params.startDate = filters.dateRange[0];
    params.endDate = filters.dateRange[1];
  }
  return params;
};

const loadOrders = async () => {
  if (!isAdmin.value) {
    return;
  }
  loading.value = true;
  try {
    const data = await fetchOrders(queryParams.value);
    orders.value = data.records;
    total.value = data.total;
    await loadCategoryStats();
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  filters.page = 1;
  loadOrders();
};

const handleSizeChange = (size: number) => {
  filters.size = size;
  filters.page = 1;
  loadOrders();
};

const handlePageChange = (page: number) => {
  filters.page = page;
  loadOrders();
};

const resetFilters = () => {
  filters.dateRange = [];
  filters.category = '';
  filters.status = '';
  filters.keyword = '';
  filters.page = 1;
  quickStatus.value = '';
  quickCategory.value = '';
  loadOrders();
};

const triggerImport = () => {
  if (!isAdmin.value) return;
  fileInput.value?.click();
};

const handleFileChange = async (event: Event) => {
  if (!isAdmin.value) return;
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  try {
    await importOrders(file);
    ElMessage.success('导入成功');
    loadOrders();
  } finally {
    target.value = '';
  }
};

const handleBatchFetch = async () => {
  if (!isAdmin.value) return;
  const list = batchNumbers.value
    .split(/\n|,|;/)
    .map(item => item.trim())
    .filter(Boolean);
  if (list.length === 0) {
    ElMessage.warning('请先输入单号');
    return;
  }
  batchLoading.value = true;
  try {
    await fetchByTracking({ trackingNumbers: list, manualAmount: manualAmount.value ?? undefined });
    ElMessage.success('批量处理完成');
    batchNumbers.value = '';
    manualAmount.value = null;
    loadOrders();
  } finally {
    batchLoading.value = false;
  }
};

const openCreateDrawer = () => {
  if (!isAdmin.value) return;
  createVisible.value = true;
};

const openEditDialog = (row: OrderRecord) => {
  if (!isAdmin.value) return;
  editDialog.targetId = row.id;
  editDialog.form.trackingNumber = row.trackingNumber;
  editDialog.form.model = row.model ?? '';
  editDialog.form.sn = row.sn ?? '';
  editDialog.form.amount = row.amount ?? 0;
  editDialog.form.status = row.status ?? '';
  editDialog.form.remark = row.remark ?? '';
  editDialog.visible = true;
};

const submitCreate = async () => {
  if (!isAdmin.value) return;
  if (!createFormRef.value) return;
  const valid = await createFormRef.value.validate().catch(() => false);
  if (!valid) return;
  createLoading.value = true;
  try {
    const payload = Object.entries(createForm).reduce((acc: Record<string, any>, [key, value]) => {
      if (value !== '' && value !== undefined && value !== null) {
        acc[key] = value;
      }
      return acc;
    }, {}) as OrderCreateRequest;
    await createOrder(payload);
    ElMessage.success('新增成功');
    createVisible.value = false;
    Object.assign(createForm, {
      orderDate: '',
      trackingNumber: '',
      model: '',
      sn: '',
      remark: '',
      amount: undefined,
      currency: 'CNY',
      orderTime: undefined
    });
    loadOrders();
  } finally {
    createLoading.value = false;
  }
};

const changeStatus = async (row: OrderRecord, status: string) => {
  if (!isAdmin.value) return;
  try {
    await updateOrderStatus(row.id, status);
    row.status = status;
    ElMessage.success('状态已更新');
  } catch (error) {
    console.error(error);
  }
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ').replace('Z', '');
};

const changeAmount = async (row: OrderRecord, value: number, previous?: number) => {
  if (!isAdmin.value || Number.isNaN(value)) return;
  const oldValue = previous ?? row.amount ?? 0;
  try {
    await updateOrderAmount(row.id, { amount: value });
    row.amount = value;
    row.currency = 'CNY';
    ElMessage.success('金额已更新');
  } catch (error) {
    row.amount = oldValue;
    console.error(error);
  }
};

const submitEdit = async () => {
  if (!editDialog.targetId) return;
  editDialog.loading = true;
  try {
    const payload: OrderUpdateRequest = {
      trackingNumber: editDialog.form.trackingNumber,
      model: editDialog.form.model,
      sn: editDialog.form.sn,
      amount: editDialog.form.amount,
      status: editDialog.form.status,
      remark: editDialog.form.remark
    };
    const updated = await updateOrder(editDialog.targetId, payload);
    const target = orders.value.find(order => order.id === editDialog.targetId);
    if (target) {
      Object.assign(target, updated);
    }
    editDialog.visible = false;
    ElMessage.success('已更新');
    await loadCategoryStats();
  } finally {
    editDialog.loading = false;
  }
};

const loadCategoryStats = async () => {
  if (isAdmin.value) {
    try {
      const params = buildFilterPayload();
      categoryStats.value = await fetchCategoryStats(params);
    } catch (error) {
      console.error('Failed to load category stats', error);
    }
  } else {
    const stats = new Map<string, number>();
    tableData.value.forEach(order => {
      const key = order.category && order.category.trim().length > 0 ? order.category : '未分配';
      stats.set(key, (stats.get(key) ?? 0) + 1);
    });
    categoryStats.value = Array.from(stats.entries()).map(([category, count]) => ({ category, count }));
  }
  if (quickCategory.value && !categoryStats.value.some(stat => (stat.category || '未分配') === quickCategory.value)) {
    quickCategory.value = '';
  }
};

const handleUserSearch = async () => {
  const list = userSearchInput.value
    .split(/\n|,|;/)
    .map(item => item.trim())
    .filter(Boolean);
  if (!list.length) {
    ElMessage.warning('请先输入单号');
    return;
  }
  userSearchLoading.value = true;
  try {
    const results = await searchOrders(list);
    if (!results.length) {
      ElMessage.warning('未查询到对应订单');
      return;
    }
    const map = new Map<string, OrderRecord>();
    userOrders.value.forEach(record => {
      if (record.trackingNumber) {
        map.set(record.trackingNumber, record);
      }
    });
    results.forEach(record => {
      if (record.trackingNumber) {
        map.set(record.trackingNumber, record);
      }
    });
    userOrders.value = Array.from(map.values());
    saveUserOrders();
    await loadCategoryStats();
  } finally {
    userSearchLoading.value = false;
  }
};

const clearUserResults = () => {
  userOrders.value = [];
  localStorage.removeItem(USER_HISTORY_KEY);
  loadCategoryStats();
};

const exportUserOrders = () => {
  if (!userOrders.value.length) return;
  const headers = ['下单日期', '运单号', '型号', 'SN', '分类', '金额', '币种', '状态', '备注', '创建人'];
  const csvRows = [headers.join(',')];
  userOrders.value.forEach(order => {
    csvRows.push([
      order.orderDate ?? '',
      order.trackingNumber ?? '',
      order.model ?? '',
      order.sn ?? '',
      order.category ?? '',
      order.amount ?? '',
      order.currency ?? '',
      statusLabel(order.status),
      order.remark ?? '',
      order.createdBy ?? ''
    ].map(value => `"${String(value).replace(/"/g, '""')}"`).join(','));
  });
  const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `orders-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  window.URL.revokeObjectURL(url);
};

watch(isAdmin, value => {
  if (value) {
    loadOrders();
  } else {
    loadUserOrders();
  }
}, { immediate: true });

function loadUserOrders() {
  try {
    const cached = localStorage.getItem(USER_HISTORY_KEY);
    if (cached) {
      userOrders.value = JSON.parse(cached);
    }
  } catch (error) {
    console.warn('Failed to load cached user orders', error);
  }
  loadCategoryStats();
}

function saveUserOrders() {
  try {
    localStorage.setItem(USER_HISTORY_KEY, JSON.stringify(userOrders.value));
  } catch (error) {
    console.warn('Failed to persist user orders', error);
  }
}
</script>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.settle-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-search-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.muted {
  color: var(--text-muted);
  font-size: 12px;
}

.sn-text {
  display: inline-block;
}

.sn-duplicate {
  color: #f56c6c;
  font-weight: 600;
}

.quick-tools {
  margin: 16px 0;
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.quick-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.quick-filter-row:last-child {
  margin-bottom: 0;
}

.quick-filter-row .label {
  font-weight: 600;
  color: var(--text-muted);
}

.quick-filter-row .el-check-tag {
  cursor: pointer;
}

.amount-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.currency-label {
  font-size: 14px;
  color: #303133;
}
</style>
