import React, { useEffect, useState } from 'react'
import {
  Card,
  Row,
  Col,
  Statistic,
  Progress,
  Table,
  Tag,
  Space,
  DatePicker,
  Button,
  Typography,
  Tabs,
  Modal,
  Form,
  InputNumber,
  Select,
  Input,
  Tooltip,
  Divider,
  Empty,
  Spin,
  Badge,
  Alert,
  message,
} from 'antd'
import {
  ThunderboltOutlined,
  LineChartOutlined,
  TeamOutlined,
  BulbOutlined,
  ExperimentOutlined,
  RiseOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DownloadOutlined,
  PlayCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { feedbackApi } from '@/services/api'
import type {
  FeedbackDashboardData,
  EntityFeedbackStats,
  DoctorFeedbackStats,
  ModelTrainLog,
  CorrectionTypeStats,
  TrainType,
  ModelTrainRequest,
} from '@/types'
import {
  CorrectionTypeMap,
  TrainStatusMap,
  TrainTypeMap,
  EntityTypeLabelMap,
} from '@/types'
import dayjs, { Dayjs } from 'dayjs'
import { useNavigate } from 'react-router-dom'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

const FeedbackDashboard: React.FC = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<FeedbackDashboardData | null>(null)
  const [days, setDays] = useState(30)
  const [trainModalVisible, setTrainModalVisible] = useState(false)
  const [trainForm] = Form.useForm()
  const [training, setTraining] = useState(false)

  useEffect(() => {
    loadDashboard()
  }, [days])

  const loadDashboard = async () => {
    setLoading(true)
    try {
      const result = await feedbackApi.getDashboard({ days })
      setData(result)
    } catch (e) {
      console.error('加载反馈仪表盘失败', e)
    } finally {
      setLoading(false)
    }
  }

  const handleTriggerTraining = async () => {
    try {
      const values = await trainForm.validateFields()
      setTraining(true)
      const req: ModelTrainRequest = {
        trainType: values.trainType || 'INCREMENTAL',
        maxFeedbackCount: values.maxFeedbackCount,
        minQualityScore: values.minQualityScore,
        epochs: values.epochs,
        batchSize: values.batchSize,
        learningRate: values.learningRate,
        remark: values.remark,
      }
      await feedbackApi.triggerTraining(req)
      message.success('训练任务已启动，训练完成后可在日志中查看结果')
      setTrainModalVisible(false)
      trainForm.resetFields()
      loadDashboard()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error('启动训练失败')
    } finally {
      setTraining(false)
    }
  }

  const handleExportTrainingData = async () => {
    try {
      const content = await feedbackApi.exportTrainingData({ format: 'TSV', limit: 1000 })
      const blob = new Blob([content], { type: 'text/tab-separated-values;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `training-data-${dayjs().format('YYYYMMDD-HHmmss')}.tsv`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      message.success('训练数据已导出')
    } catch (e) {
      message.error('导出失败')
    }
  }

  if (!data && !loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Empty description="暂无反馈数据" />
      </div>
    )
  }

  const overview = data?.overview
  const trend = data?.feedbackTrend || []
  const entityStats = data?.entityTypeStats || []
  const correctionStats = data?.correctionTypeStats || []
  const topDoctors = data?.topDoctors || []
  const trainLogs = data?.recentTrainLogs || []

  const maxFeedbackCount = Math.max(...trend.map((t) => t.feedbackCount), 1)
  const maxF1 = 0.98

  const f1Color = (score?: number) => {
    if (!score) return '#8c8c8c'
    if (score >= 0.9) return '#52c41a'
    if (score >= 0.8) return '#faad14'
    return '#ff4d4f'
  }

  const improvementColor = (improvement?: number) => {
    if (!improvement) return '#8c8c8c'
    if (improvement > 0) return '#52c41a'
    if (improvement < 0) return '#ff4d4f'
    return '#8c8c8c'
  }

  const correctionColumns = [
    {
      title: '修正类型',
      dataIndex: 'correctionType',
      width: 120,
      render: (t: keyof typeof CorrectionTypeMap) => {
        const info = CorrectionTypeMap[t]
        return <Tag color={info?.color}>{info?.label || t}</Tag>
      },
    },
    {
      title: '数量',
      dataIndex: 'count',
      width: 100,
      render: (v: number) => <Text strong>{v}</Text>,
    },
    {
      title: '占比',
      dataIndex: 'percentage',
      render: (v: number) => (
        <Progress
          percent={Math.round(v * 100)}
          size="small"
          status={v >= 0.5 ? 'exception' : v >= 0.3 ? 'active' : 'success'}
        />
      ),
    },
  ]

  const entityColumns = [
    {
      title: '实体类型',
      dataIndex: 'entityType',
      width: 140,
      render: (t: keyof typeof EntityTypeLabelMap) => {
        const info = EntityTypeLabelMap[t]
        return <Tag color={info?.color || 'default'}>{info?.label || t}</Tag>
      },
    },
    {
      title: '反馈总数',
      dataIndex: 'feedbackCount',
      width: 100,
      render: (v: number) => <Text strong>{v}</Text>,
      sorter: (a: EntityFeedbackStats, b: EntityFeedbackStats) => a.feedbackCount - b.feedbackCount,
    },
    {
      title: '修改/新增/删除',
      width: 180,
      render: (_: any, r: EntityFeedbackStats) => (
        <Space size={4}>
          <Tag color="orange">{r.correctionCount}</Tag>
          <Tag color="green">{r.additionCount}</Tag>
          <Tag color="red">{r.deletionCount}</Tag>
        </Space>
      ),
    },
    {
      title: '平均置信度',
      dataIndex: 'avgOriginalConfidence',
      width: 140,
      render: (v: number) =>
        typeof v === 'number' ? (
          <Progress percent={Math.round(v * 100)} size="small" />
        ) : (
          '-'
        ),
    },
    {
      title: '已用于训练占比',
      dataIndex: 'usedForTrainingRate',
      render: (v: number) =>
        typeof v === 'number' ? (
          <Progress percent={Math.round(v * 100)} size="small" />
        ) : (
          '-'
        ),
    },
  ]

  const doctorColumns = [
    {
      title: '排名',
      width: 60,
      render: (_: any, _r: any, idx: number) => {
        const colors = ['#faad14', '#bfbfbf', '#d48806']
        const icons = ['🥇', '🥈', '🥉']
        if (idx < 3) {
          return (
            <span style={{ fontSize: 18 }}>
              {icons[idx]}
            </span>
          )
        }
        return <Text type="secondary">{idx + 1}</Text>
      },
    },
    {
      title: '医生姓名',
      dataIndex: 'feedbackUserName',
      width: 120,
      render: (v: string, r: DoctorFeedbackStats) => (
        <Space>
          <Text strong>{v}</Text>
          {r.department && <Tag color="blue">{r.department}</Tag>}
        </Space>
      ),
    },
    {
      title: '反馈总数',
      dataIndex: 'feedbackCount',
      width: 100,
      render: (v: number) => <Text strong style={{ color: '#1677ff' }}>{v}</Text>,
    },
    {
      title: '修改数',
      dataIndex: 'correctionCount',
      width: 80,
    },
    {
      title: '平均质量分',
      dataIndex: 'avgQualityScore',
      width: 140,
      render: (v: number) =>
        typeof v === 'number' ? (
          <Progress
            percent={Math.round(v)}
            size="small"
            status={v >= 80 ? 'success' : v >= 60 ? 'normal' : 'exception'}
          />
        ) : (
          '-'
        ),
    },
    {
      title: '贡献分',
      dataIndex: 'contributionScore',
      width: 120,
      render: (v: number) => (
        <Tag color={v >= 80 ? 'gold' : v >= 50 ? 'blue' : 'default'}>
          {v?.toFixed(1)}
        </Tag>
      ),
      sorter: (a: DoctorFeedbackStats, b: DoctorFeedbackStats) =>
        (a.contributionScore || 0) - (b.contributionScore || 0),
    },
  ]

  const trainColumns = [
    {
      title: '批次号',
      dataIndex: 'trainBatchNo',
      width: 180,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: '训练类型',
      dataIndex: 'trainType',
      width: 110,
      render: (t: keyof typeof TrainTypeMap) => {
        const info = TrainTypeMap[t]
        return <Tag color={info?.color}>{info?.label || t}</Tag>
      },
    },
    {
      title: '反馈样本数',
      dataIndex: 'feedbackCount',
      width: 100,
    },
    {
      title: 'F1分数',
      dataIndex: 'f1Score',
      width: 120,
      render: (v: number) =>
        typeof v === 'number' ? (
          <Text strong style={{ color: f1Color(v) }}>
            {(v * 100).toFixed(2)}%
          </Text>
        ) : (
          '-'
        ),
    },
    {
      title: '提升幅度',
      dataIndex: 'f1Improvement',
      width: 110,
      render: (v: number) =>
        typeof v === 'number' ? (
          <Space>
            {v > 0 ? <RiseOutlined /> : v < 0 ? '▼' : '—'}
            <Text strong style={{ color: improvementColor(v) }}>
              {v > 0 ? '+' : ''}
              {(v * 100).toFixed(2)}%
            </Text>
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: '状态',
      dataIndex: 'trainStatus',
      width: 100,
      render: (t: keyof typeof TrainStatusMap) => {
        const info = TrainStatusMap[t]
        return (
          <Tag icon={t === 'RUNNING' ? <ClockCircleOutlined spin /> : <CheckCircleOutlined />} color={info?.color}>
            {info?.label || t}
          </Tag>
        )
      },
    },
    {
      title: '耗时',
      dataIndex: 'trainDurationSec',
      width: 90,
      render: (v: number) => (v ? `${v}s` : '-'),
    },
    {
      title: '触发人',
      dataIndex: 'triggeredByName',
      width: 90,
    },
    {
      title: '创建时间',
      dataIndex: 'createdTime',
      width: 160,
      render: (v: string) => dayjs(v).format('MM-DD HH:mm'),
    },
  ]

  return (
    <div className="page-container">
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Title level={4} style={{ margin: 0 }}>
            <ThunderboltOutlined style={{ color: '#1677ff' }} /> 医生反馈与主动学习仪表盘
          </Title>
          {overview?.latestF1Score && (
            <Badge
              count={`F1: ${(overview.latestF1Score * 100).toFixed(1)}%`}
              style={{ backgroundColor: f1Color(overview.latestF1Score) }}
            />
          )}
          {overview?.f1Improvement != null && overview.f1Improvement !== 0 && (
            <Tag color={overview.f1Improvement > 0 ? 'green' : 'red'}>
              {overview.f1Improvement > 0 ? '↑' : '↓'} {Math.abs(overview.f1Improvement * 100).toFixed(2)}%
            </Tag>
          )}
        </Space>
        <Space>
          <Select
            value={days}
            style={{ width: 130 }}
            onChange={setDays}
            options={[
              { label: '最近7天', value: 7 },
              { label: '最近14天', value: 14 },
              { label: '最近30天', value: 30 },
              { label: '最近90天', value: 90 },
            ]}
          />
          <Button icon={<DownloadOutlined />} onClick={handleExportTrainingData}>
            导出训练数据
          </Button>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={() => setTrainModalVisible(true)}
          >
            触发增量训练
          </Button>
        </Space>
      </div>

      {overview && (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic
                title="总反馈数"
                value={overview.totalFeedbackCount}
                prefix={<ThunderboltOutlined />}
                valueStyle={{ color: '#1677ff' }}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                待训练：{overview.pendingTrainingCount} | 已训练：{overview.usedForTrainingCount}
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic
                title="模型F1分数"
                value={overview.latestF1Score}
                precision={4}
                prefix={<ExperimentOutlined />}
                valueStyle={{ color: f1Color(overview.latestF1Score) }}
                formatter={(v) => `${((v as number) * 100).toFixed(2)}%`}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                共训练 {overview.totalTrainCount} 次
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic
                title="F1提升幅度"
                value={overview.f1Improvement}
                precision={4}
                prefix={<RiseOutlined />}
                valueStyle={{ color: improvementColor(overview.f1Improvement) }}
                formatter={(v) => {
                  const n = v as number
                  return `${n > 0 ? '+' : ''}${(n * 100).toFixed(2)}%`
                }}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                主动学习效果
              </Text>
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic
                title="活跃医生数"
                value={overview.activeDoctorCount}
                prefix={<TeamOutlined />}
                valueStyle={{ color: '#52c41a' }}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                平均质量分：{overview.averageQualityScore?.toFixed(1) || 0}/100
              </Text>
            </Card>
          </Col>
        </Row>
      )}

      <Tabs
        defaultActiveKey="trend"
        items={[
          {
            key: 'trend',
            label: (
              <span>
                <LineChartOutlined /> 反馈趋势
              </span>
            ),
            children: (
              <Card loading={loading}>
                {trend.length === 0 ? (
                  <Empty description="暂无趋势数据" />
                ) : (
                  <div>
                    <Alert
                      type="info"
                      showIcon
                      message={`近 ${days} 天共收到 ${trend.reduce((sum, t) => sum + t.feedbackCount, 0)} 条反馈`}
                      style={{ marginBottom: 16 }}
                    />
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                      {trend.map((t, idx) => (
                        <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                          <Text style={{ width: 100, color: '#595959' }}>{t.date}</Text>
                          <div style={{ flex: 1 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <Progress
                                percent={Math.round((t.feedbackCount / maxFeedbackCount) * 100)}
                                showInfo={false}
                                style={{ flex: 1, minWidth: 200 }}
                                status={t.feedbackCount > maxFeedbackCount * 0.7 ? 'success' : 'active'}
                              />
                              <Space size={8}>
                                <Tag color="blue">{t.feedbackCount} 条</Tag>
                                {t.correctionCount > 0 && <Tag color="orange">改{t.correctionCount}</Tag>}
                                {t.additionCount > 0 && <Tag color="green">增{t.additionCount}</Tag>}
                                {t.deletionCount > 0 && <Tag color="red">删{t.deletionCount}</Tag>}
                              </Space>
                            </div>
                            <div style={{ marginTop: 4, fontSize: 12, color: '#8c8c8c' }}>
                              已用于训练 {t.usedForTrainingCount} 条 · 平均质量 {t.avgQualityScore?.toFixed(0) || 0} 分 · {t.activeDoctorCount} 位医生
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </Card>
            ),
          },
          {
            key: 'entity',
            label: (
              <span>
                <BulbOutlined /> 实体类型统计
              </span>
            ),
            children: (
              <Card loading={loading}>
                <Table
                  size="small"
                  rowKey="entityType"
                  dataSource={entityStats}
                  columns={entityColumns}
                  pagination={false}
                  locale={{ emptyText: '暂无实体类型统计数据' }}
                />
              </Card>
            ),
          },
          {
            key: 'correction',
            label: (
              <span>
                <FileTextOutlined /> 修正类型分布
              </span>
            ),
            children: (
              <Card loading={loading}>
                <Table
                  size="small"
                  rowKey="correctionType"
                  dataSource={correctionStats}
                  columns={correctionColumns}
                  pagination={false}
                  locale={{ emptyText: '暂无修正类型统计' }}
                />
              </Card>
            ),
          },
          {
            key: 'doctors',
            label: (
              <span>
                <TeamOutlined /> 贡献医生排行
              </span>
            ),
            children: (
              <Card loading={loading}>
                <Table
                  size="small"
                  rowKey="feedbackUserId"
                  dataSource={topDoctors}
                  columns={doctorColumns}
                  pagination={false}
                  locale={{ emptyText: '暂无医生贡献数据' }}
                />
              </Card>
            ),
          },
          {
            key: 'train',
            label: (
              <span>
                <ExperimentOutlined /> 模型训练日志
              </span>
            ),
            children: (
              <Card loading={loading}>
                <Table
                  size="small"
                  rowKey="id"
                  dataSource={trainLogs}
                  columns={trainColumns}
                  pagination={false}
                  locale={{ emptyText: '暂无训练日志' }}
                />
              </Card>
            ),
          },
        ]}
      />

      <Modal
        title="触发增量微调训练"
        open={trainModalVisible}
        onCancel={() => setTrainModalVisible(false)}
        footer={
          <Space>
            <Button onClick={() => setTrainModalVisible(false)}>取消</Button>
            <Button type="primary" loading={training} onClick={handleTriggerTraining}>
              开始训练
            </Button>
          </Space>
        }
        width={560}
      >
        <Alert
          type="info"
          showIcon
          message="训练将使用医生反馈数据对现有模型进行增量微调，预计耗时3-10分钟"
          style={{ marginBottom: 16 }}
        />
        <Form form={trainForm} layout="vertical">
          <Form.Item
            label="训练类型"
            name="trainType"
            initialValue="INCREMENTAL"
          >
            <Select
              options={[
                { label: '增量微调（推荐）', value: 'INCREMENTAL' },
                { label: '全量重新训练', value: 'FULL' },
                { label: '周度自动训练', value: 'WEEKLY' },
              ]}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="最大反馈数量" name="maxFeedbackCount" initialValue={500}>
                <InputNumber min={10} max={5000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="最低质量分" name="minQualityScore" initialValue={60}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="Epochs" name="epochs" initialValue={10}>
                <InputNumber min={1} max={50} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Batch Size" name="batchSize" initialValue={16}>
                <InputNumber min={1} max={128} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="学习率" name="learningRate" initialValue={0.001}>
                <InputNumber step={0.0001} min={0.00001} max={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} placeholder="可选，记录本次训练的说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default FeedbackDashboard
