import api from './client'

// ── Auth ──────────────────────────────────────────────────────────────
export const authApi = {
  register: (name: string, email: string, password: string) =>
    api.post('/auth/register', { name, email, password }).then(r => r.data),
  login: (email: string, password: string) =>
    api.post('/auth/login', { email, password }).then(r => r.data),
  refresh: (refreshToken: string) =>
    api.post('/auth/refresh', { refreshToken }).then(r => r.data),
}

// ── Groups ────────────────────────────────────────────────────────────
export const groupsApi = {
  list: () => api.get('/groups').then(r => r.data),
  get: (groupId: string) => api.get(`/groups/${groupId}`).then(r => r.data),
  create: (data: { name: string; description?: string; currency?: string; budgetAmount?: number }) =>
    api.post('/groups', data).then(r => r.data),
  addMember: (groupId: string, email: string) =>
    api.post(`/groups/${groupId}/members`, { email }).then(r => r.data),
  getBalances: (groupId: string) =>
    api.get(`/groups/${groupId}/balances`).then(r => r.data),
  getSimplifiedDebts: (groupId: string) =>
    api.get(`/groups/${groupId}/simplified-debts`).then(r => r.data),
}

// ── Expenses ──────────────────────────────────────────────────────────
export const expensesApi = {
  list: (groupId: string, page = 0, size = 20) =>
    api.get(`/groups/${groupId}/expenses`, { params: { page, size } }).then(r => r.data),
  create: (groupId: string, data: Record<string, unknown>) =>
    api.post(`/groups/${groupId}/expenses`, data).then(r => r.data),
  reverse: (transactionId: string) =>
    api.delete(`/expenses/${transactionId}`).then(r => r.data),
  edit: (transactionId: string, data: Record<string, unknown>) =>
    api.put(`/expenses/${transactionId}`, data).then(r => r.data),
}

// ── Settlements ───────────────────────────────────────────────────────
export const settlementsApi = {
  initiate: (groupId: string, payeeId: string, amount: string, idempotencyKey: string) =>
    api.post(`/groups/${groupId}/settlements`, { payeeId, amount, idempotencyKey }).then(r => r.data),
  get: (settlementId: string) =>
    api.get(`/settlements/${settlementId}`).then(r => r.data),
}

// ── Notifications ─────────────────────────────────────────────────────
export const notificationsApi = {
  list: (unreadOnly = false) =>
    api.get('/notifications', { params: { unreadOnly } }).then(r => r.data),
  markRead: (id: number) =>
    api.post(`/notifications/${id}/read`).then(r => r.data),
}

// ── Users ─────────────────────────────────────────────────────────────
export const usersApi = {
  getMe: () => api.get('/users/me').then(r => r.data),
}

