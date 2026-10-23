import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Typography, Card, Row, Col, Tag, Button, Spin, Empty, Segmented, Select, Space, Badge, Modal, Descriptions } from 'antd';
import {
  ThunderboltOutlined,
  CheckCircleOutlined,
  GiftOutlined,
  AppstoreOutlined,
  PhoneOutlined,
  InfoCircleOutlined,
  FireOutlined,
} from '@ant-design/icons';
import { getPlans } from '../api/client';
import { StorePlan } from '../types/store';
import { OrderModal } from '../components/OrderModal';

const { Title, Paragraph, Text } = Typography;

export const PlanListPage: React.FC = () => {
  const [durationFilter, setDurationFilter] = useState<number | undefined>(undefined);
  const [sortOrder, setSortOrder] = useState<string>('asc');
  const [selectedPlanForOrder, setSelectedPlanForOrder] = useState<StorePlan | null>(null);
  const [detailPlan, setDetailPlan] = useState<StorePlan | null>(null);

  const { data: plans, isLoading, error } = useQuery<StorePlan[]>({
    queryKey: ['plans', durationFilter, sortOrder],
    queryFn: () => getPlans(durationFilter, sortOrder),
  });

  const formatGb = (mb: number) => {
    const gb = mb / 1024;
    return Number.isInteger(gb) ? `${gb}GB` : `${gb.toFixed(1)}GB`;
  };

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '32px 16px' }}>
      {/* Hero Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, #0b2265 0%, #0052cc 60%, #00c7e6 100%)',
          borderRadius: '16px',
          padding: '40px 32px',
          color: '#fff',
          marginBottom: '32px',
          boxShadow: '0 8px 24px rgba(0, 82, 204, 0.15)',
        }}
      >
        <Space direction="vertical" size={12}>
          <Tag color="#ff5630" style={{ fontWeight: 700, padding: '4px 10px', fontSize: '13px' }}>
            <FireOutlined /> GÓI CƯỚC DATA TỐC ĐỘ CAO
          </Tag>
          <Title level={2} style={{ color: '#fff', margin: 0 }}>
            Kết nối không giới hạn cùng VNSKY
          </Title>
          <Paragraph style={{ color: '#e0e7ff', fontSize: '16px', margin: 0, maxWidth: '650px' }}>
            Lướt web, xem video, miễn phí data TikTok & YouTube. Đăng ký dễ dàng, thanh toán an toàn tức thì qua VNPAY.
          </Paragraph>
        </Space>
      </div>

      {/* Filter and Sort Bar */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: '16px',
          marginBottom: '24px',
          background: '#fff',
          padding: '16px 20px',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
        }}
      >
        <Space wrap>
          <Text strong style={{ color: '#475569' }}>Thời hạn:</Text>
          <Segmented
            options={[
              { label: 'Tất cả gói', value: 'all' },
              { label: '1 Tháng', value: 1 },
              { label: '6 Tháng', value: 6 },
              { label: '12 Tháng', value: 12 },
            ]}
            value={durationFilter ?? 'all'}
            onChange={(val) => setDurationFilter(val === 'all' ? undefined : (val as number))}
          />
        </Space>

        <Space>
          <Text strong style={{ color: '#475569' }}>Sắp xếp giá:</Text>
          <Select
            value={sortOrder}
            onChange={(val) => setSortOrder(val)}
            style={{ width: 160 }}
            options={[
              { value: 'asc', label: 'Giá tăng dần' },
              { value: 'desc', label: 'Giá giảm dần' },
            ]}
          />
        </Space>
      </div>

      {/* Plan Grid */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '64px 0' }}>
          <Spin size="large" tip="Đang tải danh sách gói cước..." />
        </div>
      ) : error ? (
        <Empty description="Không thể tải danh sách gói cước. Vui lòng thử lại sau." />
      ) : !plans || plans.length === 0 ? (
        <Empty description="Hiện tại chưa có gói cước khả dụng." />
      ) : (
        <Row gutter={[24, 24]}>
          {plans.map((plan) => (
            <Col xs={24} sm={12} lg={8} key={plan.id}>
              <Badge.Ribbon
                text={plan.durationMonths > 1 ? `${plan.durationMonths} Tháng` : 'Hot'}
                color={plan.durationMonths > 1 ? '#0052cc' : '#ff5630'}
              >
                <Card
                  hoverable
                  style={{
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    borderRadius: '12px',
                    borderColor: '#e2e8f0',
                  }}
                  bodyStyle={{ display: 'flex', flexDirection: 'column', height: '100%', padding: '24px' }}
                >
                  <Title level={4} style={{ marginBottom: 4, color: '#1e293b' }}>
                    {plan.name}
                  </Title>
                  <Text type="secondary" style={{ fontSize: '13px' }}>
                    Mã gói: <Tag color="blue">{plan.code}</Tag>
                  </Text>

                  <div style={{ margin: '16px 0', borderBottom: '1px dashed #e2e8f0', paddingBottom: '16px' }}>
                    <span style={{ fontSize: '28px', fontWeight: 800, color: '#0052cc' }}>
                      {plan.price.toLocaleString('vi-VN')}
                    </span>
                    <span style={{ color: '#64748b', fontSize: '14px', marginLeft: '4px' }}>
                      đ/{plan.durationMonths > 1 ? `${plan.durationMonths}T` : 'tháng'}
                    </span>
                  </div>

                  {/* Highlights */}
                  <Space direction="vertical" style={{ width: '100%', flex: 1 }} size={10}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <ThunderboltOutlined style={{ color: '#ffab00', fontSize: '16px' }} />
                      <Text strong style={{ color: '#334155' }}>
                        {formatGb(plan.dataQuotaMb)}/{plan.quotaType === 'DAILY' ? 'ngày' : 'tháng'}
                      </Text>
                    </div>

                    {plan.firstCycleBonuses && plan.firstCycleBonuses.length > 0 && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <GiftOutlined style={{ color: '#ff5630', fontSize: '16px' }} />
                        <Text style={{ color: '#ff5630', fontSize: '13px', fontWeight: 600 }}>
                          +{formatGb(plan.firstCycleBonuses[0].amount)} Data chu kỳ đầu
                        </Text>
                      </div>
                    )}

                    {plan.appQuotas && plan.appQuotas.length > 0 && (
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                        <AppstoreOutlined style={{ color: '#36b37e', fontSize: '16px', marginTop: '3px' }} />
                        <div>
                          <Text style={{ fontSize: '13px', color: '#475569' }}>Miễn phí data app:</Text>
                          <div style={{ marginTop: '4px' }}>
                            {plan.appQuotas.map((app) => (
                              <Tag color="cyan" key={app.appId} style={{ marginBottom: '4px' }}>
                                {app.appName}
                              </Tag>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}

                    {plan.voiceMinutes > 0 && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <PhoneOutlined style={{ color: '#6554c0', fontSize: '16px' }} />
                        <Text style={{ fontSize: '13px', color: '#475569' }}>
                          {plan.voiceMinutes} phút gọi nội mạng
                        </Text>
                      </div>
                    )}

                    {plan.description && (
                      <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ fontSize: '13px', marginTop: 8 }}>
                        {plan.description}
                      </Paragraph>
                    )}
                  </Space>

                  {/* Action Buttons */}
                  <div style={{ display: 'flex', gap: '8px', marginTop: '20px' }}>
                    <Button
                      icon={<InfoCircleOutlined />}
                      onClick={() => setDetailPlan(plan)}
                      style={{ flex: 1 }}
                    >
                      Chi tiết
                    </Button>
                    <Button
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      onClick={() => setSelectedPlanForOrder(plan)}
                      style={{ flex: 1.4, background: '#0052cc' }}
                    >
                      Mua ngay
                    </Button>
                  </div>
                </Card>
              </Badge.Ribbon>
            </Col>
          ))}
        </Row>
      )}

      {/* Plan Details Modal */}
      <Modal
        title={`Chi tiết gói cước: ${detailPlan?.name}`}
        open={!!detailPlan}
        onCancel={() => setDetailPlan(null)}
        footer={[
          <Button key="close" onClick={() => setDetailPlan(null)}>
            Đóng
          </Button>,
          <Button
            key="buy"
            type="primary"
            style={{ background: '#0052cc' }}
            onClick={() => {
              const p = detailPlan;
              setDetailPlan(null);
              setSelectedPlanForOrder(p);
            }}
          >
            Mua ngay
          </Button>,
        ]}
      >
        {detailPlan && (
          <Descriptions column={1} bordered size="small" style={{ marginTop: 16 }}>
            <Descriptions.Item label="Mã gói">{detailPlan.code}</Descriptions.Item>
            <Descriptions.Item label="Giá cước">{detailPlan.price.toLocaleString('vi-VN')} đ</Descriptions.Item>
            <Descriptions.Item label="Thời hạn">
              {detailPlan.durationMonths} tháng ({detailPlan.cycleDays} ngày/chu kỳ)
            </Descriptions.Item>
            <Descriptions.Item label="Dung lượng data">
              {formatGb(detailPlan.dataQuotaMb)} ({detailPlan.quotaType === 'DAILY' ? 'Mỗi ngày' : 'Mỗi chu kỳ'})
            </Descriptions.Item>
            <Descriptions.Item label="Chính sách hết data">
              {detailPlan.cutoffPolicy === 'THROTTLE'
                ? `Hạ băng thông còn ${detailPlan.throttleSpeedKbps || 512} Kbps (truy cập không ngắt kết nối)`
                : 'Ngắt kết nối Internet cho đến chu kỳ tiếp theo'}
            </Descriptions.Item>
            <Descriptions.Item label="Thoại">
              {detailPlan.voiceMinutes > 0 ? `${detailPlan.voiceMinutes} phút` : 'Theo giá cước hiện hành'}
            </Descriptions.Item>
            <Descriptions.Item label="Mô tả">{detailPlan.description || 'Không có mô tả thêm'}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* Order Modal (B15) */}
      <OrderModal
        plan={selectedPlanForOrder}
        open={!!selectedPlanForOrder}
        onCancel={() => setSelectedPlanForOrder(null)}
      />
    </div>
  );
};
