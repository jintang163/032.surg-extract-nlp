import React, { useEffect, useState, useMemo } from 'react'
import {
  Card,
  Row,
  Col,
  Statistic,
  DatePicker,
  Select,
  Space,
  Table,
  Tag,
  Progress,
  Tabs,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  message,
  Tooltip,
  Empty,
  Spin,
  Popconfirm,
  Typography,
  Alert,
} from 'antd'
import {
  SafetyOutlined,
  TrophyOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FundOutlined,
  TeamOutlined,
  CloudSyncOutlined,
  SettingOutlined,
  RadarChartOutlined,
  RiseOutlined,
  FallOutlined,
  DownOutlined,
  UpOutlined,
} from '@ant-design/icons'
import ReactECharts, { EChartsOption } from 'echarts-for-react'
import dayjs, { Dayjs } from 'dayjs'
import { qualityBenchmarkApi } from '@/services/api'
import type {
  QualityBenchmarkDashboard,
  IndicatorDeviation,
  DepartmentRanking,
  QualityRadarData,
  QualityBenchmark,
  QualityBenchmarkCreateForm,
  IndicatorCategory,
  BenchmarkDirection,
  DeviationLevel,
} from '@/types'
import {
  IndicatorCategoryMap,
  BenchmarkDirectionMap,
  DeviationLevelMap,
  DeviationLevelColorMap,
} from '@/types'
import { useAuthStore } from '@/store/authStore'

const { Text } = Typography
const { RangePicker } = DatePicker
const { Option } = Select
const { TabPane } = Tabs

const QualityBenchmarkPage: React.FC = () => {
  const { userInfo } = useAuthStore()
  const isAdmin = userInfo?.role === 'ADMIN'

  const [loading, setLoading] = useState(false)
  const [dashboardData, setDashboardData] = useState<QualityBenchmarkDashboard | null>(null)
  const [radarData, setRadarData] = useState<QualityRadarData | null>(null)
  const [rankings, setRankings] = useState<DepartmentRanking[]>([])
  const [deviations, setDeviations] = useState<IndicatorDeviation[]>([])

  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(90, 'day'),
    dayjs(),
  ])
  const [benchmarkYear, setBenchmarkYear] = useState<number>(dayjs().year())
  const [selectedCategory, setSelectedCategory] = useState<IndicatorCategory | undefined>(undefined)
  const [selectedDeptsForRadar, setSelectedDeptsForRadar] = useState<string[]>([])

  const [benchmarkList, setBenchmarkList] = useState<QualityBenchmark[]>([])
  const [benchmarkTotal, setBenchmarkTotal] = useState(0)
  const [benchmarkPage, setBenchmarkPage] = useState(1)
  const [benchmarkPageSize, setBenchmarkPageSize] = useState(10)
  const [benchmarkLoading, setBenchmarkLoading] = useState(false)
  const [benchmarkFilter, setBenchmarkFilter] = useState<{
    category?: IndicatorCategory
    enabled?: number
    year?: number
  }>({})

  const [modalVisible, setModalVisible] = useState(false)
  const [editingBenchmark, setEditingBenchmark] = useState<QualityBenchmark | null>(null)
  const [form] = Form.useForm<QualityBenchmarkCreateForm>()
  const [submitting, setSubmitting] = useState(false)

  const [categoryOptions, setCategoryOptions] = useState<{ code: string; label: string }[]>([])
  const [directionOptions, setDirectionOptions] = useState<{ code: string; label: string }[]>([])

  useEffect(() => {
    loadDicts()
    loadAllData()
  }, [])

  useEffect(() => {
    loadDashboard()
    loadRankings()
    loadRadar()
  }, [dateRange, benchmarkYear, selectedCategory])

  useEffect(() => {
    loadRadar()
  }, [selectedDeptsForRadar])

  const loadDicts = async () => {
    try {
      const [cats, dirs] = await Promise.all([
        qualityBenchmarkApi.getIndicatorCategories(),
        qualityBenchmarkApi.getDirections(),
      ])
      setCategoryOptions(cats)
      setDirectionOptions(dirs)
    } catch (e) {
      console.error('加载字典失败', e)
    }
  }

  const loadAllData = () => {
    loadDashboard()
    loadRankings()
    loadRadar()
    loadDeviations()
  }

  const loadDashboard = async () => {
    setLoading(true)
    try {
      const data = await qualityBenchmarkApi.getDashboard({
        startDate: dateRange[0]?.format('YYYY-MM-DD'),
        endDate: dateRange[1]?.format('YYYY-MM-DD'),
        benchmarkYear,
      })
      setDashboardData(data)
    } catch (e) {
      console.error('加载仪表盘失败', e)
      message.error('加载仪表盘失败')
    } finally {
      setLoading(false)
    }
  }

  const loadRankings = async () => {
    try {
      const data = await qualityBenchmarkApi.getDepartmentRankings({
        startDate: dateRange[0]?.format('YYYY-MM-DD'),
        endDate: dateRange[1]?.format('YYYY-MM-DD'),
        benchmarkYear,
        indicatorCategory: selectedCategory,
      })
      setRankings(data)
    } catch (e) {
      console.error('加载科室排名失败', e)
    }
  }

  const loadRadar = async () => {
    try {
      const data = await qualityBenchmarkApi.getRadarChartData({
        departments: selectedDeptsForRadar.length ? selectedDeptsForRadar : undefined,
        startDate: dateRange[0]?.format('YYYY-MM-DD'),
        endDate: dateRange[1]?.format('YYYY-MM-DD'),
        benchmarkYear,
        indicatorCategory: selectedCategory,
      })
      setRadarData(data)
    } catch (e) {
      console.error('加载雷达图失败', e)
    }
  }

  const loadDeviations = async () => {
    try {
      const data = await qualityBenchmarkApi.getDeviations({
        startDate: dateRange[0]?.format('YYYY-MM-DD'),
        endDate: dateRange[1]?.format('YYYY-MM-DD'),
        benchmarkYear,
      })
      setDeviations(data)
    } catch (e) {
      console.error('加载偏离度失败', e)
    }
  }

  const loadBenchmarkList = async () => {
    setBenchmarkLoading(true)
    try {
      const result = await qualityBenchmarkApi.listBenchmarks({
        pageNum: benchmarkPage,
        pageSize: benchmarkPageSize,
        indicatorCategory: benchmarkFilter.category,
        enabled: benchmarkFilter.enabled,
        benchmarkYear: benchmarkFilter.year,
      })
      setBenchmarkList(result.list || [])
      setBenchmarkTotal(result.total || 0)
    } catch (e) {
      console.error('加载基准列表失败', e)
    } finally {
      setBenchmarkLoading(false)
    }
  }

  useEffect(() => {
    if (isAdmin) loadBenchmarkList()
  }, [benchmarkPage, benchmarkPageSize, benchmarkFilter, isAdmin])

  const radarOption: EChartsOption = useMemo(() => {
    if (!radarData?.indicatorNames?.length || !radarData?.series?.length) {
      return {}
    }
    const indicator = radarData.indicatorNames.map((name) => ({
      name,
      max: 100,
    }))
    const colors = ['#1677ff', '#52c41a', '#faad14', '#722ed1', '#13c2c2', '#eb2f96']
    return {
      tooltip: { trigger: 'item' },
      legend: {
        data: radarData.series.map((s) => s.name),
        bottom: 0,
        type: 'scroll',
      },
      radar: {
        indicator,
        shape: 'polygon',
        splitNumber: 5,
        axisName: {
          color: '#333',
          fontSize: 12,
        },
        splitLine: {
          lineStyle: { color: 'rgba(0,0,0,0.1)' },
        },
        splitArea: {
          areaStyle: { color: ['rgba(22,119,255,0.02)', 'rgba(22,119,255,0.05)'] },
        },
      },
      series: [
        {
          type: 'radar',
          emphasis: { lineStyle: { width: 4 } },
          data: radarData.series.map((s, idx) => ({
            name: s.name,
            value: s.values,
            symbol: 'circle',
            symbolSize: 6,
            lineStyle: { width: 2, color: colors[idx % colors.length] },
            itemStyle: { color: colors[idx % colors.length] },
            areaStyle: {
              color: colors[idx % colors.length],
              opacity: s.name === '区域基准' ? 0.05 : 0.15,
            },
          })),
        },
      ],
    }
  }, [radarData])

  const handleAddBenchmark = () => {
    setEditingBenchmark(null)
    form.resetFields()
    form.setFieldsValue({
      benchmarkYear: dayjs().year(),
      enabled: 1,
      direction: 'LOWER_BETTER',
      department: 'ALL',
      source: '区域质控中心',
      region: '区域',
    })
    setModalVisible(true)
  }

  const handleEditBenchmark = (record: QualityBenchmark) => {
    setEditingBenchmark(record)
    form.setFieldsValue({
      ...record,
    })
    setModalVisible(true)
  }

  const handleDeleteBenchmark = async (id: number) => {
    try {
      await qualityBenchmarkApi.deleteBenchmark(id)
      message.success('删除成功')
      loadBenchmarkList()
    } catch (e: any) {
      message.error(e?.message || '删除失败')
    }
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      if (editingBenchmark) {
        await qualityBenchmarkApi.updateBenchmark(editingBenchmark.id, values)
        message.success('更新成功')
      } else {
        await qualityBenchmarkApi.createBenchmark(values)
        message.success('创建成功')
      }
      setModalVisible(false)
      loadBenchmarkList()
      loadAllData()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.message || '保存失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleInitDefaults = async () => {
    try {
      await qualityBenchmarkApi.initDefaultBenchmarks()
      message.success('已初始化默认基准数据')
      loadBenchmarkList()
      loadAllData()
    } catch (e: any) {
      message.error(e?.message || '初始化失败')
    }
  }

  const renderDeviationTag = (level: DeviationLevel) => (
    <Tag color={DeviationLevelColorMap[level]} icon={
      level === 'PASS' ? <CheckCircleOutlined /> :
      level === 'WARNING' ? <WarningOutlined /> : <ExclamationCircleOutlined />
    }>
      {DeviationLevelMap[level]}
    </Tag>
  )

  const renderDeviationTrend = (deviation: IndicatorDeviation) => {
    const isBetterLower = deviation.direction === 'LOWER_BETTER'
    const isBetterHigher = deviation.direction === 'HIGHER_BETTER'
    const positive = deviation.deviationRate > 0
    let good = false
    if (isBetterLower) good = !positive
    else if (isBetterHigher) good = positive
    const color = good ? '#52c41a' : '#ff4d4f'
    const icon = deviation.deviationRate > 0 ? <UpOutlined /> : <DownOutlined />
    return (
      <span style={{ color }}>
        {icon} {Math.abs(deviation.deviationRate).toFixed(2)}%
      </span>
    )
  }

  const rankingColumns = [
    {
      title: '排名',
      dataIndex: 'ranking',
      width: 70,
      render: (val: number) => (
        <Space>
          {val <= 3 ? (
            <TrophyOutlined style={{ color: val === 1 ? '#faad14' : val === 2 ? '#bfbfbf' : '#d48806' }} />
          ) : null}
          <Text strong style={{ color: val <= 3 ? '#faad14' : undefined }}>{val}</Text>
        </Space>
      ),
    },
    {
      title: '科室',
      dataIndex: 'department',
      width: 140,
      render: (val: string) => (
        <Space>
          <TeamOutlined style={{ color: '#1677ff' }} />
          <Text strong>{val}</Text>
        </Space>
      ),
    },
    {
      title: '综合评分',
      dataIndex: 'compositeScore',
      width: 120,
      render: (val: number) => (
        <Progress
          percent={Math.round(val)}
          size="small"
          strokeColor={val >= 80 ? '#52c41a' : val >= 60 ? '#faad14' : '#ff4d4f'}
        />
      ),
      sorter: (a: DepartmentRanking, b: DepartmentRanking) => a.compositeScore - b.compositeScore,
      defaultSortOrder: 'descend' as const,
    },
    {
      title: '达标率',
      dataIndex: 'passRate',
      width: 120,
      render: (val: number) => <span>{val.toFixed(2)}%</span>,
    },
    {
      title: '达标',
      dataIndex: 'passedIndicators',
      width: 70,
      render: (val: number) => <Tag color="success">{val}</Tag>,
    },
    {
      title: '预警',
      dataIndex: 'warningIndicators',
      width: 70,
      render: (val: number) => val > 0 ? <Tag color="warning">{val}</Tag> : <span>-</span>,
    },
    {
      title: '严重偏离',
      dataIndex: 'criticalIndicators',
      width: 90,
      render: (val: number) => val > 0 ? <Tag color="error">{val}</Tag> : <span>-</span>,
    },
    {
      title: '指标总数',
      dataIndex: 'totalIndicators',
      width: 90,
    },
  ]

  const deviationColumns = [
    {
      title: '科室',
      dataIndex: 'department',
      width: 120,
      render: (val?: string) => val || '全部',
    },
    {
      title: '指标名称',
      dataIndex: 'indicatorName',
      width: 140,
    },
    {
      title: '分类',
      dataIndex: 'indicatorCategory',
      width: 100,
      render: (val: IndicatorCategory) => (
        <Tag color="blue">{IndicatorCategoryMap[val] || val}</Tag>
      ),
    },
    {
      title: '实际值',
      dataIndex: 'actualValue',
      width: 100,
      render: (val: number, record: IndicatorDeviation) => `${val.toFixed(2)}${record.unit || ''}`,
    },
    {
      title: '区域基准',
      dataIndex: 'benchmarkValue',
      width: 110,
      render: (val: number, record: IndicatorDeviation) => (
        <Text type="secondary">{val.toFixed(2)}{record.unit || ''}</Text>
      ),
    },
    {
      title: '偏离率',
      dataIndex: 'deviationRate',
      width: 110,
      render: (_val: number, record: IndicatorDeviation) => renderDeviationTrend(record),
    },
    {
      title: '状态',
      dataIndex: 'deviationLevel',
      width: 110,
      render: (val: DeviationLevel) => renderDeviationTag(val),
    },
  ]

  const benchmarkColumns = [
    {
      title: '指标名称',
      dataIndex: 'indicatorName',
      width: 160,
      render: (val: string, record: QualityBenchmark) => (
        <Space>
          <Text strong>{val}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>({record.indicatorCode})</Text>
        </Space>
      ),
    },
    {
      title: '分类',
      dataIndex: 'indicatorCategory',
      width: 100,
      render: (val: IndicatorCategory) => IndicatorCategoryMap[val] || val,
    },
    {
      title: '单位',
      dataIndex: 'unit',
      width: 70,
    },
    {
      title: '基准值',
      dataIndex: 'benchmarkValue',
      width: 100,
      render: (val: number, record: QualityBenchmark) => `${val}${record.unit || ''}`,
    },
    {
      title: '预警阈值',
      dataIndex: 'warningThreshold',
      width: 100,
      render: (val?: number) => val != null ? val : '-',
    },
    {
      title: '严重阈值',
      dataIndex: 'criticalThreshold',
      width: 100,
      render: (val?: number) => val != null ? val : '-',
    },
    {
      title: '优劣方向',
      dataIndex: 'directionLabel',
      width: 100,
      render: (_val: string, record: QualityBenchmark) =>
        BenchmarkDirectionMap[record.direction as BenchmarkDirection] || record.direction,
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 120,
    },
    {
      title: '适用科室',
      dataIndex: 'department',
      width: 100,
      render: (val?: string) => val === 'ALL' ? '全部' : val,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (val?: number) => (
        <Tag color={val === 1 ? 'success' : 'default'}>
          {val === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: any, record: QualityBenchmark) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEditBenchmark(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除此基准配置？"
            onConfirm={() => handleDeleteBenchmark(record.id)}
            okType="danger"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const topDeptOptions = useMemo(() =>
    rankings.slice(0, 10).map((r) => ({ value: r.department, label: r.department })),
    [rankings]
  )

  return (
    <div className="page-container">
      <Card style={{ marginBottom: 16 }}>
        <Space wrap size="large">
          <Space>
            <Text strong>时间范围：</Text>
            <RangePicker
              value={dateRange}
              onChange={(val) => val && setDateRange(val as [Dayjs, Dayjs])}
            />
          </Space>
          <Space>
            <Text strong>基准年份：</Text>
            <Select
              style={{ width: 120 }}
              value={benchmarkYear}
              onChange={setBenchmarkYear}
            >
              {[0, 1, 2, 3].map((y) => (
                <Option key={y} value={dayjs().year() - y}>
                  {dayjs().year() - y}年
                </Option>
              ))}
            </Select>
          </Space>
          <Space>
            <Text strong>指标分类：</Text>
            <Select
              style={{ width: 140 }}
              allowClear
              placeholder="全部"
              value={selectedCategory}
              onChange={setSelectedCategory}
            >
              {categoryOptions.map((c) => (
                <Option key={c.code} value={c.code}>{c.label}</Option>
              ))}
            </Select>
          </Space>
          <Button icon={<ReloadOutlined />} onClick={loadAllData}>
            刷新
          </Button>
        </Space>
      </Card>

      <Spin spinning={loading}>
        <Tabs defaultActiveKey="dashboard">
          <TabPane
            tab={
              <span>
                <SafetyOutlined /> 质控仪表盘
              </span>
            }
            key="dashboard"
          >
            {dashboardData ? (
              <>
                <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                  <Col xs={24} sm={12} md={6}>
                    <Card>
                      <Statistic
                        title="综合质控评分"
                        value={dashboardData.compositeScore}
                        precision={2}
                        suffix="分"
                        prefix={<SafetyOutlined style={{ color: '#1677ff' }} />}
                        valueStyle={{
                          color: dashboardData.compositeScore >= 80 ? '#52c41a'
                            : dashboardData.compositeScore >= 60 ? '#faad14' : '#ff4d4f'
                        }}
                      />
                      <Progress
                        percent={Math.round(dashboardData.compositeScore)}
                        showInfo={false}
                        strokeColor={dashboardData.compositeScore >= 80 ? '#52c41a'
                          : dashboardData.compositeScore >= 60 ? '#faad14' : '#ff4d4f'}
                        style={{ marginTop: 8 }}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} sm={12} md={6}>
                    <Card>
                      <Statistic
                        title="整体达标率"
                        value={dashboardData.overallPassRate}
                        precision={2}
                        suffix="%"
                        prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
                        valueStyle={{ color: '#52c41a' }}
                      />
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        共 {dashboardData.totalIndicators} 个指标
                      </Text>
                    </Card>
                  </Col>
                  <Col xs={24} sm={12} md={6}>
                    <Card>
                      <Statistic
                        title="达标指标"
                        value={dashboardData.passedCount}
                        prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
                        valueStyle={{ color: '#52c41a' }}
                      />
                      <Statistic
                        title="预警指标"
                        value={dashboardData.warningCount}
                        prefix={<WarningOutlined style={{ color: '#faad14' }} />}
                        valueStyle={{ color: '#faad14', fontSize: 16 }}
                        style={{ marginTop: 8 }}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} sm={12} md={6}>
                    <Card>
                      <Statistic
                        title="严重偏离"
                        value={dashboardData.criticalCount}
                        prefix={<ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />}
                        valueStyle={{ color: '#ff4d4f' }}
                      />
                      <Statistic
                        title="参评科室"
                        value={dashboardData.evaluatedDepartments}
                        prefix={<TeamOutlined style={{ color: '#1677ff' }} />}
                        style={{ marginTop: 8 }}
                      />
                    </Card>
                  </Col>
                </Row>

                <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                  <Col xs={24} lg={12}>
                    <Card
                      title={
                        <Space>
                          <RadarChartOutlined style={{ color: '#722ed1' }} />
                          <span>科室质控雷达图对比</span>
                        </Space>
                      }
                      extra={
                        <Select
                          mode="multiple"
                          placeholder="选择科室对比"
                          style={{ minWidth: 240 }}
                          allowClear
                          maxTagCount={3}
                          value={selectedDeptsForRadar}
                          onChange={setSelectedDeptsForRadar}
                          options={topDeptOptions}
                        />
                      }
                      style={{ height: '100%' }}
                    >
                      {radarData?.indicatorNames?.length ? (
                        <ReactECharts option={radarOption} style={{ height: 380 }} />
                      ) : (
                        <Empty description="暂无雷达图数据" style={{ padding: '80px 0' }} />
                      )}
                    </Card>
                  </Col>
                  <Col xs={24} lg={12}>
                    <Card
                      title={
                        <Space>
                          <TrophyOutlined style={{ color: '#faad14' }} />
                          <span>科室质控排名</span>
                        </Space>
                      }
                    >
                      <Table
                        dataSource={rankings}
                        columns={rankingColumns}
                        rowKey="department"
                        size="small"
                        pagination={{ pageSize: 6, showSizeChanger: false }}
                        scroll={{ y: 340 }}
                      />
                    </Card>
                  </Col>
                </Row>

                <Card
                  title={
                    <Space>
                      <FundOutlined style={{ color: '#eb2f96' }} />
                      <span>指标偏离度分析（TOP异常）</span>
                    </Space>
                  }
                  style={{ marginBottom: 16 }}
                >
                  {dashboardData.topDeviations?.length ? (
                    <Table
                      dataSource={dashboardData.topDeviations}
                      columns={deviationColumns}
                      rowKey={(r) => `${r.department}-${r.indicatorCode}`}
                      size="small"
                      pagination={false}
                    />
                  ) : (
                    <Empty description="暂无偏离度数据" />
                  )}
                </Card>
              </>
            ) : (
              <Empty description="暂无仪表盘数据" style={{ padding: '100px 0' }} />
            )}
          </TabPane>

          <TabPane
            tab={
              <span>
                <TeamOutlined /> 科室排名详情
              </span>
            }
            key="rankings"
          >
            <Card>
              <Alert
                message="按综合质控评分对各科室进行排名，综合考量达标率、偏离程度等因素"
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
              />
              <Table
                dataSource={rankings}
                columns={[
                  ...rankingColumns,
                  {
                    title: '详细指标',
                    key: 'detail',
                    width: 100,
                    render: (_: any, record: DepartmentRanking) => (
                      <Tooltip
                        title={
                          <div style={{ maxWidth: 320 }}>
                            {record.indicatorDeviations?.map((d) => (
                              <div key={d.indicatorCode} style={{ marginBottom: 4 }}>
                                {renderDeviationTag(d.deviationLevel)}
                                <span style={{ marginLeft: 8 }}>{d.indicatorName}:</span>
                                <span style={{ marginLeft: 4 }}>
                                  {d.actualValue.toFixed(2)}{d.unit || ''}
                                  <Text type="secondary"> / 基准 {d.benchmarkValue}{d.unit || ''}</Text>
                                </span>
                              </div>
                            ))}
                          </div>
                        }
                      >
                        <Button type="link" size="small">查看详情</Button>
                      </Tooltip>
                    ),
                  },
                ]}
                rowKey="department"
                pagination={{ pageSize: 20, showSizeChanger: true }}
              />
            </Card>
          </TabPane>

          <TabPane
            tab={
              <span>
                <FundOutlined /> 全部偏离度
              </span>
            }
            key="deviations"
          >
            <Card>
              <Alert
                message="展示本院各科室指标与区域基准的偏离情况，红色表示严重偏离需重点关注"
                type="warning"
                showIcon
                style={{ marginBottom: 16 }}
              />
              <Table
                dataSource={deviations}
                columns={deviationColumns}
                rowKey={(r) => `${r.department}-${r.indicatorCode}`}
                pagination={{ pageSize: 20, showSizeChanger: true }}
              />
            </Card>
          </TabPane>

          {isAdmin && (
            <TabPane
              tab={
                <span>
                  <SettingOutlined /> 基准数据管理
                </span>
              }
              key="benchmarks"
            >
              <Card
                extra={
                  <Space>
                    <Button onClick={handleInitDefaults} icon={<CloudSyncOutlined />}>
                      同步区域基准
                    </Button>
                    <Button type="primary" onClick={handleAddBenchmark} icon={<PlusOutlined />}>
                      新增基准
                    </Button>
                  </Space>
                }
              >
                <Space wrap style={{ marginBottom: 16 }}>
                  <Select
                    placeholder="指标分类"
                    style={{ width: 140 }}
                    allowClear
                    value={benchmarkFilter.category}
                    onChange={(v) => setBenchmarkFilter({ ...benchmarkFilter, category: v })}
                  >
                    {categoryOptions.map((c) => (
                      <Option key={c.code} value={c.code}>{c.label}</Option>
                    ))}
                  </Select>
                  <Select
                    placeholder="状态"
                    style={{ width: 120 }}
                    allowClear
                    value={benchmarkFilter.enabled}
                    onChange={(v) => setBenchmarkFilter({ ...benchmarkFilter, enabled: v })}
                  >
                    <Option value={1}>启用</Option>
                    <Option value={0}>禁用</Option>
                  </Select>
                  <Select
                    placeholder="基准年份"
                    style={{ width: 120 }}
                    allowClear
                    value={benchmarkFilter.year}
                    onChange={(v) => setBenchmarkFilter({ ...benchmarkFilter, year: v })}
                  >
                    {[0, 1, 2, 3].map((y) => (
                      <Option key={y} value={dayjs().year() - y}>
                        {dayjs().year() - y}年
                      </Option>
                    ))}
                  </Select>
                  <Button onClick={loadBenchmarkList} icon={<ReloadOutlined />}>查询</Button>
                </Space>
                <Table
                  dataSource={benchmarkList}
                  columns={benchmarkColumns}
                  rowKey="id"
                  loading={benchmarkLoading}
                  pagination={{
                    current: benchmarkPage,
                    pageSize: benchmarkPageSize,
                    total: benchmarkTotal,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    onChange: (p, ps) => {
                      setBenchmarkPage(p)
                      setBenchmarkPageSize(ps)
                    },
                  }}
                />
              </Card>
            </TabPane>
          )}
        </Tabs>
      </Spin>

      <Modal
        title={editingBenchmark ? '编辑质控基准' : '新增质控基准'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        width={720}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ benchmarkYear: dayjs().year(), enabled: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="指标编码"
                name="indicatorCode"
                rules={[{ required: true, message: '请输入指标编码' }]}
              >
                <Input placeholder="如：avg_stay_days" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="指标名称"
                name="indicatorName"
                rules={[{ required: true, message: '请输入指标名称' }]}
              >
                <Input placeholder="如：平均住院日" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="指标分类"
                name="indicatorCategory"
                rules={[{ required: true, message: '请选择指标分类' }]}
              >
                <Select placeholder="请选择">
                  {categoryOptions.map((c) => (
                    <Option key={c.code} value={c.code}>{c.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="单位"
                name="unit"
              >
                <Input placeholder="如：天、%、元" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                label="区域基准值"
                name="benchmarkValue"
                rules={[{ required: true, message: '请输入基准值' }]}
              >
                <InputNumber style={{ width: '100%' }} step={0.01} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="预警阈值" name="warningThreshold">
                <InputNumber style={{ width: '100%' }} step={0.01} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="严重偏离阈值" name="criticalThreshold">
                <InputNumber style={{ width: '100%' }} step={0.01} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="优劣方向"
                name="direction"
                rules={[{ required: true, message: '请选择' }]}
              >
                <Select placeholder="请选择">
                  {directionOptions.map((d) => (
                    <Option key={d.code} value={d.code}>{d.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="基准年份" name="benchmarkYear">
                <Select>
                  {[0, 1, 2, 3].map((y) => (
                    <Option key={y} value={dayjs().year() - y}>
                      {dayjs().year() - y}年
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="数据来源" name="source">
                <Input placeholder="如：区域质控中心" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="区域" name="region">
                <Input placeholder="如：区域" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="适用科室" name="department">
                <Input placeholder="ALL或具体科室名" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="排序" name="sortOrder">
                <InputNumber style={{ width: '100%' }} step={1} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="是否启用" name="enabled" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="指标说明" name="description">
            <Input.TextArea rows={3} placeholder="指标的详细说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default QualityBenchmarkPage
