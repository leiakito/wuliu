<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>结账管理</h2>
        <p class="sub">生成、确认与导出待结账记录</p>
      </div>
      <div class="actions">
        <el-button
          v-if="isAdmin"
          type="primary"
          plain
          @click="openBatchPriceDialog"
        >批量设置价格</el-button>
        <el-button
          v-if="isAdmin"
          type="success"
          plain
          :disabled="!selectedIds.length"
          @click="openBatchConfirmDialog"
        >批量确认</el-button>
        <el-button @click="exportData" :loading="exporting">导出 Excel</el-button>
        <el-button
          v-if="isAdmin"
          type="danger"
          plain
          :disabled="!selectedIds.length"
          @click="handleDelete"
        >
          删除所选
        </el-button>
      </div>
    </div>

    <el-card>
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 160px">
            <el-option label="待结账" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="filters.batch" placeholder="例如 BATCH-20250118" clearable />
        </el-form-item>
        <el-form-item label="复合搜索">
          <el-input
            v-model="filters.keyword"
            placeholder="单号 / 型号 / SN"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item label="归属用户">
          <el-select
            v-model="filters.ownerUsername"
            placeholder="全部"
            clearable
            filterable
            allow-create
            default-first-option
            style="width: 160px"
          >
            <el-option v-for="user in submissionUserOptions" :key="user" :label="user" :value="user" />
          </el-select>
        </el-form-item>
        <!-- <el-form-item label="型号">
          <el-input
            v-model="filters.model"
            placeholder="输入型号关键字"
            clearable
            style="width: 200px"
          />
        </el-form-item> -->
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table
        ref="tableRef"
        :data="records"
        v-loading="loading"
        height="520"
        @selection-change="handleSelection"
        @row-click="handleRowClick"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="trackingNumber" label="单号" width="160" />
        <el-table-column prop="model" label="型号" width="160">
          <template #default="{ row }">{{ row.model || '-' }}</template>
        </el-table-column>
        <el-table-column prop="orderSn" label="SN" width="180">
          <template #default="{ row }">{{ row.orderSn || '-' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="结账金额" width="180">
          <template #default="{ row }">
            <template v-if="isAdmin">
              <el-input-number
                v-model="row.amount"
                :min="0"
                :step="10"
                size="small"
                :controls="false"
                :disabled="amountUpdating[row.id]"
                @change="value => handleAmountInline(row, value)"
              />
            </template>
            <template v-else>￥{{ formatAmount(row.amount) }}</template>
          </template>
        </el-table-column>
        <el-table-column prop="ownerUsername" label="归属用户" width="140">
          <template #default="{ row }">{{ row.ownerUsername || '-' }}</template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="180">
          <template #default="{ row }">{{ formatDate(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="orderAmount" label="订单金额" width="140">
          <template #default="{ row }">￥{{ formatAmount(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'warning'">
              {{ row.status === 'CONFIRMED' ? '已确认' : '待结账' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderStatus" label="订单状态" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.orderStatus" :type="row.orderStatus === 'PAID' ? 'success' : 'info'">
              {{ statusLabel(row.orderStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="settleBatch" label="批次" width="180" />
        <el-table-column prop="payableAt" label="应付日期" width="140" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button
              v-if="isAdmin"
              link
              type="primary"
              @click="openAmountDialog(row)">
              修改金额
            </el-button>
            <el-button
              v-if="isAdmin && row.status !== 'CONFIRMED'"
              link
              type="primary"
              @click="openConfirm(row)">
              确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100, 200]"
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="confirmDialog.visible" title="确认结账" width="480px">
      <el-form label-width="100px">
        <el-form-item label="金额">
          <el-input-number v-model="confirmDialog.form.amount" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="confirmDialog.loading" @click="submitConfirm">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchPriceDialog.visible" title="批量设置价格" width="420px">
      <el-form label-width="100px">
        <el-form-item label="作用范围">
          <el-radio-group v-model="batchPriceDialog.form.scope">
            <el-radio-button label="MODEL">按型号筛选</el-radio-button>
            <el-radio-button label="SELECTION" :disabled="!selectedIds.length">已选择记录</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="batchPriceDialog.form.scope === 'MODEL'">
        <el-form-item label="型号">
          <el-select
            v-model="batchPriceDialog.form.model"
            placeholder="请选择型号"
            filterable
            allow-create
          >
            <el-option v-for="model in modelOptions" :key="model" :label="model" :value="model" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态范围">
          <el-select v-model="batchPriceDialog.form.status" placeholder="全部" clearable style="width: 200px">
            <el-option label="待结账" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
          </el-select>
        </el-form-item>
        </template>
        <el-form-item label="金额">
          <el-input-number v-model="batchPriceDialog.form.amount" :min="0" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchPriceDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchPriceDialog.loading" @click="submitBatchPrice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchConfirmDialog.visible" title="批量确认" width="420px">
      <el-form label-width="100px">
        <el-form-item label="金额">
          <el-input-number v-model="batchConfirmDialog.form.amount" :min="0" :step="10" placeholder="保留原金额" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchConfirmDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchConfirmDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchConfirmDialog.loading" @click="submitBatchConfirm">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="amountDialog.visible" title="修改金额" width="420px">
      <el-form label-width="100px">
        <el-form-item label="金额">
          <el-input-number v-model="amountDialog.form.amount" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="amountDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="amountDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="amountDialog.loading" @click="submitAmount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { TableInstance } from 'element-plus';
import {
  fetchSettlements,
  confirmSettlement,
  deleteSettlements,
  exportSettlements,
  updateSettlementPriceByModel,
  confirmSettlementsBatch,
  updateSettlementAmount
} from '@/api/settlements';
import type {
  SettlementRecord,
  SettlementFilterRequest,
  SettlementConfirmRequest,
  SettlementExportRequest,
  SettlementBatchPriceRequest,
  SettlementBatchConfirmRequest,
  SettlementAmountRequest
} from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');
const settlementStatusMap: Record<string, string> = {
  PENDING: '待结账',
  CONFIRMED: '已确认',
  PAID: '已打款',
  UNPAID: '未打款',
  NOT_RECEIVED: '未收货'
};

const PAGE_SIZE_KEY = 'settlements-page-size';
const savedPageSize = Number(localStorage.getItem(PAGE_SIZE_KEY)) || 50;

const filters = reactive({
  status: '',
  batch: '',
  ownerUsername: '',
  keyword: '',
  dateRange: [] as string[],
  page: 1,
  size: Number.isNaN(savedPageSize) ? 50 : savedPageSize
});

const records = ref<SettlementRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);
const tableRef = ref<TableInstance>();

const selectedIds = ref<number[]>([]);
const selectedRecords = ref<SettlementRecord[]>([]);
const autoSearchSuspended = ref(false);
const amountUpdating = reactive<Record<number, boolean>>({});

const submissionUserOptions = ref<string[]>([]);
const modelOptions = ref<string[]>([]);


const confirmDialog = reactive({
  visible: false,
  loading: false,
  targetId: 0,
  form: { amount: 0, remark: '' }
});

const batchPriceDialog = reactive({
  visible: false,
  loading: false,
  form: { model: '', amount: null as number | null, status: '', scope: 'MODEL' as 'MODEL' | 'SELECTION' }
});

const batchConfirmDialog = reactive({
  visible: false,
  loading: false,
  form: { amount: null as number | null, remark: '' }
});

const amountDialog = reactive({
  visible: false,
  loading: false,
  targetId: 0,
  form: { amount: 0, remark: '' }
});

const params = computed<SettlementFilterRequest>(() => {
  const result: SettlementFilterRequest = {
    page: filters.page,
    size: filters.size,
    status: filters.status || undefined,
    batch: filters.batch || undefined,
    ownerUsername: filters.ownerUsername?.trim() || undefined,
    keyword: filters.keyword?.trim() || undefined
  };
  if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
    result.startDate = filters.dateRange[0];
    result.endDate = filters.dateRange[1];
  }
  return result;
});

const loadData = async () => {
  loading.value = true;
  try {
    const data = await fetchSettlements(params.value);
    records.value = data.records;
    total.value = data.total;
    refreshFilterOptions();
  } finally {
    loading.value = false;
  }
};

const triggerAutoSearch = () => {
  if (autoSearchSuspended.value) {
    return;
  }
  filters.page = 1;
  loadData();
};

const refreshFilterOptions = () => {
  const submissionSet = new Set<string>();
  const modelSet = new Set<string>();
  records.value.forEach(record => {
    if (record.model) {
      modelSet.add(record.model);
    }
    if (record.ownerUsername) {
      submissionSet.add(record.ownerUsername);
    }
  });
  submissionUserOptions.value = Array.from(submissionSet).sort((a, b) => a.localeCompare(b));
  modelOptions.value = Array.from(modelSet).sort((a, b) => a.localeCompare(b));
};

const resetFilters = () => {
  autoSearchSuspended.value = true;
  filters.status = '';
  filters.batch = '';
  filters.ownerUsername = '';
  filters.keyword = '';
  filters.dateRange = [];
  filters.page = 1;
  autoSearchSuspended.value = false;
  loadData();
};

const handleSizeChange = (size: number) => {
  filters.size = size;
  localStorage.setItem(PAGE_SIZE_KEY, String(size));
  filters.page = 1;
  loadData();
};

const handlePageChange = (page: number) => {
  filters.page = page;
  loadData();
};

watch(() => filters.status, triggerAutoSearch);
watch(() => filters.batch, triggerAutoSearch);
watch(() => filters.ownerUsername, triggerAutoSearch);
watch(() => filters.keyword, triggerAutoSearch);
watch(() => filters.dateRange, triggerAutoSearch, { deep: true });

const handleSelection = (rows: SettlementRecord[]) => {
  selectedRecords.value = rows;
  selectedIds.value = rows.map(row => row.id);
};

const handleRowClick = (row: SettlementRecord) => {
  const alreadySelected = selectedIds.value.includes(row.id);
  tableRef.value?.toggleRowSelection(row, !alreadySelected);
};

const handleAmountInline = async (row: SettlementRecord, value?: number) => {
  if (!isAdmin.value) return;
  if (value === undefined || value === null) return;
  amountUpdating[row.id] = true;
  try {
    await updateSettlementAmount(row.id, { amount: value, remark: row.remark });
    row.amount = value;
    row.orderAmount = value;
    ElMessage.success('金额已更新');
  } finally {
    amountUpdating[row.id] = false;
  }
};

const openBatchPriceDialog = () => {
  if (!isAdmin.value) return;
  batchPriceDialog.form.scope = selectedIds.value.length ? 'SELECTION' : 'MODEL';
  batchPriceDialog.form.model = modelOptions.value[0] ?? '';
  batchPriceDialog.form.amount = null;
  batchPriceDialog.form.status = filters.status || '';
  batchPriceDialog.visible = true;
};

const openBatchConfirmDialog = () => {
  if (!isAdmin.value) return;
  if (!selectedIds.value.length) {
    ElMessage.info('请先选择结算记录');
    return;
  }
  batchConfirmDialog.form.amount = null;
  batchConfirmDialog.form.remark = '';
  batchConfirmDialog.visible = true;
};

const openConfirm = (row: SettlementRecord) => {
  confirmDialog.visible = true;
  confirmDialog.targetId = row.id;
  confirmDialog.form.amount = row.amount ?? row.orderAmount ?? 0;
  confirmDialog.form.remark = row.remark ?? '';
};

const openAmountDialog = (row: SettlementRecord) => {
  if (!isAdmin.value) return;
  amountDialog.targetId = row.id;
  amountDialog.form.amount = row.amount ?? row.orderAmount ?? 0;
  amountDialog.form.remark = row.remark ?? '';
  amountDialog.visible = true;
};

const submitConfirm = async () => {
  if (!confirmDialog.targetId) return;
  confirmDialog.loading = true;
  try {
    const payload: SettlementConfirmRequest = {
      amount: confirmDialog.form.amount,
      remark: confirmDialog.form.remark
    };
    await confirmSettlement(confirmDialog.targetId, payload);
    ElMessage.success('已确认');
    confirmDialog.visible = false;
    loadData();
  } finally {
    confirmDialog.loading = false;
  }
};

const handleDelete = async () => {
  if (!isAdmin.value) return;
  if (!selectedIds.value.length) {
    ElMessage.info('请选择记录');
    return;
  }
  await ElMessageBox.confirm('确认删除选中的结算记录吗？', '提示', { type: 'warning' });
  await deleteSettlements(selectedIds.value);
  ElMessage.success('已删除');
  selectedIds.value = [];
  loadData();
};

const submitBatchPrice = async () => {
  if (!isAdmin.value) return;
  if (batchPriceDialog.form.amount === null || batchPriceDialog.form.amount === undefined) {
    ElMessage.warning('请输入金额');
    return;
  }
  if (batchPriceDialog.form.scope === 'MODEL') {
    if (!batchPriceDialog.form.model || !batchPriceDialog.form.model.trim()) {
      ElMessage.warning('请选择型号');
      return;
    }
    batchPriceDialog.loading = true;
    try {
    const payload: SettlementBatchPriceRequest = {
      model: batchPriceDialog.form.model.trim(),
      amount: batchPriceDialog.form.amount,
      status: batchPriceDialog.form.status?.trim() || undefined,
      batch: filters.batch || undefined,
      ownerUsername: filters.ownerUsername?.trim() || undefined
    };
      if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
        payload.startDate = filters.dateRange[0];
        payload.endDate = filters.dateRange[1];
      }
      await updateSettlementPriceByModel(payload);
      ElMessage.success('批量价格已更新');
      batchPriceDialog.visible = false;
      loadData();
    } finally {
      batchPriceDialog.loading = false;
    }
  } else {
    if (!selectedIds.value.length) {
      ElMessage.warning('请选择结算记录');
      return;
    }
    batchPriceDialog.loading = true;
    try {
      const payload: SettlementAmountRequest = {
        amount: batchPriceDialog.form.amount,
        remark: undefined
      };
      await Promise.all(selectedIds.value.map(id => updateSettlementAmount(id, payload)));
      ElMessage.success('已更新所选记录金额');
      batchPriceDialog.visible = false;
      loadData();
    } finally {
      batchPriceDialog.loading = false;
    }
  }
};

const submitBatchConfirm = async () => {
  if (!isAdmin.value) return;
  if (!selectedIds.value.length) {
    ElMessage.info('请先选择结算记录');
    return;
  }
  batchConfirmDialog.loading = true;
  try {
    const payload: SettlementBatchConfirmRequest = {
      ids: [...selectedIds.value],
      amount: batchConfirmDialog.form.amount ?? undefined,
      remark: batchConfirmDialog.form.remark || undefined
    };
    await confirmSettlementsBatch(payload);
    ElMessage.success('批量确认成功');
    batchConfirmDialog.visible = false;
    selectedIds.value = [];
    selectedRecords.value = [];
    loadData();
  } finally {
    batchConfirmDialog.loading = false;
  }
};

const submitAmount = async () => {
  if (!isAdmin.value) return;
  if (!amountDialog.targetId) return;
  amountDialog.loading = true;
  try {
    const payload: SettlementAmountRequest = {
      amount: amountDialog.form.amount,
      remark: amountDialog.form.remark || undefined
    };
    await updateSettlementAmount(amountDialog.targetId, payload);
    ElMessage.success('金额已更新');
    amountDialog.visible = false;
    loadData();
  } finally {
    amountDialog.loading = false;
  }
};

const downloadExcel = async (params: SettlementExportRequest, fileName = 'settlements.xlsx') => {
  const response = await exportSettlements(params);
  const blob = new Blob([response.data], { type: 'application/octet-stream' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  window.URL.revokeObjectURL(url);
};

const exportData = async () => {
  exporting.value = true;
  try {
    const exportParams: SettlementExportRequest = {
      status: filters.status || undefined,
      batch: filters.batch || undefined,
      ownerUsername: filters.ownerUsername?.trim() || undefined
    };
    if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
      exportParams.startDate = filters.dateRange[0];
      exportParams.endDate = filters.dateRange[1];
    }
    await downloadExcel(exportParams);
  } finally {
    exporting.value = false;
  }
};

const formatDate = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ').replace('Z', '');
};

const formatAmount = (value?: number) => {
  if (!value) return '0.00';
  return value.toFixed(2);
};

const statusLabel = (value?: string) => settlementStatusMap[value ?? ''] || '未知';

loadData();
</script>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
}

.filter-form {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
