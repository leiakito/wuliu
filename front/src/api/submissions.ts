import apiClient from './client';
import type {
  PageResponse,
  UserSubmission,
  UserSubmissionCreateRequest,
  UserSubmissionQueryRequest
} from '@/types/models';

export type SubmissionQueryParams = UserSubmissionQueryRequest;

export const submitUserSubmission = (payload: UserSubmissionCreateRequest) =>
  apiClient.post<UserSubmission>('/user-submissions', payload);

export const fetchMySubmissions = (params: SubmissionQueryParams) =>
  apiClient.get<PageResponse<UserSubmission>>('/user-submissions/mine', { params });

export const fetchAllSubmissions = (params: SubmissionQueryParams) =>
  apiClient.get<PageResponse<UserSubmission>>('/user-submissions', { params });
