import { apiRequest } from '@/api/apiClient';
import type { StaffArea } from '@/api/types';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  username: string;
  fullName: string;
  area: StaffArea;
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/api/auth/login', { method: 'POST', body: request });
}
