<template>
  <div class="page submissions-page">
    <div class="page-header">
      <div>
        <h2>单号提交</h2>
        <p class="sub">提交后即可在下方列表中查看处理进度</p>
      </div>
      <el-tag type="info">{{ isAdmin ? '管理员视图' : '个人视图' }}</el-tag>
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
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="单号">
          <el-input v-model="filters.trackingNumber" placeholder="支持模糊搜索" clearable />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="用户名">
          <el-input v-model="filters.username" placeholder="仅管理员可筛选" clearable />
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
        <el-table-column v-if="isAdmin" prop="username" label="用户名" width="140" />
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import {
  fetchAllSubmissions,
  fetchMySubmissions,
  submitUserSubmission,
  type SubmissionQueryParams
} from '@/api/submissions';
import type { OrderRecord, UserSubmission, UserSubmissionCreateRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
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

const buildQueryParams = (): SubmissionQueryParams => ({
  page: pagination.page,
  size: pagination.size,
  status: filters.status || undefined,
  trackingNumber: filters.trackingNumber ? filters.trackingNumber.trim() : undefined,
  username: isAdmin.value && filters.username ? filters.username.trim() : undefined
});

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
  const normalized = form.trackingNumber.trim();
  if (!normalized) {
    return;
  }
  const existsLocally = submissions.value.some(
    item => item.trackingNumber?.trim().toUpperCase() === normalized.toUpperCase()
  );
  if (existsLocally) {
    ElMessage.warning('该单号已提交，请勿重复提交');
    return;
  }
  form.trackingNumber = normalized;
  submitLoading.value = true;
  try {
    await submitUserSubmission(form);
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
    const headers = ['下单日期', '订单时间', '单号', '型号', '物流公司', '订单状态', '提交状态', '提交时间'];
    const rows = submissions.value.map(item => {
      const order = item.order;
      return [
        order?.orderDate ?? '-',
        formatOrderTime(order?.orderTime),
        item.trackingNumber ?? '-',
        order?.model ?? '-',
        order?.category ?? '-',
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

watch(isAdmin, () => {
  pagination.page = 1;
  loadData();
});

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.submissions-page .page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
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

.table-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
