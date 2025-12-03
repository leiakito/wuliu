import apiClient from './client';
export const submitUserSubmission = (payload) => apiClient.post('/user-submissions', payload);
export const submitUserSubmissionsBatch = (payload) => apiClient.post('/user-submissions/batch', payload);
export const fetchMySubmissions = (params) => apiClient.get('/user-submissions/mine', { params });
export const fetchAllSubmissions = (params) => apiClient.get('/user-submissions', { params });
export const listOwnerUsernames = (params) => apiClient.get('/user-submissions/owners', { params });
// 新增：提交日志分页查询（管理员）
export const fetchSubmissionLogs = (params) => apiClient.get('/user-submission-logs', { params });
