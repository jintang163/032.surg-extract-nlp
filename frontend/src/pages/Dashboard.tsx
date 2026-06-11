import React, { useEffect, useState } from 'react'
import {
  Row,
  Col,
  Card,
  Statistic,
  Progress,
  List,
  Tag,
  Button,
  Space,
  Empty,
  Timeline,
  Typography,
  Tooltip,
} from 'antd'
import {
  FileTextOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  ArrowRightOutlined,
  PlusOutlined,
  FileDoneOutlined,
  RiseOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { homePageApi, recordApi } from '@/services/api'
import type { EfficiencyStats, SurgeryRecord, ProcessStatus } from '@/types'
import { ProcessStatusMap } from '@/types'
import dayjs from 'dayjs'

const { Title, Text } = Typography

const Dashboard: React.FC = () => {
  const navigate = useNavigate()
  const [stats, setStats] = useState<EfficiencyStats | null>(null)
  const [recentRecords, setRecentRecords] = useState<SurgeryRecord[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      const [statsData, recordsData] = await Promise.all([
        homePageApi.getEfficiencyStats(),
        recordApi.list({ pageNum: 1, pageSize: 5 }),
      ])
      setStats(statsData)
      setRecentRecords(recordsData.records || [])
    } catch (e) {
      console.error('加载数据失败', e)
    } finally {
      setLoading(false)
    }
  }

  const timelineItems = [
    {
      color: 'blue',
      children: (
        <div>
          <Text strong>上传手术记录</Text>
          <div>
            <Text type="secondary">支持拖拽上传文本、Word、PDF、图片文件</Text>
          </div>
        </div>
      ),
    },
    {
      color: 'cyan',
      children: (
        <div>
          <Text strong>OCR识别与预处理</Text>
          <div>
            <Text type="secondary">自动识别文本内容，清洗噪声数据</Text>
          </div>
        </div>
      ),
    },
    {
      color: 'green',
      children: (
        <div>
          <Text strong>NLP实体抽取</Text>
          <div>
            <Text type="secondary">BERT-BiLSTM-CRF模型 + 正则 + 规则引擎</Text>
          </div>
        </div>
      ),
    },
    {
      color: 'purple',
      children: (
        <div>
          <Text strong>病案首页自动填充</Text>
          <div>
            <Text type="secondary">结构化数据映射，人工确认即可提交</Text>
          </div>
        </div>
      ),
    },
  ]

  return (
    <div className="page-container">
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="手术记录总数"
              value={stats?.totalRecords || 0}
              prefix={<FileTextOutlined style={{ color: '#1677ff' }} />}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="智能提取完成"
              value={stats?.autoFilledCount || 0}
              prefix={<FileDoneOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="已确认提交"
              value={stats?.confirmedCount || 0}
              prefix={<CheckCircleOutlined style={{ color: '#722ed1' }} />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="平均录入时长"
              value={stats?.avgFillSeconds ? Math.round(stats.avgFillSeconds / 60) : 0}
              suffix="分钟"
              prefix={<ClockCircleOutlined style={{ color: '#fa8c16' }} />}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} lg={16}>
          <Card
            title={
              <Space>
                <ThunderboltOutlined style={{ color: '#1677ff' }} />
                <span>效率提升统计</span>
              </Space>
            }
            extra={
              <Tag color="green" icon={<RiseOutlined />}>
                {stats?.efficiencyRate || '0%'}
              </Tag>
            }
            loading={loading}
          >
            <Row gutter={[24, 16]}>
              <Col xs={12}>
                <div style={{ textAlign: 'center', padding: '12px 0' }}>
                  <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 8 }}>
                    累计节省时间
                  </div>
                  <div style={{ fontSize: 32, fontWeight: 700, color: '#52c41a' }}>
                    {stats?.savedHours || 0}
                    <span style={{ fontSize: 14, color: '#8c8c8c', marginLeft: 4 }}>
                      小时
                    </span>
                  </div>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                    约 {stats?.savedMinutes || 0} 分钟
                  </div>
                </div>
              </Col>
              <Col xs={12}>
                <div style={{ padding: '12px 0' }}>
                  <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 8 }}>
                    人工预估 vs 实际用时
                  </div>
                  <div style={{ marginBottom: 12 }}>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                      人工预估：{Math.round((stats?.totalManualEstSeconds || 0) / 60)} 分钟
                    </div>
                    <Progress
                      percent={100}
                      showInfo={false}
                      strokeColor="#ffccc7"
                      trailColor="#f5f5f5"
                      size="small"
                    />
                  </div>
                  <div>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                      实际用时：{Math.round((stats?.totalActualSeconds || 0) / 60)} 分钟
                    </div>
                    <Progress
                      percent={
                        stats?.totalManualEstSeconds
                          ? Math.min(
                              100,
                              Math.round(
                                (stats.totalActualSeconds / stats.totalManualEstSeconds) *
                                  100
                              )
                            )
                          : 0
                      }
                      showInfo={false}
                      strokeColor="#95de64"
                      trailColor="#f5f5f5"
                      size="small"
                    />
                  </div>
                </div>
              </Col>
            </Row>
            <div
              style={{
                marginTop: 16,
                padding: 12,
                background: '#f6ffed',
                borderRadius: 6,
                border: '1px solid #b7eb8f',
              }}
            >
              <Space size={8}>
                <TeamOutlined style={{ color: '#52c41a' }} />
                <Text>
                  相比人工录入，效率提升 <Text strong style={{ color: '#52c41a' }}>
                    {stats?.efficiencyRate || '0%'}
                  </Text>
                </Text>
              </Space>
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title={
              <Space>
                <PlusOutlined style={{ color: '#1677ff' }} />
                <span>快速操作</span>
              </Space>
            }
            loading={loading}
          >
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Button
                type="primary"
                size="large"
                block
                icon={<PlusOutlined />}
                onClick={() => navigate('/records')}
                style={{ height: 48 }}
              >
                上传手术记录
              </Button>
              <Button
                size="large"
                block
                icon={<FileTextOutlined />}
                onClick={() => navigate('/records')}
              >
                查看全部记录
              </Button>
              <Button
                size="large"
                block
                icon={<CheckCircleOutlined />}
                onClick={() => navigate('/records?status=NER_DONE')}
              >
                待处理病案首页
              </Button>
            </Space>
            <div style={{ marginTop: 20 }}>
              <Title level={5} style={{ marginBottom: 12 }}>
                处理流程
              </Title>
              <Timeline items={timelineItems} />
            </div>
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <FileTextOutlined style={{ color: '#1677ff' }} />
            <span>最近上传的手术记录</span>
          </Space>
        }
        extra={
          <Button
            type="link"
            onClick={() => navigate('/records')}
            icon={<ArrowRightOutlined />}
          >
            查看全部
          </Button>
        }
        loading={loading}
      >
        {recentRecords.length > 0 ? (
          <List
            dataSource={recentRecords}
            renderItem={(item) => {
              const statusInfo = ProcessStatusMap[item.processStatus as ProcessStatus]
              return (
                <List.Item
                  key={item.id}
                  actions={[
                    <Button
                      type="link"
                      size="small"
                      onClick={() => navigate(`/records/${item.id}`)}
                    >
                      查看详情
                    </Button>,
                    (item.processStatus === 'NER_DONE' ||
                      item.processStatus === 'COMPLETED') && (
                      <Button
                        type="primary"
                        size="small"
                        onClick={() => navigate(`/homepage/${item.id}`)}
                      >
                        填写首页
                      </Button>
                    ),
                  ].filter(Boolean) as any}
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        <a onClick={() => navigate(`/records/${item.id}`)}>
                          {item.patientName || '（未识别姓名）'}
                        </a>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {item.hospitalNo || item.recordNo}
                        </Text>
                      </Space>
                    }
                    description={
                      <Space size={12} wrap>
                        <Text type="secondary">{item.originalFileName}</Text>
                        <Tooltip title="上传时间">
                          <Text type="secondary">
                            {dayjs(item.uploadTime).format('YYYY-MM-DD HH:mm')}
                          </Text>
                        </Tooltip>
                        <Tag
                          color={statusInfo?.color}
                          className={
                            item.processStatus === 'OCR_PROCESSING' ||
                            item.processStatus === 'NER_PROCESSING'
                              ? 'status-tag-processing'
                              : ''
                          }
                        >
                          {statusInfo?.label}
                        </Tag>
                      </Space>
                    }
                  />
                </List.Item>
              )
            }}
          />
        ) : (
          <Empty description="暂无记录，点击上方按钮上传手术记录" />
        )}
      </Card>
    </div>
  )
}

export default Dashboard
