<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>硬件价格</h2>
        <p class="sub">只记录型号、价格与录入人，支持上传 Excel 批量导入</p>
      </div>
      <div class="actions">
        <input ref="fileInput" type="file" accept=".xls,.xlsx" multiple hidden @change="handleFileChange" />
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

    <!-- 日期 -> 型号与价格 两级菜单视图 -->
    <el-card class="menu-card" v-if="groupedList.length">
      <div class="menu-header">
        <span class="title">按日期查看</span>
        <span class="muted small">共 {{ prices.length }} 条</span>
      </div>
      <el-scrollbar height="70vh">
        <el-menu class="group-menu" :unique-opened="true">
          <el-sub-menu v-for="(group, idx) in groupedList" :key="group.date" :index="String(idx)">
            <template #title>
              <span>{{ group.date }}</span>
              <el-tag size="small" style="margin-left: 8px">{{ group.items.length }}</el-tag>
            </template>
            <el-menu-item v-for="item in group.items" :key="item.id" :index="`${idx}-${item.id}`">
              <div class="menu-item-line">
                <span class="name">{{ item.itemName }}</span>
                <span class="right">
                  <span class="price" v-if="item.price !== null && item.price !== undefined">￥{{ formatPrice(item.price) }}</span>
                  <span class="price muted" v-else>-</span>
                  <span class="muted small meta">{{ formatDateTime(item.createdAt) }} · {{ item.createdBy }}</span>
                  <span class="row-actions">
                    <el-button link type="primary" @click.stop="openDialog(item)">编辑</el-button>
                    <el-button link type="danger" @click.stop="handleDelete(item)">删除</el-button>
                  </span>
                </span>
              </div>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
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

    <el-dialog v-model="batchDialog.visible" title="导入Excel" width="760px">
      <p class="muted" style="margin-bottom: 12px">
        支持单个或多个 Excel 文件，文件名需包含日期（yyyy-MM-dd），如 <code>2025-10-10.xlsx</code>，系统会自动识别并导入。
      </p>
      <div
        class="upload-drop"
        @click="triggerFileSelect"
        @dragover.prevent
        @drop.prevent="handleDrop"
      >
        <div class="upload-left">
          <el-icon><UploadFilled /></el-icon>
          <div>
            <div class="upload-title">拖拽或点击上传</div>
            <div class="muted">可一次选择多个 .xls / .xlsx 文件，自动匹配日期</div>
          </div>
        </div>
        <el-button>选择文件</el-button>
      </div>

      <el-table v-if="batchDialog.files.length" :data="batchDialog.files" size="small" class="file-table">
        <el-table-column prop="name" label="文件名" min-width="260">
          <template #default="{ row }">
            <div class="file-name">{{ row.name }}</div>
            <div class="muted small">大小 {{ formatSize(row.size) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="priceDate" label="识别日期" width="160">
          <template #default="{ row }">
            <span v-if="row.priceDate">{{ row.priceDate }}</span>
            <el-tag v-else type="danger" size="small">未识别</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ready'" type="success" size="small">待导入</el-tag>
            <el-tag v-else type="danger" size="small">校验失败</el-tag>
            <span v-if="row.message" class="muted small">{{ row.message }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeFile(row.uid)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="batchDialog.uploading" class="progress-block">
        <el-progress :percentage="batchDialog.progress" :stroke-width="12" :status="batchDialog.progress >= 100 ? 'success' : undefined" />
        <div class="muted small">文件较大时请耐心等待，上传与解析进度将同步更新。</div>
      </div>

      <el-alert
        v-if="batchDialog.results.length"
        :type="batchDialog.results.some(item => !item.success) ? 'warning' : 'success'"
        show-icon
        :closable="false"
        class="result-alert"
      >
        <template #title>导入结果</template>
        <div class="result-list">
          <div v-for="item in batchDialog.results" :key="item.fileName" class="result-item">
            <div class="result-main">
              <span class="file-name">{{ item.fileName }}</span>
              <el-tag :type="item.success ? 'success' : 'danger'" size="small">
                {{ item.success ? '成功' : '失败' }}
              </el-tag>
              <span class="muted small">{{ item.priceDate || '未识别日期' }}</span>
            </div>
            <div class="result-desc">
              <span v-if="item.success">导入 {{ item.successCount ?? 0 }} 条，跳过 {{ item.skippedCount ?? 0 }} 条</span>
              <span v-else>{{ item.message }}</span>
              <span class="muted small">耗时 {{ Math.round((item.durationMillis ?? 0) / 1000) }}s</span>
            </div>
            <ul v-if="item.errors?.length" class="error-list">
              <li v-for="(err, idx) in item.errors" :key="idx">{{ err }}</li>
            </ul>
          </div>
        </div>
      </el-alert>

      <template #footer>
        <div class="dialog-footer">
          <div class="muted small">
            已选 {{ batchDialog.files.length }} 个文件，总大小 {{ formatSize(batchDialog.files.reduce((sum, f) => sum + f.size, 0)) }} / 500MB；仅处理文件名格式为 <code>yyyy-MM-dd.xlsx</code> 的记录。
          </div>
          <div class="footer-actions">
            <el-button @click="batchDialog.visible = false" :disabled="batchDialog.uploading">取消</el-button>
            <el-button type="primary" :loading="batchDialog.uploading" @click="submitBatch">开始导入</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import { fetchHardwarePrices, createHardwarePrice, updateHardwarePrice, deleteHardwarePrice, importHardwarePrices } from '@/api/hardware';
import type { HardwarePrice, HardwarePriceRequest, HardwareImportResult } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
// 所有用户都可以使用全部功能
const isAdmin = computed(() => true);
const fileInput = ref<HTMLInputElement>();
const FILTER_STORAGE_KEY = 'hardware-price-filters';

const filters = reactive({
  range: [] as string[],
  itemName: ''
});
const autoSearchSuspended = ref(false);

interface ImportFileItem {
  uid: number;
  file: File;
  name: string;
  size: number;
  priceDate?: string;
  status: 'ready' | 'error';
  message?: string;
}

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
  uploading: false,
  progress: 0,
  files: [] as ImportFileItem[],
  results: [] as HardwareImportResult[]
});

const formRef = ref<FormInstance>();

const rules: FormRules = {
  priceDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  itemName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'change' }]
};

const sortedPrices = computed(() => prices.value);

// 按日期分组用于两级菜单展示
interface PriceGroup { date: string; items: HardwarePrice[] }
const groupedList = computed<PriceGroup[]>(() => {
  const map = new Map<string, HardwarePrice[]>();
  for (const p of prices.value) {
    const date = p.priceDate && /^\d{4}-\d{2}-\d{2}$/.test(p.priceDate) ? p.priceDate : '未标注日期';
    if (!map.has(date)) map.set(date, []);
    map.get(date)!.push(p);
  }
  const groups = Array.from(map.entries()).map(([date, items]) => ({
    date,
    items: items.slice().sort((a, b) => (a.itemName || '').localeCompare(b.itemName || '', 'zh-CN'))
  }));
  const isDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s);
  groups.sort((a, b) => {
    const ad = isDate(a.date), bd = isDate(b.date);
    if (ad && bd) return b.date.localeCompare(a.date);
    if (ad) return -1; // 有效日期在前
    if (bd) return 1;
    return a.date.localeCompare(b.date, 'zh-CN');
  });
  return groups;
});

const defaultRange = () => {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 6);
  return [formatDate(start), formatDate(end)];
};

const formatDate = (date: Date) => {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

const loadStoredFilters = () => {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY);
    if (!raw) return false;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed.range) && parsed.range.length === 2) {
      filters.range = parsed.range;
    }
    if (typeof parsed.itemName === 'string') {
      filters.itemName = parsed.itemName;
    }
    return true;
  } catch (error) {
    console.warn('Failed to load hardware price filters', error);
    return false;
  }
};

const persistFilters = () => {
  try {
    localStorage.setItem(
      FILTER_STORAGE_KEY,
      JSON.stringify({ range: filters.range, itemName: filters.itemName })
    );
  } catch (error) {
    console.warn('Failed to persist hardware price filters', error);
  }
};

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
  batchDialog.uploading = false;
  batchDialog.progress = 0;
  batchDialog.files = [];
  batchDialog.results = [];
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
  if (target.files && target.files.length) {
    addFiles(target.files);
    target.value = '';
  }
};

const handleDrop = (event: DragEvent) => {
  if (event.dataTransfer?.files?.length) {
    addFiles(event.dataTransfer.files);
  }
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

const parseDateFromName = (name: string) => {
  const match = name.match(/(20\d{2}-\d{2}-\d{2})/);
  return match ? match[1] : '';
};

const formatSize = (size: number) => {
  if (!size && size !== 0) return '-';
  if (size < 1024) return `${size}B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`;
  return `${(size / 1024 / 1024).toFixed(1)}MB`;
};

const addFiles = (fileList: FileList | File[]) => {
  const MAX_TOTAL_SIZE = 500 * 1024 * 1024; // 500MB
  const existingKeys = new Set(batchDialog.files.map(f => `${f.name}-${f.priceDate ?? ''}`));

  // 计算当前已选文件的总大小
  const currentTotalSize = batchDialog.files.reduce((sum, f) => sum + f.size, 0);

  // 计算新增文件的总大小
  const newFilesSize = Array.from(fileList).reduce((sum, file) => sum + file.size, 0);

  // 检查总大小是否超限
  if (currentTotalSize + newFilesSize > MAX_TOTAL_SIZE) {
    const totalSizeMB = ((currentTotalSize + newFilesSize) / 1024 / 1024).toFixed(1);
    ElMessage.error(`文件总大小不能超过 500MB，当前已选 ${totalSizeMB}MB`);
    return;
  }

  Array.from(fileList).forEach(file => {
    const priceDate = parseDateFromName(file.name);
    const key = `${file.name}-${priceDate}`;
    if (existingKeys.has(key)) {
      return;
    }
    existingKeys.add(key);
    const item: ImportFileItem = {
      uid: Date.now() + Math.random(),
      file,
      name: file.name,
      size: file.size,
      priceDate,
      status: 'ready',
      message: ''
    };
    const isExcel = /\.(xls|xlsx)$/i.test(file.name);
    if (!isExcel) {
      item.status = 'error';
      item.message = '仅支持 .xls / .xlsx 文件';
    } else if (!priceDate) {
      item.status = 'error';
      item.message = '文件名需包含日期（yyyy-MM-dd）';
    }
    batchDialog.files.push(item);
  });
  batchDialog.results = [];
};

const removeFile = (uid: number) => {
  batchDialog.files = batchDialog.files.filter(item => item.uid !== uid);
  batchDialog.results = [];
};

const submitBatch = async () => {
  if (!batchDialog.files.length) {
    ElMessage.warning('请先选择 Excel 文件');
    return;
  }
  const invalid = batchDialog.files.filter(item => item.status === 'error' || !item.priceDate);
  if (invalid.length) {
    ElMessage.error('存在未通过校验的文件，请先处理或移除');
    return;
  }
  batchDialog.uploading = true;
  batchDialog.progress = 8;
  batchDialog.results = [];
  try {
    const results = await importHardwarePrices(
      batchDialog.files.map(item => item.file),
      percent => {
        batchDialog.progress = Math.min(98, Math.max(batchDialog.progress, percent));
      }
    );
    batchDialog.results = results;
    batchDialog.progress = 100;
    const hasError = results.some(item => !item.success);
    const message = hasError ? '部分文件导入失败，请查看结果' : '导入完成';
    if (hasError) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
    loadPrices();
  } finally {
    batchDialog.uploading = false;
    setTimeout(() => {
      batchDialog.progress = 0;
    }, 600);
  }
};

const triggerAutoSearch = () => {
  if (autoSearchSuspended.value) return;
  loadPrices();
};

watch(
  () => filters.range,
  () => {
    persistFilters();
    triggerAutoSearch();
  },
  { deep: true }
);
watch(() => filters.itemName, () => {
  persistFilters();
  triggerAutoSearch();
});

const initFilters = () => {
  autoSearchSuspended.value = true;
  const restored = loadStoredFilters();
  if (!restored) {
    filters.range = defaultRange();
    filters.itemName = '';
  }
  autoSearchSuspended.value = false;
  loadPrices();
};

initFilters();
</script>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
}

.table-card {
  margin-top: 16px;
}

.menu-card {
  margin-top: 16px;
}
.menu-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.group-menu {
  border-right: none;
}
.menu-item-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.menu-item-line .name {
  max-width: 46%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.menu-item-line .right {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.menu-item-line .price {
  min-width: 88px;
  text-align: right;
}
.menu-item-line .meta {
  margin-left: 12px;
}
.menu-item-line .row-actions .el-button {
  padding: 0 4px;
}

.upload-drop {
  border: 1px dashed var(--el-border-color);
  padding: 12px 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  transition: border-color 0.2s ease;
}

.upload-drop:hover {
  border-color: var(--el-color-primary);
}

.upload-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-left .el-icon {
  font-size: 28px;
  color: var(--el-color-primary);
}

.upload-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.file-table {
  margin-top: 12px;
}

.file-name {
  font-weight: 600;
}

.muted.small {
  font-size: 12px;
}

.progress-block {
  margin-top: 12px;
}

.result-alert {
  margin-top: 16px;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
}

.result-main {
  display: flex;
  gap: 10px;
  align-items: center;
}

.result-desc {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 4px;
}

.error-list {
  margin: 6px 0 0;
  padding-left: 16px;
  color: var(--el-color-danger);
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.footer-actions {
  display: flex;
  gap: 12px;
}
</style>
