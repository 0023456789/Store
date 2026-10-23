import React from 'react';
import { Layout, Menu, Typography, Space } from 'antd';
import { PhoneOutlined, ShoppingCartOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';

const { Header } = Layout;
const { Title, Text } = Typography;

export const Navbar: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey = location.pathname === '/lookup' ? 'lookup' : 'plans';

  return (
    <Header
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        background: '#fff',
        borderBottom: '1px solid #f0f0f0',
        padding: '0 24px',
        position: 'sticky',
        top: 0,
        zIndex: 100,
      }}
    >
      <Space direction="horizontal" style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>
        <div
          style={{
            background: 'linear-gradient(135deg, #0052cc 0%, #2684ff 100%)',
            color: '#fff',
            fontWeight: 800,
            fontSize: '18px',
            padding: '4px 12px',
            borderRadius: '6px',
            letterSpacing: '1px',
          }}
        >
          VNSKY
        </div>
        <Title level={4} style={{ margin: 0, color: '#1f2937' }}>
          Storefront
        </Title>
      </Space>

      <Menu
        mode="horizontal"
        selectedKeys={[selectedKey]}
        style={{ borderBottom: 'none', minWidth: '300px' }}
        items={[
          {
            key: 'plans',
            icon: <ShoppingCartOutlined />,
            label: 'Gói Cước',
            onClick: () => navigate('/'),
          },
          {
            key: 'lookup',
            icon: <SearchOutlined />,
            label: 'Tra Cứu Đơn Hàng',
            onClick: () => navigate('/lookup'),
          },
        ]}
      />

      <Space>
        <PhoneOutlined style={{ color: '#0052cc' }} />
        <Text strong style={{ color: '#0052cc' }}>
          Hotline: 1900 5222
        </Text>
      </Space>
    </Header>
  );
};
