<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>物流单号</h2>
        <p class="sub">管理订单信息,支持批量导入和单个新增</p>
      </div>
      <div class="actions">
        <input ref="fileInput" type="file" accept=".xls,.xlsx" hidden @change="handleFileChange" />
        <el-button @click="triggerImport">批量导入</el-button>
        <el-button type="primary" @click="openCreateDrawer">新增单号</el-button>
      </div>
    </div>

    <el-card>
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
        <el-form-item label="归属用户">
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

    <el-card v-if="false" class="user-search-card">
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

    <!-- 数据变更提醒列表 -->
    <el-card v-if="diffNotices.length > 0" class="diff-notice-card">
      <template #header>
        <div class="diff-notice-header">
          <div class="header-left">
            <i class="el-icon-warning-filled" style="color: #f59e0b; font-size: 18px;"></i>
            <span class="header-title">数据变更提醒</span>
            <el-tag type="warning" size="small" v-if="diffNoticesAll.length === diffNotices.length">
              {{ diffNotices.length }} 条
            </el-tag>
            <el-tag type="warning" size="small" v-else>
              显示 {{ diffNotices.length }} 条，共 {{ diffNoticesAll.length }} 条
            </el-tag>
          </div>
          <div class="header-actions">
            <el-button size="small" @click="exportDiffNotices">
              导出变更 <span v-if="diffNoticesAll.length > diffNotices.length">(全部 {{ diffNoticesAll.length }} 条)</span>
            </el-button>
            <el-button size="small" type="danger" @click="clearDiffNotices">清空提醒</el-button>
          </div>
        </div>
      </template>

      <el-table :data="diffNotices" size="small" max-height="300">
        <el-table-column prop="trackingNumber" label="运单号" width="160">
          <template #default="{ row }">
            <div :style="{ color: row.isDelete ? '#f56c6c' : 'inherit', fontWeight: row.isDelete ? '600' : 'normal' }">
              {{ row.trackingNumber }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="变更类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isDelete" type="danger" size="small">
              <i class="el-icon-delete" style="margin-right: 4px;"></i>删除
            </el-tag>
            <el-tag v-else-if="row.isInvalidId" type="danger" size="small">
              <i class="el-icon-warning-filled" style="margin-right: 4px;"></i>无效ID
            </el-tag>
            <el-tag v-else type="warning" size="small">变更</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="详细信息" width="280">
          <template #default="{ row }">
            <div v-if="row.isDelete" class="delete-info">
              <div class="delete-item"><span class="field-name">型号:</span> {{ row.model || '-' }}</div>
              <div class="delete-item"><span class="field-name">SN:</span> {{ row.sn || '-' }}</div>
              <div class="delete-item warning-text">
                <i class="el-icon-warning" style="margin-right: 4px;"></i>
                将同时删除关联的结账记录
              </div>
            </div>
            <div v-else-if="row.isInvalidId" class="invalid-id-info">
              <div class="invalid-id-item"><span class="field-name">Excel ID:</span> <span class="invalid-value">{{ row.excelId }}</span></div>
              <div class="invalid-id-item"><span class="field-name">运单号:</span> {{ row.trackingNumber }}</div>
              <div class="invalid-id-item"><span class="field-name">型号:</span> {{ row.model || '-' }}</div>
              <div class="invalid-id-item"><span class="field-name">SN:</span> {{ row.sn || '-' }}</div>
              <div class="invalid-id-item warning-text">
                <i class="el-icon-info" style="margin-right: 4px;"></i>
                已按运单号+SN匹配或插入新记录
              </div>
            </div>
            <div v-else>
              <el-tag
                v-for="field in diffFields(row)"
                :key="field"
                type="warning"
                size="small"
                style="margin-right: 4px;"
              >
                {{ field }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="变更详情">
          <template #default="{ row }">
            <div v-if="row.isInvalidId" class="invalid-id-warning">
              Excel中的ID不存在于数据库（可能已被删除或填写错误），系统已按运单号+SN自动匹配
            </div>
            <div v-else-if="!row.isDelete" class="diff-details">
              <div
                v-for="field in diffFields(row)"
                :key="field"
                class="diff-item"
              >
                <span class="field-name">{{ field }}:</span>
                <!-- 样式字段显示颜色块 -->
                <template v-if="field.endsWith('(样式)')">
                  <span class="style-value">
                    <template v-if="formatStyleValue(row.before, field)">
                      <span
                        v-if="formatStyleValue(row.before, field)?.bg"
                        class="color-block"
                        :style="{ backgroundColor: formatStyleValue(row.before, field)?.bg }"
                        :title="formatStyleValue(row.before, field)?.bg"
                      ></span>
                      <span v-if="formatStyleValue(row.before, field)?.fg" class="color-text">
                        字:<span
                          class="color-block"
                          :style="{ backgroundColor: formatStyleValue(row.before, field)?.fg }"
                          :title="formatStyleValue(row.before, field)?.fg"
                        ></span>
                      </span>
                      <el-icon v-if="formatStyleValue(row.before, field)?.strike" title="删除线"><Delete /></el-icon>
                      <span v-if="formatStyleValue(row.before, field)?.bold" class="bold-indicator" title="加粗">B</span>
                    </template>
                    <span v-else class="no-style">-</span>
                  </span>
                  <span class="arrow">→</span>
                  <span class="style-value">
                    <template v-if="formatStyleValue(row.after, field)">
                      <span
                        v-if="formatStyleValue(row.after, field)?.bg"
                        class="color-block"
                        :style="{ backgroundColor: formatStyleValue(row.after, field)?.bg }"
                        :title="formatStyleValue(row.after, field)?.bg"
                      ></span>
                      <span v-if="formatStyleValue(row.after, field)?.fg" class="color-text">
                        字:<span
                          class="color-block"
                          :style="{ backgroundColor: formatStyleValue(row.after, field)?.fg }"
                          :title="formatStyleValue(row.after, field)?.fg"
                        ></span>
                      </span>
                      <el-icon v-if="formatStyleValue(row.after, field)?.strike" title="删除线"><Delete /></el-icon>
                      <span v-if="formatStyleValue(row.after, field)?.bold" class="bold-indicator" title="加粗">B</span>
                    </template>
                    <span v-else class="no-style">-</span>
                  </span>
                </template>
                <!-- 普通字段显示文本 -->
                <template v-else>
                  <span class="old-value">{{ formatDiffValue(row.before, field) }}</span>
                  <span class="arrow">→</span>
                  <span class="new-value">{{ formatDiffValue(row.after, field) }}</span>
                </template>
              </div>
            </div>
            <div v-else class="delete-warning">
              Excel中已删除此记录，需要手动确认是否从数据库中删除
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.isDelete"
              type="danger"
              size="small"
              @click="handleConfirmDelete(row)"
            >
              确认删除
            </el-button>
            <el-button
              v-else
              text
              type="primary"
              size="small"
              @click="removeDiffNotice(row)"
            >
              {{ row.isInvalidId ? '知道了' : '清除' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-button type="success" @click="handleExportExcel('all')">
            <el-icon><Download /></el-icon>
            全部导出
          </el-button>
          <el-button type="primary" :disabled="!selectedRows.length" @click="handleExportExcel('selected')">
            <el-icon><Download /></el-icon>
            导出选中 ({{ selectedRows.length }})
          </el-button>
        </div>
        <div class="toolbar-right">
          <el-popover placement="bottom" :width="300" trigger="click">
            <template #reference>
              <el-button>
                <el-icon><Setting /></el-icon>
                导出设置
              </el-button>
            </template>
            <div class="export-settings">
              <h4>选择导出列</h4>
              <el-checkbox-group v-model="exportColumns">
                <el-checkbox v-for="col in availableExportColumns" :key="col.key" :label="col.key">
                  {{ col.label }}
                </el-checkbox>
              </el-checkbox-group>
              <el-divider />
              <el-button size="small" @click="selectAllColumns">全选</el-button>
              <el-button size="small" @click="deselectAllColumns">取消全选</el-button>
            </div>
          </el-popover>
        </div>
      </div>
      <el-table
        ref="tableRef"
        :data="filteredTableData"
        v-loading="tableLoading"
        style="width: 100%"
        :default-sort="{ prop: sortState.prop, order: sortState.order || undefined }"
        @sort-change="handleSortChange"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
        highlight-current-row
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="orderTime" label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="运单号" width="160">
          <template #default="{ row }">
            <span
              :style="styleFor(row, 'tracking')"
              class="copyable-cell"
              @click.stop="copyText(row.trackingNumber)"
              :title="row.trackingNumber ? '点击复制' : ''"
            >{{ row.trackingNumber }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" width="160">
          <template #default="{ row }">
            <span
              :style="styleFor(row, 'model')"
              class="copyable-cell"
              @click.stop="copyText(row.model)"
              :title="row.model ? '点击复制' : ''"
            >{{ row.model }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sn" label="SN" width="200">
          <template #default="{ row }">
            <span
              class="sn-text copyable-cell"
              :style="styleFor(row, 'sn')"
              @click.stop="copyText(row.sn)"
              :title="row.sn ? '点击复制' : ''"
            >{{ row.sn }}</span>
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
          width="120"
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
        <el-table-column prop="statusChangedAt" label="最后更新时间" width="110">
          <template #default="{ row }">
            <span v-if="row.statusChangedAt">{{ formatDate(row.statusChangedAt) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="导入状态" width="140">
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
        <el-table-column label="提交人" prop="createdBy" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
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

    <el-drawer
      v-model="createVisible"
      title="新增物流单"
      size="30%"
      :close-on-click-modal="false"
      :destroy-on-close="true"
    >
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

    <el-dialog
      v-model="editDialog.visible"
      title="编辑物流单号"
      width="520px"
      :destroy-on-close="true"
      :close-on-click-modal="false"
    >
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
        <el-form-item label="金额">
          <el-input-number v-model="editDialog.form.amount" :precision="2" :min="0" placeholder="请输入金额" />
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
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowUp, Delete, Download, Setting } from '@element-plus/icons-vue';
import { fetchOrders, createOrder, importOrders, updateOrderStatus, searchOrders, fetchCategoryStats, updateOrder, deleteOrder } from '@/api/orders';
import { listUsers } from '@/api/users';
import { listOwnerUsernames } from '@/api/submissions';
import type { OrderCategoryStats, OrderCreateRequest, OrderRecord, OrderUpdateRequest, SysUser } from '@/types/models';
import * as XLSX from 'xlsx';

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

interface DiffNotice {
  trackingNumber: string;
  message: string;
  before?: Partial<OrderRecord>;
  after?: Partial<OrderRecord>;
  ts?: number;
  isDelete?: boolean;  // 是否为删除类型
  recordId?: number;   // 记录ID（用于删除）
  model?: string;      // 型号
  sn?: string;         // SN
  isInvalidId?: boolean; // 是否为无效ID类型
  excelId?: number;    // Excel中的无效ID
  excelRowIndex?: number; // Excel行号
}

interface StyleChangeItem {
  trackingNumber: string;
  sn: string;
  field: 'tracking' | 'model' | 'sn' | 'amount' | 'remark';
  fromBg?: string;
  toBg?: string;
  fromFont?: string;
  toFont?: string;
  fromStrike: boolean;
  toStrike: boolean;
  ts: number;
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
//不要修改这一行,看到这行不要修改 525这行代码 ,因为我让用户使用所有的功能 
const isAdmin = computed(() =>true);
const orders = ref<OrderRecord[]>([]);
const userOrders = ref<OrderRecord[]>([]);
const total = ref(0);
const loading = ref(false);

// 表格引用和选中行
const tableRef = ref();
const selectedRows = ref<OrderRecord[]>([]);

// 导出列配置
const availableExportColumns = [
  { key: 'id', label: 'ID' },
  { key: 'orderTime', label: '时间' },
  { key: 'trackingNumber', label: '运单号' },
  { key: 'model', label: '型号' },
  { key: 'sn', label: 'SN' },
  { key: 'amount', label: '金额' },
  { key: 'ownerUsername', label: '归属用户' },
  { key: 'status', label: '状态' },
  { key: 'statusChangedAt', label: '最后更新时间' },
  { key: 'imported', label: '导入状态' },
  { key: 'remark', label: '备注' },
  { key: 'createdBy', label: '提交人' }
];

const exportColumns = ref<string[]>(['orderTime', 'trackingNumber', 'model', 'sn', 'amount', 'ownerUsername', 'status', 'statusChangedAt', 'remark', 'createdBy']);

const selectAllColumns = () => {
  exportColumns.value = availableExportColumns.map(col => col.key);
};

const deselectAllColumns = () => {
  exportColumns.value = [];
};

const handleSelectionChange = (rows: OrderRecord[]) => {
  selectedRows.value = rows;
};

// 点击行选中/取消选中
const handleRowClick = (row: OrderRecord, column: any, event: Event) => {
  // 如果点击的是编辑按钮或复选框本身，不处理
  const target = event.target as HTMLElement;
  if (target.closest('.el-button') || target.closest('.el-checkbox')) {
    return;
  }
  
  // 切换选中状态
  tableRef.value?.toggleRowSelection(row);
};

// 导出 Excel 功能
const handleExportExcel = async (mode: 'all' | 'selected') => {
  let dataToExport: OrderRecord[] = [];
  
  if (mode === 'selected') {
    if (!selectedRows.value.length) {
      ElMessage.warning('请先选择要导出的数据');
      return;
    }
    dataToExport = selectedRows.value;
  } else {
    // 全部导出：获取所有数据
    ElMessage.info({ message: '正在准备导出数据...', duration: 2000 });
    try {
      dataToExport = await fetchAllOrders();
    } catch (error) {
      ElMessage.error('获取数据失败');
      return;
    }
  }

  if (!dataToExport.length) {
    ElMessage.warning('没有可导出的数据');
    return;
  }

  if (!exportColumns.value.length) {
    ElMessage.warning('请至少选择一个导出列');
    return;
  }

  // 构建表头
  const headers = exportColumns.value.map(key => {
    const col = availableExportColumns.find(c => c.key === key);
    return col?.label || key;
  });

  // 构建数据行
  const rows = dataToExport.map(record => {
    return exportColumns.value.map(key => {
      const value = (record as any)[key];
      
      // 特殊字段格式化
      if (key === 'orderTime') {
        return formatDateTime(value);
      }
      if (key === 'statusChangedAt') {
        return value ? formatDate(value) : '-';
      }
      if (key === 'status') {
        return statusLabel(value);
      }
      if (key === 'imported') {
        return value ? '已录入系统' : '-';
      }
      if (key === 'amount') {
        return value !== null && value !== undefined ? Number(value).toFixed(2) : '';
      }
      
      return value ?? '';
    });
  });

  // 创建工作表
  const wsData = [headers, ...rows];
  const ws = XLSX.utils.aoa_to_sheet(wsData);

  // 设置列宽
  const colWidths = exportColumns.value.map(key => {
    const widthMap: Record<string, number> = {
      id: 10,
      orderTime: 20,
      trackingNumber: 25,
      model: 20,
      sn: 25,
      amount: 12,
      ownerUsername: 15,
      status: 12,
      statusChangedAt: 15,
      imported: 15,
      remark: 30,
      createdBy: 15
    };
    return { wch: widthMap[key] || 15 };
  });
  ws['!cols'] = colWidths;

  // 设置单元格格式 - 居中对齐
  const range = XLSX.utils.decode_range(ws['!ref'] || 'A1');
  for (let R = range.s.r; R <= range.e.r; R++) {
    for (let C = range.s.c; C <= range.e.c; C++) {
      const cellAddress = XLSX.utils.encode_cell({ r: R, c: C });
      if (!ws[cellAddress]) continue;
      
      // 设置单元格样式
      ws[cellAddress].s = {
        alignment: {
          horizontal: 'center',
          vertical: 'center',
          wrapText: true
        },
        font: R === 0 ? { bold: true, sz: 12 } : { sz: 11 },
        border: {
          top: { style: 'thin', color: { rgb: '000000' } },
          bottom: { style: 'thin', color: { rgb: '000000' } },
          left: { style: 'thin', color: { rgb: '000000' } },
          right: { style: 'thin', color: { rgb: '000000' } }
        }
      };
      
      // 表头特殊样式
      if (R === 0) {
        ws[cellAddress].s.fill = {
          fgColor: { rgb: '4472C4' },
          patternType: 'solid'
        };
        ws[cellAddress].s.font.color = { rgb: 'FFFFFF' };
      }
    }
  }

  // 设置行高
  ws['!rows'] = [];
  for (let R = range.s.r; R <= range.e.r; R++) {
    ws['!rows'][R] = { hpt: R === 0 ? 25 : 20 };
  }

  // 创建工作簿
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, '订单数据');

  // 生成文件名
  const timestamp = new Date().toISOString().slice(0, 10);
  const filename = mode === 'selected' 
    ? `订单数据-选中${dataToExport.length}条-${timestamp}.xlsx`
    : `订单数据-全部${dataToExport.length}条-${timestamp}.xlsx`;

  // 下载文件
  XLSX.writeFile(wb, filename);

  ElMessage.success({
    message: `成功导出 ${dataToExport.length} 条数据`,
    duration: 3000
  });
};

// 差异提醒列表
const diffNotices = ref<DiffNotice[]>([]); // UI显示用（最新200条）
const diffNoticesAll = ref<DiffNotice[]>([]); // 完整列表（用于导出）
const DIFF_NOTICES_KEY = 'orders-diff-notices';
const DIFF_NOTICES_ALL_KEY = 'orders-diff-notices-all';

// 已确认的变更记录（用于避免重复提醒）
interface AcknowledgedChange {
  recordId?: number;           // 记录ID
  trackingNumber: string;      // 运单号
  field: string;               // 字段名
  value: string;               // 字段值
  styleValue?: string;         // 样式值（如果是样式变更）
  acknowledgedAt: number;      // 确认时间戳
}
const acknowledgedChanges = ref<AcknowledgedChange[]>([]);
const ACKNOWLEDGED_CHANGES_KEY = 'orders-acknowledged-changes';
const MAX_ACKNOWLEDGED_CHANGES = 1000; // 最多保留1000条已确认记录

// 加载已确认的变更记录
const loadAcknowledgedChanges = () => {
  try {
    const raw = localStorage.getItem(ACKNOWLEDGED_CHANGES_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        acknowledgedChanges.value = parsed;
      }
    }
  } catch (e) {
    console.warn('加载已确认变更记录失败', e);
  }
};

// 保存已确认的变更记录
const saveAcknowledgedChanges = () => {
  try {
    // 只保留最近的记录，避免占用过多存储空间
    const toSave = acknowledgedChanges.value.slice(0, MAX_ACKNOWLEDGED_CHANGES);
    localStorage.setItem(ACKNOWLEDGED_CHANGES_KEY, JSON.stringify(toSave));
  } catch (e) {
    console.warn('保存已确认变更记录失败', e);
  }
};

const loadDiffNoticesFromCache = () => {
  try {
    // 加载完整列表（用于导出）
    const rawAll = localStorage.getItem(DIFF_NOTICES_ALL_KEY);
    const raw = localStorage.getItem(DIFF_NOTICES_KEY);

    let allData: any[] = [];
    let displayData: any[] = [];

    // 优先从完整列表加载
    if (rawAll) {
      const parsedAll = JSON.parse(rawAll);
      if (Array.isArray(parsedAll)) {
        let baseTime = Date.now();
        allData = parsedAll
          .map((n: any, index: number) => ({
            ...n,
            // 如果没有时间戳，为每条记录分配唯一的时间戳（递减）
            ts: typeof n?.ts === 'number' ? n.ts : baseTime - index
          }))
          .sort((a: any, b: any) => (b.ts || 0) - (a.ts || 0));
      }
    }

    // 加载显示列表
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        let baseTime = Date.now();
        displayData = parsed
          .map((n: any, index: number) => ({
            ...n,
            // 如果没有时间戳，为每条记录分配唯一的时间戳（递减）
            ts: typeof n?.ts === 'number' ? n.ts : baseTime - index
          }))
          .sort((a: any, b: any) => (b.ts || 0) - (a.ts || 0));
      }
    }

    // 数据迁移：如果完整列表为空，但显示列表有数据，说明是旧版本数据
    if (allData.length === 0 && displayData.length > 0) {
      // 从显示列表迁移到完整列表
      diffNoticesAll.value = displayData;
      diffNotices.value = displayData.slice(0, MAX_DIFF_NOTICES);
      // 立即保存到新格式
      saveDiffNoticesToCache();
      ElMessage.info({
        message: `已从旧版本迁移 ${displayData.length} 条变更提醒`,
        duration: 3000
      });
    } else {
      // 正常加载
      diffNoticesAll.value = allData;
      diffNotices.value = displayData;
    }
  } catch (e) {
    console.warn('加载变更提醒缓存失败', e);
  }
};

const saveDiffNoticesToCache = () => {
  try {
    // 保存UI显示列表（最新200条）
    const data = JSON.stringify(diffNotices.value || []);
    localStorage.setItem(DIFF_NOTICES_KEY, data);

    // 保存完整列表（用于导出）
    const dataAll = JSON.stringify(diffNoticesAll.value || []);
    localStorage.setItem(DIFF_NOTICES_ALL_KEY, dataAll);
  } catch (e: any) {
    // localStorage 超限或不可用时的降级处理
    if (e.name === 'QuotaExceededError' || e.code === 22) {
      console.warn('localStorage 超限，尝试清理旧数据后重试');
      try {
        // UI列表只保留最新50条
        diffNotices.value = diffNotices.value.slice(0, 50);
        const data = JSON.stringify(diffNotices.value);
        localStorage.setItem(DIFF_NOTICES_KEY, data);

        // 完整列表保留最新500条（用于导出）
        diffNoticesAll.value = diffNoticesAll.value.slice(0, 500);
        const dataAll = JSON.stringify(diffNoticesAll.value);
        localStorage.setItem(DIFF_NOTICES_ALL_KEY, dataAll);

        ElMessage.warning({
          message: '存储空间不足，已自动清理部分历史提醒',
          duration: 3000
        });
      } catch (retryError) {
        console.error('清理后仍无法保存，已放弃缓存:', retryError);
        // 彻底清空以避免下次加载出错
        localStorage.removeItem(DIFF_NOTICES_KEY);
        localStorage.removeItem(DIFF_NOTICES_ALL_KEY);
      }
    } else {
      console.warn('保存变更提醒缓存失败:', e);
    }
  }
};
















const userSearchInput = ref('');
const userSearchLoading = ref(false);
const userSearchDebounce = ref<number | null>(null);
const adminSearchDebounce = ref<number | null>(null);
// 所有用户都使用同样的数据源
const tableData = computed(() => orders.value);
const tableLoading = computed(() => loading.value);
const USER_HISTORY_KEY = 'user-order-history';
const quickStatus = ref('');

// 用户下拉选项（从后端获取）
const userOptions = ref<SysUser[]>([]);
const userLoading = ref(false);

const loadUsers = async () => {
  // 所有用户都可以加载用户列表（用于归属用户筛选）
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
      // 发现重复 SN 时不再输出日志，避免控制台噪音
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
  // 加载本地缓存的变更提醒
  loadDiffNoticesFromCache();
  // 加载已确认的变更记录
  loadAcknowledgedChanges();
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
  form: { trackingNumber: '', model: '', sn: '', status: '', remark: '', amount: undefined } as OrderUpdateRequest
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
  // 取消正在进行的后台差异计算
  abortDiffCalculation();

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
    // 立即执行搜索
    loadOrders();
  }
};

const clearStatusFilter = async () => {
  // 取消正在进行的后台差异计算
  abortDiffCalculation();

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
  // 取消正在进行的后台差异计算（如果有）
  abortDiffCalculation();

  // 所有登录用户都可以加载订单数据
  loading.value = true;
  try {
    const params = queryParams.value;
    const data = await fetchOrders(params);
    // 直接使用后端返回的数据，不做任何去重处理
    orders.value = data.records || [];
    total.value = data.total || 0;

    // 优化：不需要每次都清空样式缓存，因为缓存key包含了记录ID
    // 只在导入新数据时清空缓存即可（见 handleFileChange）
    // styleCache.clear(); // 已移除
  } catch (error) {
    console.error('❌ 加载订单失败:', error);
    orders.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleSearch = async () => {
  // 取消正在进行的后台差异计算
  abortDiffCalculation();

  // 统一在点击查询时进行清洗，避免Excel前缀等脏数据
  filters.keyword = sanitizeSingleInput(filters.keyword);
  filters.page = 1;
  // 所有用户都使用统一的查询接口
  await loadOrders();
};

const triggerAdminAutoSearch = () => {
  // 所有用户都可以使用自动搜索
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
  // 取消正在进行的后台差异计算
  abortDiffCalculation();

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
  // 所有用户都可以批量导入
  fileInput.value?.click();
};



// 获取所有订单（遵守后端每页最大1000条的限制并分页拉取）
// 使用 ID 升序排序确保分页稳定，避免漏掉记录
const fetchAllOrders = async (targetIds?: Set<number>): Promise<OrderRecord[]> => {
  const all: OrderRecord[] = [];
  const pageSize = 1000; // OrderController 会将 size>1000 回退为50，这里主动按上限分页
  let page = 1;
  let lastId = 0; // 用于基于游标的分页（备用方案）
  const seenIds = new Set<number>(); // 防止重复

  // targetIds 用于差异比对时的早停：找到全部目标ID后即可退出分页请求
  const needIds = targetIds ? new Set(Array.from(targetIds).filter(id => !!id)) : undefined;

  try {
    while (true) {
      // 使用 ID 排序确保分页稳定性，避免同日期数据导致的分页不稳定
      const data = await fetchOrders({ 
        page, 
        size: pageSize,
        sortBy: 'orderDate',  // 保持日期排序
        sortOrder: 'desc'
      });
      const records = data.records || [];
      
      // 过滤重复记录（防止分页边界问题）
      const newRecords = records.filter(r => {
        if (!r.id || seenIds.has(r.id)) return false;
        seenIds.add(r.id);
        return true;
      });
      
      all.push(...newRecords);

      if (needIds) {
        newRecords.forEach(r => {
          if (r.id && needIds.has(r.id)) {
            needIds.delete(r.id);
          }
        });
        if (needIds.size === 0) break; // 所有目标记录已获取，提前结束
      }

      if (records.length < pageSize) break; // 已到最后一页
      
      // 安全检查：如果连续3页没有新数据，说明可能出现问题，停止
      if (newRecords.length === 0 && page > 1) {
        console.warn('fetchAllOrders: 连续获取到重复数据，停止分页');
        break;
      }
      
      page += 1;
      
      // 防止无限循环
      if (page > 1000) {
        console.warn('fetchAllOrders: 已达到最大分页数1000，停止获取');
        break;
      }
    }
  } catch (error) {
    console.error('Failed to fetch all orders:', error);
  }

  console.log(`fetchAllOrders: 共获取 ${all.length} 条记录，分 ${page} 页`);
  return all;
};

// 按ID列表批量获取订单（分页拉取，避免被单页上限截断）
const fetchOrdersByIds = async (ids: number[]): Promise<OrderRecord[]> => {
  if (!ids || ids.length === 0) return [];

  const idSet = new Set(ids.filter(id => typeof id === 'number'));
  if (idSet.size === 0) return [];

  try {
    const allOrders = await fetchAllOrders(idSet);
    return allOrders.filter(order => order.id && idSet.has(order.id));
  } catch (error) {
    console.error('Failed to fetch orders by IDs:', error);
    return [];
  }
};

const captureDiffSnapshot = async () => {
  const all = await fetchAllOrders();
  return buildOrderSnapshot(all);
};

// 差异提醒数量上限（避免内存和存储问题）
const MAX_DIFF_NOTICES = 200;

// 合并差异提醒
const mergeDiffNotices = (newNotices: DiffNotice[]) => {
  if (!newNotices.length) {
    return;
  }

  const existing = new Map<string, DiffNotice>();

  // 从完整列表合并（而不是从显示列表）
  diffNoticesAll.value.forEach(item => {
    // 使用记录ID作为键的一部分，确保每条记录的变更都能保留
    const recordId = (item.after as any)?.id || (item.before as any)?.id;
    const key = recordId ? `ID-${recordId}` : `${(item.trackingNumber || '').toUpperCase()}-${item.message}`;
    existing.set(key, item);
  });

  newNotices.forEach(item => {
    // 使用记录ID作为键的一部分，确保每条记录的变更都能保留
    const recordId = (item.after as any)?.id || (item.before as any)?.id;
    const key = recordId ? `ID-${recordId}` : `${(item.trackingNumber || '').toUpperCase()}-${item.message}`;
    item.ts = Date.now();
    existing.set(key, item);
  });

  // 按变更类型和时间排序：删除类型优先，然后按时间倒序
  const allNotices = Array.from(existing.values()).sort((a, b) => {
    // 删除类型优先
    if (a.isDelete && !b.isDelete) return -1;
    if (!a.isDelete && b.isDelete) return 1;
    // 同类型按时间倒序
    return (b.ts || 0) - (a.ts || 0);
  });

  // 保存完整列表（用于导出）
  diffNoticesAll.value = allNotices;

  // UI只显示最新的 MAX_DIFF_NOTICES 条
  diffNotices.value = allNotices.slice(0, MAX_DIFF_NOTICES);

  // 如果超过上限，提示用户（可导出全部）
  if (allNotices.length > MAX_DIFF_NOTICES) {
    console.warn(`差异提醒过多，已自动限制为最新 ${MAX_DIFF_NOTICES} 条（共发现 ${allNotices.length} 条）`);
    ElMessage.warning({
      message: `发现 ${allNotices.length} 处变更，仅显示最新 ${MAX_DIFF_NOTICES} 条（可导出全部）`,
      duration: 5000,
      showClose: true
    });
  }

  saveDiffNoticesToCache();
};

// 清空差异提醒
const clearDiffNotices = () => {
  // 将所有提醒标记为已确认（避免下次重复提醒）
  diffNoticesAll.value.forEach(notice => {
    if (notice.after) {
      const recordId = (notice.after as any)?.id;
      const trackingNumber = notice.trackingNumber || '';
      const changedFields = diffFields(notice);

      changedFields.forEach(fieldLabel => {
        const fieldMap: Record<string, string> = {
          '运单号': 'trackingNumber',
          '型号': 'model',
          'SN': 'sn',
          '金额': 'amount',
          '备注': 'remark'
        };

        let field = '';
        let value = '';
        let styleValue = '';

        if (fieldLabel.endsWith('(样式)')) {
          const baseField = fieldLabel.replace('(样式)', '');
          field = fieldMap[baseField];
          if (field) {
            const bgKey = `${field}BgColor` as any;
            const fgKey = `${field}FontColor` as any;
            const strikeKey = `${field}Strike` as any;

            const bg = (notice.after as any)?.[bgKey] || '';
            const fg = (notice.after as any)?.[fgKey] || '';
            const strike = (notice.after as any)?.[strikeKey] || false;

            styleValue = JSON.stringify({ bg, fg, strike });
            field = `${field}Style`;
          }
        } else {
          field = fieldMap[fieldLabel];
          if (field) {
            value = String((notice.after as any)?.[field] || '');
          }
        }

        if (field) {
          acknowledgedChanges.value.push({
            recordId,
            trackingNumber,
            field,
            value,
            styleValue: styleValue || undefined,
            acknowledgedAt: Date.now()
          });
        }
      });
    }
  });

  // 限制已确认记录数量
  if (acknowledgedChanges.value.length > MAX_ACKNOWLEDGED_CHANGES) {
    acknowledgedChanges.value = acknowledgedChanges.value.slice(0, MAX_ACKNOWLEDGED_CHANGES);
  }

  saveAcknowledgedChanges();

  // 清空提醒列表
  diffNotices.value = [];
  diffNoticesAll.value = [];
  try {
    localStorage.removeItem(DIFF_NOTICES_KEY);
    localStorage.removeItem(DIFF_NOTICES_ALL_KEY);
  } catch {}
  ElMessage.success('已清空变更提醒');
};

// 删除单条差异提醒
const removeDiffNotice = (row: DiffNotice) => {
  if (!row) return;

  // 记录已确认的变更（避免下次导入时重复提醒）
  // 特殊类型（删除、无效ID）直接标记确认，不需要详细字段信息
  if (row.isDelete || row.isInvalidId) {
    const recordId = row.recordId || (row.before as any)?.id;
    const trackingNumber = row.trackingNumber || '';
    const sn = row.sn || (row.before as any)?.sn || '';

    // 为特殊类型创建一个标记
    const specialType = row.isDelete ? 'DELETE' : 'INVALID_ID';

    acknowledgedChanges.value.unshift({
      recordId,
      trackingNumber,
      field: `__SPECIAL__${specialType}`,
      value: sn || trackingNumber,
      acknowledgedAt: Date.now()
    });

    saveAcknowledgedChanges();
  } else if (row.after) {
    const recordId = (row.after as any)?.id;
    const trackingNumber = row.trackingNumber || '';

    // 提取所有发生变更的字段
    const changedFields = diffFields(row);

    changedFields.forEach(fieldLabel => {
      // 将字段标签转换为字段名
      const fieldMap: Record<string, string> = {
        '运单号': 'trackingNumber',
        '型号': 'model',
        'SN': 'sn',
        '金额': 'amount',
        '备注': 'remark'
      };

      let field = '';
      let value = '';
      let styleValue = '';

      // 判断是否为样式变更
      if (fieldLabel.endsWith('(样式)')) {
        const baseField = fieldLabel.replace('(样式)', '');
        field = fieldMap[baseField];
        if (field) {
          // 记录样式值
          const bgKey = `${field}BgColor` as any;
          const fgKey = `${field}FontColor` as any;
          const strikeKey = `${field}Strike` as any;

          const bg = (row.after as any)?.[bgKey] || '';
          const fg = (row.after as any)?.[fgKey] || '';
          const strike = (row.after as any)?.[strikeKey] || false;

          styleValue = JSON.stringify({ bg, fg, strike });
          field = `${field}Style`;
        }
      } else {
        field = fieldMap[fieldLabel];
        if (field) {
          value = String((row.after as any)?.[field] || '');
        }
      }

      if (field) {
        // 添加到已确认列表
        acknowledgedChanges.value.unshift({
          recordId,
          trackingNumber,
          field,
          value,
          styleValue: styleValue || undefined,
          acknowledgedAt: Date.now()
        });
      }
    });

    // 限制已确认记录数量
    if (acknowledgedChanges.value.length > MAX_ACKNOWLEDGED_CHANGES) {
      acknowledgedChanges.value = acknowledgedChanges.value.slice(0, MAX_ACKNOWLEDGED_CHANGES);
    }

    saveAcknowledgedChanges();
  }

  // 生成唯一标识键，与 mergeDiffNotices 中的逻辑保持一致
  const getUniqueKey = (item: DiffNotice) => {
    const recordId = (item.after as any)?.id || (item.before as any)?.id;
    if (recordId) {
      return `ID-${recordId}`;
    }
    return `${(item.trackingNumber || '').toUpperCase()}-${item.message}-${item.ts || 0}`;
  };

  const rowKey = getUniqueKey(row);

  const filterFn = (n: DiffNotice) => {
    const nKey = getUniqueKey(n);
    return nKey !== rowKey;
  };

  // 同时从两个列表中删除
  diffNotices.value = diffNotices.value.filter(filterFn);
  diffNoticesAll.value = diffNoticesAll.value.filter(filterFn);

  // 如果显示列表少于最大数量，且完整列表还有更多，则补充显示
  if (diffNotices.value.length < MAX_DIFF_NOTICES && diffNoticesAll.value.length > diffNotices.value.length) {
    // 从完整列表中取前 MAX_DIFF_NOTICES 条作为显示列表
    diffNotices.value = diffNoticesAll.value.slice(0, MAX_DIFF_NOTICES);
  }

  saveDiffNoticesToCache();
 
};

// 确认删除订单及关联数据
const handleConfirmDelete = async (row: DiffNotice) => {
  if (!row.recordId) {
    ElMessage.error('缺少记录ID，无法删除');
    return;
  }

  try {
    // 显示确认对话框
    await ElMessageBox.confirm(
      `确认删除以下记录及其关联数据？\n\n运单号: ${row.trackingNumber}\n型号: ${row.model || '-'}\nSN: ${row.sn || '-'}\n\n此操作将同时删除：\n• 订单记录\n• 订单样式\n• 结账记录\n\n此操作不可撤销！`,
      '确认删除',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: false
      }
    );

    // 调用删除API
    await deleteOrder(row.recordId);

    // 从两个提醒列表中移除
    diffNotices.value = diffNotices.value.filter(n => n.recordId !== row.recordId);
    diffNoticesAll.value = diffNoticesAll.value.filter(n => n.recordId !== row.recordId);
    saveDiffNoticesToCache();

    // 刷新订单列表
    await loadOrders();

    ElMessage.success('删除成功');
  } catch (error: any) {
    if (error === 'cancel') {
      ElMessage.info('已取消删除');
    } else {
      console.error('删除失败:', error);
      ElMessage.error('删除失败: ' + (error.message || '未知错误'));
    }
  }
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
  // 取消正在进行的后台差异计算
  abortDiffCalculation();

  // 所有用户都可以处理文件上传
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  // 导入前捕获快照
  let prevSnapshot: Map<number, Partial<OrderRecord>> | undefined;
  let isFirstImport = false;

  try {
    prevSnapshot = await captureDiffSnapshot();

    // 判断是否为首次导入（数据库为空）
    if (prevSnapshot.size === 0) {
      isFirstImport = true;
    } else {
      // 只在非首次导入时显示准备对比的提示
      ElMessage.info({
        message: '正在准备数据对比...',
        duration: 2000
      });
    }
  } catch (error) {
    console.warn('Failed to capture snapshot:', error);
  }

  startImportProgress();
  try {
    const report: any = await importOrders(file);

    // 清空旧的样式缓存，确保使用新导入的样式
    styleCache.clear();

    // 解析样式信息：仅本次会话用于展示（仅传回了发生变化的行）
    try {
      const styles: ImportStyle[] = report?.styles || [];
      const map = new Map<string, ImportStyle>();
      styles.forEach(s => {
        // 仅按记录ID缓存样式，避免同一tracking+SN的其他旧记录被新样式"覆盖显示"
        if ((s as any).id) {
          map.set(`ID-${(s as any).id}`, s);
        }
      });
      importStyles.value = map;
    } catch {}

    finishImportProgress();
    const skipped = Number(report?.skippedUnchanged || 0);
    const imported = Number(report?.importedCount || 0);

    // 首次导入时提示不同的消息
    if (isFirstImport) {
      ElMessage.success({
        message: `首次导入完成：成功导入 ${imported} 条记录`,
        duration: 5000,
        showClose: true
      });
    } 

    // 处理删除的记录（仅在非首次导入时）
    if (!isFirstImport && Array.isArray(report?.deletedRecords) && report.deletedRecords.length > 0) {
      const deletedNotices = report.deletedRecords
        .filter((deleted: any) => {
          // 检查是否已确认过这个删除
          const recordId = deleted.id;
          const trackingNumber = (deleted.trackingNumber || '').toUpperCase();
          const sn = deleted.sn || '';

          return !acknowledgedChanges.value.some(ack =>
            ack.field === '__SPECIAL__DELETE' &&
            (ack.recordId === recordId || ack.trackingNumber.toUpperCase() === trackingNumber) &&
            (ack.value === sn || ack.value === trackingNumber)
          );
        })
        .map((deleted: any) => ({
          trackingNumber: deleted.trackingNumber || '未知',
          model: deleted.model,
          sn: deleted.sn,
          message: `🗑️ Excel中已删除，需要确认是否从数据库删除`,
          isDelete: true,
          recordId: deleted.id,
          before: deleted,
          after: null,
          ts: Date.now()
        }));

      if (deletedNotices.length > 0) {
        mergeDiffNotices(deletedNotices);
        ElMessage.warning({
          message: `检测到 ${deletedNotices.length} 条记录在Excel中已删除，请查看提醒并确认`,
          duration: 8000,
          showClose: true
        });
      }
    }

    // 处理新增ID（Excel中填写了新ID，如最大值+1）
    // 由于后端已改为保留Excel中的ID，这段代码通常不会被触发
    // 如果触发，说明用户填写了新的ID（正常情况），不需要警告
    if (!isFirstImport && Array.isArray(report?.invalidIds) && report.invalidIds.length > 0) {
      // 不再显示警告和差异提醒，因为这是用户主动填写的新ID
    }

    // 刷新当前页数据
    await loadOrders();

    // 判断是否需要进行差异计算
    // 如果本次导入没有实质性变化（后端已做检测），则跳过前端差异计算
    const hasRealChanges = imported > 0 ||
                          (report?.deletedRecords && report.deletedRecords.length > 0) ||
                          (report?.invalidIds && report.invalidIds.length > 0);

    // 获取变化记录的ID列表（后端已经标记了哪些记录变化）
    const changedIds: number[] = report?.changedIds || [];

    // 🔍 调试日志：检查差异计算条件
    // 调试信息已移除，避免控制台噪音

    // 异步计算差异（仅在非首次导入、有快照且有实质性变化时）
    if (!isFirstImport && prevSnapshot && prevSnapshot.size > 0 && hasRealChanges && changedIds.length > 0) {
      // 使用 requestIdleCallback 在浏览器空闲时计算
      if ('requestIdleCallback' in window) {
        requestIdleCallback(async () => {
          await performDiffCalculation(prevSnapshot!, changedIds);
        });
      } else {
        // 降级方案：延迟执行
        setTimeout(async () => {
          await performDiffCalculation(prevSnapshot!, changedIds);
        }, 500);
      }
    } else if (!isFirstImport && !hasRealChanges) {
      // 如果没有实质性变化，提示用户
      // 后端检测到数据未变化，跳过差异计算
    }

    // 记录本次导入时间戳
    localStorage.setItem('last-import-timestamp', String(Date.now()));

  } catch (error) {
    finishImportProgress();
    throw error;
  } finally {
    target.value = '';
  }
};

// 差异计算任务控制
let diffCalculationAborted = false;
let isDiffCalculating = false;

// 取消正在进行的差异计算
const abortDiffCalculation = () => {
  if (isDiffCalculating) {
    diffCalculationAborted = true;
  }
};

// 独立的差异计算函数（优化性能 + 可中断）
const performDiffCalculation = async (prevSnapshot: Map<number, Partial<OrderRecord>>, changedIds: number[]) => {
  // 标记计算开始
  isDiffCalculating = true;
  diffCalculationAborted = false;

  try {
    ElMessage.info({
      message: `正在对比 ${changedIds.length} 条变更记录...`,
      duration: 3000
    });

    // 优化：只获取变化的记录，而不是全部记录
    const changedRecords = await fetchOrdersByIds(changedIds);

    // 检查是否已被取消
    if (diffCalculationAborted) {
      return;
    }

    // 计算差异（只对比变化的记录）
    const diffs = computeDifferences(prevSnapshot, changedRecords, importStyles.value);

    if (diffs.length > 0) {
      mergeDiffNotices(diffs);
    }

    // 只有未被取消才显示完成消息
    if (!diffCalculationAborted) {
      if (diffs.length > 0) {
        // 提取前3条变更的运单号
        const sampleTrackingNumbers = diffs
          .slice(0, 3)
          .map(diff => diff.trackingNumber || '未知')
          .join('、');

        const moreCount = diffs.length > 3 ? ` 等${diffs.length}条` : '';

      }
    }
  } catch (error) {
    if (!diffCalculationAborted) {
      console.warn('数据对比失败:', error);
      ElMessage.error({
        message: '数据对比失败',
        duration: 2000
      });
    }
  } finally {
    isDiffCalculating = false;
    diffCalculationAborted = false;
  }
};

const openCreateDrawer = () => {
  // 所有用户都可以新增订单
  createVisible.value = true;
};

const openEditDialog = (row: OrderRecord) => {
  // 所有用户都可以编辑订单
  editDialog.targetId = row.id;
  editDialog.form.trackingNumber = row.trackingNumber;
  editDialog.form.model = row.model ?? '';
  editDialog.form.sn = row.sn ?? '';
  editDialog.form.status = row.status ?? '';
  editDialog.form.remark = row.remark ?? '';
  editDialog.form.amount = row.amount ?? undefined;
  editDialog.form.version = row.version ?? 0; // 保存版本号
  editDialog.visible = true;
};

const submitCreate = async () => {
  // 所有用户都可以提交创建
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
  // 所有用户都可以修改订单状态
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

const formatDate = (value?: string) => {
  if (!value) return '';
  return value.substring(0, 10);
};

// 复制文本到剪贴板
const copyText = (text?: string) => {
  if (!text) return;
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制');
  });
};

const buildOrderSnapshot = (list: OrderRecord[]) => {
  // 使用 ID 作为 key，这样即使运单号、SN等所有字段都改了，也能通过ID匹配到同一条记录
  const map = new Map<number, Partial<OrderRecord>>();
  list.forEach(item => {
    if (!item.id) return; // 没有ID的记录跳过

    map.set(item.id, {
      id: item.id,
      trackingNumber: item.trackingNumber,
      model: item.model,
      sn: item.sn,
      amount: item.amount,
      remark: item.remark,
      // 包含所有字段的样式信息
      trackingBgColor: (item as any).trackingBgColor,
      trackingFontColor: (item as any).trackingFontColor,
      trackingStrike: (item as any).trackingStrike,
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

const computeDifferences = (prevMap: Map<number, Partial<OrderRecord>>, nextList: OrderRecord[], importedStyles?: Map<string, ImportStyle>) => {
  if (!prevMap.size) {
    return [];
  }

  const fieldLabels: Record<string, string> = {
    trackingNumber: '运单号',
    model: '型号',
    sn: 'SN',
    amount: '金额',
    remark: '备注'
  };
  const notices: { trackingNumber: string; message: string; before?: Partial<OrderRecord>; after?: Partial<OrderRecord> }[] = [];

  // 优化：值规范化函数提取到外部，避免重复创建
  const normalizeVal = (val: unknown) => {
    if (val === null || val === undefined) return '';
    if (typeof val === 'string') return val.trim();
    return String(val);
  };

  // 检测是否包含中文字符
  const containsChinese = (str: string | null | undefined) => {
    if (!str) return false;
    return /[\u4e00-\u9fa5]/.test(str);
  };

  // 为中文记录构建组合键Map（运单号+SN+时间）
  const chineseKeyMap = new Map<string, Partial<OrderRecord>>();
  const makeCompositeKey = (r: Partial<OrderRecord>) =>
    `${r.trackingNumber || ''}|${r.sn || ''}|${r.orderTime || ''}`;

  prevMap.forEach((record) => {
    if (containsChinese(record.trackingNumber) || containsChinese(record.sn)) {
      chineseKeyMap.set(makeCompositeKey(record), record);
    }
  });

  // 样式值规范化：将空值、白色、黑色统一为空字符串
  const normalizeStyleVal = (val: unknown, isColor: boolean = true) => {
    if (val === null || val === undefined || val === '') return '';
    const str = String(val).trim().toUpperCase();
    if (isColor) {
      // 白色的各种表示都视为无背景色
      if (str === '#FFFFFF' || str === '#FFF' || str === 'FFFFFF' || str === 'FFF' || str === 'WHITE') {
        return '';
      }
      // 黑色的各种表示都视为无字体色
      if (str === '#000000' || str === '#000' || str === '000000' || str === '000' || str === 'BLACK') {
        return '';
      }
    }
    return str;
  };

  // 遍历新记录，通过ID匹配旧记录
  // 开始对比差异

  nextList.forEach(order => {
    if (!order.id) {
      console.warn('⚠️ 跳过没有ID的记录:', order.trackingNumber);
      return;
    }

    // 中文记录使用组合键匹配，非中文记录使用ID匹配
    const isChinese = containsChinese(order.trackingNumber) || containsChinese(order.sn);
    const prev = isChinese
      ? chineseKeyMap.get(makeCompositeKey(order))
      : prevMap.get(order.id);

    if (!prev) {
      // 中文记录如果组合键匹配不到，说明是新增的（不报新增提醒，因为中文记录每次导入都是新ID）
      if (isChinese) return;
      // 新增的记录
      // 新增记录提示已移除日志
      notices.push({
        trackingNumber: order.trackingNumber || `ID-${order.id}`,
        message: '🆕 新增记录',
        before: {},
        after: {
          id: order.id, // 包含ID，用于唯一标识
          trackingNumber: order.trackingNumber,
          model: order.model,
          sn: order.sn,
          amount: order.amount,
          remark: order.remark
        }
      });
      return;
    }

    const changed: string[] = [];
    const before: Partial<OrderRecord> = {};
    const after: Partial<OrderRecord> = {};

    // 检测内容变化（优化：使用 for...of 代替 forEach，性能更好）
    for (const field of Object.keys(fieldLabels)) {
      const prevVal = (prev as any)[field];
      const currVal = (order as any)[field];
      const prevNorm = normalizeVal(prevVal);
      const currNorm = normalizeVal(currVal);

      if (prevNorm !== currNorm) {
        changed.push(fieldLabels[field]);
        (before as any)[field] = prevVal;
        (after as any)[field] = currVal;
      }
    }
    
    // 检测样式变化（比较快照中的样式和当前数据库中的样式）
    // 支持的字段：trackingNumber, model, sn, amount, remark
    const styleFields = ['tracking', 'model', 'sn', 'amount', 'remark'];
    for (const field of styleFields) {
      const bgKey = `${field}BgColor` as keyof OrderRecord;
      const fgKey = `${field}FontColor` as keyof OrderRecord;
      const strikeKey = `${field}Strike` as keyof OrderRecord;

      // 规范化样式值进行比较
      const prevBg = normalizeStyleVal((prev as any)?.[bgKey], true);
      const prevFg = normalizeStyleVal((prev as any)?.[fgKey], true);
      const prevStrike = !!(prev as any)?.[strikeKey];

      // 使用当前数据库中的样式（已通过 attachStyles 加载）
      const currBg = normalizeStyleVal((order as any)?.[bgKey], true);
      const currFg = normalizeStyleVal((order as any)?.[fgKey], true);
      const currStrike = !!(order as any)?.[strikeKey];

      // 只有在规范化后仍有差异时才认为是变更
      if (prevBg !== currBg || prevFg !== currFg || prevStrike !== currStrike) {
        // 转换字段名为友好显示名称
        const displayField = field === 'tracking' ? 'trackingNumber' : field;
        const fieldLabel = fieldLabels[displayField] || displayField;
        const styleLabel = `${fieldLabel}(样式)`;
        if (!changed.includes(styleLabel)) {
          changed.push(styleLabel);

          // 保存样式变化信息到 before 和 after 对象
          (before as any)[bgKey] = prevBg || undefined;
          (before as any)[fgKey] = prevFg || undefined;
          (before as any)[strikeKey] = prevStrike;

          (after as any)[bgKey] = currBg || undefined;
          (after as any)[fgKey] = currFg || undefined;
          (after as any)[strikeKey] = currStrike;
        }
      }
    }

    if (changed.length) {
      // 确保 before 和 after 都包含 id 字段
      before.id = prev.id;
      after.id = order.id;

      notices.push({
        trackingNumber: order.trackingNumber || `ID-${order.id}`,
        message: `字段变更：${changed.join('、')}`,
        before,
        after
      });
    }
  });

  // 对比完成

  // 过滤已确认的变更（避免重复提醒）
  const filteredNotices = notices.filter(notice => {
    if (!notice.after) return true; // 保留删除类型的提醒

    // 🆕 新增记录总是显示，不过滤（即使之前有相同数据被清空过）
    if (notice.message && notice.message.includes('新增记录')) {
      return true;
    }

    const recordId = (notice.after as any)?.id;
    const trackingNumber = notice.trackingNumber || '';

    // 检查这个变更是否已经被确认过
    const changedFields = Object.keys(fieldLabels).filter(field => {
      const beforeVal = (notice.before as any)?.[field];
      const afterVal = (notice.after as any)?.[field];
      return String(beforeVal || '') !== String(afterVal || '');
    });

    const styleFields = ['tracking', 'model', 'sn', 'amount', 'remark'];
    const changedStyleFields = styleFields.filter(field => {
      const bgKey = `${field}BgColor`;
      const fgKey = `${field}FontColor`;
      const strikeKey = `${field}Strike`;

      const beforeBg = (notice.before as any)?.[bgKey] || '';
      const afterBg = (notice.after as any)?.[bgKey] || '';
      const beforeFg = (notice.before as any)?.[fgKey] || '';
      const afterFg = (notice.after as any)?.[fgKey] || '';
      const beforeStrike = !!(notice.before as any)?.[strikeKey];
      const afterStrike = !!(notice.after as any)?.[strikeKey];

      return beforeBg !== afterBg || beforeFg !== afterFg || beforeStrike !== afterStrike;
    });

    // 将样式字段名转换为保存时使用的格式
    const styleFieldMap: Record<string, string> = {
      'tracking': 'trackingNumber',
      'model': 'model',
      'sn': 'sn',
      'amount': 'amount',
      'remark': 'remark'
    };

    // 检查每个变更的字段是否已确认
    const allChangedFields = [
      ...changedFields,
      ...changedStyleFields.map(f => `${styleFieldMap[f] || f}Style`)
    ];

    const hasUnacknowledgedChange = allChangedFields.some(field => {
      const isStyleField = field.endsWith('Style');

      if (isStyleField) {
        // 检查样式变更
        const baseField = field.replace('Style', '');
        // 将 trackingNumber 转回 tracking 以获取正确的 key
        const styleKey = baseField === 'trackingNumber' ? 'tracking' : baseField;
        const bgKey = `${styleKey}BgColor`;
        const fgKey = `${styleKey}FontColor`;
        const strikeKey = `${styleKey}Strike`;

        const currentStyle = JSON.stringify({
          bg: (notice.after as any)?.[bgKey] || '',
          fg: (notice.after as any)?.[fgKey] || '',
          strike: !!(notice.after as any)?.[strikeKey]
        });

        return !acknowledgedChanges.value.some(ack =>
          (ack.recordId === recordId || ack.trackingNumber.toUpperCase() === trackingNumber.toUpperCase()) &&
          ack.field === field &&
          ack.styleValue === currentStyle
        );
      } else {
        // 检查普通字段变更
        const currentValue = String((notice.after as any)?.[field] || '');

        return !acknowledgedChanges.value.some(ack =>
          (ack.recordId === recordId || ack.trackingNumber.toUpperCase() === trackingNumber.toUpperCase()) &&
          ack.field === field &&
          ack.value === currentValue
        );
      }
    });

    return hasUnacknowledgedChange;
  });

  // 过滤后保留的差异
  if (notices.length > filteredNotices.length) {
    console.warn('⚠️ 有 ' + (notices.length - filteredNotices.length) + ' 条变更因已确认而被过滤');
  }

  return filteredNotices;
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
  const fields: { key: keyof OrderRecord; label: string; isStyle?: boolean }[] = [
    { key: 'trackingNumber', label: '运单号' },
    { key: 'model', label: '型号' },
    { key: 'sn', label: 'SN' },
    { key: 'amount', label: '金额' },
    { key: 'remark', label: '备注' }
  ];
  
  const result: string[] = [];
  
  // 检查内容字段变化
  fields.forEach(({ key, label }) => {
    const beforeVal = (item.before as any)?.[key];
    const afterVal = (item.after as any)?.[key];
    if (String(beforeVal ?? '') !== String(afterVal ?? '')) {
      result.push(label);
    }
  });
  
  // 检查样式字段变化（背景色、字体色、删除线）
  const styleFields = ['model', 'sn', 'amount', 'remark'];
  styleFields.forEach(field => {
    const bgKey = `${field}BgColor` as any;
    const fgKey = `${field}FontColor` as any;
    const strikeKey = `${field}Strike` as any;
    
    const beforeBg = (item.before as any)?.[bgKey];
    const afterBg = (item.after as any)?.[bgKey];
    const beforeFg = (item.before as any)?.[fgKey];
    const afterFg = (item.after as any)?.[fgKey];
    const beforeStrike = (item.before as any)?.[strikeKey];
    const afterStrike = (item.after as any)?.[strikeKey];
    
    if (beforeBg !== afterBg || beforeFg !== afterFg || beforeStrike !== afterStrike) {
      const fieldLabel = fields.find(f => f.key === field)?.label || field;
      const styleLabel = `${fieldLabel}(样式)`;
      if (!result.includes(styleLabel)) {
        result.push(styleLabel);
      }
    }
  });
  
  return result;
};

// 格式化样式值为视觉组件（返回对象而不是字符串）
const formatStyleValue = (obj: Partial<OrderRecord> | undefined, label: string) => {
  if (!obj) return null;

  const fieldName = label.replace('(样式)', '');
  const fieldMap: Record<string, string> = {
    '运单号': 'tracking',
    '型号': 'model',
    'SN': 'sn',
    '金额': 'amount',
    '备注': 'remark'
  };
  const field = fieldMap[fieldName];
  if (!field) return null;

  const bgKey = `${field}BgColor` as any;
  const fgKey = `${field}FontColor` as any;
  const strikeKey = `${field}Strike` as any;
  const boldKey = `${field}Bold` as any;

  const bg = (obj as any)?.[bgKey];
  const fg = (obj as any)?.[fgKey];
  const strike = (obj as any)?.[strikeKey];
  const bold = (obj as any)?.[boldKey];

  return { bg, fg, strike, bold };
};

const formatDiffValue = (obj: Partial<OrderRecord> | undefined, label: string) => {
  if (!obj) return '-';

  // 处理样式字段（如 "型号(样式)"）- 返回null表示需要使用组件渲染
  if (label.endsWith('(样式)')) {
    return null; // 交给模板使用formatStyleValue渲染
  }
  
  // 处理普通字段
  const map: Record<string, keyof OrderRecord> = {
    '运单号': 'trackingNumber',
    '型号': 'model',
    'SN': 'sn',
    '金额': 'amount',
    '备注': 'remark'
  };
  const key = map[label];
  const val = key ? (obj as any)[key] : undefined;

  // 格式化金额
  if (label === '金额' && typeof val === 'number') {
    return `￥${val.toFixed(2)}`;
  }

  return val === undefined || val === null || val === '' ? '-' : String(val);
};

const scheduleDiffCalculation = (prevSnapshot: Map<number, Partial<OrderRecord>>, latest: OrderRecord[], importedStyles?: Map<string, ImportStyle>) => {
  // 轻量异步排队，避免阻塞后续操作或导航
  setTimeout(() => {
    const diffs = computeDifferences(prevSnapshot, latest, importedStyles);
    mergeDiffNotices(diffs);
  }, 0);
};

// 将导入报告中的样式直接转为变更项（即使后端未保存该行，也能展示出来）
const materializeImportedStyleChanges = (
  prevMap: Map<number, Partial<OrderRecord>>,
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
  prevMap: Map<number, Partial<OrderRecord>>,
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
  // 使用完整列表导出（而不是只导出显示的200条）
  if (!diffNoticesAll.value.length) {
    ElMessage.info('暂无可导出的变更提醒');
    return;
  }

  const totalCount = diffNoticesAll.value.length;

  ElMessage.info({
    message: `正在导出全部 ${totalCount} 条变更提醒...`,
    duration: 2000
  });

  const headers = ['运单号/SN', '变更类型', '变更详情', '重复单号1', '重复单号2', '备注'];
  const rows: string[][] = [];

  // 导出全部数据
  diffNoticesAll.value.forEach(item => {
    // 处理删除类型
    if (item.isDelete) {
      rows.push([
        item.trackingNumber || '-',
        '删除记录',
        `型号: ${item.model || '-'}, SN: ${item.sn || '-'}`,
        '',
        '',
        'Excel中已删除此记录，需确认是否从数据库删除'
      ]);
      return;
    }

    // 处理无效ID类型
    if (item.isInvalidId) {
      rows.push([
        item.trackingNumber || '-',
        '无效ID',
        `Excel ID: ${(item as any).excelId}, 型号: ${item.model || '-'}, SN: ${item.sn || '-'}`,
        '',
        '',
        'Excel中的ID不存在于数据库，已按运单号+SN自动匹配'
      ]);
      return;
    }

    // 处理常规变更
    const fields = diffFields(item);
    if (!fields.length) return;
    fields.forEach(label => {
      rows.push([
        item.trackingNumber,
        label,
        formatDiffValue(item.before, label),
        formatDiffValue(item.after, label),
        '',
        ''
      ]);
    });
  });

  if (!rows.length) {
    ElMessage.info('暂无可导出的变更提醒');
    return;
  }

  // 使用 xlsx 生成真正的 Excel 文件
  const wsData = [headers, ...rows];
  const ws = XLSX.utils.aoa_to_sheet(wsData);

  // 设置自定义列宽（单位是字符宽度）
  const colWidths = [
    { wch: 30 },  // 运单号/SN
    { wch: 15 },  // 变更类型
    { wch: 35 },  // 变更详情
    { wch: 35 },  // 重复单号1
    { wch: 35 },  // 重复单号2
    { wch: 60 }   // 备注
  ];
  ws['!cols'] = colWidths;

  // 创建工作簿并添加工作表
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, '变更提醒');

  // 生成Excel文件并下载
  XLSX.writeFile(wb, `数据变更提醒-${new Date().toISOString().slice(0, 10)}.xlsx`);

  ElMessage.success({
    message: `成功导出 ${totalCount} 条变更提醒`,
    duration: 2000
  });
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
      remark: editDialog.form.remark,
      amount: editDialog.form.amount,
      version: editDialog.form.version // 传递版本号
    };
    await updateOrder(editDialog.targetId, payload);
    editDialog.visible = false;
    ElMessage.success('已更新');
    // 单条编辑不需要差异检测和统计刷新，只刷新当前页面数据
    loadOrders();
  } catch (error: any) {
    // 检查是否是乐观锁冲突
    const errorMessage = error?.response?.data?.message || error?.message || '';
    if (errorMessage.includes('已被') || errorMessage.includes('修改')) {
      ElMessage({
        type: 'warning',
        message: '⚠️ 该订单已被其他用户修改，已自动刷新最新数据，请重新操作',
        duration: 5000,
        showClose: true
      });
    }
    // 刷新数据获取最新版本号
    await loadOrders();
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

// 样式缓存，避免重复计算
const styleCache = new Map<string, Record<string, string>>();

// 将导入样式映射到行上的内联样式（优化版：使用缓存）
const styleFor = (row: OrderRecord, field: 'tracking' | 'model' | 'sn' | 'amount' | 'remark') => {
  try {
    const cacheKey = `${row.id}-${field}`;

    // 检查缓存
    if (styleCache.has(cacheKey)) {
      return styleCache.get(cacheKey)!;
    }

    // 先按 record.id 精确匹配，避免同一 tracking+SN 的不同记录互相"覆盖样式"
    let s: any | undefined;
    if (row.id) {
      s = importStyles.value.get(`ID-${row.id}`) as any;
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

    // 2) 若无，则回退到后端返回的持久化样式字段
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

    if (!map) {
      styleCache.set(cacheKey, {});
      return {};
    }

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

    // 缓存结果
    styleCache.set(cacheKey, style);
    return style;
  } catch (error) {
    console.warn('样式应用失败:', error);
    return {};
  }
};

const exportUserOrders = () => {
  if (!userOrders.value.length) return;
  const headers = ['运单号', '型号', 'SN', '分类', '状态', '备注', '创建人'];
  const csvRows = [headers.join(',')];
  userOrders.value.forEach(order => {
    csvRows.push([
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
  // 所有用户都加载订单数据和用户列表
  loadOrders();
  loadUsers();
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
  // 取消正在进行的后台差异计算
  abortDiffCalculation();

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

    // 状态列循环筛选

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

async function loadCategoryStats() {
  // 所有用户都可以加载分类统计
  try {
    await fetchCategoryStats(buildFilterPayload());
  } catch (error) {
    console.warn('Failed to load category stats', error);
  }
}

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
.paid-date {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.status-changed-date {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

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

.copyable-cell {
  cursor: pointer;
}

.copyable-cell:hover {
  color: #409eff;
  text-decoration: underline;
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

/* 数据变更提醒卡片 */
.diff-notice-card {
  margin: 16px 0;
  background: #fffbf0;
  border: 2px solid #f59e0b;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.15);
}

.diff-notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #d97706;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.diff-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.diff-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.field-name {
  font-weight: 600;
  color: #374151;
  min-width: 60px;
}

.old-value {
  color: #ef4444;
  text-decoration: line-through;
  background: #fee;
  padding: 2px 6px;
  border-radius: 4px;
}

.new-value {
  color: #10b981;
  font-weight: 600;
  background: #d1fae5;
  padding: 2px 6px;
  border-radius: 4px;
}

.diff-item .arrow {
  color: #f59e0b;
  font-weight: bold;
}

/* 删除提醒样式 */
.delete-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.delete-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.delete-item .field-name {
  font-weight: 600;
  color: #374151;
  min-width: 50px;
}

.delete-item.warning-text {
  color: #f59e0b;
  font-weight: 600;
  margin-top: 4px;
}

.delete-warning {
  color: #ef4444;
  font-size: 13px;
  line-height: 1.5;
}

/* 无效ID提醒样式 */
.invalid-id-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.invalid-id-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.invalid-id-item .field-name {
  font-weight: 600;
  color: #374151;
  min-width: 70px;
}

.invalid-id-item .invalid-value {
  color: #ef4444;
  font-weight: 700;
  background: #fee;
  padding: 2px 8px;
  border-radius: 4px;
}

.invalid-id-item.warning-text {
  color: #3b82f6;
  font-weight: 500;
  margin-top: 4px;
}

.invalid-id-warning {
  color: #d97706;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 500;
}

/* 重复SN提醒样式 */
.duplicate-sn-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.duplicate-sn-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.duplicate-sn-item .field-name {
  font-weight: 600;
  color: #374151;
  min-width: 70px;
}

.duplicate-sn-item .duplicate-value {
  color: #f59e0b;
  font-weight: 700;
  background: #fffbeb;
  padding: 2px 8px;
  border-radius: 4px;
}

.duplicate-sn-item .count-badge {
  color: #dc2626;
  font-weight: 700;
  background: #fee;
  padding: 2px 8px;
  border-radius: 4px;
}

.duplicate-sn-item.warning-text {
  color: #f59e0b;
  font-weight: 500;
  margin-top: 4px;
}

.duplicate-sn-warning {
  color: #d97706;
  font-size: 13px;
  line-height: 1.5;
}

.duplicate-sn-warning .duplicate-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  color: #d97706;
  font-size: 14px;
}

.duplicate-sn-warning .duplicate-rows {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin: 8px 0;
  padding: 8px;
  background: #fffbeb;
  border-radius: 4px;
}

.duplicate-sn-warning .rows-label {
  font-weight: 600;
  color: #92400e;
  margin-right: 8px;
}

.duplicate-sn-warning .duplicate-tip {
  display: flex;
  align-items: center;
  margin-top: 8px;
  padding: 6px 10px;
  background: #fef3c7;
  border-left: 3px solid #f59e0b;
  border-radius: 4px;
  color: #78350f;
  font-size: 12px;
  line-height: 1.5;
}

/* 样式值显示 */
.style-value {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.color-block {
  display: inline-block;
  width: 24px;
  height: 16px;
  border: 1px solid #ddd;
  border-radius: 3px;
  vertical-align: middle;
  cursor: help;
}

.color-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.no-style {
  color: #999;
  font-size: 12px;
}

.style-value .el-icon {
  color: #f56c6c;
  font-size: 16px;
}

.bold-indicator {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 18px;
  background: #409eff;
  color: white;
  font-weight: bold;
  font-size: 13px;
  border-radius: 3px;
  cursor: help;
}

/* 表格工具栏 */
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.toolbar-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.toolbar-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 导出设置弹窗 */
.export-settings {
  padding: 8px 0;
}

.export-settings h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.export-settings .el-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.export-settings .el-checkbox {
  margin-right: 0;
}

.export-settings .el-divider {
  margin: 12px 0;
}

.export-settings .el-button {
  margin-right: 8px;
}

/* 表格行可点击样式 */
:deep(.el-table__body tr) {
  cursor: pointer;
}

:deep(.el-table__body tr:hover) {
  background-color: #ecf5ff !important;
}

:deep(.el-table__body tr.current-row) {
  background-color: #e6f7ff !important;
}
</style>
