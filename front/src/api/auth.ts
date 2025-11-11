import apiClient from './client';
import type { LoginResponse, UserProfile } from '@/types/models';

export interface LoginPayload {
  username: string;
  password: string;
}

export const login = (payload: LoginPayload) =>
  apiClient.post<LoginResponse>('/auth/login', payload);

export const getProfile = () => apiClient.get<UserProfile>('/auth/me');
