<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>硬件价格</h2>
        <p class="sub">只记录型号、价格与录入人，支持上传 Excel 批量导入</p>
      </div>
      <div class="actions" v-if="isAdmin">
        <input ref="fileInput" type="file" accept=".xls,.xlsx" hidden @change="handleFileChange" />
        <el-button @click="openBatchDialog">导入Excel</el-button>
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
        <el-form-item label="型号">
          <el-input
            v-model="filters.itemName"
            placeholder="输入型号关键字"
            clearable
            style="width: 200px"
            @keyup.enter="loadPrices"
          />
          <el-button style="margin-left: 8px" @click="clearItemName">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="sortedPrices" v-loading="loading">
        <el-table-column prop="priceDate" label="日期" width="140" />
        <el-table-column prop="itemName" label="型号"> </el-table-column>
        <el-table-column prop="price" label="价格" width="140">
          <template #default="{ row }">￥{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
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
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="dialog.form.price" :min="0" :step="50" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialog.visible" title="导入Excel" width="560px">
      <p class="muted" style="margin-bottom: 12px">
        上传 Excel：首列 <code>型号</code>，第二列 <code>价格</code>，日期使用下方选择的值。
      </p>
      <div class="batch-form file-line">
        <el-date-picker
          v-model="batchDialog.form.priceDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期（默认为今日）"
        />
        <el-input v-model="batchDialog.fileName" placeholder="请选择 Excel 文件" readonly />
        <el-button @click="triggerFileSelect">选择文件</el-button>
      </div>
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchDialog.loading" @click="submitBatch">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="batchProgress.visible"
      title="Excel 导入中"
      width="360px"
      :show-close="false"
      align-center
    >
      <p class="muted" style="margin-bottom: 12px">正在导入数据，请稍候…</p>
      <el-progress :percentage="batchProgress.percent" :stroke-width="12" status="success" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fetchHardwarePrices, createHardwarePrice, updateHardwarePrice, deleteHardwarePrice, importHardwarePrices } from '@/api/hardware';
import type { HardwarePrice, HardwarePriceRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');
const fileInput = ref<HTMLInputElement>();

const filters = reactive({
  range: [] as string[],
  itemName: ''
});
const autoSearchSuspended = ref(false);

const prices = ref<HardwarePrice[]>([]);
const loading = ref(false);

const dialog = reactive({
  visible: false,
  loading: false,
  editingId: 0,
  form: { priceDate: '', itemName: '', price: 0 } as HardwarePriceRequest
});

const batchDialog = reactive({
  visible: false,
  loading: false,
  form: { priceDate: '' },
  fileName: ''
});
const batchProgress = reactive({
  visible: false,
  percent: 0,
  timer: null as number | null
});
const selectedFile = ref<File | null>(null);

const formRef = ref<FormInstance>();

const rules: FormRules = {
  priceDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  itemName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'change' }]
};

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
    const params: { startDate?: string; endDate?: string; itemName?: string } = {};
    if (Array.isArray(filters.range) && filters.range.length === 2) {
      params.startDate = filters.range[0];
      params.endDate = filters.range[1];
    }
    if (filters.itemName) {
      params.itemName = filters.itemName;
    }
    prices.value = await fetchHardwarePrices(params);
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  autoSearchSuspended.value = true;
  filters.range = defaultRange();
  filters.itemName = '';
  autoSearchSuspended.value = false;
  loadPrices();
};

const clearItemName = () => {
  filters.itemName = '';
};

const openDialog = (price?: HardwarePrice) => {
  if (price) {
    dialog.editingId = price.id;
    Object.assign(dialog.form, {
      priceDate: price.priceDate,
      itemName: price.itemName,
      price: price.price
    });
  } else {
    dialog.editingId = 0;
    Object.assign(dialog.form, {
      priceDate: (Array.isArray(filters.range) && filters.range[1]) ? filters.range[1] : formatDate(new Date()),
      itemName: '',
      price: 0
    });
  }
  dialog.visible = true;
};

const openBatchDialog = () => {
  selectedFile.value = null;
  batchDialog.fileName = '';
  batchDialog.form.priceDate = (Array.isArray(filters.range) && filters.range[1])
    ? filters.range[1]
    : formatDate(new Date());
  if (fileInput.value) {
    fileInput.value.value = '';
  }
  batchDialog.visible = true;
};

const triggerFileSelect = () => {
  fileInput.value?.click();
};

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  selectedFile.value = file;
  batchDialog.fileName = file.name;
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
const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const normalized = value.replace('T', ' ').replace('Z', '');
  return normalized.slice(0, 19);
};

const startBatchProgress = () => {
  batchProgress.visible = true;
  batchProgress.percent = 10;
  if (batchProgress.timer) {
    clearInterval(batchProgress.timer);
  }
  batchProgress.timer = window.setInterval(() => {
    if (batchProgress.percent < 90) {
      batchProgress.percent += 10;
    }
  }, 300);
};

const finishBatchProgress = () => {
  if (batchProgress.timer) {
    clearInterval(batchProgress.timer);
    batchProgress.timer = null;
  }
  batchProgress.percent = 100;
  setTimeout(() => {
    batchProgress.visible = false;
    batchProgress.percent = 0;
  }, 400);
};

const submitBatch = async () => {
  if (!batchDialog.form.priceDate) {
    ElMessage.warning('请先选择日期');
    return;
  }
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 Excel 文件');
    return;
  }
  startBatchProgress();
  batchDialog.loading = true;
  try {
    await importHardwarePrices(selectedFile.value, batchDialog.form.priceDate);
    ElMessage.success('导入成功');
    batchDialog.visible = false;
    selectedFile.value = null;
    batchDialog.fileName = '';
    if (fileInput.value) {
      fileInput.value.value = '';
    }
    loadPrices();
  } finally {
    batchDialog.loading = false;
    finishBatchProgress();
  }
};

const triggerAutoSearch = () => {
  if (autoSearchSuspended.value) return;
  loadPrices();
};

watch(() => filters.range, triggerAutoSearch, { deep: true });
watch(() => filters.itemName, triggerAutoSearch);

resetFilters();

onBeforeUnmount(() => {
  if (batchProgress.timer) {
    clearInterval(batchProgress.timer);
  }
});
</script>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
}

.table-card {
  margin-top: 16px;
}

.batch-form {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: center;
}

.batch-form .el-input,
.batch-form .el-input-number,
.batch-form .el-date-picker {
  flex: 1;
}

.batch-form.file-line .el-button {
  flex: 0 0 110px;
}
</style>
