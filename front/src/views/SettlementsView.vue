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
          type="primary"
          plain
          @click="openBatchSnPriceDialog"
        >SN批量设置价格</el-button>
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
        <el-form-item label="单号">
          <el-input
            v-model="filters.trackingNumber"
            placeholder="输入单号"
            clearable
            style="width: 200px"
            @clear="handleInputClear"
          />
        </el-form-item>
        <el-form-item label="型号">
          <el-input
            v-model="filters.model"
            placeholder="输入型号"
            clearable
            style="width: 200px"
            @clear="handleInputClear"
          />
        </el-form-item>
        <el-form-item label="SN">
          <el-input
            v-model="filters.orderSn"
            placeholder="输入 SN"
            clearable
            style="width: 200px"
            @clear="handleInputClear"
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
        :data="sortedRecords"
        v-loading="loading"
        height="520"
        :default-sort="defaultSort"
        highlight-current-row
        :current-row="currentRow"
        :row-class-name="rowClassName"
        @selection-change="handleSelection"
        @row-click="handleRowClick"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column
          prop="orderTime"
          label="下单时间"
          width="180"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        >
          <template #default="{ row }">{{ formatDate(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column
          prop="trackingNumber"
          label="单号"
          width="160"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        />
        <el-table-column prop="model" label="型号" width="160">
          <template #default="{ row }">{{ row.model || '-' }}</template>
        </el-table-column>
        <el-table-column prop="orderSn" label="SN" width="180">
          <template #default="{ row }">{{ row.orderSn || '-' }}</template>
        </el-table-column>
        <el-table-column
          prop="amount"
          label="结账金额"
          width="180"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        >
          <template #default="{ row }">￥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="ownerUsername" label="归属用户" width="140">
          <template #default="{ row }">{{ row.ownerUsername || '-' }}</template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="状态"
          width="120"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        >
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'warning'">
              {{ row.status === 'CONFIRMED' ? '已确认' : '待结账' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="orderStatus"
          label="订单状态"
          width="140"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        >
          <template #default="{ row }">
            <el-tag v-if="row.orderStatus" :type="row.orderStatus === 'PAID' ? 'success' : 'info'">
              {{ statusLabel(row.orderStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="payableAt" label="应付日期" width="140" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="120">
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
        :page-sizes="[20, 50, 100, 200]"
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="confirmDialog.visible" title="确认结账" width="480px">
      <el-form label-width="100px">
        <el-form-item label="金额">
          <div class="amount-text">￥{{ formatAmount(confirmDialog.form.amount) }}</div>
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
        <el-form-item label="备注">
          <el-input v-model="batchConfirmDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchConfirmDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchConfirmDialog.loading" @click="submitBatchConfirm">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchSnPriceDialog.visible" title="SN批量设置价格" width="520px">
      <el-form label-width="100px">
        <el-form-item label="SN列表">
          <el-input
            v-model="batchSnPriceDialog.form.snInput"
            type="textarea"
            :rows="6"
            placeholder="支持多个SN，使用换行/逗号/分号分隔"
          />
          <div style="margin-top: 8px; font-size: 12px; color: #909399;">
            已识别 {{ batchSnPriceDialog.parsedSns.length }} 个SN
            <span v-if="batchSnPriceDialog.duplicateSns.length > 0" style="color: #f56c6c;">
              (发现 {{ batchSnPriceDialog.duplicateSns.length }} 个重复)
            </span>
          </div>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="batchSnPriceDialog.form.amount" :min="0" :step="10" />
        </el-form-item>
        <el-alert
          v-if="batchSnPriceDialog.duplicateSns.length > 0"
          type="warning"
          :closable="false"
          style="margin-bottom: 12px;"
        >
          <template #title>
            <div>检测到重复SN，将跳过以下SN：</div>
            <div style="margin-top: 8px; max-height: 100px; overflow-y: auto;">
              {{ batchSnPriceDialog.duplicateSns.join('、') }}
            </div>
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="batchSnPriceDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="batchSnPriceDialog.loading"
          @click="submitBatchSnPrice"
          :disabled="batchSnPriceDialog.parsedSns.length === 0"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { TableInstance } from 'element-plus';
import {
  fetchSettlements,
  confirmSettlement,
  deleteSettlements,
  exportSettlements,
  updateSettlementPriceByModel,
  confirmSettlementsBatch,
  updateSettlementAmount,
  updateSettlementPriceBySn
} from '@/api/settlements';
import type {
  SettlementRecord,
  SettlementFilterRequest,
  SettlementConfirmRequest,
  SettlementExportRequest,
  SettlementBatchPriceRequest,
  SettlementBatchConfirmRequest,
  SettlementBatchSnPriceRequest
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
  ownerUsername: '',
  trackingNumber: '',
  model: '',
  orderSn: '',
  dateRange: [] as string[],
  page: 1,
  size: Number.isNaN(savedPageSize) ? 50 : savedPageSize,
  sortProp: 'orderTime',
  sortOrder: 'ascending' as SortOrder
});

const records = ref<SettlementRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);
const tableRef = ref<TableInstance>();
const currentRow = ref<SettlementRecord | null>(null);
const defaultSort = { prop: 'orderTime', order: 'ascending' as const };
type SortOrder = 'ascending' | 'descending' | null;
const sortState = reactive<{ prop: string; order: SortOrder }>({
  prop: defaultSort.prop,
  order: defaultSort.order
});

const selectedIds = ref<number[]>([]);
const selectedRecords = ref<SettlementRecord[]>([]);

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

const batchSnPriceDialog = reactive({
  visible: false,
  loading: false,
  form: { snInput: '', amount: null as number | null },
  parsedSns: [] as string[],
  duplicateSns: [] as string[]
});

const params = computed<SettlementFilterRequest>(() => {
  const result: SettlementFilterRequest = {
    page: filters.page,
    size: filters.size,
    status: filters.status || undefined,
    ownerUsername: filters.ownerUsername?.trim() || undefined,
    trackingNumber: filters.trackingNumber?.trim() || undefined,
    model: filters.model?.trim() || undefined,
    orderSn: filters.orderSn?.trim() || undefined,
    sortProp: filters.sortProp || undefined,
    sortOrder: filters.sortOrder || undefined
  };
  if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
    result.startDate = filters.dateRange[0];
    result.endDate = filters.dateRange[1];
  }
  return result;
});

// getSortValue 和 getTimeValue 已移除，改为服务端排序

const sortedRecords = computed(() => {
  // 移除客户端过滤和排序，所有逻辑由服务端完成
  return records.value;
});

const sanitizeIdentifier = (value: string) => value ? value.replace(/\s+/g, '') : '';

// hasFilters、hasSearch 和 matchRow 已移除，改为服务端过滤

const loadData = async () => {
  loading.value = true;
  try {
    filters.trackingNumber = sanitizeIdentifier(filters.trackingNumber);
    filters.orderSn = sanitizeIdentifier(filters.orderSn);

    console.log('查询参数:', params.value);

    // 后端已支持所有筛选条件，直接使用分页查询即可
    const data: any = await fetchSettlements(params.value);

    console.log('后端返回数据:', data);
    console.log('记录数量:', data?.records?.length, '总数:', data?.total);

    records.value = data.records || [];
    total.value = data.total || 0;

    console.log('更新后的 records.value:', records.value.length);

    refreshFilterOptions();
    focusFirstMatch();
  } finally {
    loading.value = false;
  }
};

// triggerAutoSearch 已移除，改为手动点击查询按钮触发

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
  // 先清空数据，减少渲染压力
  records.value = [];
  total.value = 0;

  // 清空筛选条件
  filters.status = '';
  filters.ownerUsername = '';
  filters.trackingNumber = '';
  filters.model = '';
  filters.orderSn = '';
  filters.dateRange = [];
  filters.page = 1;

  // 重置后立即加载数据
  loadData();
};

const handleInputClear = () => {
  resetFilters();
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

// 移除自动搜索的 watch，改为手动点击查询按钮触发

const handleSelection = (rows: SettlementRecord[]) => {
  selectedRecords.value = rows;
  selectedIds.value = rows.map(row => row.id);
};

const focusFirstMatch = () => {
  // 服务端已经过滤数据，不需要客户端再次匹配
  // 简单选中第一行
  if (sortedRecords.value.length > 0) {
    const target = sortedRecords.value[0];
    currentRow.value = target;
    nextTick(() => {
      tableRef.value?.setCurrentRow(target);
    });
  } else {
    currentRow.value = null;
  }
};

const rowClassName = () => {
  // 移除客户端高亮逻辑，所有数据都是服务端过滤后的结果
  return '';
};

const handleSortChange = (options: { prop: string; order: SortOrder }) => {
  sortState.prop = options.prop ?? '';
  sortState.order = options.order ?? null;
  filters.sortProp = sortState.prop;
  filters.sortOrder = sortState.order;
  filters.page = 1;
  loadData();
};

const handleRowClick = (row: SettlementRecord) => {
  const alreadySelected = selectedIds.value.includes(row.id);
  tableRef.value?.toggleRowSelection(row, !alreadySelected);
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
      const payload = {
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

const openBatchSnPriceDialog = () => {
  if (!isAdmin.value) return;
  batchSnPriceDialog.form.snInput = '';
  batchSnPriceDialog.form.amount = null;
  batchSnPriceDialog.parsedSns = [];
  batchSnPriceDialog.duplicateSns = [];
  batchSnPriceDialog.visible = true;
};

const parseSns = (input: string): string[] => {
  return input
    .split(/[\n,;]/)
    .map(sn => sn.trim())
    .filter(sn => sn.length > 0);
};

const findDuplicateSns = (sns: string[]): string[] => {
  const snSet = new Set<string>();
  const duplicates = new Set<string>();

  sns.forEach(sn => {
    const normalizedSn = sn.toUpperCase();
    if (snSet.has(normalizedSn)) {
      duplicates.add(sn);
    } else {
      snSet.add(normalizedSn);
    }
  });

  return Array.from(duplicates);
};

const submitBatchSnPrice = async () => {
  if (!isAdmin.value) return;

  const sns = parseSns(batchSnPriceDialog.form.snInput);
  const duplicates = findDuplicateSns(sns);

  // 更新显示的解析结果和重复项
  batchSnPriceDialog.parsedSns = sns;
  batchSnPriceDialog.duplicateSns = duplicates;

  if (sns.length === 0) {
    ElMessage.warning('请输入至少一个SN');
    return;
  }

  if (batchSnPriceDialog.form.amount === null || batchSnPriceDialog.form.amount === undefined) {
    ElMessage.warning('请输入金额');
    return;
  }

  // 过滤掉重复的SN
  const uniqueSns = sns.filter(sn => !duplicates.includes(sn));

  if (uniqueSns.length === 0) {
    ElMessage.warning('所有SN都重复，无法设置价格');
    return;
  }

  batchSnPriceDialog.loading = true;
  try {
    const payload: SettlementBatchSnPriceRequest = {
      sns: uniqueSns,
      amount: batchSnPriceDialog.form.amount
    };

    const result = await updateSettlementPriceBySn(payload);
    const updatedCount = result?.updatedCount ?? 0;
    const skippedSns = result?.skippedSns ?? [];

    // 构建提示消息
    let message = `已更新 ${updatedCount} 个SN的价格`;

    if (duplicates.length > 0) {
      message += `，跳过 ${duplicates.length} 个重复输入的SN`;
    }

    if (skippedSns && skippedSns.length > 0) {
      message += `，跳过 ${skippedSns.length} 个已有价格的SN`;
      ElMessage.warning({
        message: `${message}\n已有价格的SN: ${skippedSns.join('、')}`,
        duration: 5000,
        showClose: true
      });
    } else {
      ElMessage.success(message);
    }

    batchSnPriceDialog.visible = false;
    loadData();
  } finally {
    batchSnPriceDialog.loading = false;
  }
};

// 监听输入变化，实时解析SN和检测重复
watch(() => batchSnPriceDialog.form.snInput, (input) => {
  const sns = parseSns(input);
  const duplicates = findDuplicateSns(sns);
  batchSnPriceDialog.parsedSns = sns;
  batchSnPriceDialog.duplicateSns = duplicates;
});

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

// fetchAllSettlements 已移除，改为后端直接过滤返回结果

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

.amount-text {
  font-weight: 600;
}

.highlight-row {
  background: #f0f9eb !important;
}
</style>
