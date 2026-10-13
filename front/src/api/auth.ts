import { request } from './http'

export type Role = 'USER' | 'ADMIN' | 'GUEST'

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
  guest: () => request<CurrentUser>('POST', '/api/auth/guest'),
  logout: () => request<void>('POST', '/api/auth/logout'),
  me: () => request<CurrentUser>('GET', '/api/auth/me'),
}
