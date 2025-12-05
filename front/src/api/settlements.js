import apiClient from './client';
const serializeParams = (params) => {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === '') {
            return;
        }
        if (Array.isArray(value)) {
            value.forEach(item => {
                if (item !== undefined && item !== null && item !== '') {
                    search.append(key, String(item));
                }
            });
        }
        else {
            search.append(key, String(value));
        }
    });
    return search.toString();
};
export const fetchSettlements = (params) => apiClient.get('/settlements', { params });
export const confirmSettlement = (id, payload) => apiClient.put(`/settlements/${id}/confirm`, payload);
export const deleteSettlements = (ids) => apiClient.delete('/settlements', { data: ids });
export const deleteConfirmedSettlements = () => apiClient.delete('/settlements/confirmed');
export const exportSettlements = async (params) => {
    const response = await apiClient.get('/settlements/export', {
        params,
        paramsSerializer: () => serializeParams(params),
        responseType: 'blob'
    });
    return response;
};
export const updateSettlementPriceByModel = (payload) => apiClient.put('/settlements/price-by-model', payload);
export const confirmSettlementsBatch = (payload) => apiClient.put('/settlements/confirm-batch', payload);
export const updateSettlementAmount = (id, payload) => apiClient.put(`/settlements/${id}/amount`, payload);
export const updateSettlementPriceBySn = (payload) => apiClient.put('/settlements/price-by-sn', payload);
export const confirmAllSettlements = (payload) => apiClient.put('/settlements/confirm-all', payload);
export const moveToDraft = (ids) => apiClient.put('/settlements/move-to-draft', ids);
export const moveToPending = (ids) => apiClient.put('/settlements/move-to-pending', ids);
