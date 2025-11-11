import apiClient from './client';
export const fetchHardwarePrices = (params) => apiClient.get('/hardware/prices', { params });
export const createHardwarePrice = (payload) => apiClient.post('/hardware/prices', payload);
export const updateHardwarePrice = (id, payload) => apiClient.put(`/hardware/prices/${id}`, payload);
export const deleteHardwarePrice = (id) => apiClient.delete(`/hardware/prices/${id}`);
