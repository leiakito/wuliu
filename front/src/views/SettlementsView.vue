<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>结账管理</h2>
        <p class="sub">生成、确认与导出待结账记录</p>
      </div>
      <div class="actions">
        <el-button @click="exportData" :loading="exporting">导出 Excel</el-button>
        <el-button type="primary" @click="openGenerateDialog">生成待结账</el-button>
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
        <el-form-item label="日期">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="records" v-loading="loading" height="520" @selection-change="handleSelection">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="trackingNumber" label="单号" width="160" />
        <el-table-column prop="amount" label="金额" width="120">
          <template #default="{ row }">{{ row.amount ?? '-' }} {{ row.currency }}</template>
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
        :page-sizes="[10, 20, 50]"
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="generateDialog" title="生成待结账" width="600px">
      <el-form label-width="120px">
        <el-form-item label="单号列表">
          <el-input
            v-model="generateNumbers"
            type="textarea"
            :rows="4"
            placeholder="每行一个单号"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generateLoading" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>

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
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  fetchSettlements,
  generateSettlement,
  confirmSettlement,
  deleteSettlements,
  exportSettlements
} from '@/api/settlements';
import type {
  SettlementRecord,
  SettlementFilterRequest,
  SettlementConfirmRequest,
  SettlementExportRequest
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

const filters = reactive({
  status: '',
  batch: '',
  dateRange: [] as string[],
  page: 1,
  size: 20
});

const records = ref<SettlementRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);

const selectedIds = ref<number[]>([]);
const selectedRecords = ref<SettlementRecord[]>([]);

const generateDialog = ref(false);
const generateNumbers = ref('');
const generateLoading = ref(false);

const confirmDialog = reactive({
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
    batch: filters.batch || undefined
  };
  if (filters.dateRange.length === 2) {
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
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.status = '';
  filters.batch = '';
  filters.dateRange = [];
  filters.page = 1;
  loadData();
};

const handleSizeChange = (size: number) => {
  filters.size = size;
  filters.page = 1;
  loadData();
};

const handlePageChange = (page: number) => {
  filters.page = page;
  loadData();
};

const handleSelection = (rows: SettlementRecord[]) => {
  selectedRecords.value = rows;
  selectedIds.value = rows.map(row => row.id);
};

const openGenerateDialog = () => {
  const numbers = (selectedRecords.value.length ? selectedRecords.value : records.value.filter(item => item.status === 'PENDING'))
    .map(item => item.trackingNumber)
    .filter(Boolean);
  generateNumbers.value = numbers.join('\n');
  if (!numbers.length) {
    ElMessage.info('暂无可用的待结账单号，请先选择或筛选记录');
  }
  generateDialog.value = true;
};

const handleGenerate = async () => {
  const tracking = generateNumbers.value
    .split(/\n|,|;/)
    .map(item => item.trim())
    .filter(Boolean);
  if (!tracking.length) {
    ElMessage.warning('请输入单号');
    return;
  }
  generateLoading.value = true;
  try {
    await generateSettlement({ trackingNumbers: tracking });
    ElMessage.success('生成成功，正在写入 Excel');
    generateDialog.value = false;
    generateNumbers.value = '';
    loadData();
    await downloadExcel({ trackingNumbers: tracking }, 'pending-settlements.xlsx');
  } finally {
    generateLoading.value = false;
  }
};

const openConfirm = (row: SettlementRecord) => {
  confirmDialog.visible = true;
  confirmDialog.targetId = row.id;
  confirmDialog.form.amount = row.amount ?? 0;
  confirmDialog.form.remark = row.remark ?? '';
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
      batch: filters.batch || undefined
    };
    if (filters.dateRange.length === 2) {
      exportParams.startDate = filters.dateRange[0];
      exportParams.endDate = filters.dateRange[1];
    }
    await downloadExcel(exportParams);
  } finally {
    exporting.value = false;
  }
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
