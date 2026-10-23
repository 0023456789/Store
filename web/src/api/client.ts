import axios from 'axios';
import { ApiResponse, CreateOrderRequest, CreatePaymentResponse, StoreOrder, StorePlan } from '../types/store';

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const getPlans = async (durationMonths?: number, sort?: string): Promise<StorePlan[]> => {
  const params: Record<string, any> = {};
  if (durationMonths) params.durationMonths = durationMonths;
  if (sort) params.sort = sort;
  const res = await api.get<ApiResponse<StorePlan[]>>('/store/plans', { params });
  return res.data.result;
};

export const getPlan = async (code: string): Promise<StorePlan> => {
  const res = await api.get<ApiResponse<StorePlan>>(`/store/plans/${code}`);
  return res.data.result;
};

export const createOrder = async (data: CreateOrderRequest): Promise<StoreOrder> => {
  const res = await api.post<ApiResponse<StoreOrder>>('/store/orders', data);
  return res.data.result;
};

export const getOrder = async (orderCode: string, phone: string): Promise<StoreOrder> => {
  const res = await api.get<ApiResponse<StoreOrder>>(`/store/orders/${orderCode}`, {
    params: { phone },
  });
  return res.data.result;
};

export const createPayment = async (orderCode: string, bankCode?: string): Promise<CreatePaymentResponse> => {
  const res = await api.post<ApiResponse<CreatePaymentResponse>>(
    `/store/orders/${orderCode}/payments`,
    bankCode ? { bankCode } : {}
  );
  return res.data.result;
};
