export interface LoginResponse {
  token: string;
  role: string;
  username: string;
}

export interface UserProfile {
  username: string;
  role: string;
}

export interface PageResponse<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

export interface OrderRecord {
  id: number;
  orderDate: string;
  orderTime?: string;
  trackingNumber: string;
  model?: string;
  sn?: string;
  remark?: string;
  category?: string;
  status?: string;
  amount?: number;
  currency?: string;
  weight?: number;
  createdBy?: string;
  updatedBy?: string;
  imported?: boolean;
  createdAt?: string;
  updatedAt?: string;
  inCurrentSettlement?: boolean;
  
  // 单元格格式字段 - 运单号列
  trackingBgColor?: string;
  trackingFontColor?: string;
  trackingStrike?: boolean;
  
  // 单元格格式字段 - 型号列
  modelBgColor?: string;
  modelFontColor?: string;
  modelStrike?: boolean;
  
  // 单元格格式字段 - SN列
  snBgColor?: string;
  snFontColor?: string;
  snStrike?: boolean;
  
  // 单元格格式字段 - 金额列
  amountBgColor?: string;
  amountFontColor?: string;
  amountStrike?: boolean;
  
  // 单元格格式字段 - 备注列
  remarkBgColor?: string;
  remarkFontColor?: string;
  remarkStrike?: boolean;
  
  // Excel 行号（用于位置对齐）
  excelRowIndex?: number;
}

export interface HardwarePrice {
  id: number;
  priceDate: string;
  itemName: string;
  price: number;
  createdBy?: string;
  createdAt?: string;
}

export interface HardwarePriceRequest {
  priceDate: string;
  itemName: string;
  price: number;
}

export interface HardwarePriceBatchRequest {
  items: HardwarePriceRequest[];
}

export interface HardwareImportResult {
  fileName: string;
  priceDate?: string;
  success: boolean;
  message?: string;
  successCount?: number;
  insertedCount?: number;
  updatedCount?: number;
  skippedCount?: number;
  totalRows?: number;
  durationMillis?: number;
  errors?: string[];
  records?: HardwarePrice[];
}

export interface OrderFilterRequest {
  startDate?: string;
  endDate?: string;
  category?: string;
  status?: string;
  keyword?: string;
  createdBy?: string;
  page?: number;
  size?: number;
}

export interface OrderCreateRequest {
  orderDate?: string;
  trackingNumber: string;
  model?: string;
  sn?: string;
  remark?: string;
  amount?: number;
  currency?: string;
}

export interface OrderUpdateRequest {
  trackingNumber?: string;
  model?: string;
  sn?: string;
  amount?: number;
  status?: string;
  remark?: string;
}

export interface SettlementRecord {
  id: number;
  orderId?: number;
  trackingNumber: string;
  model?: string;
  orderSn?: string;
  amount?: number;
  currency?: string;
  manualInput?: boolean;
  status?: string;
  warning?: boolean;
  settleBatch?: string;
  payableAt?: string;
  remark?: string;
  createdAt?: string;
  ownerUsername?: string;
  orderTime?: string;
  orderStatus?: string;
  orderAmount?: number;

  // 单元格格式字段 - 运单号列（继承自订单样式，仅用于展示）
  trackingBgColor?: string;
  trackingFontColor?: string;
  trackingStrike?: boolean;
  // 单元格格式字段 - 型号列
  modelBgColor?: string;
  modelFontColor?: string;
  modelStrike?: boolean;
  // 单元格格式字段 - SN 列
  snBgColor?: string;
  snFontColor?: string;
  snStrike?: boolean;
  // 单元格格式字段 - 金额列
  amountBgColor?: string;
  amountFontColor?: string;
  amountStrike?: boolean;
  // 单元格格式字段 - 备注列
  remarkBgColor?: string;
  remarkFontColor?: string;
  remarkStrike?: boolean;
}

export interface OrderCategoryStats {
  category: string;
  count: number;
}

export interface SettlementFilterRequest {
  status?: string;
  batch?: string;
  ownerUsername?: string;
  model?: string;
  trackingNumber?: string;
  orderSn?: string;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
  sortProp?: string;
  sortOrder?: 'ascending' | 'descending';
}

export interface SettlementConfirmRequest {
  amount: number;
  remark?: string;
}

export interface SettlementExportRequest {
  startDate?: string;
  endDate?: string;
  status?: string;
  batch?: string;
  ownerUsername?: string;
  trackingNumbers?: string[];
}

export interface SettlementBatchPriceRequest {
  model: string;
  amount: number;
  status?: string;
  batch?: string;
  ownerUsername?: string;
  startDate?: string;
  endDate?: string;
}

export interface SettlementBatchConfirmRequest {
  ids: number[];
  amount?: number;
  remark?: string;
}

export interface SettlementAmountRequest {
  amount: number;
  remark?: string;
}

export interface SettlementBatchSnPriceRequest {
  sns: string[];
  amount: number;
}

export interface SettlementBatchSnPriceResponse {
  updatedCount: number;
  skippedSns: string[];
}

export interface DashboardResponse {
  orderCount: number;
  waitingSettlementCount: number;
  totalAmount: number;
  pendingAmount: number;
}

export interface SysUser {
  id: number;
  username: string;
  fullName?: string;
  role: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserRequest {
  username: string;
  fullName?: string;
  role?: string;
  status?: string;
  password?: string;
}

export interface ResetPasswordRequest {
  password: string;
}

export interface SysLog {
  id: number;
  username: string;
  action: string;
  detail?: string;
  ip?: string;
  createdAt: string;
}

export interface UserSubmission {
  id: number;
  username: string;
  ownerUsername?: string;
  trackingNumber: string;
  status: string;
  amount?: number;
  submissionDate?: string;
  remark?: string;
  createdAt?: string;
  order?: OrderRecord;
}

export interface UserSubmissionCreateRequest {
  trackingNumber: string;
  username?: string;
}

export interface UserSubmissionQueryRequest {
  page?: number;
  size?: number;
  status?: string;
  username?: string;
  trackingNumber?: string;
}

export interface UserSubmissionBatchRequest {
  trackingNumbers: string[];
  rawContent?: string;
  username?: string;
}

export interface UserSubmissionLog {
  id: number;
  username?: string;
  content: string;
  createdAt: string;
}

export interface SubmissionLogQueryRequest {
  page?: number;
  size?: number;
  username?: string;
  keyword?: string;
  startTime?: string;
  endTime?: string;
}
