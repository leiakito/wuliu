import apiClient from './client';
import type { HardwarePrice, HardwarePriceRequest, HardwarePriceBatchRequest, HardwareImportResult } from '@/types/models';

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

export const importHardwarePrices = (files: File[], onProgress?: (percent: number) => void) => {
  const formData = new FormData();
  files.forEach(file => formData.append('files', file));
  return apiClient.post<HardwareImportResult[]>('/hardware/prices/import/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000,
    onUploadProgress: event => {
      if (event.total) {
        const percent = Math.round((event.loaded / event.total) * 100);
        onProgress?.(percent);
      }
    }
  });
};

export const updateHardwarePrice = (id: number, payload: HardwarePriceRequest) =>
  apiClient.put<HardwarePrice>(`/hardware/prices/${id}`, payload);

export const deleteHardwarePrice = (id: number) =>
  apiClient.delete<void>(`/hardware/prices/${id}`);
