import apiClient from './client';
export const fetchLogs = (page = 1, size = 20, params) => apiClient.get('/logs', {
    params: { page, size, ...params }
});
export const exportLogs = (params) => apiClient.get('/logs/export', {
    params,
    responseType: 'blob'
});
