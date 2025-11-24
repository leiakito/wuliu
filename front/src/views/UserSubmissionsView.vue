<template>
  <div class="page submissions-page">
    <div class="page-header">
      <div>
        <h2>单号提交</h2>
        <p class="sub">提交后即可在下方列表中查看处理进度</p>
      </div>
      <div class="header-actions">
        <el-tag type="info">{{ isAdmin ? '管理员视图' : '个人视图' }}</el-tag>
        <el-button v-if="isAdmin" type="primary" plain @click="goSubmissionLogs">提交记录</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="form-row">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>提交单号</span>
              <small>系统会自动匹配订单信息</small>
            </div>
          </template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="单号" prop="trackingNumber">
              <el-input v-model.trim="form.trackingNumber" placeholder="请输入或粘贴物流单号" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交单号</el-button>
              <el-button @click="resetForm">清空</el-button>
              <el-button text @click="openBatchDialog">批量提交</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="tips-card">
          <template #header>
            <div class="card-header">
              <span>操作说明</span>
            </div>
          </template>
          <el-form v-if="isAdmin" label-width="90px" class="inline-user-form">
            <el-form-item label="归属用户">
              <div class="user-select-row">
                <el-select
                  v-model="selectedOwner"
                  filterable
                  clearable
                  placeholder="不选则归属当前管理员"
                  :loading="userLoading"
                  style="flex: 1"
                >
                  <el-option
                    v-for="user in userOptions"
                    :key="user.username"
                    :label="user.fullName ? `${user.fullName}（${user.username}）` : user.username"
                    :value="user.username"
                  />
                </el-select>
                <el-button
                  type="primary"
                  link
                  :loading="creatingUser"
                  style="margin-left: 8px"
                  @click="quickCreateUser"
                >
                  新建
                </el-button>
              </div>
              <el-input
                v-model.trim="createUserForm.username"
                placeholder="用户名（必填）"
                size="small"
                class="user-create-input"
              />
            </el-form-item>
          </el-form>
          <ul>
            <li>状态默认为「待处理」，运营/财务确认后会更新状态</li>
            <li>系统自动关联订单金额、型号、物流公司等信息</li>
            <li v-if="isAdmin">管理员可使用下方筛选器按用户名或状态查询所有提交</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            placeholder="全部"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="单号">
          <el-input v-model="filters.trackingNumber" placeholder="支持模糊搜索" clearable />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="用户名">
          <el-select
            v-model="filters.username"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
            :loading="userLoading"
            @change="handleSearch"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.username"
              :label="user.fullName ? `${user.fullName}（${user.username}）` : user.username"
              :value="user.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button text :loading="exportLoading" :disabled="!submissions.length" @click="exportSubmissions">导出 Excel</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="submissions" v-loading="listLoading" style="width: 100%">
        <el-table-column label="下单日期" width="140">
          <template #default="{ row }">{{ row.order?.orderDate ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="订单时间" width="180">
          <template #default="{ row }">{{ formatOrderTime(row.order?.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="单号" min-width="160" />
        <el-table-column label="型号" min-width="160">
          <template #default="{ row }">{{ row.order?.model ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="物流公司" width="120">
          <template #default="{ row }">{{ row.order?.category ?? '-' }}</template>
        </el-table-column>
        <el-table-column v-if="isAdmin" label="归属用户" width="140">
          <template #default="{ row }">
            {{ row.ownerUsername || row.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="140">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.order?.status)">
              {{ orderStatusLabel(row.order?.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="提交状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        class="table-pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-card v-if="isAdmin && invalidTrackings.length" class="invalid-card">
      <template #header>
        <div class="card-header">
          <span>未在物流单号中的单号</span>
          <div>
            <el-button text size="small" @click="exportInvalidTrackings">导出</el-button>
            <el-button text size="small" @click="clearInvalidTrackings">清空</el-button>
          </div>
        </div>
      </template>
      <ul class="invalid-list">
        <li v-for="item in invalidTrackings" :key="item">{{ item }}</li>
      </ul>
    </el-card>

    <el-dialog v-model="batchDialog.visible" title="批量提交单号" width="520px">
      <div class="batch-tip">
          <p>支持一次粘贴多个单号，系统会自动提取每行中的第一个编号。</p>
          <p>批量提交请确保每个单号单独换行。</p>
          <p>示例：</p>
          <pre class="batch-example">JDX044863610899
343138058920</pre>
        </div>
      <el-input
        v-model="batchDialog.raw"
        type="textarea"
        :rows="8"
        placeholder="每行一个单号，可附带备注"
      />
      <div v-if="batchDialog.list.length" class="batch-preview">
        <span>已识别 {{ batchDialog.list.length }} 个单号：</span>
        <el-scrollbar max-height="120">
          <div class="batch-tags">
            <el-tag v-for="item in batchDialog.list" :key="item" class="tag-item">
              {{ item }}
            </el-tag>
          </div>
        </el-scrollbar>
      </div>
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchDialog.loading" @click="submitBatch">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import {
  fetchAllSubmissions,
  fetchMySubmissions,
  submitUserSubmission,
  submitUserSubmissionsBatch,
  type SubmissionQueryParams
} from '@/api/submissions';
import { searchOrders } from '@/api/orders';
import { listUsers, createUser } from '@/api/users';
import type { OrderRecord, SysUser, UserSubmission, UserSubmissionCreateRequest, UserSubmissionBatchRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
const router = useRouter();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

const statusOptions = [
  { label: '待处理', value: 'PENDING', type: 'warning' },
  { label: '处理中', value: 'PROCESSING', type: 'info' },
  { label: '已完成', value: 'COMPLETED', type: 'success' }
] as const;

const statusLabel = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return match ? match.label : '待处理';
};

const statusTag = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return (match?.type as string) ?? 'warning';
};

const orderStatusDict = [
  { value: 'PAID', label: '已打款', tag: 'success' },
  { value: 'UNPAID', label: '未打款', tag: 'warning' },
  { value: 'NOT_RECEIVED', label: '未收货', tag: 'info' }
] as const;

const orderStatusLabel = (value?: string) =>
  orderStatusDict.find(item => item.value === value)?.label ?? '未知';

const orderStatusTag = (value?: string) =>
  orderStatusDict.find(item => item.value === value)?.tag ?? 'info';

const formRef = ref<FormInstance>();
const form = reactive<UserSubmissionCreateRequest>({
  trackingNumber: ''
});

const rules: FormRules<UserSubmissionCreateRequest> = {
  trackingNumber: [{ required: true, message: '请输入单号', trigger: 'blur' }]
};

const submitLoading = ref(false);
const listLoading = ref(false);
const exportLoading = ref(false);
const submissions = ref<UserSubmission[]>([]);
const pagination = reactive({ page: 1, size: 10, total: 0 });
const filters = reactive({
  status: '',
  trackingNumber: '',
  username: ''
});
const userOptions = ref<SysUser[]>([]);
const userLoading = ref(false);
const selectedOwner = ref('');
const OWNER_STORAGE_KEY = 'submission-owner';
const INVALID_STORAGE_KEY = 'submission-invalid-trackings';
const invalidTrackings = ref<string[]>([]);

const batchDialog = reactive({
  visible: false,
  loading: false,
  raw: '',
  list: [] as string[]
});
const createUserForm = reactive({
  username: ''
});
const creatingUser = ref(false);

const buildQueryParams = (): SubmissionQueryParams => ({
  page: pagination.page,
  size: pagination.size,
  status: filters.status || undefined,
  trackingNumber: filters.trackingNumber ? filters.trackingNumber.trim() : undefined,
  username: isAdmin.value && filters.username ? filters.username.trim() : undefined
});

const loadUserOptions = async () => {
  if (!isAdmin.value) return;
  userLoading.value = true;
  try {
    userOptions.value = await listUsers();
  } finally {
    userLoading.value = false;
  }
};

const loadStoredOwner = () => {
  if (!isAdmin.value) return;
  const stored = localStorage.getItem(OWNER_STORAGE_KEY);
  if (stored) {
    selectedOwner.value = stored;
  }
};

const quickCreateUser = async () => {
  if (!isAdmin.value) return;
  const username = createUserForm.username.trim();
  if (!username) {
    ElMessage.warning('请输入用户名');
    return;
  }
  creatingUser.value = true;
  try {
    const payload = {
      username,
      role: 'USER',
      status: 'ENABLED'
    };
    const newUser = await createUser(payload);
    ElMessage.success('用户已创建');
    userOptions.value.push(newUser);
    selectedOwner.value = newUser.username;
    createUserForm.username = '';
  } catch (error) {
    console.error(error);
  } finally {
    creatingUser.value = false;
  }
};

const loadData = async () => {
  listLoading.value = true;
  try {
    const params = buildQueryParams();
    const response = isAdmin.value ? await fetchAllSubmissions(params) : await fetchMySubmissions(params);
    submissions.value = response.records;
    pagination.total = response.total;
  } finally {
    listLoading.value = false;
  }
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  const normalized = normalizeTrackingNumber(form.trackingNumber);
  if (!normalized) {
    return;
  }
  const missing = await checkMissingTrackings([normalized]);
  if (missing.length) {
    recordInvalidTrackings(missing);
    ElMessage.warning(`单号未在物流单号中：${missing.join('、')}`);
    return;
  }
  const existsLocally = submissions.value.some(
    item => item.trackingNumber?.trim().toUpperCase() === normalized.toUpperCase()
  );
  if (existsLocally) {
    ElMessage.warning('该单号已提交，请勿重复提交');
    return;
  }
  const payload: UserSubmissionCreateRequest = {
    trackingNumber: normalized,
    username: isAdmin.value ? (selectedOwner.value?.trim() || undefined) : undefined
  };
  submitLoading.value = true;
  try {
    await submitUserSubmission(payload);
    ElMessage.success('提交成功');
    resetForm();
    await loadData();
  } catch (error: any) {
    const message = error?.response?.data?.message ?? '提交失败，请稍后重试';
    if (typeof message === 'string' && message.includes('已提交')) {
      ElMessage.warning(message);
    } else {
      ElMessage.error(message);
    }
  } finally {
    submitLoading.value = false;
  }
};

const resetForm = () => {
  form.trackingNumber = '';
  formRef.value?.clearValidate();
};

const handleSearch = () => {
  pagination.page = 1;
  loadData();
};

const normalizeTrackingNumber = (value: string) =>
  (value ?? '')
    .replace(/^[='‘’“”"`\u200B-\u200F\uFEFF]+/, '')
    .trim()
    .replace(/-+$/, '');

const recordInvalidTrackings = (list: string[]) => {
  const existing = new Set(invalidTrackings.value);
  list.forEach(item => {
    if (!existing.has(item)) {
      invalidTrackings.value.push(item);
    }
  });
  persistInvalidTrackings();
};

const checkMissingTrackings = async (trackings: string[]) => {
  const normalized = trackings.map(t => normalizeTrackingNumber(t)).filter(Boolean);
  if (!normalized.length) return [];
  const result = await searchOrders(normalized);
  const existingSet = new Set(
    result
      .map(item => item.trackingNumber)
      .filter(Boolean)
      .map(t => t.trim().toUpperCase())
  );
  return normalized.filter(t => !existingSet.has(t.toUpperCase()));
};

const persistInvalidTrackings = () => {
  try {
    localStorage.setItem(INVALID_STORAGE_KEY, JSON.stringify(invalidTrackings.value));
  } catch (error) {
    console.warn('Failed to persist invalid trackings', error);
  }
};

const loadInvalidTrackings = () => {
  try {
    const cached = localStorage.getItem(INVALID_STORAGE_KEY);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed)) {
        invalidTrackings.value = parsed;
      }
    }
  } catch (error) {
    console.warn('Failed to load invalid trackings', error);
  }
};

const resetFilters = () => {
  filters.status = '';
  filters.trackingNumber = '';
  filters.username = '';
  handleSearch();
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  loadData();
};

const handleSizeChange = (size: number) => {
  pagination.size = size;
  pagination.page = 1;
  loadData();
};

const openBatchDialog = () => {
  batchDialog.raw = '';
  batchDialog.list = [];
  batchDialog.visible = true;
  if (isAdmin.value && !userOptions.value.length) {
    loadUserOptions();
  }
};

const parseBatchInput = () => {
  const raw = batchDialog.raw ?? '';
  const seen = new Set<string>();
  const result: string[] = [];
  const lines = raw.split(/\n+/);
  lines.forEach(line => {
    const cleaned = line.replace(/["“”]/g, ' ').trim();
    if (!cleaned) return;
    const matcher = /[A-Za-z0-9-]{4,}/g;
    const match = cleaned.match(matcher);
    if (!match || !match.length) return;
    let candidate = match[0].trim();
    if (!candidate) return;
    if (candidate.endsWith('-')) {
      candidate = candidate.replace(/-+$/g, '');
    }
    if (candidate.length < 12) {
      return;
    }
    const normalized = candidate.toUpperCase();
    if (seen.has(normalized)) return;
    seen.add(normalized);
    result.push(candidate);
  });
  batchDialog.list = result;
};

watch(() => batchDialog.raw, () => {
  parseBatchInput();
});

const submitBatch = async () => {
  if (!batchDialog.list.length) {
    ElMessage.warning('请先输入单号');
    return;
  }
  const missing = await checkMissingTrackings(batchDialog.list);
  if (missing.length) {
    recordInvalidTrackings(missing);
    ElMessage.warning(`以下单号未在物流单号中：${missing.join('、')}`);
    return;
  }
  batchDialog.loading = true;
  try {
    const payload: UserSubmissionBatchRequest = {
      trackingNumbers: [...batchDialog.list],
      rawContent: batchDialog.raw,
      username: isAdmin.value ? (selectedOwner.value?.trim() || undefined) : undefined
    };
    await submitUserSubmissionsBatch(payload);
    ElMessage.success('批量提交成功');
    batchDialog.visible = false;
    loadData();
  } finally {
    batchDialog.loading = false;
  }
};

watch(selectedOwner, value => {
  if (!isAdmin.value) return;
  if (value) {
    localStorage.setItem(OWNER_STORAGE_KEY, value);
  } else {
    localStorage.removeItem(OWNER_STORAGE_KEY);
  }
});

const goSubmissionLogs = () => {
  router.push('/submission-logs');
};

const formatDate = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 19);
};

const formatOrderTime = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 19);
};

const exportSubmissions = () => {
  if (!submissions.value.length) {
    ElMessage.info('暂无可导出的记录');
    return;
  }
  exportLoading.value = true;
  try {
    const headers = ['下单日期', '订单时间', '单号', '型号', '物流公司', '归属用户', '订单状态', '提交状态', '提交时间'];
    const rows = submissions.value.map(item => {
      const order = item.order;
      return [
        order?.orderDate ?? '-',
        formatOrderTime(order?.orderTime),
        item.trackingNumber ?? '-',
        order?.model ?? '-',
        order?.category ?? '-',
        item.ownerUsername ?? item.username ?? '-',
        orderStatusLabel(order?.status),
        statusLabel(item.status),
        formatDate(item.createdAt)
      ];
    });
    const csv = [headers, ...rows]
      .map(cols => cols.map(col => `"${String(col ?? '').replace(/"/g, '""')}"`).join(','))
      .join('\n');
    const blob = new Blob([csv], { type: 'application/vnd.ms-excel' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `submissions-${new Date().toISOString().slice(0, 10)}.xls`;
    a.click();
    window.URL.revokeObjectURL(url);
  } finally {
    exportLoading.value = false;
  }
};

const exportInvalidTrackings = () => {
  if (!invalidTrackings.value.length) {
    ElMessage.info('暂无可导出的单号');
    return;
  }
  const headers = ['未匹配的单号'];
  const rows = invalidTrackings.value.map(item => [item]);
  const csv = [headers, ...rows]
    .map(cols => cols.map(col => `"${String(col ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `invalid-trackings-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  window.URL.revokeObjectURL(url);
};

const clearInvalidTrackings = () => {
  invalidTrackings.value = [];
  persistInvalidTrackings();
};

watch(isAdmin, value => {
  pagination.page = 1;
  if (value) {
    loadUserOptions();
    loadStoredOwner();
    loadInvalidTrackings();
  } else {
    selectedOwner.value = '';
  }
  loadData();
}, { immediate: true });

</script>

<style scoped>
.submissions-page .page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-row {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.tips-card ul {
  margin: 0;
  padding-left: 18px;
  color: var(--text-muted);
}

.filter-card {
  margin-bottom: 16px;
}

.table-card {
  margin-top: 0;
}

.invalid-card {
  margin-top: 12px;
}

.invalid-list {
  margin: 0;
  padding-left: 16px;
  color: var(--text-muted);
}

.table-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.batch-preview {
  margin-top: 12px;
  font-size: 13px;
  color: var(--text-muted);
}

.batch-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.tag-item {
  font-size: 12px;
}
.batch-tip {
  margin-bottom: 12px;
  color: var(--text-muted);
}

.batch-example {
  background: #f5f5f5;
  padding: 8px 12px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', Menlo, Monaco, Consolas, 'Courier New', monospace;
}

.inline-user-form {
  margin-bottom: 8px;
}

.user-select-row {
  display: flex;
  align-items: center;
}

.user-create-input {
  margin-top: 8px;
}
</style>
