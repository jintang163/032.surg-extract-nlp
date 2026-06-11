import React, { useState } from 'react'
import { Form, Input, Button, Card, Typography, message, Checkbox } from 'antd'
import { UserOutlined, LockOutlined, MedicineBoxOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

const { Title, Text } = Typography

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuthStore()

  const onFinish = async (values: { username: string; password: string; remember: boolean }) => {
    setLoading(true)
    try {
      await login(values.username, values.password)
      message.success('登录成功')
      const from = (location.state as any)?.from || '/dashboard'
      navigate(from, { replace: true })
    } catch (e: any) {
      message.error(e?.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background:
          'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        padding: 20,
      }}
    >
      <Card
        style={{
          width: 420,
          maxWidth: '100%',
          boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
          borderRadius: 12,
        }}
        styles={{ body: { padding: 40 } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div
            style={{
              width: 64,
              height: 64,
              background: 'linear-gradient(135deg, #1677ff 0%, #69b1ff 100%)',
              borderRadius: 16,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
            }}
          >
            <MedicineBoxOutlined style={{ fontSize: 32, color: '#fff' }} />
          </div>
          <Title level={3} style={{ margin: 0 }}>
            手术记录结构化系统
          </Title>
          <Text type="secondary">智能提取 · 自动填充 · 高效录入</Text>
        </div>

        <Form
          name="login"
          onFinish={onFinish}
          initialValues={{ username: 'zhangyi', password: '123456', remember: true }}
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
              autoComplete="username"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <Form.Item name="remember" valuePropName="checked" noStyle>
                <Checkbox>记住我</Checkbox>
              </Form.Item>
              <a onClick={() => message.info('请联系管理员重置密码')}>忘记密码？</a>
            </div>
          </Form.Item>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
              style={{ height: 44, fontSize: 15, fontWeight: 500 }}
            >
              登 录
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center', color: '#8c8c8c', fontSize: 12 }}>
            <div>测试账号：admin / 123456（管理员）</div>
            <div>zhangyi / 123456（医生）</div>
          </div>
        </Form>
      </Card>
    </div>
  )
}

export default Login
