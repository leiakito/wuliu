<template>
  <div class="page users-page">
    <div class="page-header">
      <div>
        <h2>用户管理</h2>
        <p class="sub">管理系统用户，控制访问权限和账户状态</p>
      </div>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        新增用户
      </el-button>
    </div>

    <!-- 搜索和筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="用户名">
          <el-input v-model="filterForm.username" placeholder="输入用户名搜索" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filterForm.role" placeholder="选择角色" clearable>
            <el-option label="管理员" value="ADMIN" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="选择状态" clearable>
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadUsers">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 用户统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="8">
        <el-card class="stat-card">
          <el-statistic title="总用户数" :value="stats.totalUsers">
            <template #prefix>
              <el-icon><UserFilled /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card class="stat-card">
          <el-statistic title="管理员" :value="stats.adminCount">
            <template #prefix>
              <el-icon style="color: #f56c6c"><Avatar /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card class="stat-card">
          <el-statistic title="启用用户" :value="stats.enabledCount">
            <template #prefix>
              <el-icon style="color: #67c23a"><Check /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用户列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>用户列表 ({{ users.length }} 人)</span>
          <div class="header-actions">
            <el-button size="small" @click="exportUsers">
              <el-icon><Download /></el-icon>
              导出
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="username" label="用户名" width="160">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" :style="{ background: getUserColor(row.username) }">
                {{ row.username.charAt(0).toUpperCase() }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fullName" label="姓名" width="160" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="dark">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button link type="warning" @click="resetPwd(row)">
              <el-icon><Key /></el-icon>
              重置密码
            </el-button>
            <el-button link type="danger" @click="removeUser(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑用户对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(form.id)" placeholder="请输入用户名">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="姓名" prop="fullName">
          <el-input v-model="form.fullName" placeholder="请输入姓名">
            <template #prefix>
              <el-icon><UserFilled /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN">
              <div class="role-option">
                <el-icon><Avatar /></el-icon>
                <div>
                  <div>管理员</div>
                  <div class="role-desc">拥有所有权限，可管理用户和系统设置</div>
                </div>
              </div>
            </el-option>
            <el-option label="用户" value="USER">
              <div class="role-option">
                <el-icon><User /></el-icon>
                <div>
                  <div>用户</div>
                  <div class="role-desc">基础权限，可查看和提交订单</div>
                </div>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="ENABLED">启用</el-radio>
            <el-radio label="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="默认 ChangeMe123!" show-password>
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          <el-icon><Check /></el-icon>
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listUsers, createUser, updateUser, resetPassword, deleteUser } from '@/api/users';
import type { SysUser, UserRequest } from '@/types/models';
import {
  Plus,
  Search,
  UserFilled,
  Avatar,
  Check,
  Download,
  Edit,
  Key,
  Delete,
  User,
  Lock
} from '@element-plus/icons-vue';

const users = ref<SysUser[]>([]);
const loading = ref(false);
const saving = ref(false);

const filterForm = reactive({
  username: '',
  role: '',
  status: ''
});

const stats = reactive({
  totalUsers: 0,
  adminCount: 0,
  enabledCount: 0
});

const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<UserRequest & { id?: number }>({
  username: '',
  fullName: '',
  role: 'USER',
  status: 'ENABLED',
  password: ''
});

const dialogTitle = computed(() => (form.id ? '编辑用户' : '新增用户'));

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
};

const getUserColor = (username: string) => {
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#00a870', '#9c27b0', '#ff5722'];
  const index = username.charCodeAt(0) % colors.length;
  return colors[index];
};

const updateStats = () => {
  stats.totalUsers = users.value.length;
  stats.adminCount = users.value.filter(u => u.role === 'ADMIN').length;
  stats.enabledCount = users.value.filter(u => u.status === 'ENABLED').length;
};

const loadUsers = async () => {
  loading.value = true;
  try {
    users.value = await listUsers();
    updateStats();
  } finally {
    loading.value = false;
  }
};

const resetFilter = () => {
  filterForm.username = '';
  filterForm.role = '';
  filterForm.status = '';
  loadUsers();
};

const exportUsers = () => {
  ElMessage.success('导出功能开发中');
};

const openDialog = (user?: SysUser) => {
  if (user) {
    Object.assign(form, user, { password: '', id: user.id });
  } else {
    Object.assign(form, {
      id: undefined,
      username: '',
      fullName: '',
      role: 'USER',
      status: 'ENABLED',
      password: ''
    });
  }
  dialogVisible.value = true;
};

const submit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (form.id) {
      await updateUser(form.id, form);
    } else {
      await createUser(form);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    loadUsers();
  } finally {
    saving.value = false;
  }
};

const resetPwd = async (user: SysUser) => {
  const { value } = await ElMessageBox.prompt(`为 ${user.username} 设置新密码`, '重置密码', {
    inputType: 'password',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  });
  await resetPassword(user.id, value);
  ElMessage.success('密码已重置');
};

const removeUser = async (user: SysUser) => {
  await ElMessageBox.confirm(`确认删除用户 ${user.username} ?`, '提示', { type: 'warning' });
  await deleteUser(user.id);
  ElMessage.success('已删除');
  loadUsers();
};

loadUsers();
</script>

<style scoped>
.users-page {
  max-width: 1400px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: 16px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-option {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .filter-card :deep(.el-form) {
    display: flex;
    flex-direction: column;
  }

  .filter-card :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>
