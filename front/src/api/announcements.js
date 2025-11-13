import apiClient from './client';
export const fetchAnnouncements = (params = {}) => apiClient.get('/announcements', { params });
export const createAnnouncement = (payload) => apiClient.post('/announcements', payload);
export const fetchLatestAnnouncement = () => apiClient.get('/announcements/latest');
