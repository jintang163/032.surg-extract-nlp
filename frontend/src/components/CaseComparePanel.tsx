import React, { useState, useEffect, useMemo } from 'react'
import {
  Card,
  Row,
  Col,
  Table,
  Tag,
  Progress,
  Statistic,
  Space,
  Descriptions,
  Select,
  InputNumber,
  Button,
  Tooltip,
  Badge,
  Divider,
  Typography,
  Empty,
  Spin,
  Alert,
  Slider,
  List,
  Avatar,
  Drawer,
  Modal,
  message,
  Popover,
} from 'antd'
import {
  HistoryOutlined,
  BarChartOutlined,
  DiffOutlined,
  ReloadOutlined,
  EyeOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  InfoCircleOutlined,
  FireOutlined,
  SettingOutlined,
  DatabaseOutlined,
  ScanOutlined,
} from '@ant-design/icons'
import { caseCompareApi } from '@/services/api'
import type {
  SimilarCaseResult,
  CaseStatsAnalysis,
  FieldComparison,
  NumericFieldStats,
  CategoryBucket,
  DeviationLevel,
  DeviationDirection,
  CaseFullAnalysis,
} from '@/types'
import {
  DeviationLevelMap,
  DeviationDirectionMap,
  EntityTypeLabelMap,
} from '@/types'
import dayjs from 'dayjs'

const { Title, Text } = Typography
const { Option } = Select

interface CaseComparePanelProps {
  recordId: number
  surgeryName?: string
  preopDiagnosis?: string
  postopDiagnosis?: string
  department?: string
  entities?: any[]
  onViewCaseDetail?: (caseRecordId: number) => void
}

const FieldComparisonCard: React.FC<{
  comparison: FieldComparison
}> = ({ comparison }) => {
  const levelInfo = comparison.deviationLevel
    ? DeviationLevelMap[comparison.deviationLevel]
    : null
  const dirInfo = comparison.deviationDirection
    ? DeviationDirectionMap[comparison.deviationDirection]
    : null

  const borderColor =
    comparison.deviationLevel === 'SEVERE'
      ? '#ffccc7'
      : comparison.deviationLevel === 'MODERATE'
      ? '#ffe7ba'
      : comparison.deviationLevel === 'MILD'
      ? '#d6e4ff'
      : '#d9f7be'

  const bgColor =
    comparison.deviationLevel === 'SEVERE'
      ? '#fff2f0'
      : comparison.deviationLevel === 'MODERATE'
      ? '#fff7e6'
      : comparison.deviationLevel === 'MILD'
      ? '#f0f5ff'
      : '#f6ffed'

  return (
    <div
      style={{
        border: `1px solid ${borderColor}`,
        borderRadius: 8,
        padding: 12,
        backgroundColor: bgColor,
        height: '100%',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
        <Text strong style={{ fontSize: 13 }}>{comparison.fieldLabel}</Text>
        {levelInfo && (
          <Tag
            color={levelInfo.color}
            style={{ margin: 0, fontSize: 11, padding: '0 6px', height: 18, lineHeight: '16px' }}
          >
            {levelInfo.label}
          </Tag>
        )}
      </div>

      <div style={{ marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>当前值</Text>
        <div style={{ fontSize: 16, fontWeight: 600, marginTop: 2 }}>
          {comparison.currentValue || <Text type="secondary">（未填写）</Text>}
          {comparison.unit && comparison.currentValue && (
            <span style={{ fontSize: 12, color: '#8c8c8c', marginLeft: 4 }}>
              {comparison.unit}
            </span>
          )}
        </div>
      </div>

      {comparison.currentValue && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            {dirInfo && comparison.deviationDirection !== 'WITHIN_RANGE' && comparison.deviationDirection !== 'DIFFERENT' && (
              <span style={{ color: dirInfo.color, fontWeight: 600, fontSize: 13 }}>
                {dirInfo.icon}
                {comparison.deviationPercent !== undefined &&
                  `${comparison.deviationPercent > 0 ? '+' : ''}${comparison.deviationPercent}%`}
              </span>
            )}
            {dirInfo && comparison.deviationDirection === 'WITHIN_RANGE' && (
              <span style={{ color: dirInfo.color, fontSize: 12 }}>
                <CheckCircleOutlined /> 在典型区间内
              </span>
            )}
            {dirInfo && comparison.deviationDirection === 'DIFFERENT' && (
              <span style={{ color: dirInfo.color, fontSize: 12 }}>
                <WarningOutlined /> 非常规选择
              </span>
            )}
          </div>
        </div>
      )}

      <Divider style={{ margin: '8px 0' }} />

      <div>
        <Text type="secondary" style={{ fontSize: 11 }}>历史典型值</Text>
        <div style={{ fontSize: 14, marginTop: 2, fontWeight: 500 }}>
          {comparison.typicalValue || <Text type="secondary">暂无数据</Text>}
        </div>
        {comparison.typicalRange && (
          <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 2 }}>
            典型区间：{comparison.typicalRange}
          </div>
        )}
      </div>

      {comparison.tip && (
        <Tooltip title={comparison.tip}>
          <div
            style={{
              marginTop: 10,
              padding: '6px 8px',
              backgroundColor: 'rgba(255,255,255,0.6)',
              borderRadius: 4,
              fontSize: 11,
              color: '#595959',
              lineHeight: 1.5,
            }}
          >
            <InfoCircleOutlined style={{ marginRight: 4 }} />
            {comparison.tip}
          </div>
        </Tooltip>
      )}
    </div>
  )
}

const CategoryDistributionBar: React.FC<{
  buckets: CategoryBucket[]
  fieldLabel: string
}> = ({ buckets, fieldLabel }) => {
  const maxCount = Math.max(...buckets.map((b) => b.count), 1)
  return (
    <div style={{ padding: 4 }}>
      <Text type="secondary" style={{ fontSize: 12 }}>{fieldLabel}</Text>
      <div style={{ marginTop: 8 }}>
        {buckets.slice(0, 5).map((b, idx) => (
          <div key={idx} style={{ marginBottom: 6 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
              <span style={{ fontSize: 12 }}>
                {b.value}
                {b.isMostFrequent && (
                  <Tag color="gold" style={{ marginLeft: 4, fontSize: 10, height: 14, lineHeight: '12px', padding: '0 4px' }}>
                    最常见
                  </Tag>
                )}
              </span>
              <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                {b.count} 例 ({b.percentage.toFixed(1)}%)
              </span>
            </div>
            <div
              style={{
                height: 16,
                backgroundColor: '#f0f0f0',
                borderRadius: 4,
                overflow: 'hidden',
                position: 'relative',
              }}
            >
              <div
                style={{
                  height: '100%',
                  width: `${(b.count / maxCount) * 100}%`,
                  backgroundColor: b.isMostFrequent ? '#faad14' : '#1677ff',
                  transition: 'width 0.3s',
                }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

const CaseComparePanel: React.FC<CaseComparePanelProps> = ({
  recordId,
  surgeryName,
  preopDiagnosis,
  postopDiagnosis,
  department,
  entities = [],
  onViewCaseDetail,
}) => {
  const [loading, setLoading] = useState(false)
  const [fullAnalysis, setFullAnalysis] = useState<CaseFullAnalysis | null>(null)
  const [activeTab, setActiveTab] = useState<'similar' | 'stats' | 'diff'>('diff')
  const [viewingCase, setViewingCase] = useState<SimilarCaseResult | null>(null)
  const [settingsVisible, setSettingsVisible] = useState(false)
  const [timeRangeMonths, setTimeRangeMonths] = useState(6)
  const [topN, setTopN] = useState(10)
  const [minScore, setMinScore] = useState(0.5)

  const entityMap = useMemo(() => {
    const map: Record<string, string> = {}
    entities.forEach((e) => {
      if (e?.entityType) map[e.entityType] = e.entityValue
    })
    return map
  }, [entities])

  const effectiveSurgeryName = surgeryName || entityMap['SURGERY_NAME'] || ''
  const effectivePreop = preopDiagnosis || entityMap['PREOP_DIAGNOSIS'] || ''
  const effectivePostop = postopDiagnosis || entityMap['POSTOP_DIAGNOSIS'] || ''
  const effectiveDepartment = department || ''

  const loadData = async () => {
    if (!effectiveSurgeryName) {
      setFullAnalysis(null)
      return
    }
    setLoading(true)
    try {
      const result = await caseCompareApi.getFullAnalysis(recordId, {
        surgeryName: effectiveSurgeryName,
        preopDiagnosis: effectivePreop,
        postopDiagnosis: effectivePostop,
        department: effectiveDepartment,
        timeRangeMonths,
        topN,
        minScore,
      })
      setFullAnalysis(result as CaseFullAnalysis)
    } catch (e) {
      message.error('加载历史病例分析失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [recordId, effectiveSurgeryName, timeRangeMonths, topN, minScore])

  const similarCasesColumns = [
    {
      title: '相似度',
      dataIndex: 'score',
      width: 100,
      render: (score: number) => (
        <div>
          <Progress
            type="dashboard"
            percent={Math.round(score * 100)}
            size={56}
            showInfo={true}
            status={score >= 0.8 ? 'success' : score >= 0.6 ? 'normal' : 'exception'}
          />
        </div>
      ),
    },
    {
      title: '手术名称',
      dataIndex: 'surgeryName',
      ellipsis: true,
      render: (v: string, r: SimilarCaseResult) => (
        <Tooltip title={v}>
          <Space direction="vertical" size={2}>
            <span>{v || '-'}</span>
            <div>
              {r.surgeryLevel && (
                <Tag color="orange" style={{ fontSize: 10, height: 16, lineHeight: '14px', padding: '0 4px' }}>
                  {r.surgeryLevel}
                </Tag>
              )}
              {r.incisionLevel && (
                <Tag color="red" style={{ fontSize: 10, height: 16, lineHeight: '14px', padding: '0 4px' }}>
                  {r.incisionLevel}级切口
                </Tag>
              )}
            </div>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: '诊断',
      dataIndex: 'postopDiagnosis',
      ellipsis: true,
      render: (v: string, r: SimilarCaseResult) => (
        <Tooltip title={v || r.preopDiagnosis}>
          <span>{v || r.preopDiagnosis || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: '失血量',
      dataIndex: 'bloodLoss',
      width: 100,
      render: (v: number) => (v != null ? `${v}ml` : '-'),
    },
    {
      title: '术者',
      dataIndex: 'surgeon',
      width: 100,
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    {
      title: '上传时间',
      dataIndex: 'uploadTime',
      width: 130,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: any, r: SimilarCaseResult) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              if (onViewCaseDetail) {
                onViewCaseDetail(r.recordId)
              } else {
                setViewingCase(r)
              }
            }}
          >
            查看
          </Button>
        </Space>
      ),
    },
  ]

  const numericFields = Object.entries(fullAnalysis?.stats.numericStats || {})
  const categoryFields = Object.entries(fullAnalysis?.stats.categoryStats || {})
  const fieldComparisons = Object.entries(fullAnalysis?.stats.fieldComparisons || {})

  const summaryStats = useMemo(() => {
    const stats = fullAnalysis?.stats
    if (!stats) return null
    const bloodLoss = stats.numericStats['bloodLoss']
    const incisionLevel = stats.categoryStats['incisionLevel']
    const mostFreqIncision = incisionLevel?.find((b) => b.isMostFrequent) || incisionLevel?.[0]
    return {
      totalCases: stats.totalCases,
      timeRange: stats.timeRangeDescription,
      avgBloodLoss: bloodLoss?.avg,
      medianBloodLoss: bloodLoss?.median,
      bloodLossRange: bloodLoss?.typicalRange,
      mostFreqIncision: mostFreqIncision?.value,
      incisionPct: mostFreqIncision?.percentage,
    }
  }, [fullAnalysis])

  return (
    <Card
      title={
        <Space>
          <HistoryOutlined style={{ color: '#1677ff' }} />
          <span>历史病例对比与参考</span>
          {fullAnalysis?.stats && (
            <Badge
              count={`${fullAnalysis.stats.totalCases}例`}
              style={{ backgroundColor: '#1677ff', fontSize: 11 }}
            />
          )}
        </Space>
      }
      extra={
        <Space>
          <Popover
            content={
              <Space direction="vertical" size={12} style={{ width: 280 }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>时间范围：过去 {timeRangeMonths} 个月</Text>
                  <Slider
                    min={1}
                    max={24}
                    value={timeRangeMonths}
                    onChange={(v) => setTimeRangeMonths(v as number)}
                    marks={{ 1: '1M', 3: '3M', 6: '6M', 12: '1Y', 24: '2Y' }}
                  />
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>相似病例数：{topN}</Text>
                  <InputNumber
                    min={5}
                    max={50}
                    value={topN}
                    onChange={(v) => v && setTopN(v)}
                    style={{ width: '100%' }}
                  />
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>最小相似度：{(minScore * 100).toFixed(0)}%</Text>
                  <Slider
                    min={0.1}
                    max={0.95}
                    step={0.05}
                    value={minScore}
                    onChange={(v) => setMinScore(v as number)}
                    marks={{ 0.3: '30%', 0.5: '50%', 0.7: '70%', 0.9: '90%' }}
                  />
                </div>
                <Button type="primary" size="small" block onClick={() => setSettingsVisible(false)}>
                  应用配置
                </Button>
              </Space>
            }
            title="检索配置"
            trigger="click"
            open={settingsVisible}
            onOpenChange={setSettingsVisible}
          >
            <Button icon={<SettingOutlined />} size="small">配置</Button>
          </Popover>
          <Button icon={<ReloadOutlined />} size="small" onClick={loadData} loading={loading}>刷新</Button>
        </Space>
      }
      loading={loading}
      style={{ marginBottom: 16 }}
    >
      {!effectiveSurgeryName ? (
        <Alert
          message="请先识别手术名称"
          description="系统需要根据手术名称匹配历史病例，请完成NLP实体抽取后再使用此功能"
          type="info"
          showIcon
          icon={<ScanOutlined />}
        />
      ) : !fullAnalysis?.stats || fullAnalysis.stats.totalCases === 0 ? (
        <Empty
          description={
            <Space direction="vertical" align="center">
              <Text>暂无匹配的历史病例</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                可尝试调小时间范围或相似度阈值
              </Text>
            </Space>
          }
        />
      ) : (
        <>
          {summaryStats && (
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
              <Col xs={12} sm={6}>
                <div className="stat-card" style={{ padding: 12 }}>
                  <div className="stat-label">匹配病例数</div>
                  <div className="stat-value" style={{ fontSize: 22 }}>
                    {summaryStats.totalCases}
                    <span style={{ fontSize: 12, color: '#8c8c8c', marginLeft: 4 }}>
                      {summaryStats.timeRange}
                    </span>
                  </div>
                </div>
              </Col>
              <Col xs={12} sm={6}>
                <div className="stat-card" style={{ padding: 12 }}>
                  <div className="stat-label">平均失血量</div>
                  <div className="stat-value" style={{ fontSize: 22, color: '#eb2f96' }}>
                    {summaryStats.avgBloodLoss != null
                      ? Math.round(summaryStats.avgBloodLoss)
                      : '-'}
                    <span style={{ fontSize: 12, color: '#8c8c8c', marginLeft: 4 }}>ml</span>
                  </div>
                </div>
              </Col>
              <Col xs={12} sm={6}>
                <div className="stat-card" style={{ padding: 12 }}>
                  <div className="stat-label">失血量典型区间</div>
                  <div className="stat-value" style={{ fontSize: 18, color: '#722ed1' }}>
                    {summaryStats.bloodLossRange || '-'}
                  </div>
                </div>
              </Col>
              <Col xs={12} sm={6}>
                <div className="stat-card" style={{ padding: 12 }}>
                  <div className="stat-label">最常用切口等级</div>
                  <div className="stat-value" style={{ fontSize: 22, color: '#fa541c' }}>
                    {summaryStats.mostFreqIncision || '-'}
                    {summaryStats.incisionPct != null && (
                      <span style={{ fontSize: 12, color: '#8c8c8c', marginLeft: 4 }}>
                        ({summaryStats.incisionPct.toFixed(0)}%)
                      </span>
                    )}
                  </div>
                </div>
              </Col>
            </Row>
          )}

          <div
            style={{
              display: 'flex',
              borderBottom: '1px solid #f0f0f0',
              marginBottom: 16,
            }}
          >
            {[
              { key: 'diff', label: '差异对比', icon: <DiffOutlined /> },
              { key: 'similar', label: '相似病例', icon: <HistoryOutlined /> },
              { key: 'stats', label: '统计分析', icon: <BarChartOutlined /> },
            ].map((t) => (
              <div
                key={t.key}
                onClick={() => setActiveTab(t.key as any)}
                style={{
                  padding: '8px 16px',
                  cursor: 'pointer',
                  borderBottom: activeTab === t.key ? '2px solid #1677ff' : '2px solid transparent',
                  color: activeTab === t.key ? '#1677ff' : '#595959',
                  fontWeight: activeTab === t.key ? 600 : 400,
                  fontSize: 13,
                  transition: 'all 0.2s',
                }}
              >
                <Space size={6}>
                  {t.icon}
                  {t.label}
                </Space>
              </div>
            ))}
          </div>

          {activeTab === 'diff' && (
            <>
              {fieldComparisons.length > 0 ? (
                <Row gutter={[12, 12]}>
                  {fieldComparisons.map(([key, comp]) => (
                    <Col key={key} xs={24} sm={12} lg={8} xl={6}>
                      <FieldComparisonCard comparison={comp} />
                    </Col>
                  ))}
                </Row>
              ) : (
                <Empty description="暂无差异对比数据" />
              )}
            </>
          )}

          {activeTab === 'similar' && (
            <>
              <Table
                size="small"
                rowKey="recordId"
                dataSource={fullAnalysis.similarCases}
                columns={similarCasesColumns}
                pagination={{
                  pageSize: 5,
                  showSizeChanger: false,
                  showTotal: (t) => `共 ${t} 例相似病例`,
                }}
                scroll={{ x: 700 }}
                locale={{
                  emptyText: (
                    <Empty description="未找到相似病例，可尝试放宽相似度阈值" />
                  ),
                }}
              />
            </>
          )}

          {activeTab === 'stats' && (
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={12}>
                <Card
                  size="small"
                  title={
                    <Space>
                      <BarChartOutlined />
                      数值字段统计
                    </Space>
                  }
                >
                  {numericFields.length > 0 ? (
                    <div>
                      {numericFields.map(([key, stats]: [string, NumericFieldStats]) => (
                        <div key={key} style={{ marginBottom: 16 }}>
                          <Row gutter={8} align="middle">
                            <Col span={6}>
                              <Text strong style={{ fontSize: 12 }}>{stats.fieldLabel}</Text>
                            </Col>
                            <Col span={18}>
                              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <Text type="secondary" style={{ fontSize: 11 }}>
                                  中位 {stats.median?.toFixed?.(0) ?? '-'}
                                </Text>
                                <Text type="secondary" style={{ fontSize: 11 }}>
                                  均 {stats.avg?.toFixed?.(0) ?? '-'}
                                </Text>
                                <Text type="secondary" style={{ fontSize: 11 }}>
                                  范围 {stats.min?.toFixed?.(0) ?? '-'}-{stats.max?.toFixed?.(0) ?? '-'}
                                  {stats.unit || ''}
                                </Text>
                              </div>
                              <div
                                style={{
                                  marginTop: 4,
                                  position: 'relative',
                                  height: 20,
                                  backgroundColor: '#f5f5f5',
                                  borderRadius: 4,
                                }}
                              >
                                {stats.percentile25 != null && stats.percentile75 != null && stats.max != null && stats.min != null && stats.max > stats.min && (
                                  <>
                                    <div
                                      style={{
                                        position: 'absolute',
                                        left: `${((stats.percentile25 - stats.min) / (stats.max - stats.min)) * 100}%`,
                                        right: `${100 - ((stats.percentile75 - stats.min) / (stats.max - stats.min)) * 100}%`,
                                        top: 3,
                                        bottom: 3,
                                        backgroundColor: '#1677ff33',
                                        borderRadius: 2,
                                      }}
                                    />
                                    {stats.median != null && (
                                      <div
                                        style={{
                                          position: 'absolute',
                                          left: `${((stats.median - stats.min) / (stats.max - stats.min)) * 100}%`,
                                          top: 0,
                                          bottom: 0,
                                          width: 2,
                                          backgroundColor: '#1677ff',
                                        }}
                                      />
                                    )}
                                  </>
                                )}
                              </div>
                              {stats.typicalRange && (
                                <div style={{ fontSize: 11, color: '#1677ff', marginTop: 2 }}>
                                  典型区间：{stats.typicalRange}
                                </div>
                              )}
                            </Col>
                          </Row>
                          {key !== numericFields[numericFields.length - 1][0] && (
                            <Divider style={{ margin: '12px 0' }} dashed />
                          )}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <Empty description="暂无数值统计" />
                  )}
                </Card>
              </Col>

              <Col xs={24} lg={12}>
                <Card
                  size="small"
                  title={
                    <Space>
                      <FireOutlined />
                      分类字段分布
                    </Space>
                  }
                >
                  {categoryFields.length > 0 ? (
                    <div>
                      {categoryFields.map(([key, buckets]: [string, CategoryBucket[]]) => (
                        <div key={key}>
                          <CategoryDistributionBar
                            buckets={buckets}
                            fieldLabel={
                              {
                                incisionLevel: '切口等级',
                                incisionHealing: '切口愈合',
                                surgeryLevel: '手术等级',
                                anesthesiaType: '麻醉方式',
                              }[key] || key
                            }
                          />
                          {key !== categoryFields[categoryFields.length - 1][0] && (
                            <Divider style={{ margin: '8px 0' }} dashed />
                          )}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <Empty description="暂无分类分布" />
                  )}
                </Card>
              </Col>
            </Row>
          )}
        </>
      )}

      <Drawer
        title={
          <Space>
            <HistoryOutlined />
            历史病例详情
          </Space>
        }
        open={!!viewingCase}
        onClose={() => setViewingCase(null)}
        width={560}
      >
        {viewingCase && (
          <div>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="记录编号">
                {viewingCase.recordNo}
              </Descriptions.Item>
              <Descriptions.Item label="相似度">
                <Progress
                  percent={Math.round(viewingCase.score * 100)}
                  size="small"
                  status={viewingCase.score >= 0.8 ? 'success' : 'normal'}
                />
              </Descriptions.Item>
              <Descriptions.Item label="手术名称">
                {viewingCase.surgeryName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="手术等级">
                {viewingCase.surgeryLevel || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="切口等级">
                {viewingCase.incisionLevel || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="麻醉方式">
                {viewingCase.anesthesiaType || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="失血量">
                {viewingCase.bloodLoss != null ? `${viewingCase.bloodLoss} ml` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="输血量">
                {viewingCase.bloodTransfusion != null ? `${viewingCase.bloodTransfusion} ml` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="输液量">
                {viewingCase.fluidInfusion != null ? `${viewingCase.fluidInfusion} ml` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="术前诊断">
                {viewingCase.preopDiagnosis || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="术后诊断">
                {viewingCase.postopDiagnosis || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="手术医生">
                {viewingCase.surgeon || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="科室">
                {viewingCase.department || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="手术日期">
                {viewingCase.surgeryDate ? dayjs(viewingCase.surgeryDate).format('YYYY-MM-DD') : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="上传时间">
                {viewingCase.uploadTime ? dayjs(viewingCase.uploadTime).format('YYYY-MM-DD HH:mm') : '-'}
              </Descriptions.Item>
            </Descriptions>

            <Divider />

            <Alert
              message="参考说明"
              description="以上为相似历史病例的结构化数据，可作为当前病例填写的参考依据。如有疑问，建议点击右上角按钮跳转到完整病例详情页面查看原始文本。"
              type="info"
              showIcon
              icon={<InfoCircleOutlined />}
            />
          </div>
        )}
      </Drawer>
    </Card>
  )
}

export default CaseComparePanel
