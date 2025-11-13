import apiClient from './client';
export const submitUserSubmission = (payload) => apiClient.post('/user-submissions', payload);
export const fetchMySubmissions = (params) => apiClient.get('/user-submissions/mine', { params });
export const fetchAllSubmissions = (params) => apiClient.get('/user-submissions', { params });
