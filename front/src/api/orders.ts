import apiClient from './client';
import type {
  OrderCategoryStats,
  OrderCreateRequest,
  OrderFilterRequest,
  OrderRecord,
  PageResponse
} from '@/types/models';

export interface BatchFetchPayload {
  trackingNumbers: string[];
  manualAmount?: number;
}

export const fetchOrders = (params: OrderFilterRequest) =>
  apiClient.get<PageResponse<OrderRecord>>('/orders', { params });

export const fetchOrdersWithConfig = (
  params: OrderFilterRequest,
  config: Record<string, any> = {}
) => apiClient.get<PageResponse<OrderRecord>>('/orders', { params, ...config });

export const createOrder = (payload: OrderCreateRequest) =>
  apiClient.post<OrderRecord>('/orders', payload);

export const importOrders = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.post<{
    duplicateSn?: string[];
    duplicateSnDetail?: Record<string, string[]>;
    styles?: Array<{
      trackingNumber?: string;
      sn?: string;
      trackingBgColor?: string;
      trackingFontColor?: string;
      trackingStrike?: boolean;
      modelBgColor?: string;
      modelFontColor?: string;
      modelStrike?: boolean;
      snBgColor?: string;
      snFontColor?: string;
      snStrike?: boolean;
      amountBgColor?: string;
      amountFontColor?: string;
      amountStrike?: boolean;
      remarkBgColor?: string;
      remarkFontColor?: string;
      remarkStrike?: boolean;
    }>;
    styleChanges?: Array<{
      trackingNumber?: string;
      sn?: string;
      field: string;
      fromBg?: string;
      toBg?: string;
      fromFont?: string;
      toFont?: string;
      fromStrike?: boolean;
      toStrike?: boolean;
    }>;
  }>('/orders/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  });
};

export const fetchByTracking = (payload: BatchFetchPayload) =>
  apiClient.post<OrderRecord[]>('/orders/fetch', payload);

export const updateOrderStatus = (id: number, status: string) =>
  apiClient.patch<void>(`/orders/${id}/status`, null, { params: { status } });

export const searchOrders = (trackingNumbers: string[]) =>
  apiClient.post<OrderRecord[]>('/orders/search', { trackingNumbers });

export const updateOrderAmount = (id: number, payload: { amount: number; currency?: string; remark?: string }) =>
  apiClient.patch<void>(`/orders/${id}/amount`, payload);

export const updateOrder = (id: number, payload: Partial<OrderCreateRequest>) =>
  apiClient.put<OrderRecord>(`/orders/${id}`, payload);

export const fetchCategoryStats = (params: Partial<OrderFilterRequest>) =>
  apiClient.get<OrderCategoryStats[]>('/orders/categories', { params });
