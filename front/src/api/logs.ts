import apiClient from './client';
import type { PageResponse, SysLog } from '@/types/models';

export const fetchLogs = (page = 1, size = 20, params?: {
  keyword?: string;
  action?: string;
  startDate?: string;
  endDate?: string;
}) =>
  apiClient.get<PageResponse<SysLog>>('/logs', {
    params: { page, size, ...params }
  });

export const exportLogs = (params?: {
  keyword?: string;
  action?: string;
  startDate?: string;
  endDate?: string;
}) =>
  apiClient.get('/logs/export', {
    params,
    responseType: 'blob'
  });
