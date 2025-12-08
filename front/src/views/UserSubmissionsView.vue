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
            <el-form-item label="订单日期">
              <el-date-picker
                v-model="form.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="可选，用于匹配指定日期的订单"
                style="width: 100%"
              />
              <div class="form-tip">中文单号建议选择日期，以区分不同日期的订单</div>
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
          <el-form label-width="90px" class="inline-user-form">
            <el-form-item label="归属人">
              <div class="owner-input-wrapper">
                <el-input
                  v-model="selectedOwner"
                  readonly
                  :placeholder="isAdmin ? '点击管理按钮选择归属人' : '点击管理按钮选择归属人'"
                  style="flex: 1"
                  @click="openOwnerDialog"
                />
                <el-button type="primary" style="margin-left: 8px" @click="openOwnerDialog">管理</el-button>
              </div>
            </el-form-item>
          </el-form>
          <ul>
            <li>请在左侧输入或粘贴物流单号进行提交</li>
            <li>状态默认为「待处理」,确认后会更新状态</li>
            <li>系统会自动关联订单金额、型号等信息</li>
            <li>提交的单号会在物流单号库中查找,找到后会在结账管理界面生成待结账订单</li>
            <li v-if="isAdmin">管理员可使用下方筛选器按归属人或状态查询所有提交</li>
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
        <el-form-item v-if="isAdmin" label="归属人">
          <el-select
            v-model="filters.username"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
            @change="handleSearch"
          >
            <el-option
              v-for="owner in ownerOptions"
              :key="owner"
              :label="owner"
              :value="owner"
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
        <el-table-column v-if="isAdmin" label="归属人" width="140">
          <template #default="{ row }">
            {{ row.ownerUsername || row.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="提交人" width="140">
          <template #default="{ row }">
            {{ row.username || '-' }}
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

    <el-card v-if="invalidTrackings.length" class="invalid-card">
      <template #header>
        <div class="card-header">
          <span>未在物流单号中的单号</span>
          <div>
            <el-button text size="small" @click="exportInvalidTrackings">导出</el-button>
            <el-button text size="small" @click="clearInvalidTrackings">清空</el-button>
          </div>
        </div>
      </template>
      <el-table :data="invalidTrackings" size="small" style="width: 100%">
        <el-table-column label="单号" min-width="240">
          <template #default="{ row, $index }">
            <el-input v-model.trim="row.trackingNumber" placeholder="请输入单号" @change="persistInvalidTrackings" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <el-input v-model.trim="row.remark" placeholder="可填写备注" @change="persistInvalidTrackings" />
          </template>
        </el-table-column>
        <el-table-column label="归属人" width="200">
          <template #default="{ row }">
            <span>{{ row.ownerUsername || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row, $index }">
            <el-button text type="danger" size="small" @click="clearInvalidOne($index)">清除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="batchDialog.visible"
      title="批量提交单号"
      width="520px"
      :close-on-click-modal="false"
      :destroy-on-close="true"
    >
      <div class="batch-tip">
          <p>支持一次粘贴多个单号，系统会自动提取每行中的第一个编号。</p>
          <p>批量提交请确保每个单号单独换行。</p>
          <p>示例：</p>
          <pre class="batch-example">JDX044863610899
343138058920</pre>
        </div>
      <el-form-item label="订单日期" style="margin-bottom: 12px;">
        <el-date-picker
          v-model="batchDialog.orderDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="可选，用于匹配指定日期的订单"
          style="width: 100%"
        />
        <div class="form-tip">中文单号建议选择日期，以区分不同日期的订单</div>
      </el-form-item>
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

    <el-dialog
      v-model="ownerDialogVisible"
      title="归属人管理"
      width="500px"
      :close-on-click-modal="false"
      :destroy-on-close="true"
    >
      <div class="owner-dialog-header">
        <el-button type="primary" size="small" @click="addOwner">添加</el-button>
        <el-button
          :type="deleteMode ? 'danger' : 'default'"
          size="small"
          @click="deleteMode = !deleteMode"
        >
          {{ deleteMode ? '取消删除' : '删除' }}
        </el-button>
      </div>
      <div class="owner-grid">
        <div
          v-for="owner in ownerOptions"
          :key="owner"
          class="owner-item"
          :class="{
            'owner-item--selected': selectedOwner === owner,
            'owner-item--delete-mode': deleteMode
          }"
          @click="handleOwnerClick(owner)"
        >
          {{ owner }}
        </div>
      </div>
      <div v-if="!ownerOptions.length" class="owner-empty">暂无归属人，点击添加按钮新建</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import * as XLSX from 'xlsx'
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  fetchAllSubmissions,
  fetchMySubmissions,
  submitUserSubmission,
  submitUserSubmissionsBatch,
  type SubmissionQueryParams
} from '@/api/submissions';
import { searchOrders } from '@/api/orders';
import type { OrderRecord, UserSubmission, UserSubmissionCreateRequest, UserSubmissionBatchRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
const router = useRouter();
// 所有用户都可以使用全部功能
const isAdmin = computed(() => true);

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
  trackingNumber: '',
  orderDate: ''
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
const ownerOptions = ref<string[]>([]);
const selectedOwner = ref('');
const ownerDialogVisible = ref(false);
const deleteMode = ref(false);
const OWNER_STORAGE_KEY = 'submission-owner';
const INVALID_STORAGE_KEY = 'submission-invalid-trackings';
type InvalidTracking = { trackingNumber: string; remark?: string; ownerUsername?: string };
const invalidTrackings = ref<InvalidTracking[]>([]);

const batchDialog = reactive({
  visible: false,
  loading: false,
  raw: '',
  list: [] as string[],
  orderDate: ''
});

const buildQueryParams = (): SubmissionQueryParams => ({
  page: pagination.page,
  size: pagination.size,
  status: filters.status || undefined,
  trackingNumber: filters.trackingNumber ? filters.trackingNumber.trim() : undefined,
  username: isAdmin.value && filters.username ? filters.username.trim() : undefined
});

const loadOwnerOptions = async () => {
  try {
    const response = await fetch('/api/user-submissions/owners', {
      headers: {
        'Authorization': `Bearer ${auth.token}`
      }
    });
    if (response.ok) {
      const data = await response.json();
      ownerOptions.value = data.data || [];
    }
  } catch (error) {
    console.error('加载归属人列表失败', error);
  }
};

const handleDeleteOwner = async (ownerName: string) => {
  try {
    await ElMessageBox.confirm(
      `确认删除归属人 "${ownerName}" 吗？删除后该归属人将从列表中移除，但已关联的单号不受影响。`,
      '删除确认',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );

    const response = await fetch(`/api/user-submissions/owners/${encodeURIComponent(ownerName)}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${auth.token}`
      }
    });

    if (response.ok) {
      ElMessage.success('删除成功');
      // 如果删除的是当前选中的归属人，清空选中
      if (selectedOwner.value === ownerName) {
        selectedOwner.value = '';
      }
      // 重新加载归属人列表
      await loadOwnerOptions();
    } else {
      ElMessage.error('删除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除归属人失败', error);
      ElMessage.error('删除失败');
    }
  }
};

const openOwnerDialog = () => {
  deleteMode.value = false;
  ownerDialogVisible.value = true;
  if (!ownerOptions.value.length) {
    loadOwnerOptions();
  }
};

const addOwner = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请输入新归属人名称', '添加归属人', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /^.+$/,
      inputErrorMessage: '名称不能为空'
    });
    if (value && value.trim()) {
      const newOwner = value.trim();
      if (!ownerOptions.value.includes(newOwner)) {
        ownerOptions.value.push(newOwner);
      }
      selectedOwner.value = newOwner;
      ownerDialogVisible.value = false;
      ElMessage.success('添加成功');
    }
  } catch {
    // 用户取消
  }
};

const handleOwnerClick = async (owner: string) => {
  if (deleteMode.value) {
    await handleDeleteOwner(owner);
  } else {
    selectedOwner.value = owner;
    ownerDialogVisible.value = false;
  }
};

const loadStoredOwner = () => {
  const stored = localStorage.getItem(OWNER_STORAGE_KEY);
  if (stored) {
    selectedOwner.value = stored;
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
  // 检查本地是否已存在相同单号（如果指定了日期，还需匹配日期）
  const existsLocally = submissions.value.some(item => {
    const trackingMatch = item.trackingNumber?.trim().toUpperCase() === normalized.toUpperCase();
    if (!trackingMatch) return false;
    // 如果没有指定日期，检查是否有任何相同单号
    if (!form.orderDate) return true;
    // 如果指定了日期，检查是否有相同单号+日期的提交
    return item.orderDate === form.orderDate;
  });
  if (existsLocally) {
    const msg = form.orderDate
      ? `该单号在 ${form.orderDate} 已提交，请勿重复提交`
      : '该单号已提交，请勿重复提交';
    ElMessage.warning(msg);
    return;
  }
  const payload: UserSubmissionCreateRequest = {
    trackingNumber: normalized,
    username: selectedOwner.value?.trim() || undefined,
    orderDate: form.orderDate || undefined
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
  form.orderDate = '';
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
  // 按单号去重（不区分大小写）
  const existsSet = new Set(invalidTrackings.value.map(it => (it.trackingNumber || '').trim().toUpperCase()));
  list.forEach(tn => {
    const normalized = (tn || '').trim();
    if (!normalized) return;
    const key = normalized.toUpperCase();
    if (existsSet.has(key)) return;
    invalidTrackings.value.push({
      trackingNumber: normalized,
      remark: '',
      ownerUsername: selectedOwner.value?.trim() || ''
    });
    existsSet.add(key);
  });
  persistInvalidTrackings();
};

const checkMissingTrackings = async (trackings: string[]) => {
  const normalized = trackings.map(t => normalizeTrackingNumber(t)).filter(Boolean);
  if (!normalized.length) return [];
  const result = await searchOrders(normalized);
  const existing = result
    .map(item => item.trackingNumber)
    .filter(Boolean)
    .map(t => t.trim().toUpperCase());
  return normalized.filter(t => {
    const upper = t.toUpperCase();
    if (existing.includes(upper)) {
      return false;
    }
    return !existing.some(num => num.startsWith(`${upper}-`));
  });
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
    if (!cached) return;
    const parsed = JSON.parse(cached);
    if (!Array.isArray(parsed)) return;
    // 兼容老数据（字符串数组）与新结构（对象数组）
    invalidTrackings.value = parsed.map((it: any) => {
      if (typeof it === 'string') {
        return { trackingNumber: it, remark: '', ownerUsername: selectedOwner.value?.trim() || '' } as InvalidTracking;
      }
      return {
        trackingNumber: (it?.trackingNumber ?? '').trim(),
        remark: (it?.remark ?? '').trim(),
        ownerUsername: it?.ownerUsername ?? ''
      } as InvalidTracking;
    }).filter((it: InvalidTracking) => !!it.trackingNumber);
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
  batchDialog.orderDate = '';
  batchDialog.visible = true;
  if (!ownerOptions.value.length) {
    loadOwnerOptions();
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
      username: selectedOwner.value?.trim() || undefined,
      orderDate: batchDialog.orderDate || undefined
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
    // 表头
    const headers = [
      '下单日期', '订单时间', '单号', '型号', '物流公司',
      '归属人', '提交人', '订单状态', '提交状态', '提交时间'
    ];

    // 数据行
    const rows = submissions.value.map(item => {
      const order = item.order;
      return [
        order?.orderDate ?? '-',
        formatOrderTime(order?.orderTime),
        item.trackingNumber ?? '-',
        order?.model ?? '-',
        order?.category ?? '-',
        item.ownerUsername ?? '-',
        item.username ?? '-',
        orderStatusLabel(order?.status),
        statusLabel(item.status),
        formatDate(item.createdAt)
      ];
    });

    // 组合所有数据
    const worksheetData = [headers, ...rows];

    // 创建 Sheet
    const worksheet = XLSX.utils.aoa_to_sheet(worksheetData);

    // ⭐ 设置列宽（wch: 字符宽度）
    worksheet['!cols'] = [
      { wch: 12 }, // 下单日期
      { wch: 20 }, // 订单时间
      { wch: 20 }, // 单号
      { wch: 15 }, // 型号
      { wch: 12 }, // 物流公司
      { wch: 15 }, // 归属用户
        { wch: 14 }, // 提交人
      { wch: 12 }, // 订单状态
      { wch: 12 }, // 提交状态
      { wch: 20 }, // 提交时间
    ];

    // 创建工作簿
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Submissions');

    // 导出 Excel
    XLSX.writeFile(
      workbook,
      `submissions-${new Date().toISOString().slice(0, 10)}.xlsx`
    );

  } finally {
    exportLoading.value = false;
  }
};

const exportInvalidTrackings = () => {
  if (!invalidTrackings.value.length) {
    ElMessage.info('暂无可导出的单号');
    return;
  }

  const headers = ['单号', '备注', '归属人'];

  const rows = invalidTrackings.value.map(item => [
    item.trackingNumber ?? '',
    item.remark ?? '',
    item.ownerUsername ?? ''
  ]);

  // 组合数据
  const worksheetData = [headers, ...rows];

  // 创建 sheet
  const worksheet = XLSX.utils.aoa_to_sheet(worksheetData);

  // ⭐ 设置列宽
  worksheet['!cols'] = [
    { wch: 22 }, // 单号
    { wch: 30 }, // 备注
    { wch: 16 }  // 归属人
  ];

  // 创建工作簿
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Invalid Trackings');

  // 导出 xlsx
  XLSX.writeFile(
    workbook,
    `未在物流单号中的单号-${new Date().toISOString().slice(0, 10)}.xlsx`
  );
};
const clearInvalidTrackings = () => {
  invalidTrackings.value = [];
  persistInvalidTrackings();
};

const clearInvalidOne = (index: number) => {
  if (index < 0 || index >= invalidTrackings.value.length) return;
  invalidTrackings.value.splice(index, 1);
  persistInvalidTrackings();
};

watch(isAdmin, value => {
  pagination.page = 1;
  loadOwnerOptions();
  loadStoredOwner();
  loadInvalidTrackings();  // 所有用户都可以加载无效单号列表
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

.owner-input-wrapper {
  display: flex;
  width: 100%;
}

.owner-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 8px;
}

.owner-option span {
  flex: 1;
}

.owner-dialog-header {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.owner-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.owner-item {
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  text-align: center;
  transition: all 0.2s;
  background: #fff;
}

.owner-item:hover {
  border-color: #409eff;
  color: #409eff;
}

.owner-item--selected {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
}

.owner-item--delete-mode {
  border-color: #f56c6c;
}

.owner-item--delete-mode:hover {
  background: #fef0f0;
  border-color: #f56c6c;
  color: #f56c6c;
}

.owner-empty {
  color: #909399;
  text-align: center;
  padding: 40px 0;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
