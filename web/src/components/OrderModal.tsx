import React, { useState } from 'react';
import { Modal, Form, Input, Select, Typography, Space, Divider, message, Button, Alert } from 'antd';
import { CreditCardOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { StorePlan, CreateOrderRequest } from '../types/store';
import { createOrder, createPayment } from '../api/client';

const { Title, Text } = Typography;

interface OrderModalProps {
  plan: StorePlan | null;
  open: boolean;
  onCancel: () => void;
}

export const OrderModal: React.FC<OrderModalProps> = ({ plan, open, onCancel }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  if (!plan) return null;

  const handleSubmit = async (values: any) => {
    try {
      setLoading(true);

      const orderPayload: CreateOrderRequest = {
        planCode: plan.code,
        msisdn: values.msisdn.trim(),
        customerName: values.customerName.trim(),
        customerPhone: values.customerPhone.trim(),
        customerEmail: values.customerEmail?.trim() || undefined,
      };

      // Step 1: Create Order (B6)
      const order = await createOrder(orderPayload);
      message.success(`Đã tạo đơn hàng ${order.orderCode}! Đang chuyển hướng sang VNPAY...`);

      // Store phone in localStorage for order lookup/polling on result page
      localStorage.setItem('last_customer_phone', order.customerPhone);
      localStorage.setItem('last_order_code', order.orderCode);

      // Step 2: Create Payment Attempt & Build VNPAY URL (B8)
      const payment = await createPayment(order.orderCode, values.bankCode || undefined);

      // Step 3: Redirect to VNPAY Checkout
      window.location.href = payment.paymentUrl;
    } catch (err: any) {
      setLoading(false);
      const errMsg = err.response?.data?.message || err.message || 'Không thể tạo đơn hàng';
      message.error(errMsg);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <CreditCardOutlined style={{ color: '#0052cc', fontSize: '20px' }} />
          <span>Đặt mua gói cước: {plan.name}</span>
        </Space>
      }
      open={open}
      onCancel={onCancel}
      footer={null}
      destroyOnClose
      width={560}
    >
      <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', marginBottom: '16px' }}>
        <Space direction="vertical" style={{ width: '100%' }} size={4}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text type="secondary">Mã gói cước:</Text>
            <Text strong>{plan.code}</Text>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text type="secondary">Thời hạn sử dụng:</Text>
            <Text strong>{plan.durationMonths} tháng ({plan.cycleDays} ngày/chu kỳ)</Text>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text type="secondary">Giá thanh toán:</Text>
            <Title level={4} style={{ color: '#0052cc', margin: 0 }}>
              {plan.price.toLocaleString('vi-VN')} đ
            </Title>
          </div>
        </Space>
      </div>

      <Form form={form} layout="vertical" onFinish={handleSubmit} initialValues={{ bankCode: '' }}>
        <Form.Item
          label="Số thuê bao nhận gói"
          name="msisdn"
          rules={[
            { required: true, message: 'Vui lòng nhập số thuê bao' },
            { pattern: /^[0-9]{9,15}$/, message: 'Số thuê bao từ 9 đến 15 chữ số' },
          ]}
        >
          <Input placeholder="Ví dụ: 0987654321" size="large" />
        </Form.Item>

        <Form.Item
          label="Họ và tên người mua"
          name="customerName"
          rules={[{ required: true, message: 'Vui lòng nhập họ và tên' }]}
        >
          <Input placeholder="Ví dụ: Nguyễn Văn A" size="large" />
        </Form.Item>

        <Form.Item
          label="Số điện thoại liên hệ"
          name="customerPhone"
          rules={[
            { required: true, message: 'Vui lòng nhập số điện thoại' },
            { pattern: /^[0-9]{9,15}$/, message: 'Số điện thoại từ 9 đến 15 chữ số' },
          ]}
        >
          <Input placeholder="Ví dụ: 0912345678" size="large" />
        </Form.Item>

        <Form.Item label="Email nhận thông báo (tùy chọn)" name="customerEmail">
          <Input placeholder="khachhang@example.com" size="large" type="email" />
        </Form.Item>

        <Form.Item label="Phương thức thanh toán VNPAY" name="bankCode">
          <Select size="large">
            <Select.Option value="">Cổng VNPAY (Khách tự chọn phương thức)</Select.Option>
            <Select.Option value="VNPAYQR">VNPAY-QR (Quét mã trên app ngân hàng / ví)</Select.Option>
            <Select.Option value="VNBANK">Thẻ ATM / Tài khoản ngân hàng nội địa</Select.Option>
            <Select.Option value="INTCARD">Thẻ thanh toán quốc tế (Visa, Master, JCB)</Select.Option>
          </Select>
        </Form.Item>

        <Alert
          message="Bảo mật thanh toán"
          description="Giao dịch được xử lý an toàn qua cổng VNPAY (chuẩn PAY v2.1.0). Giá tiền luôn được chụp từ hệ thống máy chủ."
          type="info"
          showIcon
          icon={<SafetyCertificateOutlined />}
          style={{ marginBottom: '16px' }}
        />

        <Divider style={{ margin: '12px 0' }} />

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
          <Button onClick={onCancel} disabled={loading}>
            Hủy
          </Button>
          <Button type="primary" htmlType="submit" size="large" loading={loading} style={{ background: '#0052cc' }}>
            Thanh toán qua VNPAY ({plan.price.toLocaleString('vi-VN')} đ)
          </Button>
        </div>
      </Form>
    </Modal>
  );
};
