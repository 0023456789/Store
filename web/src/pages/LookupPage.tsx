import React, { useState } from 'react';
import { Card, Form, Input, Button, Descriptions, Tag, Typography, Alert } from 'antd';
import { SearchOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { getOrder, createPayment } from '../api/client';
import { StoreOrder } from '../types/store';

const { Title, Text } = Typography;

export const LookupPage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [order, setOrder] = useState<StoreOrder | null>(null);
  const [paying, setPaying] = useState(false);

  const handleSearch = async (values: { orderCode: string; phone: string }) => {
    try {
      setLoading(true);
      setErrorMsg(null);
      setOrder(null);
      const res = await getOrder(values.orderCode.trim().toUpperCase(), values.phone.trim());
      setOrder(res);
      localStorage.setItem('last_customer_phone', values.phone.trim());
      localStorage.setItem('last_order_code', values.orderCode.trim().toUpperCase());
    } catch (err: any) {
      const code = err.response?.data?.code;
      if (code === 'ORDER_NOT_FOUND') {
        setErrorMsg('Không tìm thấy đơn hàng với mã vừa nhập.');
      } else if (code === 'ORDER_PHONE_MISMATCH') {
        setErrorMsg('Số điện thoại không khớp với số đã đăng ký trên đơn hàng này.');
      } else {
        setErrorMsg(err.response?.data?.message || err.message || 'Không thể tra cứu đơn hàng.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handlePayNow = async () => {
    if (!order) return;
    try {
      setPaying(true);
      const res = await createPayment(order.orderCode);
      window.location.href = res.paymentUrl;
    } catch (err: any) {
      setPaying(false);
      setErrorMsg(err.response?.data?.message || 'Không thể tạo yêu cầu thanh toán');
    }
  };

  return (
    <div style={{ maxWidth: 800, margin: '32px auto', padding: '0 16px' }}>
      <Title level={3} style={{ marginBottom: '24px', color: '#1e293b' }}>
        Tra cứu trạng thái đơn hàng
      </Title>

      <Card style={{ borderRadius: '12px', marginBottom: '24px' }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSearch}
          initialValues={{
            orderCode: localStorage.getItem('last_order_code') || '',
            phone: localStorage.getItem('last_customer_phone') || '',
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px' }}>
            <Form.Item
              label="Mã đơn hàng"
              name="orderCode"
              rules={[{ required: true, message: 'Vui lòng nhập mã đơn hàng' }]}
            >
              <Input placeholder="Ví dụ: ORD + UUID của đơn hàng" size="large" />
            </Form.Item>

            <Form.Item
              label="Số điện thoại người mua"
              name="phone"
              rules={[
                { required: true, message: 'Vui lòng nhập số điện thoại' },
                { pattern: /^[0-9]{9,15}$/, message: 'Số điện thoại từ 9 đến 15 chữ số' },
              ]}
            >
              <Input placeholder="Ví dụ: 0912345678" size="large" />
            </Form.Item>
          </div>

          <Button type="primary" htmlType="submit" size="large" icon={<SearchOutlined />} loading={loading} style={{ background: '#0052cc' }}>
            Tra cứu đơn hàng
          </Button>
        </Form>
      </Card>

      {errorMsg && (
        <Alert message="Thông báo" description={errorMsg} type="error" showIcon style={{ marginBottom: '24px' }} />
      )}

      {order && (
        <Card title={`Chi tiết đơn hàng: ${order.orderCode}`} style={{ borderRadius: '12px' }}>
          <Descriptions column={{ xs: 1, sm: 2 }} bordered size="middle">
            <Descriptions.Item label="Mã đơn hàng">{order.orderCode}</Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <Tag color={order.status === 'PAID' ? 'green' : order.status === 'PENDING_REVIEW' ? 'orange' : order.status === 'PENDING_PAYMENT' ? 'gold' : order.status === 'CANCELLED' ? 'orange' : 'red'}>
                {order.status === 'PAID' ? 'Đã thanh toán' : order.status === 'PENDING_REVIEW' ? 'Chờ đối soát' : order.status === 'PENDING_PAYMENT' ? 'Chờ thanh toán' : order.status === 'CANCELLED' ? 'Đã hủy giao dịch' : 'Thất bại / Hết hạn'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Gói cước">{order.planName} ({order.planCode})</Descriptions.Item>
            <Descriptions.Item label="Tổng tiền thanh toán">
              <Text strong style={{ color: '#0052cc', fontSize: '16px' }}>
                {order.amount.toLocaleString('vi-VN')} đ
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Số thuê bao nhận gói">{order.msisdn}</Descriptions.Item>
            <Descriptions.Item label="Người mua">{order.customerName}</Descriptions.Item>
            <Descriptions.Item label="SĐT liên hệ">{order.customerPhone}</Descriptions.Item>
            <Descriptions.Item label="Thời hạn gói">
              {order.durationMonths} tháng ({order.cycleDays} ngày/chu kỳ)
            </Descriptions.Item>
            <Descriptions.Item label="Hạn thanh toán đơn">
              {new Date(order.expiresAt).toLocaleString('vi-VN')}
            </Descriptions.Item>
            {order.paidAt && (
              <Descriptions.Item label="Thời gian thanh toán">
                {new Date(order.paidAt).toLocaleString('vi-VN')}
              </Descriptions.Item>
            )}
          </Descriptions>

          {(order.status === 'PENDING_PAYMENT' || order.status === 'CANCELLED' || order.status === 'FAILED') && (
            <div style={{ marginTop: '24px', textAlign: 'right' }}>
              <Button
                type="primary"
                size="large"
                icon={<ShoppingCartOutlined />}
                loading={paying}
                onClick={handlePayNow}
                style={{ background: '#0052cc' }}
              >
                {order.status === 'PENDING_PAYMENT' ? 'Thanh toán ngay qua VNPAY' : 'Thử lại thanh toán qua VNPAY'}
              </Button>
            </div>
          )}
        </Card>
      )}
    </div>
  );
};
