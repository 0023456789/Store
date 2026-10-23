import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from 'antd';
import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer';
import { PlanListPage } from './pages/PlanListPage';
import { ResultPage } from './pages/ResultPage';
import { LookupPage } from './pages/LookupPage';

const { Content } = Layout;

export const App: React.FC = () => {
  return (
    <Layout style={{ minHeight: '100vh', background: '#f8fafc' }}>
      <Navbar />
      <Content>
        <Routes>
          <Route path="/" element={<PlanListPage />} />
          <Route path="/result" element={<ResultPage />} />
          <Route path="/lookup" element={<LookupPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Content>
      <Footer />
    </Layout>
  );
};
