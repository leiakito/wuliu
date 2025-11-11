<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>操作日志</h2>
        <p class="sub">系统操作审计记录，实时追踪所有用户行为</p>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="Download"
          @click="exportLogs"
          :loading="exportLoading"
        >
          导出日志
        </el-button>
        <el-button
          :icon="Refresh"
          @click="loadLogs"
          :loading="loading"
        >
          刷新
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
        <p class="label">今日日志</p>
        <p class="value">{{ todayLogsCount }}</p>
      </div>
      <div class="stat-card" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
        <p class="label">活跃用户</p>
        <p class="value">{{ activeUsersCount }}</p>
      </div>
      <div class="stat-card" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
        <p class="label">总记录数</p>
        <p class="value">{{ total }}</p>
      </div>
      <div class="stat-card" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
        <p class="label">异常操作</p>
        <p class="value">{{ warningLogsCount }}</p>
      </div>
    </div>

    <!-- 搜索过滤区域 -->
    <el-card class="filter-card">
      <div class="filter-form">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索用户名、操作或详情"
          :prefix-icon="Search"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="filters.action"
          placeholder="操作类型"
          clearable
          @change="handleSearch"
        >
          <el-option label="登录" value="登录" />
          <el-option label="登出" value="登出" />
          <el-option label="新增" value="新增" />
          <el-option label="修改" value="修改" />
          <el-option label="删除" value="删除" />
          <el-option label="查询" value="查询" />
          <el-option label="导出" value="导出" />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          @change="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">
          搜索
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <!-- 日志列表 -->
    <el-card class="table-card">
      <div class="table-header">
        <span class="table-title">日志记录</span>
        <span class="table-count">共 {{ total }} 条记录</span>
      </div>

      <div class="logs-container" v-loading="loading">
        <div v-if="logs.length === 0" class="empty-state">
          <el-empty description="暂无日志记录" />
        </div>
        <div v-else class="logs-list">
          <div
            v-for="log in logs"
            :key="log.id"
            class="log-item"
            @click="showLogDetail(log)"
          >
            <div class="log-avatar">
              <el-avatar :size="40" :src="getUserAvatar(log.username)">
                {{ log.username.charAt(0).toUpperCase() }}
              </el-avatar>
            </div>
            <div class="log-content">
              <div class="log-header">
                <span class="log-username">{{ log.username }}</span>
                <el-tag :type="getActionType(log.action)" size="small">
                  {{ log.action }}
                </el-tag>
                <span class="log-time">{{ formatTime(log.createdAt) }}</span>
              </div>
              <div class="log-detail" v-if="log.detail">
                {{ log.detail }}
              </div>
              <div class="log-footer">
                <span class="log-ip">
                  <el-icon><Location /></el-icon>
                  {{ log.ip }}
                </span>
              </div>
            </div>
            <div class="log-actions">
              <el-button
                type="text"
                size="small"
                :icon="View"
                @click.stop="showLogDetail(log)"
              >
                详情
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 日志详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="日志详情"
      width="600px"
      :destroy-on-close="true"
    >
      <div v-if="selectedLog" class="log-detail-content">
        <div class="detail-item">
          <label>操作用户：</label>
          <div class="detail-value">
            <el-avatar :size="30" :src="getUserAvatar(selectedLog.username)">
              {{ selectedLog.username.charAt(0).toUpperCase() }}
            </el-avatar>
            <span>{{ selectedLog.username }}</span>
          </div>
        </div>
        <div class="detail-item">
          <label>操作类型：</label>
          <div class="detail-value">
            <el-tag :type="getActionType(selectedLog.action)">
              {{ selectedLog.action }}
            </el-tag>
          </div>
        </div>
        <div class="detail-item">
          <label>操作时间：</label>
          <div class="detail-value">
            {{ selectedLog.createdAt }}
          </div>
        </div>
        <div class="detail-item">
          <label>IP地址：</label>
          <div class="detail-value">
            {{ selectedLog.ip || '未知' }}
          </div>
        </div>
        <div class="detail-item" v-if="selectedLog.detail">
          <label>操作详情：</label>
          <div class="detail-value">
            <pre>{{ selectedLog.detail }}</pre>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { fetchLogs, exportLogs as exportLogsApi } from '@/api/logs';
import { ElMessage } from 'element-plus';
import {
  Search,
  Download,
  Refresh,
  View,
  Location
} from '@element-plus/icons-vue';
import type { SysLog } from '@/types/models';

interface LogFilters {
  keyword?: string;
  action?: string;
  dateRange?: [string, string];
}

const logs = ref<SysLog[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const exportLoading = ref(false);
const detailVisible = ref(false);
const selectedLog = ref<SysLog | null>(null);

const filters = ref<LogFilters>({
  keyword: '',
  action: '',
  dateRange: undefined
});

// 统计数据
const todayLogsCount = computed(() => {
  const today = new Date().toDateString();
  return logs.value.filter(log =>
    new Date(log.createdAt).toDateString() === today
  ).length;
});

const activeUsersCount = computed(() => {
  const uniqueUsers = new Set(logs.value.map(log => log.username));
  return uniqueUsers.size;
});

const warningLogsCount = computed(() => {
  return logs.value.filter(log =>
    log.action === '删除' ||
    log.detail?.includes('失败') ||
    log.detail?.includes('错误')
  ).length;
});

const loadLogs = async () => {
  loading.value = true;
  try {
    const params = {
      page: page.value,
      size: size.value,
      keyword: filters.value.keyword,
      action: filters.value.action,
      startDate: filters.value.dateRange?.[0],
      endDate: filters.value.dateRange?.[1]
    };

    const data = await fetchLogs(page.value, size.value, params);
    logs.value = data.records;
    total.value = data.total;
  } catch (error) {
    ElMessage.error('加载日志失败');
  } finally {
    loading.value = false;
  }
};

const handleSizeChange = (value: number) => {
  size.value = value;
  page.value = 1;
  loadLogs();
};

const handlePageChange = (value: number) => {
  page.value = value;
  loadLogs();
};

const handleSearch = () => {
  page.value = 1;
  loadLogs();
};

const resetFilters = () => {
  filters.value = {
    keyword: '',
    action: '',
    dateRange: undefined
  };
  page.value = 1;
  loadLogs();
};

const getActionType = (action: string) => {
  const actionTypes: { [key: string]: string } = {
    '登录': 'success',
    '登出': 'info',
    '新增': 'primary',
    '修改': 'warning',
    '删除': 'danger',
    '查询': '',
    '导出': 'success'
  };
  return actionTypes[action] || '';
};

const formatTime = (time: string) => {
  const date = new Date(time);
  const now = new Date();
  const diff = now.getTime() - date.getTime();

  if (diff < 60000) {
    return '刚刚';
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`;
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`;
  } else {
    return date.toLocaleString('zh-CN');
  }
};

const getUserAvatar = (username: string) => {
  // 可以根据用户名生成头像，或者返回默认头像
  return `https://api.dicebear.com/7.x/avataaars/svg?seed=${username}`;
};

const showLogDetail = (log: SysLog) => {
  selectedLog.value = log;
  detailVisible.value = true;
};

const exportLogs = async () => {
  exportLoading.value = true;
  try {
    // 这里应该调用导出API
    ElMessage.success('日志导出功能开发中...');
  } catch (error) {
    ElMessage.error('导出失败');
  } finally {
    exportLoading.value = false;
  }
};

onMounted(() => {
  loadLogs();
});
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.filter-card {
  margin-bottom: 0;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.filter-form .el-input {
  width: 280px;
}

.filter-form .el-select {
  width: 160px;
}

.filter-form .el-date-editor {
  width: 400px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.table-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

.table-count {
  color: var(--text-muted);
  font-size: 14px;
}

.logs-container {
  min-height: 400px;
}

.logs-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
  cursor: pointer;
}

.log-item:hover {
  background: #ffffff;
  border-color: var(--primary-color);
  box-shadow: 0 4px 12px rgba(61, 127, 255, 0.1);
  transform: translateY(-2px);
}

.log-avatar {
  flex-shrink: 0;
}

.log-content {
  flex: 1;
  min-width: 0;
}

.log-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.log-username {
  font-weight: 600;
  color: #1f2d3d;
  font-size: 15px;
}

.log-time {
  color: var(--text-muted);
  font-size: 13px;
  margin-left: auto;
}

.log-detail {
  color: #64748b;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.log-footer {
  display: flex;
  align-items: center;
  gap: 16px;
}

.log-ip {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.log-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

.log-detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-item label {
  font-weight: 500;
  color: #64748b;
  font-size: 14px;
}

.detail-value {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #1f2d3d;
  font-size: 15px;
}

.detail-value pre {
  background: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 768px) {
  .filter-form {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-form .el-input,
  .filter-form .el-select,
  .filter-form .el-date-editor {
    width: 100%;
  }

  .log-item {
    flex-direction: column;
    align-items: stretch;
  }

  .log-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .log-time {
    margin-left: 0;
  }

  .log-actions {
    align-self: flex-end;
  }
}
</style>
