export interface ApiResponse<T> {
  status: number;
  code: string;
  message: string;
  result: T;
}

export interface PlanBonus {
  bonusType: string;
  amount: number;
}

export interface PlanAppQuota {
  appId: number;
  appCode: string;
  appName: string;
  quotaType: string;
  quotaMb: number;
}

export interface StorePlan {
  id: number;
  code: string;
  name: string;
  description: string;
  price: number;
  durationMonths: number;
  quotaType: string;
  dataQuotaMb: number;
  cycleDays: number;
  voiceMinutes: number;
  cutoffPolicy: string;
  throttleSpeedKbps: number | null;
  firstCycleBonuses?: PlanBonus[];
  appQuotas?: PlanAppQuota[];
}

export interface CreateOrderRequest {
  planCode: string;
  msisdn: string;
  customerName: string;
  customerPhone: string;
  customerEmail?: string;
}

export interface StoreOrder {
  orderCode: string;
  planCode: string;
  planName: string;
  durationMonths: number;
  cycleDays: number;
  planPrice: number;
  simFee: number;
  discount: number;
  amount: number;
  msisdn: string;
  customerName: string;
  customerPhone: string;
  customerEmail: string | null;
  status: 'PENDING_PAYMENT' | 'PENDING_REVIEW' | 'PAID' | 'FAILED' | 'EXPIRED' | 'CANCELLED';
  expiresAt: string;
  paidAt: string | null;
  createdAt: string;
}

export interface CreatePaymentResponse {
  txnRef: string;
  paymentUrl: string;
}
