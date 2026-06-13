import React, { useEffect, useState, useMemo, useCallback } from 'react'
import {
  Card,
  Row,
  Col,
  Table,
  Tag,
  Space,
  Button,
  Input,
  Select,
  Progress,
  Statistic,
  Modal,
  Descriptions,
  Empty,
  Tabs,
  Tooltip,
  Badge,
  Alert,
} from 'antd'
import {
  SafetyCertificateOutlined,
  SearchOutlined,
  FileExcelOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { recordApi, qcApi, homePageApi } from '@/services/api'
import type { SurgeryRecord, QcScorecard, QcCheckResult, QcViolation, ProcessStatus } from '@/types'
import { ProcessStatusMap, QcSeverityMap, QcCategoryMap } from '@/types'
import dayjs from 'dayjs'

const QualityControlPage: React.FC = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [records, setRecords] = useState<SurgeryRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)

  const [scorecardMap, setScorecardMap] = useState<Record<number, QcScorecard>>({})
  const [loadingScorecard, setLoadingScorecard] = useState<Set<number>>(new Set())
  const [selectedScorecard, setSelectedScorecard] = useState<QcScorecard | null>(null)
  const [selectedCheckResult, setSelectedCheckResult] = useState<QcCheckResult | null>(null)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [exportingId, setExportingId] = useState<number | null>(null)

  useEffect(() => {
    loadRecords()
  }, [pageNum, pageSize, statusFilter])

  const loadRecords = async () => {
    setLoading(true)
    try {
      const data = await recordApi.list({
        patientName: searchKeyword || undefined,
        status: statusFilter,
        pageNum,
        pageSize,
      })
      setRecords(data.records || [])
      setTotal(data.total || 0)
    } catch (e) {
      console.error('加载记录失败', e)
    } finally {
      setLoading(false)
    }
  }

  const loadScorecard = async (recordId: number) => {
    setLoadingScorecard((prev) => new Set(prev).add(recordId))
    try {
      const scorecard = await qcApi.getScorecard(recordId)
      setScorecardMap((prev) => ({ ...prev, [recordId]: scorecard }))
    } catch (e) {
      console.error('加载评分卡失败', e)
    } finally {
      setLoadingScorecard((prev) => {
        const next = new Set(prev)
        next.delete(recordId)
        return next
      })
    }
  }

  const handleViewDetail = async (recordId: number) => {
    try {
      const [scorecard, checkResult] = await Promise.all([
        qcApi.getScorecard(recordId),
        qcApi.validate(recordId),
      ])
      setSelectedScorecard(scorecard)
      setSelectedCheckResult(checkResult)
      setDetailModalOpen(true)
    } catch (e) {
      console.error('加载详情失败', e)
    }
  }

  const handleExport = async (recordId: number) => {
    setExportingId(recordId)
    try {
      const blob = await qcApi.exportReport(recordId) as any
      const url = window.URL.createObjectURL(new Blob([blob]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `质控报告_${recordId}.xlsx`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (e) {
      console.error('导出失败', e)
    } finally {
      setExportingId(null)
    }
  }

  const getScoreColor = (score: number) => {
    if (score >= 90) return '#52c41a'
    if (score >= 80) return '#1677ff'
    if (score >= 70) return '#fa8c16'
    if (score >= 60) return '#faad14'
    return '#ff4d4f'
  }

  const getGradeColor = (grade: string) => {
    if (grade.startsWith('A')) return 'green'
    if (grade.startsWith('B')) return 'blue'
    if (grade.startsWith('C')) return 'orange'
    if (grade.startsWith('D')) return 'gold'
    return 'red'
  }

  const columns = [
    {
      title: '记录编号',
      dataIndex: 'recordNo',
      width: 130,
      render: (text: string, r: SurgeryRecord) => (
        <a onClick={() => navigate(`/records/${r.id}`)}>{text}</a>
      ),
    },
    {
      title: '患者信息',
      width: 160,
      render: (_: any, r: SurgeryRecord) => (
        <Space direction="vertical" size={2}>
          <span>{r.patientName || '（未识别）'}</span>
          <span style={{ fontSize: 11, color: '#8c8c8c' }}>{r.hospitalNo || '-'}</span>
        </Space>
      ),
    },
    {
      title: '科室',
      dataIndex: 'department',
      width: 100,
      render: (t: string) => t || '-',
    },
    {
      title: '处理状态',
      dataIndex: 'processStatus',
      width: 110,
      render: (status: ProcessStatus) => {
        const info = ProcessStatusMap[status]
        return info ? <Tag color={info.color}>{info.label}</Tag> : status
      },
    },
    {
      title: '质控评分',
      width: 180,
      render: (_: any, r: SurgeryRecord) => {
        const sc = scorecardMap[r.id]
        if (!sc) {
          return (
            <Button
              type="link"
              size="small"
              icon={<SearchOutlined />}
              loading={loadingScorecard.has(r.id)}
              onClick={() => loadScorecard(r.id)}
            >
              检查
            </Button>
          )
        }
        return (
          <Space size={8}>
            <Progress
              type="circle"
              size={36}
              percent={Math.round(sc.overallScore)}
              strokeColor={getScoreColor(sc.overallScore)}
              format={(p) => (
                <span style={{ fontSize: 11, fontWeight: 600, color: getScoreColor(sc.overallScore) }}>
                  {p}
                </span>
              )}
            />
            <Space direction="vertical" size={0}>
              <Tag color={getGradeColor(sc.grade)} style={{ fontSize: 11, lineHeight: '16px', padding: '0 4px', margin: 0 }}>
                {sc.grade}
              </Tag>
              {sc.violations?.length > 0 && (
                <Space size={2}>
                  {sc.violations.filter((v) => v.severity === 'ERROR').length > 0 && (
                    <Tag color="red" style={{ fontSize: 10, lineHeight: '14px', padding: '0 4px', margin: 0 }}>
                      {sc.violations.filter((v) => v.severity === 'ERROR').length}错
                    </Tag>
                  )}
                  {sc.violations.filter((v) => v.severity === 'WARNING').length > 0 && (
                    <Tag color="orange" style={{ fontSize: 10, lineHeight: '14px', padding: '0 4px', margin: 0 }}>
                      {sc.violations.filter((v) => v.severity === 'WARNING').length}警
                    </Tag>
                  )}
                </Space>
              )}
            </Space>
          </Space>
        )
      },
    },
    {
      title: '上传时间',
      dataIndex: 'uploadTime',
      width: 140,
      render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right' as const,
      render: (_: any, r: SurgeryRecord) => (
        <Space size={4}>
          <Tooltip title="查看质控详情">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetail(r.id)}
            />
          </Tooltip>
          <Tooltip title="导出质控报告">
            <Button
              type="link"
              size="small"
              icon={<FileExcelOutlined />}
              loading={exportingId === r.id}
              onClick={() => handleExport(r.id)}
            />
          </Tooltip>
          {(r.processStatus === 'NER_DONE' || r.processStatus === 'COMPLETED') && (
            <Tooltip title="编辑首页">
              <Button
                type="link"
                size="small"
                onClick={() => navigate(`/homepage/${r.id}`)}
              >
                首页
              </Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="page-container">
      <div style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <SafetyCertificateOutlined style={{ fontSize: 20, color: '#1677ff' }} />
          <span style={{ fontSize: 18, fontWeight: 600 }}>数据质控与合规校验</span>
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input
              placeholder="搜索患者姓名"
              prefix={<SearchOutlined />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onPressEnter={loadRecords}
              allowClear
            />
          </Col>
          <Col xs={24} sm={8} md={4}>
            <Select
              placeholder="处理状态"
              value={statusFilter}
              onChange={(v) => setStatusFilter(v)}
              allowClear
              style={{ width: '100%' }}
              options={[
                { label: '已完成', value: 'COMPLETED' },
                { label: '抽取完成', value: 'NER_DONE' },
                { label: '处理失败', value: 'FAILED' },
              ]}
            />
          </Col>
          <Col>
            <Button type="primary" icon={<SearchOutlined />} onClick={loadRecords}>
              搜索
            </Button>
          </Col>
        </Row>
      </Card>

      <Card>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={records}
          columns={columns}
          scroll={{ x: 980 }}
          pagination={{
            current: pageNum,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, ps) => {
              setPageNum(p)
              setPageSize(ps)
            },
          }}
          locale={{
            emptyText: <Empty description="暂无记录" />,
          }}
        />
      </Card>

      <Modal
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
            <span>质控详情</span>
            {selectedScorecard && (
              <Tag color={getGradeColor(selectedScorecard.grade)}>{selectedScorecard.grade}</Tag>
            )}
          </Space>
        }
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        width={720}
        footer={
          <Space>
            <Button onClick={() => setDetailModalOpen(false)}>关闭</Button>
            {selectedScorecard && (
              <Button
                type="primary"
                icon={<FileExcelOutlined />}
                onClick={() => handleExport(selectedScorecard.recordId)}
              >
                导出报告
              </Button>
            )}
          </Space>
        }
      >
        {selectedScorecard && (
          <>
            <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
              <Col span={8} style={{ textAlign: 'center' }}>
                <Progress
                  type="dashboard"
                  size={100}
                  percent={Math.round(selectedScorecard.overallScore)}
                  strokeColor={getScoreColor(selectedScorecard.overallScore)}
                  format={(p) => (
                    <div>
                      <div style={{ fontSize: 20, fontWeight: 700, color: getScoreColor(selectedScorecard.overallScore) }}>
                        {p}
                      </div>
                      <div style={{ fontSize: 10, color: '#8c8c8c' }}>综合评分</div>
                    </div>
                  )}
                />
              </Col>
              <Col span={8} style={{ textAlign: 'center' }}>
                <Progress
                  type="dashboard"
                  size={100}
                  percent={Math.round(selectedScorecard.completenessScore)}
                  strokeColor={getScoreColor(selectedScorecard.completenessScore)}
                  format={(p) => (
                    <div>
                      <div style={{ fontSize: 20, fontWeight: 700, color: getScoreColor(selectedScorecard.completenessScore) }}>
                        {p}
                      </div>
                      <div style={{ fontSize: 10, color: '#8c8c8c' }}>完整性</div>
                    </div>
                  )}
                />
              </Col>
              <Col span={8} style={{ textAlign: 'center' }}>
                <Progress
                  type="dashboard"
                  size={100}
                  percent={Math.round(selectedScorecard.logicConsistencyScore)}
                  strokeColor={getScoreColor(selectedScorecard.logicConsistencyScore)}
                  format={(p) => (
                    <div>
                      <div style={{ fontSize: 20, fontWeight: 700, color: getScoreColor(selectedScorecard.logicConsistencyScore) }}>
                        {p}
                      </div>
                      <div style={{ fontSize: 10, color: '#8c8c8c' }}>逻辑一致性</div>
                    </div>
                  )}
                />
              </Col>
            </Row>

            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="记录编号">{selectedScorecard.recordNo}</Descriptions.Item>
              <Descriptions.Item label="患者姓名">{selectedScorecard.patientName || '-'}</Descriptions.Item>
              <Descriptions.Item label="手术名称" span={2}>{selectedScorecard.surgeryName || '-'}</Descriptions.Item>
              <Descriptions.Item label="总字段数">{selectedScorecard.totalFields}</Descriptions.Item>
              <Descriptions.Item label="已填字段数">{selectedScorecard.filledFields}</Descriptions.Item>
              <Descriptions.Item label="必填字段数">{selectedScorecard.requiredFields}</Descriptions.Item>
              <Descriptions.Item label="必填已填">{selectedScorecard.requiredFilled}</Descriptions.Item>
              <Descriptions.Item label="逻辑规则数">{selectedScorecard.logicRuleCount}</Descriptions.Item>
              <Descriptions.Item label="逻辑通过">{selectedScorecard.logicPassed}</Descriptions.Item>
            </Descriptions>

            {selectedCheckResult?.violations && selectedCheckResult.violations.length > 0 && (
              <>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>
                  <ExclamationCircleOutlined style={{ color: '#fa8c16', marginRight: 4 }} />
                  质控问题 ({selectedCheckResult.violations.length})
                </div>
                <div style={{ maxHeight: 300, overflow: 'auto' }}>
                  {selectedCheckResult.violations.map((v, idx) => (
                    <div
                      key={idx}
                      style={{
                        padding: '8px 12px',
                        marginBottom: 6,
                        background: v.severity === 'ERROR' ? '#fff2f0' : '#fffbe6',
                        borderRadius: 6,
                        borderLeft: `3px solid ${v.severity === 'ERROR' ? '#ff4d4f' : '#faad14'}`,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <Tag
                          color={QcSeverityMap[v.severity]?.color || 'default'}
                          style={{ fontSize: 10, padding: '0 4px', lineHeight: '16px', margin: 0 }}
                        >
                          {QcSeverityMap[v.severity]?.label}
                        </Tag>
                        <Tag
                          color={QcCategoryMap[v.category]?.color || 'default'}
                          style={{ fontSize: 10, padding: '0 4px', lineHeight: '16px', margin: 0 }}
                        >
                          {QcCategoryMap[v.category]?.label}
                        </Tag>
                        <span style={{ fontSize: 12, fontWeight: 500 }}>{v.ruleCode}: {v.ruleName}</span>
                      </div>
                      <div style={{ fontSize: 12, color: '#595959', marginTop: 4 }}>{v.message}</div>
                    </div>
                  ))}
                </div>
              </>
            )}

            {selectedScorecard.fieldChecks && selectedScorecard.fieldChecks.length > 0 && (
              <>
                <div style={{ fontWeight: 600, margin: '16px 0 8px' }}>字段检查明细</div>
                <Table
                  size="small"
                  rowKey="fieldName"
                  dataSource={selectedScorecard.fieldChecks}
                  pagination={false}
                  scroll={{ y: 260 }}
                  columns={[
                    {
                      title: '字段',
                      dataIndex: 'fieldLabel',
                      width: 100,
                    },
                    {
                      title: '必填',
                      dataIndex: 'required',
                      width: 50,
                      render: (v: boolean) => v ? <Tag color="red" style={{ fontSize: 10 }}>是</Tag> : '否',
                    },
                    {
                      title: '已填',
                      dataIndex: 'filled',
                      width: 50,
                      render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#d9d9d9' }} />,
                    },
                    {
                      title: '合规',
                      dataIndex: 'valid',
                      width: 50,
                      render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
                    },
                    {
                      title: '问题描述',
                      dataIndex: 'issue',
                      ellipsis: true,
                      render: (v: string) => v ? <span style={{ color: '#ff4d4f', fontSize: 12 }}>{v}</span> : '-',
                    },
                  ]}
                />
              </>
            )}
          </>
        )}
      </Modal>
    </div>
  )
}

export default QualityControlPage
