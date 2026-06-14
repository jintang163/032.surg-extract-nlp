import React, { useEffect, useState } from 'react'
import {
  Card,
  Row,
  Col,
  Typography,
  Tag,
  Button,
  Empty,
  Space,
  Spin,
  Progress,
  Tooltip,
  Badge,
  Divider,
  Drawer,
  Modal,
  message,
} from 'antd'
import {
  ThunderboltOutlined,
  FileTextOutlined,
  RobotOutlined,
  ClockCircleOutlined,
  StarOutlined,
  FireOutlined,
  TeamOutlined,
  FileSearchOutlined,
  DownloadOutlined,
  RocketOutlined,
  CheckCircleOutlined,
  CloseOutlined,
} from '@ant-design/icons'
import { nextStepApi } from '@/services/api'
import type { NextStepRecommend, GeneratedDraftResult } from '@/types'

const { Title, Text, Paragraph } = Typography

const DOC_TYPE_COLOR_MAP: Record<string, string> = {
  出院小结: 'magenta',
  术后医嘱: 'volcano',
  手术记录: 'red',
  病程记录: 'orange',
  麻醉记录: 'gold',
  知情同意书: 'lime',
  护理记录: 'green',
  查房记录: 'cyan',
  术前讨论: 'geekblue',
  术后病程: 'purple',
  其他文书: 'default',
}

const docTypeIcon = (docType: string) => {
  switch (docType) {
    case '出院小结':
      return <DownloadOutlined />
    case '术后医嘱':
    case '术后病程':
      return <ThunderboltOutlined />
    case '手术记录':
      return <FileTextOutlined />
    case '麻醉记录':
      return <TeamOutlined />
    case '病程记录':
    case '查房记录':
      return <FileSearchOutlined />
    case '知情同意书':
      return <CheckCircleOutlined />
    case '护理记录':
      return <ClockCircleOutlined />
    case '术前讨论':
      return <RocketOutlined />
    default:
      return <FileTextOutlined />
  }
}

interface RecommendCardProps {
  item: NextStepRecommend
  index: number
  generating: boolean
  onGenerate: (item: NextStepRecommend) => void
}

const RecommendCard: React.FC<RecommendCardProps> = ({ item, index, generating, onGenerate }) => {
  const scorePct = Math.round((item.score || 0) * 100)
  const color =
    index === 0
      ? '#fa8c16'
      : index === 1
      ? '#1677ff'
      : index === 2
      ? '#a0d911'
      : '#8c8c8c'

  const isTop = index === 0

  return (
    <Badge.Ribbon
      text={isTop ? '最佳推荐' : `#${index + 1}`}
      color={color}
      style={{ fontSize: 11 }}
    >
      <Card
        hoverable
        size="small"
        style={{
          borderRadius: 10,
          border: isTop ? '1.5px solid #fa8c16' : '1px solid #f0f0f0',
          height: '100%',
          background: isTop ? 'linear-gradient(135deg, #fff7e6 0%, #ffffff 60%)' : '#fff',
          position: 'relative',
        }}
        styles={{ body: { padding: 14 } }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Space size={6} style={{ alignItems: 'flex-start' }}>
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: `${DOC_TYPE_COLOR_MAP[item.documentType] || '#1677ff'}22`,
                color: DOC_TYPE_COLOR_MAP[item.documentType] || '#1677ff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
              }}
            >
              {docTypeIcon(item.documentType)}
            </div>
            <div style={{ maxWidth: 180 }}>
              <Title level={5} style={{ margin: 0, fontSize: 14 }}>
                <Tooltip title={item.templateName}>{item.templateName}</Tooltip>
              </Title>
              <Tag
                color={DOC_TYPE_COLOR_MAP[item.documentType] || 'default'}
                style={{ marginTop: 4, fontSize: 11, padding: '0 6px' }}
              >
                {item.documentType}
              </Tag>
              {item.isDefault && (
                <Tag color="gold" style={{ fontSize: 11, padding: '0 6px' }}>
                  <StarOutlined /> 默认
                </Tag>
              )}
            </div>
          </Space>

          <div style={{ textAlign: 'center', width: 54 }}>
            <Progress
              type="dashboard"
              percent={scorePct}
              size={54}
              strokeColor={color}
              showInfo={false}
            />
            <Text style={{ fontSize: 10, color }} strong>
              {scorePct}分
            </Text>
          </div>
        </div>

        <div style={{ marginTop: 10 }}>
          <Tooltip title={item.recommendedReason}>
            <Paragraph
              ellipsis={{ rows: 2, tooltip: true }}
              style={{
                fontSize: 11,
                marginBottom: 8,
                color: '#595959',
                minHeight: 30,
              }}
            >
              <RobotOutlined style={{ marginRight: 4, color: '#722ed1' }} />
              {item.recommendedReason}
            </Paragraph>
          </Tooltip>
        </div>

        <Row gutter={4} style={{ marginBottom: 8 }}>
          <Col span={12}>
            <div style={{ fontSize: 11, color: '#8c8c8c' }}>
              <FireOutlined style={{ color: '#fa541c', marginRight: 3 }} />
              历史使用 <Text strong style={{ color: '#595959' }}>{item.useCount ?? 0}</Text> 次
            </div>
          </Col>
          <Col span={12}>
            <div style={{ fontSize: 11, color: '#8c8c8c', textAlign: 'right' }}>
              <ClockCircleOutlined style={{ color: '#13c2c2', marginRight: 3 }} />
              预计 <Text strong style={{ color: '#595959' }}>{item.expectedDurationMinutes ?? 5}</Text>分钟
            </div>
          </Col>
        </Row>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 10, minHeight: 20 }}>
          {(item.tags || []).slice(0, 3).map((t) => (
            <Tag
              key={t}
              style={{
                margin: 0,
                fontSize: 10,
                padding: '0 5px',
                lineHeight: '16px',
              }}
            >
              #{t}
            </Tag>
          ))}
        </div>

        <Divider style={{ margin: '4px 0 8px 0' }} dashed />

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            fontSize: 10,
            color: '#8c8c8c',
            marginBottom: 8,
          }}
        >
          <Tooltip title="协同过滤得分：基于您和同科室医生的历史行为序列">
            <span>
              <TeamOutlined style={{ color: '#1677ff', marginRight: 2 }} />
              协同 {(item.collaborativeScore * 100).toFixed(0)}%
            </span>
          </Tooltip>
          <Tooltip title="内容匹配得分：基于当前手术/诊断关键词">
            <span>
              <FileSearchOutlined style={{ color: '#52c41a', marginRight: 2 }} />
              内容 {(item.contentScore * 100).toFixed(0)}%
            </span>
          </Tooltip>
          <Tooltip title="流行度：全系统历史使用频率">
            <span>
              <FireOutlined style={{ color: '#fa541c', marginRight: 2 }} />
              热度 {(item.popularityScore * 100).toFixed(0)}%
            </span>
          </Tooltip>
        </div>

        <Button
          type={isTop ? 'primary' : 'default'}
          size="small"
          block
          icon={<ThunderboltOutlined />}
          style={{
            marginTop: 2,
            background: isTop ? 'linear-gradient(90deg, #fa8c16 0%, #d46b08 100%)' : undefined,
          }}
          loading={generating}
          onClick={() => onGenerate(item)}
        >
          一键生成草稿
        </Button>
      </Card>
    </Badge.Ribbon>
  )
}

interface NextStepRecommendPanelProps {
  recordId: number
  processStatus?: string
  userId?: number
  onDraftGenerated?: (result: GeneratedDraftResult) => void
}

const NextStepRecommendPanel: React.FC<NextStepRecommendPanelProps> = ({
  recordId,
  processStatus,
  userId,
  onDraftGenerated,
}) => {
  const [loading, setLoading] = useState(false)
  const [recommendations, setRecommendations] = useState<NextStepRecommend[]>([])
  const [generatingId, setGeneratingId] = useState<number | null>(null)
  const [previewVisible, setPreviewVisible] = useState(false)
  const [lastResult, setLastResult] = useState<GeneratedDraftResult | null>(null)

  const canShow =
    processStatus === 'NER_DONE' ||
    processStatus === 'COMPLETED' ||
    !processStatus

  const loadRecommendations = async () => {
    if (!canShow) return
    try {
      setLoading(true)
      const res = await nextStepApi.getRecommendations(recordId, userId)
      setRecommendations(Array.isArray(res) ? res : [])
    } catch (e: any) {
      message.warning('推荐列表加载失败：' + (e?.message || '请稍后重试'))
      setRecommendations([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (recordId && canShow) {
      loadRecommendations()
    }
  }, [recordId, processStatus])

  const handleGenerate = async (item: NextStepRecommend) => {
    try {
      setGeneratingId(item.templateId)
      const res: any = await nextStepApi.generateDraft(recordId, item.templateId)
      message.success(res?.message || '草稿生成成功！')
      setLastResult(res)
      setPreviewVisible(true)
      onDraftGenerated?.(res)
      loadRecommendations()
    } catch (e: any) {
      message.error(e?.message || '生成草稿失败')
    } finally {
      setGeneratingId(null)
    }
  }

  if (!canShow) return null

  return (
    <div style={{ marginTop: 24 }}>
      <Card
        title={
          <Space size={8}>
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 8,
                background: 'linear-gradient(135deg, #722ed1, #1677ff)',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <RobotOutlined />
            </div>
            <div>
              <div style={{ fontSize: 15, fontWeight: 600, lineHeight: 1.3 }}>
                智能推荐下一步操作
              </div>
              <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 2 }}>
                协同过滤（45%）+ 内容匹配（35%）+ 流行度（20%）
              </div>
            </div>
          </Space>
        }
        extra={
          <Tooltip title="刷新推荐">
            <Button size="small" icon={<RobotOutlined />} onClick={loadRecommendations}>
              重新推荐
            </Button>
          </Tooltip>
        }
        style={{
          borderRadius: 12,
          border: '1px solid #e6f4ff',
          background: 'linear-gradient(180deg, #f9faff 0%, #ffffff 100%)',
        }}
      >
        <Spin spinning={loading} tip="AI 正在分析最优文书序列...">
          {recommendations.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <Space direction="vertical" size={4}>
                  <Text type="secondary">暂无可用的文书模板</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    请在【模板管理】中维护手术记录/出院小结等模板
                  </Text>
                </Space>
              }
            />
          ) : (
            <Row gutter={[16, 16]}>
              {recommendations.map((item, idx) => (
                <Col key={item.templateId} xs={24} sm={12} md={8} lg={6} xl={6}>
                  <RecommendCard
                    item={item}
                    index={idx}
                    generating={generatingId === item.templateId}
                    onGenerate={handleGenerate}
                  />
                </Col>
              ))}
            </Row>
          )}
        </Spin>

        <Drawer
          title={
            <Space>
              <FileTextOutlined style={{ color: '#1677ff' }} />
              草稿预览：{lastResult?.templateName}
              <Tag color="blue" style={{ marginLeft: 8 }}>
                {lastResult?.documentType}
              </Tag>
              <Tag color="green">
                {lastResult?.draftLength ?? 0} 字符
              </Tag>
            </Space>
          }
          open={previewVisible}
          onClose={() => setPreviewVisible(false)}
          width={720}
          extra={
            <Button
              type="primary"
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => {
                message.success('已保存草稿，可在模板编辑器中继续编辑')
                setPreviewVisible(false)
              }}
            >
              确认并关闭
            </Button>
          }
        >
          {lastResult && (
            <div>
              <Alert
                type="success"
                showIcon
                message={lastResult.message}
                style={{ marginBottom: 16 }}
              />
              <Card
                size="small"
                title={
                  <Space size={6}>
                    <FileTextOutlined /> 生成内容（已回写到记录.draft）
                  </Space>
                }
              >
                <pre
                  style={{
                    background: '#fafafa',
                    padding: 12,
                    borderRadius: 6,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    fontSize: 13,
                    lineHeight: 1.7,
                    color: '#262626',
                    border: '1px dashed #d9d9d9',
                    marginBottom: 0,
                    minHeight: 260,
                  }}
                >
                  {lastResult.draftPreview}
                </pre>
              </Card>
            </div>
          )}
        </Drawer>
      </Card>
    </div>
  )
}

export default NextStepRecommendPanel
