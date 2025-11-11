import apiClient from './client';
export const listUsers = () => apiClient.get('/users');
export const createUser = (payload) => apiClient.post('/users', payload);
export const updateUser = (id, payload) => apiClient.put(`/users/${id}`, payload);
export const resetPassword = (id, password) => apiClient.put(`/users/${id}/password`, { password });
export const deleteUser = (id) => apiClient.delete(`/users/${id}`);
