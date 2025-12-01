<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>物流单号</h2>
        <p class="sub">管理员可录入与维护，普通用户仅可查询并跟踪状态</p>
      </div>
      <div v-if="isAdmin" class="actions">
        <input ref="fileInput" type="file" accept=".xls,.xlsx" hidden @change="handleFileChange" />
        <el-button @click="triggerImport">批量导入</el-button>
        <el-button type="primary" @click="openCreateDrawer">新增单号</el-button>
      </div>
    </div>

    <el-card v-if="isAdmin">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="日期">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属用户" v-if="isAdmin">
          <el-select v-model="filters.ownerUsername" filterable clearable placeholder="全部" style="width: 200px" :loading="userLoading">
            <el-option
              v-for="user in userOptions"
              :key="user.username"
              :label="user.fullName ? `${user.fullName}（${user.username}）` : user.username"
              :value="user.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
            <el-input
            v-model="filters.keyword"
            placeholder="单号/SN/型号"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-else class="user-search-card">
      <template #header>
        <div class="settle-bar">
          <span>订单状态查询</span>
          <small class="muted">输入单号或 SN 即可查询是否结账/录入</small>
        </div>
      </template>
      <el-input
        v-model="userSearchInput"
        type="textarea"
        :rows="4"
        placeholder="支持多个单号或 SN，使用换行/逗号/分号分隔"
        @input="handleUserSearchInput"
      />
      <div class="user-search-actions">
        <el-button type="primary" :loading="userSearchLoading" @click="handleUserSearch">查询状态</el-button>
        <el-button text :disabled="!userOrders.length" @click="clearUserResults">清空记录</el-button>
        <el-button text :disabled="!userOrders.length" @click="exportUserOrders">导出 Excel</el-button>
      </div>
    </el-card>

    <div class="quick-tools" :class="{ 'has-filter': filters.status }">
      <div class="quick-filter-row">
        <span class="label">快速筛选：</span>
        <el-check-tag :checked="quickStatus === ''" @click="setStatusFilter('')">全部</el-check-tag>
        <el-check-tag
          v-for="item in statusOptions"
          :key="item.value"
          :checked="quickStatus === item.value"
          @click="setStatusFilter(item.value)"
        >
          {{ item.label }}
        </el-check-tag>
        <span v-if="filters.status" class="filter-hint">
          <i class="el-icon-warning"></i>
          <strong>正在筛选: {{ statusLabel(filters.status) }}</strong>
          <el-button link type="primary" size="small" @click="clearStatusFilter">清除筛选</el-button>
        </span>
      </div>


    </div>


    <el-card class="table-card">
      <el-table
        :data="filteredTableData"
        v-loading="tableLoading"
        style="width: 100%"
        :default-sort="{ prop: sortState.prop, order: sortState.order || undefined }"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="orderDate" label="下单日期" width="110" />
        <el-table-column prop="orderTime" label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="运单号" width="160">
          <template #default="{ row }">
            <span :style="styleFor(row, 'tracking')">{{ row.trackingNumber }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" width="160">
          <template #default="{ row }">
            <span :style="styleFor(row, 'model')">{{ row.model }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sn" label="SN" width="200">
          <template #default="{ row }">
            <span class="sn-text" :style="styleFor(row, 'sn')">{{ row.sn }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">
            <span :style="styleFor(row, 'amount')">
              <template v-if="row.amount !== null && row.amount !== undefined">￥{{ formatAmount(row.amount) }}</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerUsername" label="归属用户" width="100" />
        <el-table-column 
          prop="status"
          width="100"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
        >
          <template #header>
            <span class="status-header">
              状态
              <el-tooltip
                effect="dark"
                content="点击箭头循环筛选：全部 → 未打款 → 已打款"
                placement="top"
              >
                <i class="el-icon-info-filled" style="margin-left: 4px; color: #909399; font-size: 14px;"></i>
              </el-tooltip>
              <span v-if="filters.status" class="status-filter-badge">
                {{ statusLabel(filters.status) }}
              </span>
            </span>
          </template>
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column >
        <el-table-column v-if="isAdmin" label="导入状态" width="140">
          <template #default="{ row }">
            <span v-if="row.imported" class="status-text">已录入系统</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注">
          <template #default="{ row }">
            <span :style="styleFor(row, 'remark')">{{ row.remark }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createdBy" width="120" />
        <el-table-column v-if="isAdmin" label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="isAdmin"
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        :page-sizes="[20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        background
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-drawer v-if="isAdmin" v-model="createVisible" title="新增物流单" size="30%" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="日期" prop="orderDate">
          <el-date-picker v-model="createForm.orderDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="单号" prop="trackingNumber">
          <el-input v-model="createForm.trackingNumber" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="createForm.model" />
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="createForm.sn" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-if="isAdmin" v-model="editDialog.visible" title="编辑物流单号" width="520px">
      <el-form label-width="90px">
        <el-form-item label="运单号">
          <el-input v-model="editDialog.form.trackingNumber" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="editDialog.form.model" />
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="editDialog.form.sn" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editDialog.form.status" placeholder="请选择">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.loading" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importProgress.visible"
      title="批量导入中"
      width="360px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      align-center
    >
      <p class="muted" style="margin-bottom: 12px">正在上传并解析文件，请稍候…</p>
      <el-progress :percentage="importProgress.percent" :stroke-width="12" status="success" />
    </el-dialog>





    <div class="float-button-group">
      <el-button type="primary" circle class="main-float-btn" @click="scrollToTop">
        <el-icon><ArrowUp /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script lang="ts">
// 使用普通 script 块定义组件名，确保 keep-alive 的 include 能正确匹配
export default {
  name: 'OrdersView'
}
</script>

<script setup lang="ts">
import { computed, reactive, ref, watch, onBeforeUnmount, onMounted, onActivated, onDeactivated } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { ArrowUp } from '@element-plus/icons-vue';
import { fetchOrders, createOrder, importOrders, updateOrderStatus, searchOrders, fetchCategoryStats, updateOrder } from '@/api/orders';
import { listUsers } from '@/api/users';
import { listOwnerUsernames } from '@/api/submissions';
import type { OrderCategoryStats, OrderCreateRequest, OrderRecord, OrderUpdateRequest, SysUser } from '@/types/models';

type ImportStyle = {
  trackingNumber?: string;
  sn?: string;
  // 运单号列格式
  trackingBgColor?: string;
  trackingFontColor?: string;
  trackingStrike?: boolean;
  // 型号列格式
  modelBgColor?: string;
  modelFontColor?: string;
  modelStrike?: boolean;
  // SN列格式
  snBgColor?: string;
  snFontColor?: string;
  snStrike?: boolean;
  // 金额列格式
  amountBgColor?: string;
  amountFontColor?: string;
  amountStrike?: boolean;
  // 备注列格式
  remarkBgColor?: string;
  remarkFontColor?: string;
  remarkStrike?: boolean;
};
import { useAuthStore } from '@/store/auth';

interface FilterModel {
  dateRange: string[];
  status: string;
  keyword: string;
  ownerUsername?: string;
  page: number;
  size: number;
  sortBy?: string;
  sortOrder?: string;
}

const statusOptions = [
  { label: '未打款', value: 'UNPAID', tag: 'danger' },
  { label: '已打款', value: 'PAID', tag: 'success' }
] as const;

const formatAmount = (value?: number) => {
  if (value === null || value === undefined) return '0.00';
  return Number(value).toFixed(2);
};

type SortOrder = 'ascending' | 'descending' | null;

const PAGE_SIZE_KEY = 'orders-page-size';
const getSavedPageSize = () => {
  try {
    const saved = localStorage.getItem(PAGE_SIZE_KEY);
    if (!saved) return 50;

    // 移除所有非数字字符
    const cleaned = saved.replace(/[^\d]/g, '');
    const num = Number(cleaned);

    if (Number.isNaN(num) || num <= 0 || num > 1000) {
      // 如果值无效，清除并返回默认值
      localStorage.removeItem(PAGE_SIZE_KEY);
      return 50;
    }

    // 如果原始值和清理后的值不同，保存清理后的值
    if (saved !== String(num)) {
      localStorage.setItem(PAGE_SIZE_KEY, String(num));
    }

    return num;
  } catch (error) {
    console.error('Error loading page size:', error);
    localStorage.removeItem(PAGE_SIZE_KEY);
    return 50;
  }
};

const filters = reactive<FilterModel>({
  dateRange: [],
  status: '',
  keyword: '',
  ownerUsername: '',
  page: 1,
  size: getSavedPageSize()
});

const EXCEL_PREFIX_PATTERN = /^[='\u2018\u2019"\u201C\u201D`\u200B-\u200F\uFEFF]+/;
const EXCEL_PREFIX_MULTILINE_PATTERN = /^[='\u2018\u2019"\u201C\u201D`\u200B-\u200F\uFEFF]+/gm;

const sanitizeSingleInput = (value?: string) => {
  if (!value) return '';
  return value.replace(EXCEL_PREFIX_PATTERN, '').trim();
};

const sanitizeMultilineInput = (value?: string) => {
  if (!value) return '';
  return value.replace(EXCEL_PREFIX_MULTILINE_PATTERN, '');
};

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');
const orders = ref<OrderRecord[]>([]);
const userOrders = ref<OrderRecord[]>([]);
const total = ref(0);
const loading = ref(false);
















const userSearchInput = ref('');
const userSearchLoading = ref(false);
const userSearchDebounce = ref<number | null>(null);
const adminSearchDebounce = ref<number | null>(null);
const tableData = computed(() => (isAdmin.value ? orders.value : userOrders.value));
const tableLoading = computed(() => (isAdmin.value ? loading.value : userSearchLoading.value));
const USER_HISTORY_KEY = 'user-order-history';
const quickStatus = ref('');

// 用户下拉选项（从后端获取）
const userOptions = ref<SysUser[]>([]);
const userLoading = ref(false);

const loadUsers = async () => {
  if (!isAdmin.value) return;
  userLoading.value = true;
  try {
    const [sysUsers, ownerNames] = await Promise.all([
      listUsers(),
      listOwnerUsernames().catch(() => [])
    ]);
    // 合并：系统账号 + 历史归属用户（去重）
    const map = new Map<string, SysUser>();
    sysUsers.forEach(u => {
      if (u?.username) map.set(u.username, u);
    });
    ownerNames.forEach(name => {
      const key = (name || '').trim();
      if (key && !map.has(key)) {
        map.set(key, { username: key } as SysUser);
      }
    });
    userOptions.value = Array.from(map.values()).sort((a,b) => (a.username || '').localeCompare(b.username || ''));
  } finally {
    userLoading.value = false;
  }
};
const filteredTableData = computed(() => {
  let list = tableData.value;
  
  // 调试：检查是否有重复的 SN
  if (isAdmin.value && list.length > 0) {
    const snCounts = new Map<string, number>();
    list.forEach(order => {
      const sn = order.sn || '';
      snCounts.set(sn, (snCounts.get(sn) || 0) + 1);
    });
    const duplicates = Array.from(snCounts.entries()).filter(([_, count]) => count > 1);
    if (duplicates.length > 0) {
      console.log('🔍 发现重复的 SN:', duplicates);
    }
  }

  // 快速筛选（仅在非管理员视图使用，管理员视图通过后端筛选）
  if (!isAdmin.value && quickStatus.value) {
    list = list.filter(order => order.status === quickStatus.value);
  }

  // 前端排序（普通用户视图；管理员视图由后端排序）
  if (sortState.prop && sortState.order) {
    const dir = sortState.order === 'ascending' ? 1 : -1;
    return [...list].sort((a, b) => {
      if (sortState.prop === 'status') {
        const order = ['UNPAID', 'PAID'];
        const ia = order.indexOf(a.status ?? '');
        const ib = order.indexOf(b.status ?? '');
        return (ia - ib) * dir;
      }
      if (sortState.prop === 'amount') {
        const va = a.amount ?? 0;
        const vb = b.amount ?? 0;
        return va === vb ? 0 : va > vb ? dir : -dir;
      }
      const va = (a as any)[sortState.prop];
      const vb = (b as any)[sortState.prop];
      if (va === vb) return 0;
      return va > vb ? dir : -dir;
    });
  }

  return list;
});





  

onMounted(() => {
  // 清理 localStorage 中的错误数据
  try {
    const savedSize = localStorage.getItem(PAGE_SIZE_KEY);
    if (savedSize) {
      const cleaned = savedSize.replace(/[^\d]/g, '');
      const num = Number(cleaned);
      if (savedSize !== String(num) || Number.isNaN(num) || num <= 0 || num > 1000) {
        console.warn('Cleaning invalid page size from localStorage:', savedSize);
        if (num > 0 && num <= 1000) {
          localStorage.setItem(PAGE_SIZE_KEY, String(num));
        } else {
          localStorage.removeItem(PAGE_SIZE_KEY);
        }
      }
    }
  } catch (error) {
    console.error('Error cleaning localStorage:', error);
  }
});

const createVisible = ref(false);
const createLoading = ref(false);
const createFormRef = ref<FormInstance>();
const createForm = reactive<OrderCreateRequest>({
  orderDate: '',
  trackingNumber: '',
  model: '',
  sn: '',
  remark: '',
  currency: 'CNY',
 
});

const createRules: FormRules<OrderCreateRequest> = {
  trackingNumber: [{ required: true, message: '请输入单号', trigger: 'blur' }],
  sn: [{ required: true, message: '请输入 SN', trigger: 'blur' }]
};

const fileInput = ref<HTMLInputElement>();
const importProgress = reactive({
  visible: false,
  percent: 0,
  timer: null as number | null
});

// 导入样式缓存：仅用于本次会话内展示，不入库
const importStyles = ref<Map<string, ImportStyle>>(new Map());

const editDialog = reactive({
  visible: false,
  loading: false,
  targetId: 0,
  form: { trackingNumber: '', model: '', sn: '', status: '', remark: '' } as OrderUpdateRequest
});

const statusLabel = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return match ? match.label : '未知状态';
};

const statusTagType = (value?: string) => {
  const match = statusOptions.find(item => item.value === value);
  return (match?.tag as string) ?? 'info';
};

const setStatusFilter = async (value: string) => {
  // 如果点击的是当前已选中的状态，则清空；否则切换到新状态
  if (quickStatus.value === value) {
    quickStatus.value = '';
  } else {
    quickStatus.value = value;
  }

  if (isAdmin.value) {
    // 同步到表单筛选
    filters.status = quickStatus.value;
    filters.page = 1;
    console.log('状态筛选变更:', quickStatus.value || '全部');
    // 立即执行搜索
    loadOrders();
  }
};

const clearStatusFilter = async () => {
  // 清除快速筛选和表单筛选
  quickStatus.value = '';
  if (isAdmin.value) {
    filters.status = '';
    filters.page = 1;
    // 立即执行搜索
    loadOrders();
  }
};

const queryParams = computed(() => {
  // 清理并验证数字参数
  const cleanNumber = (val: any, defaultVal: number): number => {
    const str = String(val).replace(/[^\d]/g, '');
    const num = Number(str);
    return Number.isNaN(num) || num <= 0 ? defaultVal : num;
  };

  const params: any = {
    page: cleanNumber(filters.page, 1),
    size: cleanNumber(filters.size, 50),
    keyword: filters.keyword || undefined,
    status: filters.status || undefined,
    ownerUsername: filters.ownerUsername || undefined,
    sortBy: filters.sortBy || undefined,
    sortOrder: filters.sortOrder || undefined
  };
  if (filters.dateRange.length === 2) {
    params.startDate = filters.dateRange[0];
    params.endDate = filters.dateRange[1];
  }
  return params;
});

const buildFilterPayload = () => {
  const params: any = {
    keyword: filters.keyword || undefined,
    status: filters.status || undefined
  };
  if (filters.dateRange.length === 2) {
    params.startDate = filters.dateRange[0];
    params.endDate = filters.dateRange[1];
  }
  return params;
};

const loadOrders = async () => {
  if (!isAdmin.value) {
    return;
  }
  loading.value = true;
  try {
    const params = queryParams.value;
    console.log('📡 请求参数:', JSON.stringify(params, null, 2));
    const data = await fetchOrders(params);
    console.log('✅ 收到数据:', data.records.length, '条记录');
    console.log('📋 详细记录:', data.records.map(r => ({ id: r.id, sn: r.sn, trackingNumber: r.trackingNumber })));
    // 直接使用后端返回的数据，不做任何去重处理
    orders.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  // 统一在点击查询时进行清洗，避免Excel前缀等脏数据
  filters.keyword = sanitizeSingleInput(filters.keyword);
  filters.page = 1;
  loadOrders();
};

const triggerAdminAutoSearch = () => {
  if (!isAdmin.value) return;
  if (adminSearchDebounce.value) {
    clearTimeout(adminSearchDebounce.value);
  }
  adminSearchDebounce.value = window.setTimeout(() => {
    filters.page = 1;
    loadOrders();
  }, 300);
};

const handleKeywordInput = (value: string) => {
  filters.keyword = sanitizeSingleInput(value);
  triggerAdminAutoSearch();
};

const handleUserSearchInput = (value: string) => {
  userSearchInput.value = sanitizeMultilineInput(value);
};

const handleSizeChange = (size: number) => {
  try {
    // 移除任何非数字字符并转换
    let cleaned = String(size).replace(/[^\d]/g, '');
    const validSize = Number(cleaned) || 50;

    // 确保范围合理
    const finalSize = Math.min(Math.max(validSize, 1), 1000);

    filters.size = finalSize;
    localStorage.setItem(PAGE_SIZE_KEY, String(finalSize));
    filters.page = 1;
    loadOrders();
  } catch (error) {
    console.error('Error handling size change:', error);
    filters.size = 50;
    filters.page = 1;
    loadOrders();
  }
};

const handlePageChange = (page: number) => {
  filters.page = Number(page) || 1;
  loadOrders();
};

const resetFilters = () => {
  filters.dateRange = [];
  filters.status = '';
  filters.keyword = '';
  filters.page = 1;
  filters.sortBy = undefined;
  filters.sortOrder = undefined;
  quickStatus.value = '';
  sortState.prop = '';
  sortState.order = null;
  loadOrders();
};

const triggerImport = () => {
  if (!isAdmin.value) return;
  fileInput.value?.click();
};



const captureDiffSnapshot = async () => {
  const all = await fetchAllOrders();
  return buildOrderSnapshot(all);
};

const startImportProgress = () => {
  importProgress.visible = true;
  importProgress.percent = 10;
  if (importProgress.timer) {
    clearInterval(importProgress.timer);
  }
  importProgress.timer = window.setInterval(() => {
    if (importProgress.percent < 90) {
      importProgress.percent += 10;
    }
  }, 300);
};

const finishImportProgress = () => {
  if (importProgress.timer) {
    clearInterval(importProgress.timer);
    importProgress.timer = null;
  }
  importProgress.percent = 100;
  setTimeout(() => {
    importProgress.visible = false;
    importProgress.percent = 0;
  }, 400);
};

const handleFileChange = async (event: Event) => {
  if (!isAdmin.value) return;
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  startImportProgress();
  try {
    const report: any = await importOrders(file);

    // 解析样式信息：仅本次会话用于展示（仅传回了发生变化的行）
    try {
      const styles: ImportStyle[] = report?.styles || [];
      const map = new Map<string, ImportStyle>();
      styles.forEach(s => {
        // 仅按记录ID缓存样式，避免同一tracking+SN的其他旧记录被新样式“覆盖显示”
        if ((s as any).id) {
          map.set(`ID-${(s as any).id}`, s);
        }
      });
      importStyles.value = map;
    } catch {}

    finishImportProgress();
    const skipped = Number(report?.skippedUnchanged || 0);
    const imported = Number(report?.importedCount || 0);
    if (Array.isArray(report?.skippedRows) && report.skippedRows.length) {
      console.log('本次导入跳过未变化行:', report.skippedRows);
    }
    ElMessage.success({
      message: `导入完成：写入 ${imported} 行，跳过未变化 ${skipped} 行`,
      duration: 8000, // 显示更久
      showClose: true
    });
    loadOrders();
  } catch (error) {
    finishImportProgress();
    throw error;
  } finally {
    target.value = '';
  }
};

const openCreateDrawer = () => {
  if (!isAdmin.value) return;
  createVisible.value = true;
};

const openEditDialog = (row: OrderRecord) => {
  if (!isAdmin.value) return;
  editDialog.targetId = row.id;
  editDialog.form.trackingNumber = row.trackingNumber;
  editDialog.form.model = row.model ?? '';
  editDialog.form.sn = row.sn ?? '';
  editDialog.form.status = row.status ?? '';
  editDialog.form.remark = row.remark ?? '';
  editDialog.visible = true;
};

const submitCreate = async () => {
  if (!isAdmin.value) return;
  if (!createFormRef.value) return;
  const valid = await createFormRef.value.validate().catch(() => false);
  if (!valid) return;
  createLoading.value = true;
  try {
    const payload = Object.entries(createForm).reduce((acc: Record<string, any>, [key, value]) => {
      if (value !== '' && value !== undefined && value !== null) {
        acc[key] = value;
      }
      return acc;
    }, {}) as OrderCreateRequest;
    await createOrder(payload);
    ElMessage.success('新增成功');
    createVisible.value = false;
    Object.assign(createForm, {
      orderDate: '',
      trackingNumber: '',
      model: '',
      sn: '',
      remark: '',
      amount: undefined,
      currency: 'CNY',
      
    });
    // 单条新增不需要差异检测，只刷新当前页面数据
    loadOrders();
  } finally {
    createLoading.value = false;
  }
};

const changeStatus = async (row: OrderRecord, status: string) => {
  if (!isAdmin.value) return;
  try {
    await updateOrderStatus(row.id, status);
    row.status = status;
    ElMessage.success('状态已更新');
  } catch (error) {
    console.error(error);
  }
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ').replace('Z', '');
};

const buildOrderSnapshot = (list: OrderRecord[]) => {
  const map = new Map<string, Partial<OrderRecord>>();
  list.forEach(item => {
    const key = buildOrderKey(item);
    if (!key) return;
    map.set(key, {
      trackingNumber: item.trackingNumber,
      model: item.model,
      sn: item.sn,
      amount: item.amount,
      // 包含样式信息
      modelBgColor: (item as any).modelBgColor,
      modelFontColor: (item as any).modelFontColor,
      modelStrike: (item as any).modelStrike,
      snBgColor: (item as any).snBgColor,
      snFontColor: (item as any).snFontColor,
      snStrike: (item as any).snStrike,
      amountBgColor: (item as any).amountBgColor,
      amountFontColor: (item as any).amountFontColor,
      amountStrike: (item as any).amountStrike,
      remarkBgColor: (item as any).remarkBgColor,
      remarkFontColor: (item as any).remarkFontColor,
      remarkStrike: (item as any).remarkStrike
    });
  });
  return map;
};

const computeDifferences = (prevMap: Map<string, Partial<OrderRecord>>, nextList: OrderRecord[], importedStyles?: Map<string, ImportStyle>) => {
  if (!prevMap.size) return [];
  const fieldLabels: Record<string, string> = {
    trackingNumber: '运单号',
    model: '型号',
    sn: 'SN'
  };
  const notices: { trackingNumber: string; message: string; before?: Partial<OrderRecord>; after?: Partial<OrderRecord> }[] = [];
  nextList.forEach(order => {
    const key = buildOrderKey(order);
    const prev = prevMap.get(key);
    if (!prev) {
      return;
    }
    const changed: string[] = [];
    const normalizeVal = (val: unknown) => {
      if (val === null || val === undefined) return '';
      if (typeof val === 'string') return val.trim();
      return String(val);
    };
    const before: Partial<OrderRecord> = {};
    const after: Partial<OrderRecord> = {};
    
    // 检测内容变化
    Object.keys(fieldLabels).forEach(field => {
      const prevVal = (prev as any)[field];
      const currVal = (order as any)[field];
      if (normalizeVal(prevVal) !== normalizeVal(currVal)) {
        changed.push(fieldLabels[field]);
        (before as any)[field] = prevVal;
        (after as any)[field] = currVal;
      }
    });
    
    // 检测样式变化（如果提供了导入的样式）
    if (importedStyles) {
      const styleKey = `${(order.trackingNumber || '').toUpperCase()}#${(order.sn || '').toUpperCase()}`;
      const importedStyle = importedStyles.get(styleKey);
      if (importedStyle) {
        const styleFields = ['model', 'sn', 'amount', 'remark'];
        styleFields.forEach(field => {
          const bgKey = `${field}BgColor` as keyof ImportStyle;
          const fgKey = `${field}FontColor` as keyof ImportStyle;
          const strikeKey = `${field}Strike` as keyof ImportStyle;
          
          const prevBg = (prev as any)?.[bgKey];
          const prevFg = (prev as any)?.[fgKey];
          const prevStrike = (prev as any)?.[strikeKey];
          
          const currBg = importedStyle[bgKey];
          const currFg = importedStyle[fgKey];
          const currStrike = importedStyle[strikeKey];
          
          if (prevBg !== currBg || prevFg !== currFg || prevStrike !== currStrike) {
            const fieldLabel = fieldLabels[field] || field;
            if (!changed.includes(fieldLabel)) {
              changed.push(`${fieldLabel}(样式)`);
            }
          }
        });
      }
    }
    
    if (changed.length) {
      notices.push({
        trackingNumber: order.trackingNumber ?? key,
        message: `字段变更：${changed.join('、')}`,
        before,
        after
      });
    }
  });
  // 同一运单号只保留一条提醒
  const dedup: Record<string, typeof notices[number]> = {};
  notices.forEach(item => {
    const k = (item.trackingNumber ?? '').toUpperCase();
    if (!dedup[k]) {
      dedup[k] = item;
    }
  });
  return Object.values(dedup).slice(0, 20); // 避免一次性展示过多
};



const buildOrderKey = (order: OrderRecord) => {
  // 使用 追踪号+SN 作为更精细的键，避免同一运单号下多个 SN 被覆盖
  const tracking = (order.trackingNumber || '').trim().toUpperCase();
  const sn = (order.sn || '').trim().toUpperCase();
  if (tracking && sn) return `${tracking}#${sn}`;
  if (order.id) return `ID-${order.id}`;
  return tracking;
};

const diffFields = (item: DiffNotice) => {
  const fields: { key: keyof OrderRecord; label: string }[] = [
    { key: 'trackingNumber', label: '运单号' },
    { key: 'model', label: '型号' },
    { key: 'sn', label: 'SN' }
  ];
  return fields
    .filter(({ key }) => {
      const beforeVal = (item.before as any)?.[key];
      const afterVal = (item.after as any)?.[key];
      return String(beforeVal ?? '') !== String(afterVal ?? '');
    })
    .map(f => f.label);
};

const formatDiffValue = (obj: Partial<OrderRecord> | undefined, label: string) => {
  if (!obj) return '-';
  const map: Record<string, keyof OrderRecord> = {
    '运单号': 'trackingNumber',
    '型号': 'model',
    'SN': 'sn'
  };
  const key = map[label];
  const val = key ? (obj as any)[key] : undefined;
  return val === undefined || val === null || val === '' ? '-' : val;
};

const scheduleDiffCalculation = (prevSnapshot: Map<string, Partial<OrderRecord>>, latest: OrderRecord[], importedStyles?: Map<string, ImportStyle>) => {
  // 轻量异步排队，避免阻塞后续操作或导航
  setTimeout(() => {
    const diffs = computeDifferences(prevSnapshot, latest, importedStyles);
    mergeDiffNotices(diffs);
  }, 0);
};

// 将导入报告中的样式直接转为变更项（即使后端未保存该行，也能展示出来）
const materializeImportedStyleChanges = (
  prevMap: Map<string, Partial<OrderRecord>>,
  importedStyles?: Map<string, ImportStyle>
): StyleChangeItem[] => {
  if (!importedStyles || !importedStyles.size) return [];
  const out: StyleChangeItem[] = [];
  // 包含所有可能有格式的列
  const fields: Array<'tracking'|'model'|'sn'|'amount'|'remark'> = ['tracking','model','sn','amount','remark'];
  
  importedStyles.forEach((s) => {
    const tracking = (s.trackingNumber || '').toUpperCase();
    const sn = (s.sn || '').toUpperCase();
    const key = `${tracking}#${sn}`;
    const prev = prevMap.get(key) as any;

    fields.forEach((field) => {
      const bgKey = `${field}BgColor` as keyof ImportStyle;
      const fgKey = `${field}FontColor` as keyof ImportStyle;
      const strikeKey = `${field}Strike` as keyof ImportStyle;

      const toBg = (s as any)[bgKey] || '';
      const toFont = (s as any)[fgKey] || '';
      const toStrike = !!(s as any)[strikeKey];

      const fromBg = (prev as any)?.[`${field}BgColor`] || '';
      const fromFont = (prev as any)?.[`${field}FontColor`] || '';
      const fromStrike = !!((prev as any)?.[`${field}Strike`] || false);

      if (fromBg !== toBg || fromFont !== toFont || fromStrike !== toStrike) {
        out.push({
          trackingNumber: s.trackingNumber || '',
          sn: s.sn || '',
          field,
          fromBg: fromBg || undefined,
          toBg: toBg || undefined,
          fromFont: fromFont || undefined,
          toFont: toFont || undefined,
          fromStrike,
          toStrike,
          ts: Date.now()
        });
      }
    });
  });
  return out.slice(0, 1000);
};

// 计算样式变更：前端兜底生成（防止后端只返回部分变更）
const computeStyleChanges = (
  prevMap: Map<string, Partial<OrderRecord>>,
  nextList: OrderRecord[],
  importedStyles?: Map<string, ImportStyle>
): StyleChangeItem[] => {
  if (!nextList?.length) return [];
  // 包含所有可能有格式的列
  const fields: Array<'tracking' | 'model' | 'sn' | 'amount' | 'remark'> = ['tracking', 'model', 'sn', 'amount', 'remark'];

  const getStyleFromRow = (row: OrderRecord, field: typeof fields[number]) => {
    const anyRow: any = row as any;
    const map: any = {
      tracking: { bg: anyRow.trackingBgColor, fg: anyRow.trackingFontColor, strike: anyRow.trackingStrike },
      model: { bg: anyRow.modelBgColor, fg: anyRow.modelFontColor, strike: anyRow.modelStrike },
      sn: { bg: anyRow.snBgColor, fg: anyRow.snFontColor, strike: anyRow.snStrike },
      amount: { bg: anyRow.amountBgColor, fg: anyRow.amountFontColor, strike: anyRow.amountStrike },
      remark: { bg: anyRow.remarkBgColor, fg: anyRow.remarkFontColor, strike: anyRow.remarkStrike }
    };
    return map[field] || {};
  };

  const getStyleFromImported = (row: OrderRecord, field: typeof fields[number]) => {
    if (!importedStyles) return undefined;
    const key = `${(row.trackingNumber || '').toUpperCase()}#${(row.sn || '').toUpperCase()}`;
    const s: any = importedStyles.get(key);
    if (!s) return undefined;
    const map: any = {
      tracking: { bg: s.trackingBgColor, fg: s.trackingFontColor, strike: s.trackingStrike },
      model: { bg: s.modelBgColor, fg: s.modelFontColor, strike: s.modelStrike },
      sn: { bg: s.snBgColor, fg: s.snFontColor, strike: s.snStrike },
      amount: { bg: s.amountBgColor, fg: s.amountFontColor, strike: s.amountStrike },
      remark: { bg: s.remarkBgColor, fg: s.remarkFontColor, strike: s.remarkStrike }
    };
    return map[field];
  };

  const getPrevStyle = (prev: Partial<OrderRecord> | undefined, field: typeof fields[number]) => {
    const p: any = prev as any;
    const map: any = {
      tracking: { bg: p?.trackingBgColor, fg: p?.trackingFontColor, strike: p?.trackingStrike },
      model: { bg: p?.modelBgColor, fg: p?.modelFontColor, strike: p?.modelStrike },
      sn: { bg: p?.snBgColor, fg: p?.snFontColor, strike: p?.snStrike },
      amount: { bg: p?.amountBgColor, fg: p?.amountFontColor, strike: p?.amountStrike },
      remark: { bg: p?.remarkBgColor, fg: p?.remarkFontColor, strike: p?.remarkStrike }
    };
    return map[field] || {};
  };

  const result: StyleChangeItem[] = [];
  nextList.forEach(row => {
    const key = buildOrderKey(row);
    const prev = prevMap.get(key);

    fields.forEach(field => {
      const prevStyle = getPrevStyle(prev, field);
      // 先取导入样式（优先），没有再取当前行持久化样式
      const currStyle = getStyleFromImported(row, field) ?? getStyleFromRow(row, field);

      const fromBg = prevStyle?.bg || '';
      const toBg = currStyle?.bg || '';
      const fromFont = prevStyle?.fg || '';
      const toFont = currStyle?.fg || '';
      const fromStrike = !!prevStyle?.strike;
      const toStrike = !!currStyle?.strike;

      if (fromBg !== toBg || fromFont !== toFont || fromStrike !== toStrike) {
        result.push({
          trackingNumber: row.trackingNumber || '',
          sn: row.sn || '',
          field,
          fromBg: fromBg || undefined,
          toBg: toBg || undefined,
          fromFont: fromFont || undefined,
          toFont: toFont || undefined,
          fromStrike,
          toStrike,
          ts: Date.now()
        });
      }
    });
  });

  // 合理限制数量，避免 UI 卡顿
  return result.slice(0, 1000);
};

const exportDiffNotices = () => {
  if (!diffNotices.value.length) {
    ElMessage.info('暂无可导出的变更提醒');
    return;
  }
  const headers = ['运单号', '变更字段', '旧值', '新值'];
  const rows: string[][] = [];
  diffNotices.value.forEach(item => {
    const fields = diffFields(item);
    if (!fields.length) return;
    fields.forEach(label => {
      rows.push([
        item.trackingNumber,
        label,
        formatDiffValue(item.before, label),
        formatDiffValue(item.after, label)
      ]);
    });
  });
  if (!rows.length) {
    ElMessage.info('暂无可导出的变更提醒');
    return;
  }
  const csv = [headers, ...rows]
    .map(cols => cols.map(col => `"${String(col ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `order-diff-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  window.URL.revokeObjectURL(url);
};

const submitEdit = async () => {
  if (!editDialog.targetId) return;
  editDialog.loading = true;
  try {
    const payload: OrderUpdateRequest = {
      trackingNumber: editDialog.form.trackingNumber,
      model: editDialog.form.model,
      sn: editDialog.form.sn,
      status: editDialog.form.status,
      remark: editDialog.form.remark
    };
    await updateOrder(editDialog.targetId, payload);
    editDialog.visible = false;
    ElMessage.success('已更新');
    // 单条编辑不需要差异检测和统计刷新，只刷新当前页面数据
    loadOrders();
  } finally {
    editDialog.loading = false;
  }
};

const getRecordKey = (record: OrderRecord) => {
  // 优先使用 id（最唯一）
  if (record.id) {
    return `ID-${record.id}`;
  }
  // 如果没有 id，使用 trackingNumber + SN 组合，确保不同运单号的相同 SN 不会冲突
  const tracking = (record.trackingNumber || '').trim().toUpperCase();
  const sn = (record.sn || '').trim().toUpperCase();
  if (tracking && sn) {
    return `${tracking}#${sn}`;
  }
  // 最后回退到单独字段
  return sn || tracking || '';
};

const handleUserSearch = async (silent = false) => {
  const list = userSearchInput.value
    .split(/\n|,|;/)
    .map(item => sanitizeSingleInput(item))
    .filter(Boolean);
  if (!list.length) {
    if (!silent) {
      ElMessage.warning('请先输入单号或 SN');
    } else {
      userOrders.value = [];
    }
    return;
  }
  userSearchLoading.value = true;
  try {
    const results = await searchOrders(list);
    if (!results.length) {
      ElMessage.warning('未查询到对应订单');
      return;
    }
    const map = new Map<string, OrderRecord>();
    userOrders.value.forEach(record => {
      const key = getRecordKey(record);
      if (key) {
        map.set(key, record);
      }
    });
    results.forEach(record => {
      const key = getRecordKey(record);
      if (key) {
        map.set(key, record);
      }
    });
    userOrders.value = Array.from(map.values());
    saveUserOrders();
    await loadCategoryStats();
  } finally {
    userSearchLoading.value = false;
  }
};

const clearUserResults = () => {
  userOrders.value = [];
  localStorage.removeItem(USER_HISTORY_KEY);
  loadCategoryStats();
};

// 将导入样式映射到行上的内联样式
const styleFor = (row: OrderRecord, field: 'tracking' | 'model' | 'sn' | 'amount' | 'remark') => {
  try {
    // 先按 record.id 精确匹配，避免同一 tracking+SN 的不同记录互相“覆盖样式”
    let s: any | undefined;
    if (row.id) {
      s = importStyles.value.get(`ID-${row.id}`) as any;
    }
    // 再回退到 tracking#sn 级别（兼容旧数据/无 id 的情况）
    if (!s) {
      const key = `${(row.trackingNumber || '').toUpperCase()}#${(row.sn || '').toUpperCase()}`;
      s = importStyles.value.get(key) as any;
    }

    // 1) 优先使用本次导入的样式
    let map: any | undefined;
    if (s) {
      map = {
        tracking: { bg: s.trackingBgColor, fg: s.trackingFontColor, strike: s.trackingStrike },
        model: { bg: s.modelBgColor, fg: s.modelFontColor, strike: s.modelStrike },
        sn: { bg: s.snBgColor, fg: s.snFontColor, strike: s.snStrike },
        amount: { bg: s.amountBgColor, fg: s.amountFontColor, strike: s.amountStrike },
        remark: { bg: s.remarkBgColor, fg: s.remarkFontColor, strike: s.remarkStrike }
      }[field];
    }

    // 2) 若无，则回退到后端返回的持久化样式字段（针对该条记录的 orderId）
    if (!map) {
      const fallback: any = {
        tracking: { bg: (row as any).trackingBgColor, fg: (row as any).trackingFontColor, strike: (row as any).trackingStrike },
        model: { bg: (row as any).modelBgColor, fg: (row as any).modelFontColor, strike: (row as any).modelStrike },
        sn: { bg: (row as any).snBgColor, fg: (row as any).snFontColor, strike: (row as any).snStrike },
        amount: { bg: (row as any).amountBgColor, fg: (row as any).amountFontColor, strike: (row as any).amountStrike },
        remark: { bg: (row as any).remarkBgColor, fg: (row as any).remarkFontColor, strike: (row as any).remarkStrike }
      };
      map = fallback[field];
    }

    if (!map) return {};

    const style: Record<string, string> = {};

    // 背景色：非空且不是白色时才应用
    if (map.bg && map.bg !== '#FFFFFF' && map.bg !== '#FFF' && map.bg.trim() !== '') {
      style['background-color'] = map.bg as string;
    }

    // 字体色：非空且不是黑色时才应用
    if (map.fg && map.fg !== '#000000' && map.fg !== '#000' && map.fg.trim() !== '') {
      style['color'] = map.fg as string;
    }

    // 删除线：显式为 true 时才应用
    if (map.strike === true || map.strike === 'true' || map.strike === 1) {
      style['text-decoration'] = 'line-through';
    }

    return style;
  } catch (error) {
    console.warn('样式应用失败:', error);
    return {};
  }
};

const exportUserOrders = () => {
  if (!userOrders.value.length) return;
  const headers = ['下单日期', '运单号', '型号', 'SN', '分类', '状态', '备注', '创建人'];
  const csvRows = [headers.join(',')];
  userOrders.value.forEach(order => {
    csvRows.push([
      order.orderDate ?? '',
      order.trackingNumber ?? '',
      order.model ?? '',
      order.sn ?? '',
      order.category ?? '',
      statusLabel(order.status),
      order.remark ?? '',
      order.createdBy ?? ''
    ].map(value => `"${String(value).replace(/"/g, '""')}"`).join(','));
  });
  const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `orders-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  window.URL.revokeObjectURL(url);
};

watch(isAdmin, value => {
  if (value) {
    loadUsers();
    loadOrders();
  } else {
    loadUserOrders();
  }
}, { immediate: true });

watch(userSearchInput, value => {
  if (userSearchDebounce.value) {
    clearTimeout(userSearchDebounce.value);
  }
  if (!value || !value.trim()) {
    userOrders.value = [];
    return;
  }
  userSearchDebounce.value = window.setTimeout(() => {
    handleUserSearch(true);
  }, 400);
});

const sortState = reactive<{ prop: string; order: SortOrder }>({ prop: '', order: null });

const handleSortChange = (options: { prop: string; order: SortOrder }) => {
  // 如果点击的是状态列，实现循环筛选而不是排序
  if (options.prop === 'status') {
    // 循环顺序：全部 → 未打款 → 已打款 → 全部
    let nextStatus = '';
    if (!filters.status) {
      nextStatus = 'UNPAID'; // 全部 → 未打款
    } else if (filters.status === 'UNPAID') {
      nextStatus = 'PAID'; // 未打款 → 已打款
    } else {
      nextStatus = ''; // 已打款 → 全部
    }

    console.log('🔄 状态列循环筛选:', filters.status || '全部', '→', nextStatus || '全部');

    // 更新筛选状态
    quickStatus.value = nextStatus;
    filters.status = nextStatus;
    filters.page = 1;

    // 清除排序状态（因为我们在筛选，不是排序）
    sortState.prop = '';
    sortState.order = null;
    filters.sortBy = undefined;
    filters.sortOrder = undefined;

    if (isAdmin.value) {
      loadOrders();
    }
    return;
  }

  // 其他列保持原有的排序逻辑
  sortState.prop = options.prop ?? '';
  sortState.order = options.order ?? null;

  // 更新 filters 并重新加载数据（后端排序）
  if (isAdmin.value) {
    if (options.order) {
      filters.sortBy = options.prop;
      filters.sortOrder = options.order === 'ascending' ? 'asc' : 'desc';
    } else {
      filters.sortBy = undefined;
      filters.sortOrder = undefined;
    }
    filters.page = 1; // 排序后回到第一页
    loadOrders();
  }
};

const loadCategoryStats = async () => {
  if (!isAdmin.value) return;
  try {
    await fetchCategoryStats(buildFilterPayload());
  } catch (error) {
    console.warn('Failed to load category stats', error);
  }
};

// 监听表单状态筛选，同步到快速筛选（仅同步显示，不触发搜索）
watch(() => filters.status, (newValue) => {
  // 同步快速筛选的视觉状态
  quickStatus.value = newValue;
});

// watch(() => filters.keyword, triggerAdminAutoSearch); // 禁用实时搜索，改为手动点击查询
// watch(() => filters.dateRange, triggerAdminAutoSearch, { deep: true }); // 禁用实时搜索，改为手动点击查询

function loadUserOrders() {
  try {
    const cached = localStorage.getItem(USER_HISTORY_KEY);
    if (cached) {
      userOrders.value = JSON.parse(cached);
    }
  } catch (error) {
    console.warn('Failed to load cached user orders', error);
  }
  loadCategoryStats();
}

function saveUserOrders() {
  try {
    localStorage.setItem(USER_HISTORY_KEY, JSON.stringify(userOrders.value));
  } catch (error) {
    console.warn('Failed to persist user orders', error);
  }
}

const getScroller = (): HTMLElement => {
  const candidates: (HTMLElement | null)[] = [
    document.querySelector('.el-main') as HTMLElement | null,
    document.querySelector('.el-scrollbar__wrap') as HTMLElement | null,
    document.scrollingElement as HTMLElement | null,
    document.documentElement,
    document.body
  ];
  for (const el of candidates) {
    if (el && el.scrollHeight > el.clientHeight + 1) return el;
  }
  return (document.scrollingElement as HTMLElement) ?? document.documentElement ?? document.body;
};

const scrollToTop = () => {
  const scroller = getScroller();
  const duration = 150;
  const start = scroller.scrollTop;
  const startTime = performance.now();

  const animateScroll = (currentTime: number) => {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const easeOut = 1 - Math.pow(1 - progress, 4);
    scroller.scrollTop = start * (1 - easeOut);
    if (progress < 1) requestAnimationFrame(animateScroll);
  };

  requestAnimationFrame(animateScroll);
};

// =======================
// 页面状态恢复 key
// =======================
const SCROLL_KEY = 'orders-scroll'
const PAGE_KEY = 'orders-page'
let scrollHandler: (() => void) | null = null
let cachedScroller: HTMLElement | null = null

// =======================
// 获取滚动容器
// =======================
const getScrollerElement = (): HTMLElement => {
  const layoutMain = document.querySelector('.layout-main') as HTMLElement | null
  if (layoutMain) return layoutMain
  const elMain = document.querySelector('.el-main') as HTMLElement | null
  if (elMain) return elMain
  return (document.scrollingElement as HTMLElement) ?? document.documentElement ?? document.body
}

// =======================
// 恢复页码
// =======================
const restorePage = () => {
  try {
    const saved = Number(sessionStorage.getItem(PAGE_KEY))
    if (!Number.isNaN(saved) && saved > 0) {
      filters.page = saved
    }
  } catch {}
}

// =======================
// 恢复滚动
// =======================
const restoreScroll = () => {
  try {
    const saved = Number(sessionStorage.getItem(SCROLL_KEY))
    if (!Number.isNaN(saved) && saved > 0) {
      setTimeout(() => {
        const scroller = getScrollerElement()
        scroller.scrollTop = saved
        setTimeout(() => {
          if (Math.abs(scroller.scrollTop - saved) > 10) {
            scroller.scrollTop = saved
          }
        }, 100)
      }, 50)
    }
  } catch {}
}

// =======================
// 实时保存滚动位置（节流）
// =======================
let scrollSaveTimer: number | null = null
const saveScrollPosition = () => {
  if (scrollSaveTimer) return
  scrollSaveTimer = window.setTimeout(() => {
    scrollSaveTimer = null
    try {
      const scroller = getScrollerElement()
      sessionStorage.setItem(SCROLL_KEY, String(scroller.scrollTop))
    } catch {}
  }, 100)
}

// =======================
// 绑定/解绑滚动监听
// =======================
const bindScrollListener = () => {
  unbindScrollListener()
  const scroller = getScrollerElement()
  cachedScroller = scroller
  scrollHandler = saveScrollPosition
  scroller.addEventListener('scroll', scrollHandler, { passive: true })
}

const unbindScrollListener = () => {
  if (scrollHandler && cachedScroller) {
    cachedScroller.removeEventListener('scroll', scrollHandler)
  }
  scrollHandler = null
  cachedScroller = null
}

// =======================
// 首次进入页面
// =======================
onMounted(() => {
  restorePage()
  restoreScroll()
  bindScrollListener()
})

// =======================
// 从 keep-alive 中激活
// =======================
onActivated(() => {
  restorePage()
  restoreScroll()
  bindScrollListener()
})

// =======================
// 离开页面（keep-alive 缓存）
// =======================
onDeactivated(() => {
  unbindScrollListener()
})

// =======================
// 页码变化实时保存
// =======================
watch(() => filters.page, (v) => {
  sessionStorage.setItem(PAGE_KEY, String(v))
})



onBeforeUnmount(() => {
  unbindScrollListener();
  if (scrollSaveTimer) {
    clearTimeout(scrollSaveTimer);
  }
  if (importProgress.timer) {
    clearInterval(importProgress.timer);
  }
});
</script>

<style scoped>
:deep(.el-table) {
  color: #0a0a0a;
}

:deep(.el-table th),
:deep(.el-table td) {
  color: #0a0a0a;
}

.actions {
  display: flex;
  gap: 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.settle-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-search-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.muted {
  color: var(--text-muted);
  font-size: 12px;
}

.sn-text {
  display: inline-block;
}

.sn-duplicate {
  color: #f56c6c;
  font-weight: 600;
}

.quick-tools {
  margin: 16px 0;
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.quick-tools.has-filter {
  background: #fff9e6;
  border: 2px solid #e6a23c;
}

.quick-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.quick-filter-row:last-child {
  margin-bottom: 0;
}

.quick-filter-row .label {
  font-weight: 600;
  color: var(--text-muted);
}

.quick-filter-row .el-check-tag {
  cursor: pointer;
}

.filter-hint {
  margin-left: 16px;
  padding: 6px 14px;
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 6px;
  color: #856404;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.filter-hint i {
  font-size: 16px;
  color: #f59e0b;
}

.filter-hint strong {
  font-weight: 600;
  color: #d97706;
}

.diff-card {
  margin-top: 16px;
}

.diff-list {
  margin: 0;
  padding-left: 16px;
  color: var(--text-muted);
}

.diff-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.status-text {
  color: inherit;
  font-weight: normal;
}

.status-header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.status-filter-badge {
  display: inline-block;
  padding: 2px 8px;
  margin-left: 6px;
  background: #fef0f0;
  border: 1px solid #fab6b6;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 12px;
  font-weight: 600;
}

.float-button-group {
  position: fixed;
  bottom: 40px;
  right: 20px;
  z-index: 999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.sub-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}

.main-float-btn {
  width: 40px;
  height: 40px;
  font-size: 16px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.float-sub-btn {
  width: 60px;
  height: 60px;
  font-size: 24px;
  margin-left: 0 !important;
}

.color-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.color-dot {
  display: inline-block;
  width: 24px;
  height: 24px;
  border-radius: 4px;
}

.arrow {
  color: #909399;
  font-size: 12px;
}
</style>
