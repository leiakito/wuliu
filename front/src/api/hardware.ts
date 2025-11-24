import apiClient from './client';
import type { HardwarePrice, HardwarePriceRequest, HardwarePriceBatchRequest } from '@/types/models';

export interface HardwarePriceQuery {
  startDate?: string;
  endDate?: string;
  itemName?: string;
}

export const fetchHardwarePrices = (params: HardwarePriceQuery) =>
  apiClient.get<HardwarePrice[]>('/hardware/prices', { params });

export const createHardwarePrice = (payload: HardwarePriceRequest) =>
  apiClient.post<HardwarePrice>('/hardware/prices', payload);

export const createHardwarePricesBatch = (payload: HardwarePriceBatchRequest) =>
  apiClient.post<HardwarePrice[]>('/hardware/prices/batch', payload);

export const importHardwarePrices = (file: File, priceDate: string) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('priceDate', priceDate);
  return apiClient.post<HardwarePrice[]>('/hardware/prices/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  });
};

export const updateHardwarePrice = (id: number, payload: HardwarePriceRequest) =>
  apiClient.put<HardwarePrice>(`/hardware/prices/${id}`, payload);

export const deleteHardwarePrice = (id: number) =>
  apiClient.delete<void>(`/hardware/prices/${id}`);
