import apiClient from './client';
import type { SysUser, UserRequest } from '@/types/models';

export const listUsers = () => apiClient.get<SysUser[]>('/users');

export const createUser = (payload: UserRequest) =>
  apiClient.post<SysUser>('/users', payload);

export const updateUser = (id: number, payload: UserRequest) =>
  apiClient.put<void>(`/users/${id}`, payload);

export const resetPassword = (id: number, password: string) =>
  apiClient.put<void>(`/users/${id}/password`, { password });

export const deleteUser = (id: number) =>
  apiClient.delete<void>(`/users/${id}`);
