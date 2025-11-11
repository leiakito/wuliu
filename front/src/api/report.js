import apiClient from './client';
export const fetchDashboard = (params) => apiClient.get('/report/dashboard', { params });
