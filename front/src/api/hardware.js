import apiClient from './client';
export const fetchHardwarePrices = (params) => apiClient.get('/hardware/prices', { params });
export const createHardwarePrice = (payload) => apiClient.post('/hardware/prices', payload);
export const createHardwarePricesBatch = (payload) => apiClient.post('/hardware/prices/batch', payload);
export const importHardwarePrices = (files, onProgress) => {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return apiClient.post('/hardware/prices/import/batch', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 300000,
        onUploadProgress: event => {
            if (event.total) {
                const percent = Math.round((event.loaded / event.total) * 100);
                onProgress?.(percent);
            }
        }
    });
};
export const updateHardwarePrice = (id, payload) => apiClient.put(`/hardware/prices/${id}`, payload);
export const deleteHardwarePrice = (id) => apiClient.delete(`/hardware/prices/${id}`);
