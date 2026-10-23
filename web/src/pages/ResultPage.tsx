import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Result, Button, Card, Descriptions, Typography, Input, Form, message, Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, SyncOutlined, HomeOutlined, RedoOutlined } from '@ant-design/icons';
import { getOrder, createPayment } from '../api/client';
import { StoreOrder } from '../types/store';

const { Title, Paragraph } = Typography;

export const ResultPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const callbackError = searchParams.get('error') || '';
  const orderCode = searchParams.get('orderCode') || (callbackError ? '' : localStorage.getItem('last_order_code') || '');

  const [phone, setPhone] = useState<string>(localStorage.getItem('last_customer_phone') || '');
  const [pollCount, setPollCount] = useState(0);
  const [retryingPayment, setRetryingPayment] = useState(false);

  // Keep polling long enough for the scheduled QueryDR fallback to run when IPN is delayed.
  const { data: order, isLoading, refetch } = useQuery<StoreOrder>({
    queryKey: ['order', orderCode, phone],
    queryFn: () => getOrder(orderCode, phone),
    enabled: !!orderCode && !!phone,
    refetchInterval: (query) => {
      const currentStatus = query.state.data?.status || 'PENDING_PAYMENT';
      if (['PENDING_REVIEW', 'FAILED', 'CANCELLED', 'EXPIRED'].includes(currentStatus)) return false;
      if (currentStatus === 'PENDING_PAYMENT' && pollCount < 150) {
        return pollCount < 15 ? 2000 : 10000;
      }
      return false;
    },
  });

  const requiresManualReview = order?.status === 'PENDING_REVIEW';
  const paymentFailedAtGateway = ['FAILED', 'CANCELLED', 'EXPIRED'].includes(order?.status || '');
  const orderCanBeRetried = !!order && ['FAILED', 'CANCELLED'].includes(order.status)
    && new Date(order.expiresAt).getTime() > Date.now();

  useEffect(() => {
    if (order?.status === 'PENDING_PAYMENT' && !requiresManualReview
      && !paymentFailedAtGateway && pollCount < 150) {
      const timer = setInterval(() => {
        setPollCount((prev) => Math.min(prev + 1, 150));
      }, 2000);
      return () => clearInterval(timer);
    }
  }, [order?.status, requiresManualReview, paymentFailedAtGateway, pollCount]);

  const handlePhoneSubmit = (values: { phone: string }) => {
    setPhone(values.phone.trim());
    localStorage.setItem('last_customer_phone', values.phone.trim());
  };

  const handleRetryPayment = async () => {
    try {
      setRetryingPayment(true);
      const res = await createPayment(orderCode);
      window.location.href = res.paymentUrl;
    } catch (err: any) {
      setRetryingPayment(false);
      const msg = err.response?.data?.message || err.message || 'Không thể tạo lại yêu cầu thanh toán';
      message.error(msg);
    }
  };

  const currentStatus = order?.status || 'PENDING_PAYMENT';

  if (callbackError) {
    const errorMessage = callbackError === 'invalid_signature'
      ? 'Không xác minh được chữ ký phản hồi thanh toán.'
      : callbackError === 'order_not_found'
        ? 'Không tìm thấy đơn hàng tương ứng với giao dịch.'
        : 'Không thể xác minh dữ liệu phản hồi thanh toán.';
    return (
      <div style={{ maxWidth: 800, margin: '40px auto', padding: '0 16px' }}>
        <Result
          status="warning"
          title="Chưa thể xác minh kết quả thanh toán"
          subTitle={`${errorMessage} Hãy tra cứu đơn hàng để xem trạng thái mới nhất.`}
          extra={<Button type="primary" onClick={() => navigate('/lookup')}>Tra cứu đơn hàng</Button>}
        />
      </div>
    );
  }

  if (!orderCode) {
    return (
      <div style={{ maxWidth: 800, margin: '40px auto', padding: '0 16px' }}>
        <Result
          status="warning"
          title="Không tìm thấy mã đơn hàng"
          subTitle="Vui lòng kiểm tra lại liên kết hoặc tra cứu đơn hàng qua số điện thoại."
          extra={
            <Button type="primary" onClick={() => navigate('/')}>
              Về trang chủ
            </Button>
          }
        />
      </div>
    );
  }

  // If customer phone is needed for order lookup
  if (!phone) {
    return (
      <div style={{ maxWidth: 500, margin: '60px auto', padding: '0 16px' }}>
        <Card title="Xác nhận số điện thoại tra cứu đơn" style={{ borderRadius: '12px' }}>
          <Paragraph type="secondary">
            Để bảo mật thông tin đơn hàng <b>{orderCode}</b>, vui lòng nhập số điện thoại bạn đã dùng khi đặt hàng:
          </Paragraph>
          <Form onFinish={handlePhoneSubmit} layout="vertical">
            <Form.Item
              name="phone"
              rules={[
                { required: true, message: 'Vui lòng nhập số điện thoại' },
                { pattern: /^[0-9]{9,15}$/, message: 'Số điện thoại không hợp lệ' },
              ]}
            >
              <Input placeholder="Ví dụ: 0912345678" size="large" />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" block style={{ background: '#0052cc' }}>
              Xem kết quả đơn hàng
            </Button>
          </Form>
        </Card>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 800, margin: '32px auto', padding: '0 16px' }}>
      <Card style={{ borderRadius: '16px', boxShadow: '0 4px 16px rgba(0,0,0,0.06)' }}>
        {currentStatus === 'PAID' ? (
          <Result
            status="success"
            icon={<CheckCircleOutlined style={{ color: '#36b37e' }} />}
            title={<Title level={3} style={{ color: '#1e293b', margin: 0 }}>Thanh toán thành công!</Title>}
            subTitle="Gói cước đã được kích hoạt thành công trên số thuê bao của quý khách."
            extra={[
              <Button type="primary" key="home" icon={<HomeOutlined />} onClick={() => navigate('/')} style={{ background: '#0052cc' }}>
                Về trang chủ
              </Button>,
              <Button key="lookup" onClick={() => navigate('/lookup')}>
                Tra cứu đơn hàng
              </Button>,
            ]}
          />
        ) : requiresManualReview ? (
          <Result
            status="warning"
            icon={<SyncOutlined spin style={{ color: '#fa8c16' }} />}
            title="Giao dịch cần được đối soát"
            subTitle="VNPAY báo giao dịch cần kiểm tra thêm. Đơn hàng đang được giữ để đối soát; vui lòng không thanh toán lại."
            extra={<Button key="home" icon={<HomeOutlined />} onClick={() => navigate('/')}>Về danh sách gói</Button>}
          />
        ) : currentStatus === 'CANCELLED' ? (
          <Result
            status="warning"
            icon={<CloseCircleOutlined style={{ color: '#fa8c16' }} />}
            title="Bạn đã hủy giao dịch thanh toán"
            subTitle="Đơn hàng chưa được thanh toán. Bạn có thể thử lại thanh toán bất cứ lúc nào trước khi đơn hàng hết hạn."
            extra={[
              <Button
                type="primary"
                key="retry"
                icon={<RedoOutlined />}
                loading={retryingPayment}
                disabled={!orderCanBeRetried}
                onClick={handleRetryPayment}
                style={{ background: '#0052cc' }}
              >
                Thử lại thanh toán
              </Button>,
              <Button key="home" icon={<HomeOutlined />} onClick={() => navigate('/')}>
                Về danh sách gói
              </Button>,
            ]}
          />
        ) : currentStatus === 'PENDING_REVIEW' ? (
          <Result
            status="warning"
            title="Đơn hàng đang chờ đối soát"
            subTitle="Vui lòng không thanh toán lại cho đến khi giao dịch được kiểm tra xong."
            extra={<Button key="home" icon={<HomeOutlined />} onClick={() => navigate('/')}>Về danh sách gói</Button>}
          />
        ) : currentStatus === 'FAILED' || currentStatus === 'EXPIRED' ? (
          <Result
            status="error"
            icon={<CloseCircleOutlined style={{ color: '#ff5630' }} />}
            title={currentStatus === 'EXPIRED' ? 'Đơn hàng đã hết hạn' : 'Thanh toán chưa thành công'}
            subTitle={currentStatus === 'EXPIRED'
              ? 'Thời hạn thanh toán của đơn đã kết thúc. Vui lòng tạo đơn hàng mới.'
              : 'Giao dịch chưa được xác nhận thành công. Bạn có thể thử thanh toán lại khi đơn còn hạn.'}
            extra={[
              ...(orderCanBeRetried ? [
              <Button type="primary" key="retry" icon={<RedoOutlined />} loading={retryingPayment} onClick={handleRetryPayment} style={{ background: '#0052cc' }}>
                Thử lại thanh toán
              </Button>,
              ] : []),
              <Button key="home" icon={<HomeOutlined />} onClick={() => navigate('/')}>Về danh sách gói</Button>,
            ]}
          />
        ) : currentStatus === 'PENDING_PAYMENT' ? (
          <Result
            icon={<SyncOutlined spin style={{ color: '#0052cc', fontSize: '56px' }} />}
            title="Đang xác nhận kết quả thanh toán..."
            subTitle="Hệ thống đang đối soát với cổng VNPAY. Vui lòng không tắt trình duyệt."
            extra={[
              <Button key="refresh" icon={<RedoOutlined />} onClick={() => refetch()} loading={isLoading}>
                Kiểm tra lại trạng thái
              </Button>,
            ]}
          />
        ) : (
          <Result
            status="error"
            icon={<CloseCircleOutlined style={{ color: '#ff5630' }} />}
            title={<Title level={3} style={{ color: '#1e293b', margin: 0 }}>Thanh toán chưa thành công</Title>}
            subTitle="Giao dịch chưa được xác nhận thành công. Vui lòng kiểm tra trạng thái đơn hàng hoặc liên hệ bộ phận hỗ trợ."
            extra={[
              <Button
                type="primary"
                key="retry"
                icon={<RedoOutlined />}
                loading={retryingPayment}
                onClick={handleRetryPayment}
                style={{ background: '#0052cc' }}
              >
                Thử lại thanh toán
              </Button>,
              <Button key="home" icon={<HomeOutlined />} onClick={() => navigate('/')}>
                Về danh sách gói
              </Button>,
            ]}
          />
        )}

        {/* Order Details Summary */}
        {order && (
          <div style={{ marginTop: '24px', borderTop: '1px solid #f0f0f0', paddingTop: '20px' }}>
            <Descriptions title="Thông tin đơn hàng" column={{ xs: 1, sm: 2 }} bordered size="small">
              <Descriptions.Item label="Mã đơn hàng">{order.orderCode}</Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <Tag color={order.status === 'PAID' ? 'green' : order.status === 'PENDING_REVIEW' ? 'orange' : order.status === 'PENDING_PAYMENT' ? 'gold' : order.status === 'CANCELLED' ? 'orange' : 'red'}>
                  {order.status === 'PAID' ? 'Đã thanh toán' : order.status === 'PENDING_REVIEW' ? 'Chờ đối soát' : order.status === 'PENDING_PAYMENT' ? 'Đang chờ xử lý' : order.status === 'CANCELLED' ? 'Đã hủy' : 'Thất bại / Hết hạn'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Gói cước">{order.planName} ({order.planCode})</Descriptions.Item>
              <Descriptions.Item label="Số tiền">{order.amount.toLocaleString('vi-VN')} đ</Descriptions.Item>
              <Descriptions.Item label="Số thuê bao">{order.msisdn}</Descriptions.Item>
              <Descriptions.Item label="Người mua">{order.customerName}</Descriptions.Item>
              {order.status === 'PAID' && order.paidAt && (
                <Descriptions.Item label="Thời gian thanh toán">
                  {new Date(order.paidAt).toLocaleString('vi-VN')}
                </Descriptions.Item>
              )}
            </Descriptions>
          </div>
        )}
      </Card>
    </div>
  );
};
