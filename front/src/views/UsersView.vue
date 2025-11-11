<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>用户管理</h2>
        <p class="sub">仅管理员可访问，支持增删改及重置密码</p>
      </div>
      <el-button type="primary" @click="openDialog()">新增用户</el-button>
    </div>

    <el-card class="table-card">
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="username" label="用户名" width="160" />
        <el-table-column prop="fullName" label="姓名" width="160" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" @click="resetPwd(row)">重置密码</el-button>
            <el-button link type="danger" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(form.id)" />
        </el-form-item>
        <el-form-item label="姓名" prop="fullName">
          <el-input v-model="form.fullName" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" placeholder="默认 ChangeMe123!" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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

const users = ref<SysUser[]>([]);
const loading = ref(false);
const saving = ref(false);

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

const loadUsers = async () => {
  loading.value = true;
  try {
    users.value = await listUsers();
  } finally {
    loading.value = false;
  }
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
  await ElMessageBox.confirm(`确认删除 ${user.username} ?`, '提示', { type: 'warning' });
  await deleteUser(user.id);
  ElMessage.success('已删除');
  loadUsers();
};

loadUsers();
</script>
