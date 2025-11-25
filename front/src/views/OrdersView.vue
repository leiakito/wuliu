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
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
            <el-input
            v-model="filters.keyword"
            placeholder="单号/SN/型号"
            @input="handleKeywordInput"
          />
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
          <small class="muted">输入单号或 SN 即可查询是否结账/录入</small>
        </div>
      </template>
      <el-input
        v-model="userSearchInput"
        type="textarea"
        :rows="4"
        placeholder="支持多个单号或 SN，使用换行/逗号/分号分隔"
        @input="handleUserSearchInput"
      />
      <div class="user-search-actions">
        <el-button type="primary" :loading="userSearchLoading" @click="handleUserSearch">查询状态</el-button>
        <el-button text :disabled="!userOrders.length" @click="clearUserResults">清空记录</el-button>
        <el-button text :disabled="!userOrders.length" @click="exportUserOrders">导出 Excel</el-button>
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
        <em class="muted">已移除分类筛选</em>
      </div>
    </div>

    <el-card v-if="isAdmin && diffNotices.length" class="diff-card">
      <template #header>
        <div class="settle-bar">
          <div>
            <span>变更提醒</span>
            <small class="muted">导入/新增/编辑后与此前记录不一致的条目</small>
          </div>
          <el-button type="text" size="small" @click="exportDiffNotices" :disabled="!diffNotices.length">导出</el-button>
        </div>
      </template>
      <ul class="diff-list">
        <li v-for="item in diffNotices" :key="item.trackingNumber">
          <div class="diff-row">
            <div>
              <strong>{{ item.trackingNumber }}</strong>：{{ item.message }}
              <div class="diff-details">
                <span v-for="(label, idx) in diffFields(item)" :key="idx">
                  <em>{{ label }}</em>
                  <span class="diff-before">旧: {{ formatDiffValue(item.before, label) }}</span>
                  <span class="diff-after">新: {{ formatDiffValue(item.after, label) }}</span>
                </span>
              </div>
            </div>
            <el-button type="text" size="small" @click="removeDiffNotice(item.trackingNumber)">清除</el-button>
          </div>
        </li>
      </ul>
    </el-card>

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
            <span class="sn-text">{{ row.sn }}</span>
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
        :page-sizes="[20, 50, 100, 200]"
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

    <el-dialog
      v-model="importProgress.visible"
      title="批量导入中"
      width="360px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      align-center
    >
      <p class="muted" style="margin-bottom: 12px">正在上传并解析文件，请稍候…</p>
      <el-progress :percentage="importProgress.percent" :stroke-width="12" status="success" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onBeforeUnmount, onMounted } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { fetchOrders, fetchOrdersWithConfig, createOrder, importOrders, updateOrderStatus, searchOrders, fetchCategoryStats } from '@/api/orders';
import type { OrderCategoryStats, OrderCreateRequest, OrderRecord, OrderUpdateRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

interface FilterModel {
  dateRange: string[];
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

const PAGE_SIZE_KEY = 'orders-page-size';
const savedPageSize = Number(localStorage.getItem(PAGE_SIZE_KEY)) || 50;

const filters = reactive<FilterModel>({
  dateRange: [],
  status: '',
  keyword: '',
  page: 1,
  size: Number.isNaN(savedPageSize) ? 50 : savedPageSize
});

const EXCEL_PREFIX_PATTERN = /^[='‘’“”"`\u200B-\u200F\uFEFF]+/;
const EXCEL_PREFIX_MULTILINE_PATTERN = /^[='‘’“”"`\u200B-\u200F\uFEFF]+/gm;

const sanitizeSingleInput = (value?: string) => {
  if (!value) return '';
  return value.replace(EXCEL_PREFIX_PATTERN, '').trim();
};

const sanitizeMultilineInput = (value?: string) => {
  if (!value) return '';
  return value.replace(EXCEL_PREFIX_MULTILINE_PATTERN, '');
};

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');
const orders = ref<OrderRecord[]>([]);
const userOrders = ref<OrderRecord[]>([]);
const total = ref(0);
const loading = ref(false);
type DiffNotice = {
  trackingNumber: string;
  message: string;
  before?: Partial<OrderRecord>;
  after?: Partial<OrderRecord>;
};
const diffNotices = ref<DiffNotice[]>([]);
const DIFF_NOTICE_KEY = 'orders-diff-notices';
const destroyed = ref(false);
const userSearchInput = ref('');
const userSearchLoading = ref(false);
const userSearchDebounce = ref<number | null>(null);
const adminSearchDebounce = ref<number | null>(null);
const tableData = computed(() => (isAdmin.value ? orders.value : userOrders.value));
const tableLoading = computed(() => (isAdmin.value ? loading.value : userSearchLoading.value));
const USER_HISTORY_KEY = 'user-order-history';
const quickStatus = ref('');
const filteredTableData = computed(() =>
  tableData.value.filter(order => {
    const statusMatch = !quickStatus.value || order.status === quickStatus.value;
    return statusMatch;
  })
);

const loadPersistedDiffNotices = (): DiffNotice[] => {
  try {
    const raw = localStorage.getItem(DIFF_NOTICE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (error) {
    console.warn('Failed to load diff notices', error);
    return [];
  }
};

const persistDiffNotices = (list: DiffNotice[]) => {
  try {
    localStorage.setItem(DIFF_NOTICE_KEY, JSON.stringify(list.slice(0, 100)));
  } catch (error) {
    console.warn('Failed to persist diff notices', error);
  }
};

const mergeDiffNotices = (notices: DiffNotice[]) => {
  if (!notices.length) return;
  const merged = [...loadPersistedDiffNotices(), ...notices];
  persistDiffNotices(merged);
  if (!destroyed.value) {
    diffNotices.value = merged;
  }
};

onMounted(() => {
  destroyed.value = false;
  const stored = loadPersistedDiffNotices();
  if (stored.length) {
    diffNotices.value = stored;
  }
});

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
const importProgress = reactive({
  visible: false,
  percent: 0,
  timer: null as number | null
});

const editDialog = reactive({
  visible: false,
  loading: false,
  targetId: 0,
  form: { trackingNumber: '', model: '', sn: '', status: '', remark: '' } as OrderUpdateRequest
});

const statusLabel = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return match ? match.label : '未知状态';
};

const statusTagType = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return (match?.tag as string) ?? 'info';
};

const setStatusFilter = async (value: string) => {
  quickStatus.value = quickStatus.value === value ? '' : value;
  if (isAdmin.value) {
    filters.status = quickStatus.value;
    filters.page = 1;
    await loadOrders();
  }
};

const queryParams = computed(() => {
  const params: any = {
    page: filters.page,
    size: filters.size,
    keyword: filters.keyword || undefined,
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
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  filters.page = 1;
  loadOrders();
};

const triggerAdminAutoSearch = () => {
  if (!isAdmin.value) return;
  if (adminSearchDebounce.value) {
    clearTimeout(adminSearchDebounce.value);
  }
  adminSearchDebounce.value = window.setTimeout(() => {
    filters.page = 1;
    loadOrders();
  }, 300);
};

const handleKeywordInput = (value: string) => {
  filters.keyword = sanitizeSingleInput(value);
  triggerAdminAutoSearch();
};

const handleUserSearchInput = (value: string) => {
  userSearchInput.value = sanitizeMultilineInput(value);
};

const handleSizeChange = (size: number) => {
  filters.size = size;
  localStorage.setItem(PAGE_SIZE_KEY, String(size));
  filters.page = 1;
  loadOrders();
};

const handlePageChange = (page: number) => {
  filters.page = page;
  loadOrders();
};

const resetFilters = () => {
  filters.dateRange = [];
  filters.status = '';
  filters.keyword = '';
  filters.page = 1;
  quickStatus.value = '';
  loadOrders();
};

const triggerImport = () => {
  if (!isAdmin.value) return;
  fileInput.value?.click();
};

const fetchAllOrders = async () => {
  const pageSize = 500;
  let page = 1;
  const all: OrderRecord[] = [];
  while (true) {
    const data = await fetchOrdersWithConfig({ page, size: pageSize }, { timeout: 60000 });
    all.push(...data.records);
    if (data.records.length < pageSize) break;
    page += 1;
  }
  return all;
};

const captureDiffSnapshot = async () => {
  const all = await fetchAllOrders();
  return buildOrderSnapshot(all);
};

const startImportProgress = () => {
  importProgress.visible = true;
  importProgress.percent = 10;
  if (importProgress.timer) {
    clearInterval(importProgress.timer);
  }
  importProgress.timer = window.setInterval(() => {
    if (importProgress.percent < 90) {
      importProgress.percent += 10;
    }
  }, 300);
};

const finishImportProgress = () => {
  if (importProgress.timer) {
    clearInterval(importProgress.timer);
    importProgress.timer = null;
  }
  importProgress.percent = 100;
  setTimeout(() => {
    importProgress.visible = false;
    importProgress.percent = 0;
  }, 400);
};

const handleFileChange = async (event: Event) => {
  if (!isAdmin.value) return;
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  startImportProgress();
  const prevSnapshot = await captureDiffSnapshot().catch(() => new Map());
  try {
    const report = await importOrders(file);
    finishImportProgress();
    ElMessage.success('导入成功');
    const latest = await fetchAllOrders().catch(() => []);
    scheduleDiffCalculation(prevSnapshot, latest);
    loadOrders();
  } catch (error) {
    finishImportProgress();
    throw error;
  } finally {
    target.value = '';
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
    const prevSnapshot = await captureDiffSnapshot().catch(() => new Map());
    const latest = await fetchAllOrders().catch(() => []);
    scheduleDiffCalculation(prevSnapshot, latest);
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

const buildOrderSnapshot = (list: OrderRecord[]) => {
  const map = new Map<string, Partial<OrderRecord>>();
  list.forEach(item => {
    const key = buildOrderKey(item);
    if (!key) return;
    map.set(key, {
      trackingNumber: item.trackingNumber,
      model: item.model,
      sn: item.sn,
      amount: item.amount
    });
  });
  return map;
};

const computeDifferences = (prevMap: Map<string, Partial<OrderRecord>>, nextList: OrderRecord[]) => {
  if (!prevMap.size) return [];
  const fieldLabels: Record<string, string> = {
    trackingNumber: '运单号',
    model: '型号',
    sn: 'SN'
  };
  const notices: { trackingNumber: string; message: string; before?: Partial<OrderRecord>; after?: Partial<OrderRecord> }[] = [];
  nextList.forEach(order => {
    const key = buildOrderKey(order);
    const prev = prevMap.get(key);
    if (!prev) {
      return;
    }
    const changed: string[] = [];
    const normalizeVal = (val: unknown) => {
      if (val === null || val === undefined) return '';
      if (typeof val === 'string') return val.trim();
      return String(val);
    };
    const before: Partial<OrderRecord> = {};
    const after: Partial<OrderRecord> = {};
    Object.keys(fieldLabels).forEach(field => {
      const prevVal = (prev as any)[field];
      const currVal = (order as any)[field];
      if (normalizeVal(prevVal) !== normalizeVal(currVal)) {
        changed.push(fieldLabels[field]);
        (before as any)[field] = prevVal;
        (after as any)[field] = currVal;
      }
    });
    if (changed.length) {
      notices.push({
        trackingNumber: order.trackingNumber ?? key,
        message: `字段变更：${changed.join('、')}`,
        before,
        after
      });
    }
  });
  // 同一运单号只保留一条提醒
  const dedup: Record<string, typeof notices[number]> = {};
  notices.forEach(item => {
    const k = (item.trackingNumber ?? '').toUpperCase();
    if (!dedup[k]) {
      dedup[k] = item;
    }
  });
  return Object.values(dedup).slice(0, 20); // 避免一次性展示过多
};

const removeDiffNotice = (trackingNumber: string) => {
  diffNotices.value = diffNotices.value.filter(item => item.trackingNumber !== trackingNumber);
  persistDiffNotices(diffNotices.value);
};

const buildOrderKey = (order: OrderRecord) => {
  if (order.id) return `ID-${order.id}`;
  if (!order.trackingNumber) return '';
  return order.trackingNumber.trim().toUpperCase();
};

const diffFields = (item: DiffNotice) => {
  const fields: { key: keyof OrderRecord; label: string }[] = [
    { key: 'trackingNumber', label: '运单号' },
    { key: 'model', label: '型号' },
    { key: 'sn', label: 'SN' }
  ];
  return fields
    .filter(({ key }) => {
      const beforeVal = (item.before as any)?.[key];
      const afterVal = (item.after as any)?.[key];
      return String(beforeVal ?? '') !== String(afterVal ?? '');
    })
    .map(f => f.label);
};

const formatDiffValue = (obj: Partial<OrderRecord> | undefined, label: string) => {
  if (!obj) return '-';
  const map: Record<string, keyof OrderRecord> = {
    '运单号': 'trackingNumber',
    '型号': 'model',
    'SN': 'sn'
  };
  const key = map[label];
  const val = key ? (obj as any)[key] : undefined;
  return val === undefined || val === null || val === '' ? '-' : val;
};

const scheduleDiffCalculation = (prevSnapshot: Map<string, Partial<OrderRecord>>, latest: OrderRecord[]) => {
  // 轻量异步排队，避免阻塞后续操作或导航
  setTimeout(() => {
    const diffs = computeDifferences(prevSnapshot, latest);
    mergeDiffNotices(diffs);
  }, 0);
};

const exportDiffNotices = () => {
  if (!diffNotices.value.length) {
    ElMessage.info('暂无可导出的变更提醒');
    return;
  }
  const headers = ['运单号', '变更字段', '旧值', '新值'];
  const rows: string[][] = [];
  diffNotices.value.forEach(item => {
    const fields = diffFields(item);
    if (!fields.length) return;
    fields.forEach(label => {
      rows.push([
        item.trackingNumber,
        label,
        formatDiffValue(item.before, label),
        formatDiffValue(item.after, label)
      ]);
    });
  });
  if (!rows.length) {
    ElMessage.info('暂无可导出的变更提醒');
    return;
  }
  const csv = [headers, ...rows]
    .map(cols => cols.map(col => `"${String(col ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `order-diff-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  window.URL.revokeObjectURL(url);
};

const submitEdit = async () => {
  if (!editDialog.targetId) return;
  editDialog.loading = true;
  const prevSnapshot = await captureDiffSnapshot().catch(() => new Map());
  try {
    const payload: OrderUpdateRequest = {
      trackingNumber: editDialog.form.trackingNumber,
      model: editDialog.form.model,
      sn: editDialog.form.sn,
      status: editDialog.form.status,
      remark: editDialog.form.remark
    };
    await updateOrder(editDialog.targetId, payload);
    const latest = await fetchAllOrders();
    scheduleDiffCalculation(prevSnapshot, latest);
    editDialog.visible = false;
    ElMessage.success('已更新');
    await loadCategoryStats();
    loadOrders();
  } finally {
    editDialog.loading = false;
  }
};

const getRecordKey = (record: OrderRecord) => record.sn || record.trackingNumber || '';

const handleUserSearch = async (silent = false) => {
  const list = userSearchInput.value
    .split(/\n|,|;/)
    .map(item => sanitizeSingleInput(item))
    .filter(Boolean);
  if (!list.length) {
    if (!silent) {
      ElMessage.warning('请先输入单号或 SN');
    } else {
      userOrders.value = [];
    }
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
      const key = getRecordKey(record);
      if (key) {
        map.set(key, record);
      }
    });
    results.forEach(record => {
      const key = getRecordKey(record);
      if (key) {
        map.set(key, record);
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
  const headers = ['下单日期', '运单号', '型号', 'SN', '分类', '状态', '备注', '创建人'];
  const csvRows = [headers.join(',')];
  userOrders.value.forEach(order => {
    csvRows.push([
      order.orderDate ?? '',
      order.trackingNumber ?? '',
      order.model ?? '',
      order.sn ?? '',
      order.category ?? '',
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

watch(userSearchInput, value => {
  if (userSearchDebounce.value) {
    clearTimeout(userSearchDebounce.value);
  }
  if (!value || !value.trim()) {
    userOrders.value = [];
    return;
  }
  userSearchDebounce.value = window.setTimeout(() => {
    handleUserSearch(true);
  }, 400);
});

watch(() => filters.status, triggerAdminAutoSearch);
watch(() => filters.keyword, triggerAdminAutoSearch);
watch(() => filters.dateRange, triggerAdminAutoSearch, { deep: true });

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

onBeforeUnmount(() => {
  destroyed.value = true;
  if (importProgress.timer) {
    clearInterval(importProgress.timer);
  }
});
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

.diff-card {
  margin-top: 16px;
}

.diff-list {
  margin: 0;
  padding-left: 16px;
  color: var(--text-muted);
}

.diff-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

</style>
