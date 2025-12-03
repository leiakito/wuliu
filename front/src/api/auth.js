import apiClient from './client';
export const login = (payload) => apiClient.post('/auth/login', payload);
export const getProfile = () => apiClient.get('/auth/me');
