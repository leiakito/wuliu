import apiClient from './client';
import type {
  UserSubmission,
  UserSubmissionCreateRequest,
  UserSubmissionBatchRequest,
  UserSubmissionQueryRequest,
  PageResponse,
  UserSubmissionLog,
  SubmissionLogQueryRequest
} from '@/types/models';

export const submitUserSubmission = (payload: UserSubmissionCreateRequest) =>
  apiClient.post<UserSubmission>('/user-submissions', payload);

export const submitUserSubmissionsBatch = (payload: UserSubmissionBatchRequest) =>
  apiClient.post<UserSubmission[]>('/user-submissions/batch', payload);

export const fetchMySubmissions = (params: UserSubmissionQueryRequest) =>
  apiClient.get<any>('/user-submissions/mine', { params });

export const fetchAllSubmissions = (params: UserSubmissionQueryRequest) =>
  apiClient.get<any>('/user-submissions', { params });

export const listOwnerUsernames = (params?: any) =>
  apiClient.get<string[]>('/user-submissions/owners', { params });

// 新增：提交日志分页查询（管理员）
export const fetchSubmissionLogs = (params: SubmissionLogQueryRequest) =>
  apiClient.get<PageResponse<UserSubmissionLog>>('/user-submission-logs', { params });
