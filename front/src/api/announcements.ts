import apiClient from './client';
import type { Announcement, AnnouncementCreateRequest, PageResponse } from '@/types/models';

export interface AnnouncementQueryParams {
  page?: number;
  size?: number;
}

export const fetchAnnouncements = (params: AnnouncementQueryParams = {}) =>
  apiClient.get<PageResponse<Announcement>>('/announcements', { params });

export const createAnnouncement = (payload: AnnouncementCreateRequest) =>
  apiClient.post<Announcement>('/announcements', payload);

export const fetchLatestAnnouncement = () =>
  apiClient.get<Announcement | null>('/announcements/latest');
