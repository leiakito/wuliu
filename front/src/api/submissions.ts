import apiClient from './client';
import type {
  PageResponse,
  UserSubmission,
  UserSubmissionCreateRequest,
  UserSubmissionQueryRequest,
  UserSubmissionLog,
  SubmissionLogQueryRequest,
  UserSubmissionBatchRequest
} from '@/types/models';

export type SubmissionQueryParams = UserSubmissionQueryRequest;

export const submitUserSubmission = (payload: UserSubmissionCreateRequest) =>
  apiClient.post<UserSubmission>('/user-submissions', payload);

export const submitUserSubmissionsBatch = (payload: UserSubmissionBatchRequest) =>
  apiClient.post<UserSubmission[]>('/user-submissions/batch', payload);

export const fetchMySubmissions = (params: SubmissionQueryParams) =>
  apiClient.get<PageResponse<UserSubmission>>('/user-submissions/mine', { params });

export const fetchAllSubmissions = (params: SubmissionQueryParams) =>
  apiClient.get<PageResponse<UserSubmission>>('/user-submissions', { params });

export const fetchSubmissionLogs = (params: SubmissionLogQueryRequest) =>
  apiClient.get<PageResponse<UserSubmissionLog>>('/user-submission-logs', { params });
