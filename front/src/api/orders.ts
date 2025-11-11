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

export const createOrder = (payload: OrderCreateRequest) =>
  apiClient.post<OrderRecord>('/orders', payload);

export const importOrders = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.post<void>('/orders/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
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
