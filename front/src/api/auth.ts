import { request } from './http'

export type Role = 'USER' | 'ADMIN'

export interface CurrentUser {
  pseudo: string
  role: Role
}

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 128

export const authApi = {
  register: (password: string) => request<CurrentUser>('POST', '/api/auth/register', { password }),
  login: (pseudo: string, password: string) =>
    request<CurrentUser>('POST', '/api/auth/login', { pseudo, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),
  me: () => request<CurrentUser>('GET', '/api/auth/me'),
}
