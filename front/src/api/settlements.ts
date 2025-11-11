import apiClient from './client';
import type {
  PageResponse,
  SettlementRecord,
  SettlementFilterRequest,
  SettlementGenerateRequest,
  SettlementConfirmRequest,
  SettlementExportRequest
} from '@/types/models';

const serializeParams = (params: SettlementExportRequest) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    if (Array.isArray(value)) {
      value.forEach(item => {
        if (item !== undefined && item !== null && item !== '') {
          search.append(key, String(item));
        }
      });
    } else {
      search.append(key, String(value));
    }
  });
  return search.toString();
};

export const fetchSettlements = (params: SettlementFilterRequest) =>
  apiClient.get<PageResponse<SettlementRecord>>('/settlements', { params });

export const generateSettlement = (payload: SettlementGenerateRequest) =>
  apiClient.post<SettlementRecord[]>('/settlements/generate', payload);

export const confirmSettlement = (id: number, payload: SettlementConfirmRequest) =>
  apiClient.put<void>(`/settlements/${id}/confirm`, payload);

export const deleteSettlements = (ids: number[]) =>
  apiClient.delete<void>('/settlements', { data: ids });

export const exportSettlements = async (params: SettlementExportRequest) => {
  const response = await apiClient.get('/settlements/export', {
    params,
    paramsSerializer: () => serializeParams(params),
    responseType: 'blob'
  });
  return response;
};
