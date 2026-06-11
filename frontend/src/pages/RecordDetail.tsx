import React, { useEffect, useState, useRef, useMemo } from 'react'
import {
  Card,
  Button,
  Tabs,
  Row,
  Col,
  Tag,
  Space,
  Descriptions,
  Input,
  Table,
  Tooltip,
  Modal,
  Form,
  Select,
  InputNumber,
  message,
  Badge,
  Progress,
  Divider,
  Drawer,
  Typography,
  Statistic,
} from 'antd'
import {
  ArrowLeftOutlined,
  EditOutlined,
  SaveOutlined,
  SyncOutlined,
  EyeOutlined,
  FileTextOutlined,
  RocketOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  AudioOutlined,
  ScissorOutlined,
  MergeCellsOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { recordApi, homePageApi } from '@/services/api'
import type { SurgeryRecord, SurgeryEntity, EntityType, ProcessStatus } from '@/types'
import { ProcessStatusMap, EntityTypeLabelMap, SourceMap } from '@/types'
import dayjs from 'dayjs'

const { TextArea } = Input
const { Title, Text } = Typography

const RecordDetail: React.FC = () => {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const recordId = Number(id)

  const [record, setRecord] = useState<SurgeryRecord | null>(null)
  const [ocrText, setOcrText] = useState('')
  const [asrText, setAsrText] = useState('')
  const [activeTextTab, setActiveTextTab] = useState<'ocr' | 'asr'>('ocr')
  const [editingText, setEditingText] = useState('')
  const [isTextEditing, setIsTextEditing] = useState(false)
  const [entities, setEntities] = useState<SurgeryEntity[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [entityDrawer, setEntityDrawer] = useState(false)
  const [currentEntity, setCurrentEntity] = useState<SurgeryEntity | null>(null)
  const [entityForm] = Form.useForm()

  const [selectedEntityType, setSelectedEntityType] = useState<EntityType | 'ALL'>('ALL')

  useEffect(() => {
    loadData()
  }, [recordId])

  const loadData = async () => {
    setLoading(true)
    try {
      const [recordData, textData, entitiesData] = await Promise.all([
        recordApi.detail(recordId),
        recordApi.getOcrText(recordId),
        recordApi.getEntities(recordId).catch(() => []),
      ])
      setRecord(recordData)
      setOcrText(textData || '')
      setAsrText(recordData?.asrText || '')
      setEditingText(textData || '')
      setEntities(entitiesData || [])
      if (recordData?.asrText && !textData) {
        setActiveTextTab('asr')
      }
    } catch (e) {
      message.error('加载数据失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSaveText = async () => {
    setSaving(true)
    try {
      await recordApi.updateOcrText(recordId, editingText)
      setOcrText(editingText)
      setIsTextEditing(false)
      message.success('OCR文本已更新')
    } catch (e) {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleEntityUpdate = async () => {
    try {
      const values = await entityForm.validateFields()
      const updated = entities.map((e) =>
        e.id === currentEntity?.id
          ? {
              ...e,
              entityValue: values.entityValue,
              entityUnit: values.entityUnit || e.entityUnit,
              verified: 1,
            }
          : e
      )
      await recordApi.updateEntities(recordId, updated.filter((e) => e.id === currentEntity?.id))
      setEntities(updated)
      message.success('实体已更新')
      setEntityDrawer(false)
    } catch (e: any) {
      if (e?.errorFields) return
      message.error('更新失败')
    }
  }

  const openEntityEdit = (entity: SurgeryEntity) => {
    setCurrentEntity(entity)
    entityForm.setFieldsValue({
      entityType: entity.entityType,
      entityValue: entity.entityValue,
      entityUnit: entity.entityUnit,
    })
    setEntityDrawer(true)
  }

  const filteredEntities = useMemo(
    () =>
      selectedEntityType === 'ALL'
        ? entities
        : entities.filter((e) => e.entityType === selectedEntityType),
    [entities, selectedEntityType]
  )

  const displayText = useMemo(() => {
    return activeTextTab === 'ocr' ? ocrText : asrText
  }, [activeTextTab, ocrText, asrText])

  const highlightedText = useMemo(() => {
    const text = displayText
    if (!text || entities.length === 0) return text

    const sortedEntities = [...entities]
      .filter((e) => typeof e.startPos === 'number' && typeof e.endPos === 'number')
      .sort((a, b) => (a.startPos || 0) - (b.startPos || 0))

    const result: React.ReactNode[] = []
    let lastIndex = 0
    const usedRanges: { start: number; end: number }[] = []

    sortedEntities.forEach((entity, idx) => {
      const start = entity.startPos || 0
      const end = entity.endPos || 0

      const overlap = usedRanges.some(
        (r) => !(end <= r.start || start >= r.end)
      )
      if (overlap) return
      usedRanges.push({ start, end })

      if (start > lastIndex) {
        result.push(
          <span key={`text-${idx}`}>{text.substring(lastIndex, start)}</span>
        )
      }

      const entityInfo = EntityTypeLabelMap[entity.entityType as EntityType] || {
        label: entity.entityType,
        color: 'default',
      }
      const colorMap: Record<string, string> = {
        magenta: '#fff0f6',
        red: '#fff1f0',
        volcano: '#fff2e8',
        orange: '#fff7e6',
        gold: '#fffbe6',
        lime: '#fcffe6',
        green: '#f6ffed',
        cyan: '#e6fffb',
        blue: '#e6f7ff',
        geekblue: '#f0f5ff',
        purple: '#f9f0ff',
      }
      const borderColorMap: Record<string, string> = {
        magenta: '#ff85c0',
        red: '#ffa39e',
        volcano: '#ffbb96',
        orange: '#ffd591',
        gold: '#ffe58f',
        lime: '#d3f261',
        green: '#95de64',
        cyan: '#5cdbd3',
        blue: '#69c0ff',
        geekblue: '#85a5ff',
        purple: '#b37feb',
      }

      result.push(
        <Tooltip
          key={`entity-${idx}`}
          title={
            <div style={{ fontSize: 12 }}>
              <div>
                <Text strong>{entityInfo.label}</Text>：{entity.entityValue}
              </div>
              {entity.entityUnit && <div>单位：{entity.entityUnit}</div>}
              {typeof entity.confidence === 'number' && (
                <div>置信度：{(entity.confidence * 100).toFixed(1)}%</div>
              )}
              <div>
                来源：
                {SourceMap[entity.source || '']?.label || entity.source}
              </div>
              <div style={{ marginTop: 4, color: '#91d5ff' }}>点击可编辑</div>
            </div>
          }
        >
          <span
            className="entity-highlight"
            style={{
              backgroundColor: colorMap[entityInfo.color] || '#e6f7ff',
              borderColor: borderColorMap[entityInfo.color] || '#91d5ff',
              borderWidth: 1,
              borderStyle: 'solid',
            }}
            onClick={() => openEntityEdit(entity)}
          >
            {text.substring(start, end)}
            <Tag
              color={entityInfo.color}
              style={{
                marginLeft: 2,
                fontSize: 10,
                padding: '0 4px',
                height: 14,
                lineHeight: '12px',
                borderRadius: 2,
              }}
            >
              {entityInfo.label}
            </Tag>
          </span>
        </Tooltip>
      )

      lastIndex = end
    })

    if (lastIndex < text.length) {
      result.push(<span key="text-end">{text.substring(lastIndex)}</span>)
    }

    return result.length > 0 ? result : text
  }, [displayText, entities])

  const entityStats = useMemo(() => {
    const stats = new Map<string, { total: number; verified: number; avgConfidence: number }>()
    entities.forEach((e) => {
      const s = stats.get(e.entityType) || { total: 0, verified: 0, avgConfidence: 0 }
      s.total++
      if (e.verified) s.verified++
      if (typeof e.confidence === 'number') s.avgConfidence += e.confidence
      stats.set(e.entityType, s)
    })
    const result: Array<{
      type: EntityType
      label: string
      color: string
      total: number
      verified: number
      avgConfidence: number
    }> = []
    stats.forEach((v, k) => {
      const info = EntityTypeLabelMap[k as EntityType] || { label: k, color: 'default' }
      result.push({
        type: k as EntityType,
        label: info.label,
        color: info.color,
        total: v.total,
        verified: v.verified,
        avgConfidence: v.total > 0 ? v.avgConfidence / v.total : 0,
      })
    })
    return result.sort((a, b) => b.total - a.total)
  }, [entities])

  const processSteps = useMemo(() => {
    if (!record) return []
    const steps = [
      { key: 'PENDING', label: '文件上传', done: true },
      {
        key: 'OCR',
        label: 'OCR识别',
        done: ['OCR_DONE', 'NER_PROCESSING', 'NER_DONE', 'COMPLETED'].includes(
          record.processStatus
        ),
        processing: record.processStatus === 'OCR_PROCESSING',
      },
      {
        key: 'NER',
        label: '实体抽取',
        done: ['NER_DONE', 'COMPLETED'].includes(record.processStatus),
        processing: record.processStatus === 'NER_PROCESSING',
      },
      {
        key: 'DONE',
        label: '处理完成',
        done: record.processStatus === 'COMPLETED' || record.processStatus === 'NER_DONE',
        failed: record.processStatus === 'FAILED',
      },
    ]
    return steps
  }, [record])

  if (!record && !loading) {
    return <div>记录不存在</div>
  }

  const statusInfo = ProcessStatusMap[record?.processStatus as ProcessStatus]

  const entityColumns = [
    {
      title: '实体类型',
      dataIndex: 'entityType',
      width: 140,
      render: (type: EntityType) => {
        const info = EntityTypeLabelMap[type] || { label: type, color: 'default' }
        return (
          <Tag color={info.color}>
            {info.label}
            {info.required && <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>}
          </Tag>
        )
      },
    },
    {
      title: '实体值',
      dataIndex: 'entityValue',
      ellipsis: true,
      render: (val: string, e: SurgeryEntity) => (
        <Tooltip title={val}>
          <span>
            {val}
            {e.entityUnit && (
              <span style={{ color: '#8c8c8c', marginLeft: 4 }}>{e.entityUnit}</span>
            )}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 120,
      render: (val: number) =>
        typeof val === 'number' ? (
          <Progress
            percent={Math.round(val * 100)}
            size="small"
            showInfo={true}
            status={val >= 0.8 ? 'success' : val >= 0.6 ? 'normal' : 'exception'}
          />
        ) : (
          '-'
        ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 100,
      render: (src: string) => {
        const s = SourceMap[src || ''] || { label: src, color: 'default' }
        return <Tag color={s.color}>{s.label}</Tag>
      },
    },
    {
      title: '状态',
      dataIndex: 'verified',
      width: 90,
      render: (v: number) =>
        v === 1 ? (
          <Tag color="green" icon={<CheckCircleOutlined />}>
            已确认
          </Tag>
        ) : (
          <Tag color="default" icon={<CloseCircleOutlined />}>
            待确认
          </Tag>
        ),
    },
    {
      title: '操作',
      width: 80,
      fixed: 'right' as const,
      render: (_: any, e: SurgeryEntity) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEntityEdit(e)}>
          编辑
        </Button>
      ),
    },
  ]

  return (
    <div className="page-container">
      <div style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
            返回
          </Button>
          <Title level={4} style={{ margin: 0 }}>
            手术记录详情
          </Title>
          <Tag
            color={statusInfo?.color}
            className={
              record?.processStatus === 'OCR_PROCESSING' ||
              record?.processStatus === 'NER_PROCESSING'
                ? 'status-tag-processing'
                : ''
            }
            icon={
              record?.processStatus === 'OCR_PROCESSING' ||
              record?.processStatus === 'NER_PROCESSING' ? (
                <SyncOutlined spin />
              ) : null
            }
          >
            {statusInfo?.label}
          </Tag>
          {(record?.processStatus === 'NER_DONE' || record?.processStatus === 'COMPLETED') && (
            <Button
              type="primary"
              icon={<RocketOutlined />}
              onClick={() => navigate(`/homepage/${recordId}`)}
            >
              填写病案首页
            </Button>
          )}
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[24, 16]}>
          <Col xs={24} lg={16}>
            <Descriptions column={3} size="small">
              <Descriptions.Item label="记录编号">{record?.recordNo}</Descriptions.Item>
              <Descriptions.Item label="患者姓名">
                <Text strong>{record?.patientName || '（未识别）'}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="住院号">{record?.hospitalNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="性别">
                {record?.gender ? (
                  <Tag color={record.gender === '男' ? 'blue' : 'magenta'}>{record.gender}</Tag>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="年龄">
                {record?.age ? `${record.age}岁` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="科室">{record?.department || '-'}</Descriptions.Item>
              <Descriptions.Item label="文件名" span={2}>
                <Tooltip title={record?.originalFileName}>
                  <span>{record?.originalFileName}</span>
                </Tooltip>
              </Descriptions.Item>
              <Descriptions.Item label="文件大小">
                {record?.fileSize ? `${(record.fileSize / 1024).toFixed(1)} KB` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="上传时间" span={3}>
                {dayjs(record?.uploadTime).format('YYYY-MM-DD HH:mm:ss')}
              </Descriptions.Item>
            </Descriptions>
          </Col>
          <Col xs={24} lg={8}>
            <Title level={5} style={{ marginBottom: 12 }}>
              处理进度
            </Title>
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              {processSteps.map((step) => (
                <div key={step.key} style={{ display: 'flex', alignItems: 'center' }}>
                  <div style={{ width: 24 }}>
                    {step.processing ? (
                      <SyncOutlined spin style={{ color: '#1677ff' }} />
                    ) : step.done ? (
                      <CheckCircleOutlined style={{ color: '#52c41a' }} />
                    ) : step.failed ? (
                      <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                    ) : (
                      <div
                        style={{
                          width: 14,
                          height: 14,
                          borderRadius: '50%',
                          border: '2px solid #d9d9d9',
                          marginLeft: 4,
                        }}
                      />
                    )}
                  </div>
                  <span style={{ marginLeft: 8, color: step.done ? '#52c41a' : '#8c8c8c' }}>
                    {step.label}
                  </span>
                </div>
              ))}
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card
            title={
              <Space>
                <FileTextOutlined />
                <span>文本内容</span>
                {isTextEditing && <Tag color="orange">编辑中</Tag>}
              </Space>
            }
            extra={
              <Space>
                {!isTextEditing ? (
                  <Button
                    icon={<EditOutlined />}
                    onClick={() => {
                      setEditingText(displayText)
                      setIsTextEditing(true)
                    }}
                    disabled={!displayText}
                  >
                    编辑文本
                  </Button>
                ) : (
                  <>
                    <Button onClick={() => setIsTextEditing(false)}>取消</Button>
                    <Button
                      type="primary"
                      icon={<SaveOutlined />}
                      loading={saving}
                      onClick={handleSaveText}
                    >
                      保存
                    </Button>
                  </>
                )}
              </Space>
            }
            loading={loading}
          >
            {(asrText || ocrText) && (
              <Tabs
                activeKey={activeTextTab}
                onChange={(k) => setActiveTextTab(k as 'ocr' | 'asr')}
                style={{ marginBottom: 16 }}
                size="small"
              >
                {ocrText && (
                  <Tabs.TabPane
                    tab={
                      <span>
                        <FileTextOutlined /> OCR文本
                      </span>
                    }
                    key="ocr"
                  />
                )}
                {asrText && (
                  <Tabs.TabPane
                    tab={
                      <span>
                        <AudioOutlined /> ASR语音转写
                        {record?.audioDuration && (
                          <Tag color="purple" style={{ marginLeft: 6 }}>
                            {record.audioDuration.toFixed(0)}s
                          </Tag>
                        )}
                      </span>
                    }
                    key="asr"
                  />
                )}
              </Tabs>
            )}

            {isTextEditing ? (
              <TextArea
                value={editingText}
                onChange={(e) => setEditingText(e.target.value)}
                rows={20}
                style={{ fontFamily: 'monospace', lineHeight: 1.8 }}
                placeholder="请输入或粘贴文本..."
              />
            ) : displayText ? (
              <div className="ocr-text-container">{highlightedText}</div>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: '#8c8c8c' }}>
                暂无文本内容，请等待系统处理或手动编辑
              </div>
            )}
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card
            title={
              <Space>
                <ThunderboltOutlined />
                <span>实体抽取结果</span>
                <Badge count={entities.length} />
              </Space>
            }
            style={{ marginBottom: 16 }}
            loading={loading}
          >
            {entityStats.length > 0 ? (
              <Space wrap size={[8, 8]} style={{ marginBottom: 16 }}>
                <Tag
                  color={selectedEntityType === 'ALL' ? 'blue' : 'default'}
                  style={{ cursor: 'pointer', padding: '4px 10px', fontSize: 13 }}
                  onClick={() => setSelectedEntityType('ALL')}
                >
                  全部 ({entities.length})
                </Tag>
                {entityStats.map((s) => (
                  <Tag
                    key={s.type}
                    color={selectedEntityType === s.type ? s.color : 'default'}
                    style={{
                      cursor: 'pointer',
                      padding: '4px 10px',
                      fontSize: 13,
                    }}
                    onClick={() => setSelectedEntityType(s.type)}
                  >
                    {s.label} ({s.total})
                  </Tag>
                ))}
              </Space>
            ) : null}

            <Table
              size="small"
              rowKey={(r) => `${r.id}-${r.entityType}`}
              dataSource={filteredEntities}
              columns={entityColumns}
              scroll={{ y: 420, x: 600 }}
              pagination={false}
              locale={{
                emptyText: (
                  <div style={{ padding: 30 }}>
                    {entities.length === 0
                      ? '暂无抽取结果，请等待NLP处理完成'
                      : '该类型无抽取结果'}
                  </div>
                ),
              }}
            />

            {entityStats.length > 0 && (
              <>
                <Divider style={{ margin: '16px 0' }} />
                <Title level={5} style={{ marginBottom: 12 }}>
                  抽取统计
                </Title>
                <Row gutter={[12, 12]}>
                  <Col xs={12}>
                    <div className="stat-card" style={{ padding: 12 }}>
                      <div className="stat-label">抽取实体总数</div>
                      <div className="stat-value" style={{ fontSize: 20 }}>
                        {entities.length}
                      </div>
                    </div>
                  </Col>
                  <Col xs={12}>
                    <div className="stat-card" style={{ padding: 12 }}>
                      <div className="stat-label">已确认实体</div>
                      <div className="stat-value" style={{ fontSize: 20, color: '#52c41a' }}>
                        {entities.filter((e) => e.verified === 1).length}
                      </div>
                    </div>
                  </Col>
                </Row>
              </>
            )}
          </Card>

          {record?.instruments && record.instruments.length > 0 && (
            <Card
              title={
                <Space>
                  <ScissorOutlined />
                  <span>手术器械识别</span>
                  <Badge count={record.instruments.length} color="purple" />
                </Space>
              }
              style={{ marginBottom: 16 }}
              size="small"
            >
              <Space wrap size={[6, 6]}>
                {record.instruments.map((inst, idx) => (
                  <Tag
                    key={idx}
                    color="purple"
                    style={{ padding: '4px 10px', fontSize: 13 }}
                  >
                    <Tooltip
                      title={
                        <div style={{ fontSize: 12 }}>
                          <div>器械名称：{inst.instrumentName}</div>
                          {inst.category && <div>分类：{inst.category}</div>}
                          {typeof inst.confidence === 'number' && (
                            <div>置信度：{(inst.confidence * 100).toFixed(1)}%</div>
                          )}
                          {inst.count && <div>数量：{inst.count}</div>}
                        </div>
                      }
                    >
                      {inst.instrumentName}
                      {typeof inst.confidence === 'number' && (
                        <span style={{ opacity: 0.7, marginLeft: 4 }}>
                          {(inst.confidence * 100).toFixed(0)}%
                        </span>
                      )}
                    </Tooltip>
                  </Tag>
                ))}
              </Space>
            </Card>
          )}

          {record?.multimodalStatus && record.multimodalStatus !== 'NONE' && (
            <Card
              title={
                <Space>
                  <MergeCellsOutlined />
                  <span>多模态融合</span>
                </Space>
              }
              size="small"
            >
              <Descriptions column={1} size="small">
                <Descriptions.Item label="融合状态">
                  {record.multimodalStatus === 'FUSED' && (
                    <Tag color="green">融合成功</Tag>
                  )}
                  {record.multimodalStatus === 'FUSION_FAILED' && (
                    <Tag color="orange">融合失败</Tag>
                  )}
                  {record.multimodalStatus === 'FUSION_ERROR' && (
                    <Tag color="red">融合异常</Tag>
                  )}
                  {record.multimodalStatus === 'ASR_DONE' && (
                    <Tag color="purple">语音已处理</Tag>
                  )}
                  {record.multimodalStatus === 'INSTRUMENT_DONE' && (
                    <Tag color="cyan">器械已识别</Tag>
                  )}
                </Descriptions.Item>
                {record.fusionStats && (
                  <>
                    {record.fusionStats.ocr_entity_count !== undefined && (
                      <Descriptions.Item label="OCR实体数">
                        {record.fusionStats.ocr_entity_count}
                      </Descriptions.Item>
                    )}
                    {record.fusionStats.asr_entity_count !== undefined && (
                      <Descriptions.Item label="ASR实体数">
                        {record.fusionStats.asr_entity_count}
                      </Descriptions.Item>
                    )}
                    {record.fusionStats.fused_entity_count !== undefined && (
                      <Descriptions.Item label="融合后实体数">
                        {record.fusionStats.fused_entity_count}
                      </Descriptions.Item>
                    )}
                    {record.fusionStats.enhanced_text_length !== undefined && (
                      <Descriptions.Item label="增强文本长度">
                        {record.fusionStats.enhanced_text_length}
                      </Descriptions.Item>
                    )}
                  </>
                )}
              </Descriptions>
            </Card>
          )}
        </Col>
      </Row>

      <Drawer
        title="编辑实体"
        open={entityDrawer}
        onClose={() => setEntityDrawer(false)}
        width={420}
        extra={
          <Space>
            <Button onClick={() => setEntityDrawer(false)}>取消</Button>
            <Button type="primary" onClick={handleEntityUpdate}>
              确认修改
            </Button>
          </Space>
        }
      >
        {currentEntity && (
          <Form form={entityForm} layout="vertical">
            <Form.Item label="实体类型">
              <Tag color={EntityTypeLabelMap[currentEntity.entityType as EntityType]?.color}>
                {EntityTypeLabelMap[currentEntity.entityType as EntityType]?.label ||
                  currentEntity.entityType}
              </Tag>
            </Form.Item>
            <Form.Item
              label="实体值"
              name="entityValue"
              rules={[{ required: true, message: '请输入实体值' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item label="单位" name="entityUnit">
              <Select
                allowClear
                mode="tags"
                maxTagCount={1}
                options={[
                  { label: 'ml', value: 'ml' },
                  { label: '岁', value: '岁' },
                  { label: '天', value: '天' },
                  { label: 'mg', value: 'mg' },
                ]}
              />
            </Form.Item>
            <Divider />
            <Descriptions column={1} size="small">
              <Descriptions.Item label="来源">
                {SourceMap[currentEntity.source || '']?.label || currentEntity.source}
              </Descriptions.Item>
              <Descriptions.Item label="置信度">
                {typeof currentEntity.confidence === 'number'
                  ? `${(currentEntity.confidence * 100).toFixed(1)}%`
                  : '-'}
              </Descriptions.Item>
              {currentEntity.originalText && (
                <Descriptions.Item label="原文片段">
                  {currentEntity.originalText}
                </Descriptions.Item>
              )}
              {typeof currentEntity.startPos === 'number' && (
                <Descriptions.Item label="位置">
                  {currentEntity.startPos} - {currentEntity.endPos}
                </Descriptions.Item>
              )}
            </Descriptions>
          </Form>
        )}
      </Drawer>
    </div>
  )
}

export default RecordDetail
