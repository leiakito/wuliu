<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>提交记录</h2>
        <p class="sub">查看用户提交的原始文本与时间</p>
      </div>
      <div class="header-actions">
        <el-button text @click="handleAutoRefresh">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button text :loading="exportLoading" :disabled="!logs.length" @click="exportLogs">
          导出 Excel
        </el-button>
      </div>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="用户名">
          <el-select
            v-model="filters.username"
            filterable
            clearable
            placeholder="全部用户"
            style="width: 200px"
            :loading="userLoading"
          >
            <el-option v-for="user in userOptions" :key="user" :label="user" :value="user" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="搜索原文" clearable />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.range"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            unlink-panels
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="logs" v-loading="loading">
        <el-table-column prop="createdAt" label="提交时间" width="200">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="160" />
        <el-table-column label="原文本">
          <template #default="{ row }">
            <span class="log-content">{{ formatLogContent(row.content) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        class="table-pagination"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { fetchSubmissionLogs } from '@/api/submissions';
import { listUsers } from '@/api/users';
import type { SubmissionLogQueryRequest, UserSubmissionLog, SysUser } from '@/types/models';

const loading = ref(false);
const userLoading = ref(false);
const exportLoading = ref(false);
const logs = ref<UserSubmissionLog[]>([]);
const userOptions = ref<string[]>([]);
const filters = reactive({
  username: '',
  keyword: '',
  range: [] as string[]
});
const pagination = reactive({ page: 1, size: 10, total: 0 });
const autoSearchSuspended = ref(false);

const buildParams = (): SubmissionLogQueryRequest => {
  const params: SubmissionLogQueryRequest = {
    page: pagination.page,
    size: pagination.size,
    username: filters.username?.trim() || undefined,
    keyword: filters.keyword?.trim() || undefined
  };
  if (Array.isArray(filters.range) && filters.range.length === 2) {
    params.startTime = filters.range[0];
    params.endTime = filters.range[1];
  }
  return params;
};

const loadUserOptions = async () => {
  userLoading.value = true;
  try {
    const response = await listUsers();
    userOptions.value = response.map((item: SysUser) => item.username);
  } finally {
    userLoading.value = false;
  }
};

const loadLogs = async () => {
  loading.value = true;
  try {
    const response = await fetchSubmissionLogs(buildParams());
    logs.value = response.records;
    pagination.total = response.total;
  } catch (error) {
    console.error(error);
    ElMessage.error('加载提交记录失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  autoSearchSuspended.value = true;
  filters.username = '';
  filters.keyword = '';
  filters.range = [];
  pagination.page = 1;
  autoSearchSuspended.value = false;
  loadLogs();
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  loadLogs();
};

const handleSizeChange = (size: number) => {
  pagination.size = size;
  pagination.page = 1;
  loadLogs();
};

const handleAutoRefresh = () => {
  loadLogs();
};

const exportLogs = async () => {
  if (!logs.value.length) {
    ElMessage.warning('暂无数据可导出');
    return;
  }
  exportLoading.value = true;
  try {
    const headers = ['提交时间', '用户名', '原文本'];
    const rows = logs.value.map(item => [
      item.createdAt ?? '',
      item.username ?? '',
      item.content ?? ''
    ]);
    const csv = [headers, ...rows]
      .map(cols => cols.map(col => `"${String(col ?? '').replace(/"/g, '""')}"`).join(','))
      .join('\n');
    const blob = new Blob([csv], { type: 'application/vnd.ms-excel' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `submission-logs-${new Date().toISOString().slice(0, 10)}.xls`;
    a.click();
    window.URL.revokeObjectURL(url);
  } finally {
    exportLoading.value = false;
  }
};

watch(() => filters.username, value => {
  if (autoSearchSuspended.value) return;
  pagination.page = 1;
  loadLogs();
});

watch(() => filters.range, () => {
  if (autoSearchSuspended.value) return;
  pagination.page = 1;
  loadLogs();
}, { deep: true });

watch(() => filters.keyword, value => {
  if (autoSearchSuspended.value) return;
  pagination.page = 1;
  loadLogs();
});

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value.replace('T', ' ').replace('Z', ''));
  const shanghai = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(date);
  return shanghai.replace(/\//g, '-');
};

// 过滤提交原文中的空白行，只保留有内容的行
const formatLogContent = (value?: string): string => {
  if (!value) return '';
  return value
    .replace(/\u00A0/g, ' ') // 将不换行空格替换为普通空格
    .replace(/\u3000/g, ' ') // 全角空格转半角
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .join('\n');
};

loadUserOptions();
loadLogs();
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.table-card {
  margin-top: 16px;
}

.log-content {
  white-space: pre-wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
