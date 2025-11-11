import apiClient from './client';
export const fetchOrders = (params) => apiClient.get('/orders', { params });
export const createOrder = (payload) => apiClient.post('/orders', payload);
export const importOrders = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post('/orders/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
};
export const fetchByTracking = (payload) => apiClient.post('/orders/fetch', payload);
export const updateOrderStatus = (id, status) => apiClient.patch(`/orders/${id}/status`, null, { params: { status } });
export const searchOrders = (trackingNumbers) => apiClient.post('/orders/search', { trackingNumbers });
export const updateOrderAmount = (id, payload) => apiClient.patch(`/orders/${id}/amount`, payload);
export const updateOrder = (id, payload) => apiClient.put(`/orders/${id}`, payload);
export const fetchCategoryStats = (params) => apiClient.get('/orders/categories', { params });
