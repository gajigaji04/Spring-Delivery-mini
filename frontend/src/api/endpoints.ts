import type { AddressInput } from '../address/validateAddress';
import type { DemoAccount } from '../auth/demoAccounts';
import { ApiError, get, getPage, request } from './client';
import type {
  AdminSettlement,
  Menu,
  MyInfo,
  Notification,
  Order,
  OrderDetail,
  OrderStatus,
  OutboxEvent,
  OutboxStatus,
  OwnerApplication,
  OwnerApplicationStatus,
  Payment,
  SettlementStatus,
  Settlement,
  Store,
  TokenResponse,
  TossCheckout,
  TossConfig,
} from './types';

// 화면에서 쓰는 API 모음 — URL·파라미터 기준은 docs/api.md "API 목록"
export const authApi = {
  // 가입은 항상 고객 — 사장님은 가입 후 전환 신청 + 관리자 승인 (V17)
  // 이메일은 선택 (V23) — 비우면 보내지 않음, 나중에 내 정보에서 등록
  signup: (username: string, password: string, email?: string) =>
    request('/api/users/signup', {
      method: 'POST',
      body: { username, password, ...(email ? { email } : {}) },
      auth: false,
    }),
  login: async (username: string, password: string) =>
    (
      await request<TokenResponse>('/api/users/login', {
        method: 'POST',
        body: { username, password },
        auth: false,
        withCredentials: true,
      })
    ).data,
  // 배포 데모 체험 — 데모가 꺼진 배포는 빈 배열. 관리자는 비밀번호 없이 체험 로그인 (응답은 일반 로그인과 같음)
  demoAccounts: () => get<DemoAccount[]>('/api/users/demo-accounts', undefined, false),
  demoAdminLogin: async () =>
    (
      await request<TokenResponse>('/api/users/demo-login/admin', {
        method: 'POST',
        auth: false,
        withCredentials: true,
      })
    ).data,
  logout: () => request('/api/users/logout', { method: 'POST', auth: false, withCredentials: true }),
  me: () => get<MyInfo>('/api/users/me'),
  // 비밀번호 변경·설정 (V23) — Refresh 쿠키를 함께 보내 지금 기기의 로그인만 남김 (다른 기기 로그인은 서버가 폐기)
  changePassword: (newPassword: string, currentPassword?: string) =>
    request('/api/users/me/password', {
      method: 'PATCH',
      body: { newPassword, ...(currentPassword ? { currentPassword } : {}) },
      withCredentials: true,
    }),
  changeEmail: async (email: string, currentPassword?: string) =>
    (
      await request<MyInfo>('/api/users/me/email', {
        method: 'PATCH',
        body: { email, ...(currentPassword ? { currentPassword } : {}) },
      })
    ).data,
  // 이메일 인증 (V24) — 메일 링크의 토큰, 로그인 없이 호출
  confirmEmailVerification: (token: string) =>
    request('/api/users/email-verification/confirm', { method: 'POST', body: { token }, auth: false }),
  // 비밀번호 찾기 (V23) — 가입 여부와 관계없이 202
  requestPasswordReset: (email: string) =>
    request('/api/users/password-reset', { method: 'POST', body: { email }, auth: false }),
  confirmPasswordReset: (token: string, newPassword: string) =>
    request('/api/users/password-reset/confirm', { method: 'POST', body: { token, newPassword }, auth: false }),
};

export const storeApi = {
  list: (page: number, size = 20) => getPage<Store>('/api/stores', { page, size }, false),
  get: (id: number) => get<Store>(`/api/stores/${id}`, undefined, false),
  create: async (name: string) => (await request<Store>('/api/stores', { method: 'POST', body: { name } })).data,
  orders: (id: number, page: number, size = 20) => getPage<Order>(`/api/stores/${id}/orders`, { page, size }),
};

export const menuApi = {
  // 서버에 매장 필터가 없음 → 매장 화면은 받은 페이지에서 storeId로 거른다 (docs/api.md 메뉴 목록)
  list: (page: number, size = 100) => getPage<Menu>('/api/menus', { page, size }, false),
  create: async (body: { storeId: number; name: string; price: number; description?: string }) =>
    (await request<Menu>('/api/menus', { method: 'POST', body })).data,
  update: async (id: number, body: { name: string; price: number; description?: string }) =>
    (await request<Menu>(`/api/menus/${id}`, { method: 'PUT', body })).data,
  remove: (id: number) => request(`/api/menus/${id}`, { method: 'DELETE' }),
};

export const orderApi = {
  // keyset: 첫 페이지는 afterId 없이, 다음은 X-Next-After-Id 값 (뒤 페이지도 응답 시간 일정)
  list: (afterId: number | null, size = 20) => getPage<Order>('/api/orders', { afterId: afterId ?? undefined, size }),
  get: (id: number) => get<OrderDetail>(`/api/orders/${id}`),
  // 주소는 우편번호·도로명·상세로 나눠 보냄 (#77) — 상세 주소가 비면 보내지 않음(서버는 null로 저장)
  // 배달 요청사항은 비면 보내지 않음(서버는 null로 저장)
  create: async (items: { menuId: number; quantity: number }[], address: AddressInput, deliveryRequest = '') =>
    (
      await request<Order>('/api/orders', {
        method: 'POST',
        body: {
          items,
          zipCode: address.zipCode,
          roadAddress: address.roadAddress,
          detailAddress: address.detailAddress || undefined,
          deliveryRequest: deliveryRequest || undefined,
        },
      })
    ).data,
  cancel: (id: number) => request(`/api/orders/${id}/cancel`, { method: 'PATCH' }),
  changeStatus: (id: number, status: Extract<OrderStatus, 'ORDER_ACCEPTED' | 'DELIVERY_COMPLETED'>) =>
    request(`/api/orders/${id}/status`, { method: 'PATCH', body: { status } }),
};

export const paymentApi = {
  pay: async (orderId: number, idempotencyKey: string) =>
    (
      await request<Payment>('/api/payments', {
        method: 'POST',
        body: { orderId, paymentMethod: 'CARD' },
        headers: { 'Idempotency-Key': idempotencyKey },
      })
    ).data,
  list: (page: number, size = 20) => getPage<Payment>('/api/payments', { page, size }),
  get: (id: number) => get<Payment>(`/api/payments/${id}`),
};

/** 토스페이먼츠 테스트 모드 결제 (V20, docs/api.md 토스페이먼츠 결제 장) */
export const tossApi = {
  config: () => get<TossConfig>('/api/payments/toss/config'),
  checkout: async (orderId: number) =>
    (await request<TossCheckout>('/api/payments/toss/checkout', { method: 'POST', body: { orderId } })).data,
  /**
   * 결제창 성공 후 서버 승인. Idempotency-Key는 토스 주문번호 — 새로 고침·재시도가 같은 키로 가서
   * 서버가 처음 응답을 돌려줌 (승인은 한 번)
   */
  confirm: async (params: { orderId: number; paymentKey: string; tossOrderId: string; amount: number }) =>
    (
      await request<Payment>('/api/payments/toss/confirm', {
        method: 'POST',
        body: params,
        headers: { 'Idempotency-Key': params.tossOrderId },
      })
    ).data,
};

/** 가짜 결제창의 인증 단계 — 서버가 toss.payments.mode=fake일 때만 있음 (V21) */
export type FakeTossScenario = 'SUCCESS' | 'CARD_REJECTED' | 'RESPONSE_LOST' | 'PG_DELAYED';
export const fakeTossApi = {
  authorize: async (tossOrderId: string, amount: number, scenario: FakeTossScenario) =>
    (
      await request<{ paymentKey: string }>('/api/payments/fake-toss/authorize', {
        method: 'POST',
        body: { tossOrderId, amount, scenario },
      })
    ).data,
};

export const notificationApi = {
  list: (page: number, size = 20) => getPage<Notification>('/api/notifications', { page, size }),
  unreadCount: () => get<{ unreadCount: number }>('/api/notifications/unread-count'),
  markRead: (id: number) => request(`/api/notifications/${id}/read`, { method: 'PATCH' }),
};

export const settlementApi = {
  mine: (q: { storeId?: number; status?: SettlementStatus; from?: string; to?: string; page: number; size?: number }) =>
    getPage<Settlement>('/api/settlements', { ...q, size: q.size ?? 20 }),
};

export const ownerApplicationApi = {
  apply: async (businessName: string) =>
    (await request<OwnerApplication>('/api/owner-applications', { method: 'POST', body: { businessName } })).data,
  /** 신청 내역이 없으면 null (서버 404) */
  mine: async (): Promise<OwnerApplication | null> => {
    try {
      return await get<OwnerApplication>('/api/owner-applications/me');
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }
  },
};

export const adminApi = {
  // 배포 데모 데이터 초기화 — 데모 매장 정산·주문·승인 대기 신청을 처음 상태로 (데모가 꺼진 배포는 404, 1분 간격 429)
  resetDemoData: async () =>
    (await request<{ resetAt: string }>('/api/admin/demo-data/reset', { method: 'POST' })).data,
  ownerApplications: (status: OwnerApplicationStatus, page: number, size = 20) =>
    getPage<OwnerApplication>('/api/admin/owner-applications', { status, page, size }),
  approveOwner: async (id: number) =>
    (await request<OwnerApplication>(`/api/admin/owner-applications/${id}/approve`, { method: 'PATCH' })).data,
  rejectOwner: async (id: number, reason: string) =>
    (await request<OwnerApplication>(`/api/admin/owner-applications/${id}/reject`, { method: 'PATCH', body: { reason } }))
      .data,
  settlements: (date: string, status?: SettlementStatus) =>
    get<AdminSettlement[]>('/api/admin/settlements', { date, status }),
  confirm: async (id: number) =>
    (await request<AdminSettlement>(`/api/admin/settlements/${id}/confirm`, { method: 'PATCH' })).data,
  pay: async (id: number) =>
    (await request<AdminSettlement>(`/api/admin/settlements/${id}/pay`, { method: 'PATCH' })).data,
  outboxEvents: (status: OutboxStatus, page: number, size = 20) =>
    getPage<OutboxEvent>('/api/admin/outbox-events', { status, page, size }),
  republish: async (id: number) =>
    (await request<OutboxEvent>(`/api/admin/outbox-events/${id}/republish`, { method: 'POST' })).data,
};
