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
  Tooltip,
  Alert,
  Radio,
  Switch,
  Dropdown,
  MenuProps,
  Divider,
  Spin,
  Avatar,
  List,
  Badge,
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
  FilePdfOutlined,
  FileWordOutlined,
  DownloadOutlined,
  FileProtectOutlined,
  SettingOutlined,
  InfoCircleOutlined,
  StarFilled,
  CaretDownOutlined,
  PrinterOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { recordApi, qcApi, homePageApi, qcReportTemplateApi } from '@/services/api'
import type {
  SurgeryRecord,
  QcScorecard,
  QcCheckResult,
  QcViolation,
  ProcessStatus,
  QcReportTemplate,
  UserInfo,
} from '@/types'
import {
  ProcessStatusMap,
  QcSeverityMap,
  QcCategoryMap,
  QcReportTemplateFileTypeMap,
} from '@/types'
import { useAuthStore } from '@/store/authStore'
import dayjs from 'dayjs'

const { Option } = Select

type ExportFormat = 'PDF' | 'WORD' | 'EXCEL'

interface ExportConfigState {
  recordId: number | null
  templateId: number | null
  format: ExportFormat
  enableWatermark: boolean
  watermarkText: string
}

const QualityControlPage: React.FC = () => {
  const navigate = useNavigate()
  const { userInfo } = useAuthStore()
  const [loading, setLoading] = useState(false)
  const [records, setRecords] = useState<SurgeryRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined)
  const [deptFilter, setDeptFilter] = useState<string | undefined>(undefined)

  const [scorecardMap, setScorecardMap] = useState<Record<number, QcScorecard>>({})
  const [loadingScorecard, setLoadingScorecard] = useState<Set<number>>(new Set())
  const [selectedScorecard, setSelectedScorecard] = useState<QcScorecard | null>(null)
  const [selectedCheckResult, setSelectedCheckResult] = useState<QcCheckResult | null>(null)
  const [detailModalOpen, setDetailModalOpen] = useState(false)

  const [exportModalOpen, setExportModalOpen] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [exportTemplates, setExportTemplates] = useState<QcReportTemplate[]>([])
  const [templatesLoading, setTemplatesLoading] = useState(false)
  const [exportConfig, setExportConfig] = useState<ExportConfigState>({
    recordId: null,
    templateId: null,
    format: 'PDF',
    enableWatermark: true,
    watermarkText: '',
  })

  const [stats, setStats] = useState({
    totalRecords: 0,
    avgOverall: 0,
    avgCompleteness: 0,
    avgLogic: 0,
    excellentCount: 0,
    failCount: 0,
  })

  useEffect(() => {
    loadRecords()
    loadTemplates()
  }, [pageNum, pageSize, statusFilter, deptFilter])

  useEffect(() => {
    const defaultWatermark = userInfo?.department
      ? `${userInfo.department}质控科`
      : '医院质控科'
    setExportConfig((prev) => ({ ...prev, watermarkText: defaultWatermark }))
  }, [userInfo])

  const loadTemplates = async () => {
    setTemplatesLoading(true)
    try {
      const data = (await qcReportTemplateApi.available({
        department: userInfo?.department,
      })) as QcReportTemplate[]
      const withDefault = [...(data || [])].sort((a, b) => b.isDefault - a.isDefault)
      setExportTemplates(withDefault)
      if (withDefault.length > 0 && !exportConfig.templateId) {
        const def = withDefault.find((t) => t.isDefault === 1) || withDefault[0]
        setExportConfig((prev) => ({
          ...prev,
          templateId: def.id,
          enableWatermark: def.enableWatermark === 1,
          watermarkText: def.watermarkText || prev.watermarkText,
        }))
      }
    } catch (e) {
      console.error('加载可用模板失败', e)
    } finally {
      setTemplatesLoading(false)
    }
  }

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
      computeStats(data.records || [])
    } catch (e) {
      console.error('加载记录失败', e)
    } finally {
      setLoading(false)
    }
  }

  const computeStats = (list: SurgeryRecord[]) => {
    setTimeout(async () => {
      const ids = list.slice(0, 50).map((r) => r.id)
      let overallSum = 0, completeSum = 0, logicSum = 0
      let excellent = 0, fail = 0, validCount = 0
      for (const id of ids) {
        try {
          const sc = await qcApi.getScorecard(id)
          overallSum += sc.overallScore
          completeSum += sc.completenessScore
          logicSum += sc.logicConsistencyScore
          validCount++
          if (sc.overallScore >= 90) excellent++
          if (sc.overallScore < 60) fail++
          setScorecardMap((prev) => ({ ...prev, [id]: sc }))
        } catch {
          // ignore
        }
      }
      setStats({
        totalRecords: data_total,
        avgOverall: validCount > 0 ? Math.round(overallSum / validCount) : 0,
        avgCompleteness: validCount > 0 ? Math.round(completeSum / validCount) : 0,
        avgLogic: validCount > 0 ? Math.round(logicSum / validCount) : 0,
        excellentCount: excellent,
        failCount: fail,
      })
    }, 0)
    const data_total = total
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

  const openExportModal = (recordId: number) => {
    setExportConfig((prev) => ({ ...prev, recordId }))
    setExportModalOpen(true)
  }

  const handleExport = async () => {
    const { recordId, templateId, format, enableWatermark, watermarkText } = exportConfig
    if (!recordId) return
    setExporting(true)
    try {
      let blob: any
      let filename = ''
      const rec = records.find((r) => r.id === recordId)
      const timestamp = dayjs().format('YYYYMMDDHHmm')
      const namePart = rec?.patientName || rec?.recordNo || String(recordId)

      if (templateId && exportTemplates.length > 0) {
        blob = await qcApi.exportReportWithTemplate(recordId, templateId, {
          enableWatermark,
          watermarkText,
          outputFormat: format,
        })
      } else {
        blob = await qcApi.exportReport(recordId)
      }

      const ext = format === 'PDF' ? 'pdf' : format === 'WORD' ? 'docx' : 'xlsx'
      filename = `质控报告_${namePart}_${timestamp}.${ext}`

      const url = window.URL.createObjectURL(new Blob([blob]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', filename)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      messageBox('success', `${filename} 导出成功`)
      setExportModalOpen(false)
    } catch (e: any) {
      console.error('导出失败', e)
      messageBox('error', e?.message || '导出失败，请稍后重试')
    } finally {
      setExporting(false)
    }
  }

  const handleBatchExport = () => {
    const checked = records.filter((r) => scorecardMap[r.id])
    if (checked.length === 0) {
      messageBox('warning', '请先对记录执行质控检查再导出')
      return
    }
    messageBox('info', `批量导出 ${checked.length} 份报告功能开发中...`)
  }

  const handleQuickExport = async (recordId: number, format: ExportFormat) => {
    if (exportConfig.templateId == null && exportTemplates.length > 0) {
      const def = exportTemplates.find((t) => t.isDefault === 1) || exportTemplates[0]
      if (def) {
        exportConfig.templateId = def.id
      }
    }
    setExporting(true)
    try {
      let blob: any
      const rec = records.find((r) => r.id === recordId)
      const timestamp = dayjs().format('YYYYMMDDHHmm')
      const namePart = rec?.patientName || rec?.recordNo || String(recordId)

      if (exportConfig.templateId) {
        blob = await qcApi.exportReportWithTemplate(recordId, exportConfig.templateId, {
          enableWatermark: exportConfig.enableWatermark,
          watermarkText: exportConfig.watermarkText,
          outputFormat: format,
        })
      } else {
        blob = await qcApi.exportReport(recordId)
      }

      const ext = format === 'PDF' ? 'pdf' : format === 'WORD' ? 'docx' : 'xlsx'
      const filename = `质控报告_${namePart}_${timestamp}.${ext}`

      const url = window.URL.createObjectURL(new Blob([blob]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', filename)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      messageBox('success', `${filename} 导出成功`)
    } catch (e: any) {
      messageBox('error', e?.message || '导出失败')
    } finally {
      setExporting(false)
    }
  }

  const exportDropdownMenu = (recordId: number): MenuProps => ({
    items: [
      {
        key: 'PDF',
        label: (
          <Space>
            <FilePdfOutlined style={{ color: '#ff4d4f' }} />
            <span>导出 PDF（带水印）</span>
          </Space>
        ),
        onClick: () => handleQuickExport(recordId, 'PDF'),
      },
      {
        key: 'WORD',
        label: (
          <Space>
            <FileWordOutlined style={{ color: '#1677ff' }} />
            <span>导出 Word</span>
          </Space>
        ),
        onClick: () => handleQuickExport(recordId, 'WORD'),
      },
      {
        key: 'EXCEL',
        label: (
          <Space>
            <FileExcelOutlined style={{ color: '#52c41a' }} />
            <span>导出 Excel</span>
          </Space>
        ),
        onClick: () => handleQuickExport(recordId, 'EXCEL'),
      },
      { type: 'divider' },
      {
        key: 'custom',
        label: (
          <Space>
            <SettingOutlined />
            <span>自定义配置导出...</span>
          </Space>
        ),
        onClick: () => openExportModal(recordId),
      },
    ],
  })

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

  const messageBox = (type: 'success' | 'error' | 'warning' | 'info', msg: string) => {
    if (type === 'success') console.log('SUCCESS:', msg)
    if (type === 'error') console.error('ERROR:', msg)
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
      width: 220,
      fixed: 'right' as const,
      render: (_: any, r: SurgeryRecord) => (
        <Space size={2}>
          <Tooltip title="查看质控详情">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetail(r.id)}
            />
          </Tooltip>
          <Dropdown
            menu={exportDropdownMenu(r.id)}
            placement="bottomRight"
            trigger={['click']}
            disabled={!scorecardMap[r.id]}
          >
            <Tooltip title={scorecardMap[r.id] ? '选择格式导出质控报告' : '请先执行质控检查'}>
              <Button
                type="link"
                size="small"
                icon={<FileProtectOutlined />}
              >
                导出
                <CaretDownOutlined style={{ fontSize: 10 }} />
              </Button>
            </Tooltip>
          </Dropdown>
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

  const selectedTemplate = useMemo(
    () => exportTemplates.find((t) => t.id === exportConfig.templateId),
    [exportTemplates, exportConfig.templateId]
  )

  return (
    <div className="page-container">
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col xs={24} sm={24} md={12}>
            <Space size={12} wrap>
              <SafetyCertificateOutlined style={{ fontSize: 22, color: '#1677ff' }} />
              <div>
                <div style={{ fontSize: 18, fontWeight: 600 }}>数据质控中心</div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>
                  完整性与逻辑一致性合规校验 · 支持自定义模板导出带水印PDF
                </div>
              </div>
            </Space>
          </Col>
          <Col xs={24} sm={24} md={12} style={{ textAlign: 'right' }}>
            <Space>
              <Button
                icon={<FileProtectOutlined />}
                onClick={() => navigate('/qc-report-templates')}
              >
                模板管理
              </Button>
              <Button type="primary" icon={<PrinterOutlined />} onClick={handleBatchExport}>
                批量导出报告
              </Button>
            </Space>
          </Col>
        </Row>
        <Divider style={{ margin: '16px 0' }} />
        <Row gutter={[16, 12]}>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" variant="borderless">
              <Statistic
                title={<span style={{ color: '#8c8c8c', fontSize: 12 }}>记录总数</span>}
                value={total}
                valueStyle={{ fontSize: 22, fontWeight: 700 }}
                prefix={<SafetyCertificateOutlined style={{ color: '#1677ff' }} />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" variant="borderless">
              <Statistic
                title={<span style={{ color: '#8c8c8c', fontSize: 12 }}>平均综合评分</span>}
                value={stats.avgOverall}
                precision={0}
                suffix="分"
                valueStyle={{
                  fontSize: 22,
                  fontWeight: 700,
                  color: getScoreColor(stats.avgOverall),
                }}
                prefix={<FileProtectOutlined />}
              />
              <Progress percent={stats.avgOverall} showInfo={false} size="small" strokeColor={getScoreColor(stats.avgOverall)} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" variant="borderless">
              <Statistic
                title={<span style={{ color: '#8c8c8c', fontSize: 12 }}>优秀率</span>}
                value={stats.avgOverall >= 90 ? 'A' : stats.avgOverall >= 80 ? 'B' : stats.avgOverall >= 70 ? 'C' : 'D'}
                valueStyle={{ fontSize: 22, fontWeight: 700, color: getScoreColor(stats.avgOverall) }}
                prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
              />
              <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
                完整 {stats.avgCompleteness} 分 · 逻辑 {stats.avgLogic} 分
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" variant="borderless">
              <Statistic
                title={<span style={{ color: '#8c8c8c', fontSize: 12 }}>待整改</span>}
                value={stats.failCount}
                valueStyle={{ fontSize: 22, fontWeight: 700, color: '#ff4d4f' }}
                prefix={<WarningOutlined style={{ color: '#ff4d4f' }} />}
              />
              <Progress percent={100 - Math.min(100, stats.avgOverall)} showInfo={false} size="small" strokeColor="#ff4d4f" />
            </Card>
          </Col>
        </Row>
      </Card>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input
              placeholder="搜索患者姓名 / 住院号"
              prefix={<SearchOutlined />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onPressEnter={loadRecords}
              allowClear
            />
          </Col>
          <Col xs={24} sm={8} md={5}>
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
          <Col xs={24} sm={8} md={5}>
            <Select
              placeholder="筛选科室"
              value={deptFilter}
              onChange={(v) => setDeptFilter(v)}
              allowClear
              style={{ width: '100%' }}
              options={[
                { label: '普外科', value: '普外科' },
                { label: '骨科', value: '骨科' },
                { label: '妇产科', value: '妇产科' },
                { label: '神经外科', value: '神经外科' },
                { label: '心胸外科', value: '心胸外科' },
                { label: '泌尿外科', value: '泌尿外科' },
              ]}
            />
          </Col>
          <Col>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={loadRecords}>
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadRecords}>
                刷新
              </Button>
            </Space>
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
          scroll={{ x: 1000 }}
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
        width={760}
        footer={
          <Space>
            <Button onClick={() => setDetailModalOpen(false)}>关闭</Button>
            {selectedScorecard && (
              <Button
                icon={<PrinterOutlined />}
                onClick={() => {
                  setDetailModalOpen(false)
                  openExportModal(selectedScorecard.recordId)
                }}
              >
                打印/导出
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

      <Modal
        title={
          <Space>
            <FileProtectOutlined style={{ color: '#1677ff' }} />
            <span>导出质控报告</span>
            <Tag color="blue">自定义配置</Tag>
          </Space>
        }
        open={exportModalOpen}
        onCancel={() => setExportModalOpen(false)}
        width={640}
        footer={
          <Space>
            <Button onClick={() => setExportModalOpen(false)}>取消</Button>
            <Button
              type="primary"
              icon={exportConfig.format === 'PDF' ? <FilePdfOutlined /> : exportConfig.format === 'WORD' ? <FileWordOutlined /> : <FileExcelOutlined />}
              onClick={handleExport}
              loading={exporting}
            >
              导出 {exportConfig.format}
            </Button>
          </Space>
        }
      >
        <Spin spinning={templatesLoading}>
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Alert
              type="info"
              showIcon
              message="使用说明"
              description="选择预设的质控报告模板，系统将按模板格式填充真实质控数据，生成标准格式报告；可选择导出PDF（默认带科室水印）、Word或Excel。"
            />

            <Card size="small" title={<Space><FileProtectOutlined />选择报告模板</Space>}>
              {exportTemplates.length === 0 ? (
                <Empty
                  description={
                    <Space direction="vertical">
                      <span>暂无可用模板</span>
                      {userInfo?.role === 'ADMIN' && (
                        <Button type="primary" onClick={() => navigate('/qc-report-templates/new')}>
                          创建第一个模板
                        </Button>
                      )}
                    </Space>
                  }
                />
              ) : (
                <List
                  size="small"
                  grid={{ gutter: 8, xs: 1, sm: 1, md: 2 }}
                  dataSource={exportTemplates}
                  renderItem={(t) => {
                    const selected = exportConfig.templateId === t.id
                    return (
                      <List.Item
                        onClick={() => {
                          setExportConfig((prev) => ({
                            ...prev,
                            templateId: t.id,
                            enableWatermark: t.enableWatermark === 1,
                            watermarkText: t.watermarkText || prev.watermarkText,
                          }))
                        }}
                        style={{
                          cursor: 'pointer',
                          padding: '8px 12px',
                          border: `2px solid ${selected ? '#1677ff' : '#e5e7eb'}`,
                          borderRadius: 8,
                          background: selected ? '#e6f4ff' : '#fff',
                        }}
                      >
                        <Space direction="vertical" size={4} style={{ width: '100%' }}>
                          <Space align="start">
                            <Avatar
                              size={36}
                              icon={t.fileType === 'WORD' ? <FileWordOutlined /> : <FileExcelOutlined />}
                              style={{
                                background: t.fileType === 'WORD' ? '#1677ff' : '#52c41a',
                              }}
                            />
                            <div style={{ flex: 1 }}>
                              <Space size={6} wrap>
                                <span style={{ fontSize: 13, fontWeight: 600 }}>{t.templateName}</span>
                                {t.isDefault === 1 && (
                                  <Tag color="gold" icon={<StarFilled />} style={{ margin: 0, fontSize: 10 }}>默认</Tag>
                                )}
                                {t.enableWatermark === 1 && (
                                  <Badge dot color="#13c2c2" offset={[0, 0]} />
                                )}
                              </Space>
                              <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 2 }}>
                                {t.department || '全院通用'} · v{t.currentVersion} · {t.fieldBindings?.length || 0}字段
                              </div>
                            </div>
                          </Space>
                        </Space>
                      </List.Item>
                    )
                  }}
                />
              )}
              {selectedTemplate && (
                <Alert
                  style={{ marginTop: 12 }}
                  type="success"
                  showIcon
                  message={`已选: ${selectedTemplate.templateName}`}
                  description={
                    <Space size={8}>
                      <Tag>{QcReportTemplateFileTypeMap[selectedTemplate.fileType]?.label}</Tag>
                      {selectedTemplate.description && <span style={{ fontSize: 12 }}>{selectedTemplate.description}</span>}
                    </Space>
                  }
                />
              )}
            </Card>

            <Card size="small" title={<Space><DownloadOutlined />输出设置</Space>}>
              <Form layout="vertical" style={{ marginBottom: 0 }}>
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item label="输出格式" style={{ marginBottom: 0 }}>
                      <Radio.Group
                        value={exportConfig.format}
                        onChange={(e) => setExportConfig((prev) => ({ ...prev, format: e.target.value }))}
                        style={{ width: '100%' }}
                      >
                        <Radio.Button value="PDF" style={{ width: '33.33%', textAlign: 'center' }}>
                          <FilePdfOutlined style={{ color: '#ff4d4f', marginRight: 4 }} />PDF
                        </Radio.Button>
                        <Radio.Button value="WORD" style={{ width: '33.33%', textAlign: 'center' }}>
                          <FileWordOutlined style={{ color: '#1677ff', marginRight: 4 }} />Word
                        </Radio.Button>
                        <Radio.Button value="EXCEL" style={{ width: '33.34%', textAlign: 'center' }}>
                          <FileExcelOutlined style={{ color: '#52c41a', marginRight: 4 }} />Excel
                        </Radio.Button>
                      </Radio.Group>
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item label="科室水印" style={{ marginBottom: 0 }}>
                      <Space direction="vertical" style={{ width: '100%' }} size={6}>
                        <Space size={12}>
                          <Switch
                            checked={exportConfig.enableWatermark}
                            onChange={(v) => setExportConfig((prev) => ({ ...prev, enableWatermark: v }))}
                          />
                          <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                            {exportConfig.enableWatermark ? '已启用' : '未启用'}
                          </span>
                          <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
                        </Space>
                        <Input
                          size="small"
                          disabled={!exportConfig.enableWatermark}
                          placeholder="水印文字，如：普外科质控科"
                          prefix={<FileProtectOutlined />}
                          value={exportConfig.watermarkText}
                          onChange={(e) => setExportConfig((prev) => ({ ...prev, watermarkText: e.target.value }))}
                          style={{ maxWidth: 280 }}
                        />
                      </Space>
                    </Form.Item>
                  </Col>
                </Row>
              </Form>
            </Card>

            <Alert
              type={exportConfig.format === 'PDF' ? 'success' : 'warning'}
              showIcon
              message={
                exportConfig.format === 'PDF'
                  ? '将生成带科室水印的PDF质控单，符合医院质控科归档要求'
                  : 'Word/Excel格式可用于二次编辑，水印效果可能不显示'
              }
            />
          </Space>
        </Spin>
      </Modal>
    </div>
  )
}

export default QualityControlPage
