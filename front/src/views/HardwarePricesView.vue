<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>硬件价格</h2>
        <p class="sub">按日期记录 CPU / 主板 / GPU 等硬件价格</p>
      </div>
      <div class="actions" v-if="isAdmin">
        <el-button type="primary" @click="openDialog()">新增价格</el-button>
      </div>
    </div>

    <el-card>
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="日期">
          <el-date-picker
            v-model="filters.range"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            unlink-panels
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPrices">查询</el-button>
          <el-button @click="resetFilters">最近 7 天</el-button>
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="filters.category"
            placeholder="全部"
            clearable
            filterable
            style="width: 160px"
          >
            <el-option v-for="option in categoryOptions" :key="option" :label="option" :value="option" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="sortedPrices" v-loading="loading">
        <el-table-column prop="priceDate" label="日期" width="140" />
        <el-table-column prop="itemName" label="硬件"> </el-table-column>
        <el-table-column prop="category" label="分类" width="140" />
        <el-table-column prop="price" label="价格" width="140">
          <template #default="{ row }">￥{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="createdBy" label="录入人" width="140" />
        <el-table-column v-if="isAdmin" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" title="硬件价格" width="520px">
      <el-form ref="formRef" :model="dialog.form" :rules="rules" label-width="90px">
        <el-form-item label="日期" prop="priceDate">
          <el-date-picker v-model="dialog.form.priceDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="硬件名称" prop="itemName">
          <el-input v-model="dialog.form.itemName" placeholder="如 CPU i9-14900K" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="dialog.form.category" placeholder="如 CPU / 主板 / GPU" />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="dialog.form.price" :min="0" :step="50" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fetchHardwarePrices, createHardwarePrice, updateHardwarePrice, deleteHardwarePrice } from '@/api/hardware';
import type { HardwarePrice, HardwarePriceRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

const filters = reactive({
  range: [] as string[],
  category: ''
});

const prices = ref<HardwarePrice[]>([]);
const loading = ref(false);

const dialog = reactive({
  visible: false,
  loading: false,
  editingId: 0,
  form: { priceDate: '', itemName: '', category: '', price: 0, remark: '' } as HardwarePriceRequest
});

const formRef = ref<FormInstance>();

const rules: FormRules = {
  priceDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  itemName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'change' }]
};

const categoryOptions = computed(() =>
  Array.from(new Set(prices.value.map(price => price.category).filter((c): c is string => !!c)))
);
const sortedPrices = computed(() => prices.value);

const defaultRange = () => {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 6);
  return [formatDate(start), formatDate(end)];
};

const formatDate = (date: Date) => date.toISOString().slice(0, 10);

const loadPrices = async () => {
  loading.value = true;
  try {
    const params: { startDate?: string; endDate?: string; category?: string } = {};
    if (filters.range.length === 2) {
      params.startDate = filters.range[0];
      params.endDate = filters.range[1];
    }
    if (filters.category) {
      params.category = filters.category;
    }
    prices.value = await fetchHardwarePrices(params);
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.range = defaultRange();
  filters.category = '';
  loadPrices();
};

const openDialog = (price?: HardwarePrice) => {
  if (price) {
    dialog.editingId = price.id;
    Object.assign(dialog.form, {
      priceDate: price.priceDate,
      itemName: price.itemName,
      category: price.category ?? '',
      price: price.price,
      remark: price.remark ?? ''
    });
  } else {
    dialog.editingId = 0;
    Object.assign(dialog.form, {
      priceDate: filters.range[1] || formatDate(new Date()),
      itemName: '',
      category: filters.category || '',
      price: 0,
      remark: ''
    });
  }
  dialog.visible = true;
};

const submitForm = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  dialog.loading = true;
  try {
    if (dialog.editingId) {
      await updateHardwarePrice(dialog.editingId, dialog.form);
      ElMessage.success('更新成功');
    } else {
      await createHardwarePrice(dialog.form);
      ElMessage.success('新增成功');
    }
    dialog.visible = false;
    loadPrices();
  } finally {
    dialog.loading = false;
  }
};

const handleDelete = async (row: HardwarePrice) => {
  await ElMessageBox.confirm(`确认删除 ${row.itemName} 的报价吗？`, '提示', { type: 'warning' });
  await deleteHardwarePrice(row.id);
  ElMessage.success('已删除');
  loadPrices();
};

const formatPrice = (value?: number) => (value ?? 0).toFixed(2);

resetFilters();
</script>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
}

.table-card {
  margin-top: 16px;
}
</style>
