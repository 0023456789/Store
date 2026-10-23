import React from 'react';
import { Layout, Typography, Space } from 'antd';

const { Footer: AntFooter } = Layout;
const { Text } = Typography;

export const Footer: React.FC = () => {
  return (
    <AntFooter style={{ textAlign: 'center', background: '#f8fafc', borderTop: '1px solid #e2e8f0', padding: '24px 16px' }}>
      <Space direction="vertical" size={4}>
        <Text strong style={{ color: '#334155' }}>
          MVNO Storefront Demo - Cổng Thanh Toán VNPAY (PAY v2.1.0)
        </Text>
        <Text type="secondary" style={{ fontSize: '13px' }}>
          Hệ thống đọc dữ liệu gói cước chỉ đọc từ Admin Catalog DB • Đặt hàng & thanh toán tức thời qua VNPAY Sandbox
        </Text>
        <Text type="secondary" style={{ fontSize: '12px', marginTop: '8px' }}>
          © 2026 VNSKY Storefront. All rights reserved.
        </Text>
      </Space>
    </AntFooter>
  );
};
