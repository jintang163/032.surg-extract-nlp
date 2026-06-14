import React from 'react'
import { Layout, Menu, Avatar, Dropdown, Space, Badge } from 'antd'
import {
  DashboardOutlined,
  FileTextOutlined,
  FileSearchOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  BellOutlined,
  MedicineBoxOutlined,
  FileMarkdownOutlined,
  AudioOutlined,
  ApiOutlined,
  SafetyCertificateOutlined,
  FileProtectOutlined,
  BarChartOutlined,
  InboxOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

const { Header, Sider, Content } = Layout

const MainLayout: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { userInfo, logout } = useAuthStore()

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '工作台',
      onClick: () => navigate('/dashboard'),
    },
    {
      key: '/records',
      icon: <FileTextOutlined />,
      label: '手术记录管理',
      onClick: () => navigate('/records'),
    },
    {
      key: '/quality-control',
      icon: <SafetyCertificateOutlined />,
      label: '数据质控中心',
      onClick: () => navigate('/quality-control'),
    },
    {
      key: '/analytics',
      icon: <BarChartOutlined />,
      label: '统计分析仪表盘',
      onClick: () => navigate('/analytics'),
    },
    {
      key: '/feedback',
      icon: <ThunderboltOutlined />,
      label: '反馈与主动学习',
      onClick: () => navigate('/feedback'),
    },
    {
      key: '/batch',
      icon: <InboxOutlined />,
      label: '批量处理任务',
      onClick: () => navigate('/batch'),
    },
    {
      key: '/voice',
      icon: <AudioOutlined />,
      label: '语音录入',
      onClick: () => navigate('/voice'),
    },
    ...(userInfo?.role === 'ADMIN'
      ? [
          {
            key: '/templates',
            icon: <FileMarkdownOutlined />,
            label: '手术模板管理',
            onClick: () => navigate('/templates'),
          },
          {
            key: '/qc-report-templates',
            icon: <FileProtectOutlined />,
            label: '质控报告模板',
            onClick: () => navigate('/qc-report-templates'),
          },
          {
            key: '/medical-term',
            icon: <ApiOutlined />,
            label: '术语映射管理',
            onClick: () => navigate('/medical-term'),
          },
        ]
      : []),
  ]

  const getActiveKey = () => {
    const path = location.pathname
    if (path.startsWith('/records')) return '/records'
    if (path.startsWith('/homepage')) return '/records'
    if (path.startsWith('/templates')) return '/templates'
    if (path.startsWith('/voice')) return '/voice'
    if (path.startsWith('/medical-term')) return '/medical-term'
    if (path.startsWith('/quality-control')) return '/quality-control'
    if (path.startsWith('/analytics')) return '/analytics'
    if (path.startsWith('/feedback')) return '/feedback'
    if (path.startsWith('/batch')) return '/batch'
    if (path.startsWith('/qc-report-templates')) return '/qc-report-templates'
    return path
  }

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人信息',
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '系统设置',
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => {
        logout()
        navigate('/login')
      },
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        width={220}
        style={{
          background: '#001529',
          overflow: 'auto',
          height: '100vh',
          position: 'sticky',
          top: 0,
          left: 0,
        }}
        theme="dark"
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: 16,
            fontWeight: 600,
            background: 'rgba(255,255,255,0.05)',
            marginBottom: 8,
          }}
        >
          <MedicineBoxOutlined style={{ fontSize: 24, marginRight: 8, color: '#1677ff' }} />
          <span>手术结构化系统</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[getActiveKey()]}
          defaultOpenKeys={['/records']}
          items={menuItems}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            boxShadow: '0 1px 4px rgba(0,21,41,.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            position: 'sticky',
            top: 0,
            zIndex: 10,
          }}
        >
          <div style={{ fontSize: 18, fontWeight: 500, color: '#262626' }}>
            手术记录结构化提取系统
          </div>
          <Space size={20}>
            <Badge count={3} size="small">
              <BellOutlined style={{ fontSize: 18, cursor: 'pointer', color: '#595959' }} />
            </Badge>
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', padding: '0 8px', borderRadius: 4 }}>
                <Avatar size={32} icon={<UserOutlined />} style={{ background: '#1677ff' }}>
                  {userInfo?.realName?.charAt(0)}
                </Avatar>
                <span>
                  {userInfo?.realName || '用户'}
                  <span style={{ color: '#8c8c8c', fontSize: 12, marginLeft: 6 }}>
                    {userInfo?.role === 'ADMIN'
                      ? '管理员'
                      : userInfo?.role === 'DOCTOR'
                        ? '医生'
                        : '护士'}
                  </span>
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: 20, background: '#f5f7fa' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
