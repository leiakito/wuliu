import apiClient from './client';
import type { HardwarePrice, HardwarePriceRequest } from '@/types/models';

export interface HardwarePriceQuery {
  startDate?: string;
  endDate?: string;
  category?: string;
}

export const fetchHardwarePrices = (params: HardwarePriceQuery) =>
  apiClient.get<HardwarePrice[]>('/hardware/prices', { params });

export const createHardwarePrice = (payload: HardwarePriceRequest) =>
  apiClient.post<HardwarePrice>('/hardware/prices', payload);

export const updateHardwarePrice = (id: number, payload: HardwarePriceRequest) =>
  apiClient.put<HardwarePrice>(`/hardware/prices/${id}`, payload);

export const deleteHardwarePrice = (id: number) =>
  apiClient.delete<void>(`/hardware/prices/${id}`);
