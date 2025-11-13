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
}

export interface HardwarePrice {
  id: number;
  priceDate: string;
  itemName: string;
  category?: string;
  price: number;
  remark?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface HardwarePriceRequest {
  priceDate: string;
  itemName: string;
  category?: string;
  price: number;
  remark?: string;
}

export interface OrderFilterRequest {
  startDate?: string;
  endDate?: string;
  category?: string;
  status?: string;
  keyword?: string;
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
  amount?: number;
  currency?: string;
  manualInput?: boolean;
  status?: string;
  warning?: boolean;
  settleBatch?: string;
  payableAt?: string;
  remark?: string;
  createdAt?: string;
  orderStatus?: string;
  orderAmount?: number;
}

export interface OrderCategoryStats {
  category: string;
  count: number;
}

export interface SettlementFilterRequest {
  status?: string;
  batch?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
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
  trackingNumbers?: string[];
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

export interface Announcement {
  id: number;
  title: string;
  content: string;
  createdBy?: string;
  createdAt: string;
}

export interface AnnouncementCreateRequest {
  title: string;
  content: string;
}

export interface UserSubmission {
  id: number;
  username: string;
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
}

export interface UserSubmissionQueryRequest {
  page?: number;
  size?: number;
  status?: string;
  username?: string;
  trackingNumber?: string;
}
