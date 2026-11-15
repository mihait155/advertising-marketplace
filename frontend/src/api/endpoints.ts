import { api, endServerSession, session } from './client';
import type {
  AuthResponse,
  BlockedUser,
  Conversation,
  Currency,
  Deal,
  DealStatus,
  Image,
  Message,
  MessagePage,
  Offer,
  OfferStatus,
  Page,
  Posting,
  PostingCategory,
  Profile,
  PostingStatus,
  Report,
  ReportResolution,
  Review,
  ReportStatus,
  SocialAccount,
  SocialProvider,
  SocialProviderInfo,
  UnreadCount,
  User,
  UserType,
} from './types';

export interface SignupInput {
  fullName: string;
  email: string;
  phone?: string;
  userType: Exclude<UserType, 'ADMIN'>;
  password: string;
}

export interface PostingInput {
  category: PostingCategory;
  title: string;
  text: string;
  payFrom: number | null;
  payTo: number | null;
  payCurrency: string | null;
}

export interface PostingFilters {
  category?: PostingCategory;
  status?: PostingStatus;
  creatorId?: string;
  minPay?: number;
  maxPay?: number;
  currency?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export interface OfferInput {
  text: string;
  priceFrom: number;
  priceTo: number;
  currency?: string | null;
}

export const authApi = {
  login: (email: string, password: string) =>
    api<AuthResponse>('/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  signup: (input: SignupInput) => api<AuthResponse>('/auth/signup', { method: 'POST', body: input, auth: false }),
  logout: (refreshToken: string) => api<void>('/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),
  logoutAll: () => api<void>('/auth/logout-all', { method: 'POST' }),
  me: () => api<User>('/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } }),
};

export const usersApi = {
  update: (user: User, changes: { fullName: string; email: string; phone: string | null }) =>
    api<User>(`/users/${user.id}`, { method: 'PUT', body: { ...changes, userType: user.userType } }),
  remove: (id: string) => api<void>(`/users/${id}`, { method: 'DELETE' }),
  notificationSettings: () => api<{ emailMessageNotifications: boolean }>('/users/me/notification-settings'),
  updateNotificationSettings: (emailMessageNotifications: boolean) =>
    api<{ emailMessageNotifications: boolean }>('/users/me/notification-settings', {
      method: 'PUT',
      body: { emailMessageNotifications },
    }),
};

export const postingsApi = {
  list: (filters: PostingFilters) => {
    const { sort, ...rest } = filters;
    return api<Page<Posting>>('/postings', { query: { ...rest, sort } });
  },
  get: (id: string) => api<Posting>(`/postings/${id}`),
  create: (input: PostingInput & { status: 'DRAFT' | 'OPEN' }) =>
    api<Posting>('/postings', { method: 'POST', body: input }),
  update: (id: string, input: PostingInput) => api<Posting>(`/postings/${id}`, { method: 'PUT', body: input }),
  changeStatus: (id: string, status: PostingStatus) =>
    api<Posting>(`/postings/${id}/status`, { method: 'PATCH', body: { status } }),
  remove: (id: string) => api<void>(`/postings/${id}`, { method: 'DELETE' }),
};

export const offersApi = {
  forPosting: (postingId: string, status?: OfferStatus) =>
    api<Page<Offer>>(`/postings/${postingId}/offers`, { query: { status, size: 100 } }),
  mine: (status?: OfferStatus, page = 0) => api<Page<Offer>>('/offers/mine', { query: { status, page, size: 20 } }),
  create: (postingId: string, input: OfferInput) =>
    api<Offer>(`/postings/${postingId}/offers`, { method: 'POST', body: input }),
  update: (id: string, input: OfferInput) => api<Offer>(`/offers/${id}`, { method: 'PUT', body: input }),
  changeStatus: (id: string, status: OfferStatus, amount?: number) =>
    api<Offer>(`/offers/${id}/status`, { method: 'PATCH', body: { status, amount } }),
};

export const imagesApi = {
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api<Image>('/images', { method: 'POST', body: form });
  },
};

export const currenciesApi = {
  list: () => api<Currency[]>('/currencies'),
};

export const conversationsApi = {
  list: (page = 0) => api<Page<Conversation>>('/conversations', { query: { page, size: 50 } }),
  forPosting: (postingId: string) => api<Page<Conversation>>('/conversations', { query: { postingId, size: 100 } }),
  get: (id: string) => api<Conversation>(`/conversations/${id}`),
  start: (postingId: string, text?: string) =>
    api<Conversation>(`/postings/${postingId}/conversations`, { method: 'POST', body: { text } }),
  unreadCount: () => api<UnreadCount>('/conversations/unread-count'),
  messages: (id: string, before?: string | null) =>
    api<MessagePage>(`/conversations/${id}/messages`, { query: { before, limit: 40 } }),
  send: (id: string, text: string, imageIds: string[]) =>
    api<Message>(`/conversations/${id}/messages`, {
      method: 'POST',
      body: { text: text || null, imageIds: imageIds.length ? imageIds : null },
    }),
  markRead: (id: string) => api<void>(`/conversations/${id}/read`, { method: 'POST' }),
  editMessage: (id: string, text: string) => api<Message>(`/messages/${id}`, { method: 'PUT', body: { text } }),
  deleteMessage: (id: string) => api<void>(`/messages/${id}`, { method: 'DELETE' }),
  reportMessage: (id: string, reason: string) =>
    api<Report>(`/messages/${id}/reports`, { method: 'POST', body: { reason } }),
};

export const blocksApi = {
  list: () => api<BlockedUser[]>('/blocks'),
  block: (userId: string) => api<void>(`/blocks/${userId}`, { method: 'PUT' }),
  unblock: (userId: string) => api<void>(`/blocks/${userId}`, { method: 'DELETE' }),
};

export const adminApi = {
  reports: (status: ReportStatus) => api<Page<Report>>('/admin/reports', { query: { status, size: 50 } }),
  resolve: (id: string, resolution: ReportResolution) =>
    api<Report>(`/admin/reports/${id}/resolve`, { method: 'POST', body: { resolution } }),
  conversationMessages: (conversationId: string) =>
    api<MessagePage>(`/admin/conversations/${conversationId}/messages`, { query: { limit: 100 } }),
};

export const dealsApi = {
  list: (status?: DealStatus, page = 0) => api<Page<Deal>>('/deals', { query: { status, page, size: 20 } }),
  get: (id: string) => api<Deal>(`/deals/${id}`),
  deliver: (id: string, links: string[], note: string) =>
    api<Deal>(`/deals/${id}/deliveries`, { method: 'POST', body: { links, note: note || null } }),
  approve: (id: string) => api<Deal>(`/deals/${id}/approve`, { method: 'POST' }),
  requestChanges: (id: string, feedback: string) =>
    api<Deal>(`/deals/${id}/request-changes`, { method: 'POST', body: { feedback } }),
  cancel: (id: string, reason: string) => api<Deal>(`/deals/${id}/cancel`, { method: 'POST', body: { reason } }),
  review: (id: string, rating: number, text: string) =>
    api<Review>(`/deals/${id}/reviews`, { method: 'POST', body: { rating, text: text || null } }),
};

export const profilesApi = {
  get: (userId: string) => api<Profile>(`/users/${userId}/profile`),
  reviews: (userId: string, page = 0) => api<Page<Review>>(`/users/${userId}/reviews`, { query: { page, size: 20 } }),
};

export const socialApi = {
  providers: () => api<SocialProviderInfo[]>('/social/providers'),
  accounts: () => api<SocialAccount[]>('/social/accounts'),
  authorize: (provider: SocialProvider) => api<{ url: string }>(`/social/${provider}/authorize`, { method: 'POST' }),
  connect: (code: string, state: string) =>
    api<SocialAccount>('/social/connect', { method: 'POST', body: { code, state } }),
  disconnect: (id: string) => api<void>(`/social/accounts/${id}`, { method: 'DELETE' }),
};

export async function logoutCurrentSession() {
  await endServerSession();
  session.clear();
}
