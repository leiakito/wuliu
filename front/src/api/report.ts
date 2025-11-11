import apiClient from './client';
import type { DashboardResponse } from '@/types/models';

export const fetchDashboard = (params: { start?: string; end?: string }) =>
  apiClient.get<DashboardResponse>('/report/dashboard', { params });
