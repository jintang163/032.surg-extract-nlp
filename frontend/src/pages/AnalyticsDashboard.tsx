import React, { useEffect, useMemo, useState } from 'react'
import {
  Row,
  Col,
  Card,
  Statistic,
  DatePicker,
  Select,
  Space,
  Typography,
  Tag,
  Table,
  Progress,
  Tooltip,
  Empty,
  Spin,
} from 'antd'
import {
  BarChartOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  TeamOutlined,
  PieChartOutlined,
  CloudOutlined,
  WarningOutlined,
  ApartmentOutlined,
  UserOutlined,
  AppstoreOutlined,
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import dayjs, { Dayjs } from 'dayjs'
import { analyticsApi } from '@/services/api'
import type {
  AnalyticsDashboardData,
  DepartmentStats,
  SurgeonStats,
  SurgeryTypeStats,
} from '@/types'
import { EntityTypeLabelMap } from '@/types'

const { Text } = Typography
const { RangePicker } = DatePicker
const { Option } = Select

const AnalyticsDashboard: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<AnalyticsDashboardData | null>(null)
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(30, 'day'),
    dayjs(),
  ])
  const [selectedDepartment, setSelectedDepartment] = useState<string | undefined>(undefined)
  const [drillDimension, setDrillDimension] = useState<'department' | 'surgeon' | 'surgeryType'>('department')

  useEffect(() => {
    loadData()
  }, [dateRange, selectedDepartment])

  const loadData = async () => {
    setLoading(true)
    try {
      const params: Record<string, any> = {}
      if (dateRange && dateRange[0]) params.startDate = dateRange[0].format('YYYY-MM-DD')
      if (dateRange && dateRange[1]) params.endDate = dateRange[1].format('YYYY-MM-DD')
      if (selectedDepartment) params.department = selectedDepartment
      const result = await analyticsApi.getDashboard(params)
      setData(result)
    } catch (e) {
      console.error('加载统计数据失败', e)
    } finally {
      setLoading(false)
    }
  }

  const departmentOptions = useMemo(() => {
    if (!data?.departmentStats) return []
    return data.departmentStats.map((d) => ({ value: d.department, label: d.department }))
  }, [data])

  const coverageOption = useMemo(() => {
    if (!data?.coverageTrend?.length) return null
    const dates = Array.from(new Set(data.coverageTrend.map((d) => d.date))).sort()
    const deptMap = new Map<string, number[]>()
    data.coverageTrend.forEach((item) => {
      const key = item.department || '全部'
      if (!deptMap.has(key)) deptMap.set(key, [])
    })
    dates.forEach((date) => {
      deptMap.forEach((arr, key) => {
        const found = data.coverageTrend.find(
          (d) => d.date === date && (d.department || '全部') === key
        )
        arr.push(found ? found.coverageRate : 0)
      })
    })
    const series = Array.from(deptMap.entries()).slice(0, 6).map(([name, values]) => ({
      name,
      type: 'line',
      smooth: true,
      data: values,
      symbolSize: 6,
    }))
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: series.map((s) => s.name), top: 0 },
      grid: { left: 40, right: 20, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        data: dates.map((d) => dayjs(d).format('MM-DD')),
        boundaryGap: false,
      },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series,
    }
  }, [data])

  const efficiencyOption = useMemo(() => {
    if (!data?.efficiencyTrend?.length) return null
    const dates = Array.from(new Set(data.efficiencyTrend.map((d) => d.date))).sort()
    const avgSaved = dates.map((date) => {
      const items = data.efficiencyTrend.filter((d) => d.date === date)
      if (!items.length) return 0
      return items.reduce((s, i) => s + (i.timeSavedRate || 0), 0) / items.length
    })
    return {
      tooltip: { trigger: 'axis', formatter: '{b}<br/>时间节省率: {c}%' },
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      xAxis: {
        type: 'category',
        data: dates.map((d) => dayjs(d).format('MM-DD')),
        boundaryGap: false,
      },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        {
          name: '时间节省率',
          type: 'line',
          smooth: true,
          data: avgSaved,
          symbolSize: 6,
          areaStyle: { opacity: 0.15 },
          itemStyle: { color: '#52c41a' },
          lineStyle: { color: '#52c41a' },
        },
      ],
    }
  }, [data])

  const accuracyOption = useMemo(() => {
    if (!data?.accuracyTrend?.length) return null
    const dates = Array.from(new Set(data.accuracyTrend.map((d) => d.date))).sort()
    const types = Array.from(new Set(data.accuracyTrend.map((d) => d.entityType).filter(Boolean))).slice(0, 6)
    const series = types.map((type) => ({
      name: EntityTypeLabelMap[type as keyof typeof EntityTypeLabelMap]?.label || type,
      type: 'line',
      smooth: true,
      symbolSize: 5,
      data: dates.map((date) => {
        const found = data.accuracyTrend.find((d) => d.date === date && d.entityType === type)
        return found ? found.accuracyRate : null
      }),
    }))
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: series.map((s) => s.name), top: 0 },
      grid: { left: 40, right: 20, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        data: dates.map((d) => dayjs(d).format('MM-DD')),
        boundaryGap: false,
      },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series,
    }
  }, [data])

  const departmentBarOption = useMemo(() => {
    if (!data?.departmentStats?.length) return null
    const sorted = [...data.departmentStats].sort((a, b) => b.totalRecords - a.totalRecords).slice(0, 10)
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['覆盖率', '时间节省率'], top: 0 },
      grid: { left: 100, right: 40, top: 40, bottom: 30 },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', data: sorted.map((d) => d.department) },
      series: [
        {
          name: '覆盖率',
          type: 'bar',
          data: sorted.map((d) => d.coverageRate),
          itemStyle: { color: '#1677ff' },
        },
        {
          name: '时间节省率',
          type: 'bar',
          data: sorted.map((d) => d.avgTimeSavedRate || 0),
          itemStyle: { color: '#52c41a' },
        },
      ],
    }
  }, [data])

  const wordCloudOption = useMemo(() => {
    if (!data?.surgeryWordCloud?.length) return null
    const list = data.surgeryWordCloud.filter((w) => w.name && w.value > 0).slice(0, 60)
    if (!list.length) return null
    const maxValue = Math.max(...list.map((w) => w.value))
    const colors = ['#1677ff', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2', '#faad14']
    return {
      tooltip: { show: true },
      series: [
        {
          type: 'graph',
          layout: 'none',
          roam: false,
          label: {
            show: true,
            formatter: '{b}',
            position: 'inside',
          },
          data: list.map((w, i) => {
            const size = 12 + (w.value / maxValue) * 28
            return {
              name: w.name,
              value: w.value,
              symbolSize: size,
              x: (i % 8) * 80 + 50 + Math.random() * 20,
              y: Math.floor(i / 8) * 50 + 40,
              itemStyle: { color: colors[i % colors.length] },
              label: { fontSize: Math.max(10, size * 0.5) },
            }
          }),
        },
      ],
    }
  }, [data])

  const lowConfidenceOption = useMemo(() => {
    if (!data?.lowConfidenceDistribution?.length) return null
    const sorted = [...data.lowConfidenceDistribution].sort((a, b) => b.count - a.count).slice(0, 10)
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const p = params[0]
          const item = sorted[p.dataIndex]
          return `${item.entityLabel || p.name}<br/>低置信度数量: ${p.value}<br/>平均置信度: ${(item.avgConfidence * 100).toFixed(1)}%`
        },
      },
      grid: { left: 120, right: 40, top: 20, bottom: 30 },
      xAxis: { type: 'value', name: '低置信度实体数量' },
      yAxis: {
        type: 'category',
        data: sorted.map((d) => d.entityLabel || d.entityType),
      },
      series: [
        {
          type: 'bar',
          data: sorted.map((d) => d.count),
          itemStyle: {
            color: (params: any) => {
              const rate = sorted[params.dataIndex].avgConfidence
              if (rate < 0.3) return '#ff4d4f'
              if (rate < 0.5) return '#fa8c16'
              return '#faad14'
            },
          },
          label: {
            show: true,
            position: 'right',
            formatter: (params: any) => {
              const rate = sorted[params.dataIndex].avgConfidence
              return `置信度 ${(rate * 100).toFixed(0)}%`
            },
          },
        },
      ],
    }
  }, [data])

  const drillTableColumns = useMemo(() => {
    if (drillDimension === 'department') {
      return [
        { title: '科室', dataIndex: 'department', key: 'department', width: 150 },
        {
          title: '记录数',
          dataIndex: 'totalRecords',
          key: 'totalRecords',
          width: 100,
          sorter: (a: DepartmentStats, b: DepartmentStats) => a.totalRecords - b.totalRecords,
        },
        {
          title: '提取完成数',
          dataIndex: 'extractedRecords',
          key: 'extractedRecords',
          width: 110,
        },
        {
          title: '覆盖率',
          dataIndex: 'coverageRate',
          key: 'coverageRate',
          width: 180,
          render: (v: number) => (
            <Progress percent={v || 0} size="small" status={v >= 80 ? 'success' : v >= 60 ? 'normal' : 'exception'} />
          ),
          sorter: (a: DepartmentStats, b: DepartmentStats) => (a.coverageRate || 0) - (b.coverageRate || 0),
        },
        {
          title: '时间节省率',
          dataIndex: 'avgTimeSavedRate',
          key: 'avgTimeSavedRate',
          width: 180,
          render: (v: number) => (
            <Progress percent={v || 0} size="small" strokeColor="#52c41a" />
          ),
          sorter: (a: DepartmentStats, b: DepartmentStats) =>
            (a.avgTimeSavedRate || 0) - (b.avgTimeSavedRate || 0),
        },
      ]
    }
    if (drillDimension === 'surgeon') {
      return [
        { title: '手术医生', dataIndex: 'surgeon', key: 'surgeon', width: 150 },
        {
          title: '手术记录数',
          dataIndex: 'recordCount',
          key: 'recordCount',
          width: 120,
          sorter: (a: SurgeonStats, b: SurgeonStats) => a.recordCount - b.recordCount,
        },
        {
          title: '覆盖率',
          dataIndex: 'coverageRate',
          key: 'coverageRate',
          width: 180,
          render: (v: number) => (
            <Progress percent={v || 0} size="small" status={v >= 80 ? 'success' : v >= 60 ? 'normal' : 'exception'} />
          ),
        },
        {
          title: '时间节省率',
          dataIndex: 'avgTimeSavedRate',
          key: 'avgTimeSavedRate',
          width: 180,
          render: (v: number) => (
            <Progress percent={v || 0} size="small" strokeColor="#52c41a" />
          ),
          sorter: (a: SurgeonStats, b: SurgeonStats) =>
            (a.avgTimeSavedRate || 0) - (b.avgTimeSavedRate || 0),
        },
      ]
    }
    return [
      { title: '手术名称', dataIndex: 'surgeryName', key: 'surgeryName', ellipsis: true },
      {
        title: '手术记录数',
        dataIndex: 'recordCount',
        key: 'recordCount',
        width: 120,
        sorter: (a: SurgeryTypeStats, b: SurgeryTypeStats) => a.recordCount - b.recordCount,
      },
      {
        title: '覆盖率',
        dataIndex: 'coverageRate',
        key: 'coverageRate',
        width: 180,
        render: (v: number) => (
          <Progress percent={v || 0} size="small" status={v >= 80 ? 'success' : v >= 60 ? 'normal' : 'exception'} />
        ),
      },
    ]
  }, [drillDimension])

  const drillDataSource = useMemo(() => {
    if (!data) return []
    if (drillDimension === 'department') return data.departmentStats || []
    if (drillDimension === 'surgeon') return data.surgeonStats || []
    return data.surgeryTypeStats || []
  }, [data, drillDimension])

  return (
    <div className="page-container">
      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={16}>
          <Space>
            <Text strong>时间范围：</Text>
            <RangePicker
              value={dateRange}
              onChange={(v) => v && setDateRange(v as [Dayjs, Dayjs])}
              allowClear={false}
            />
          </Space>
          <Space>
            <Text strong>科室：</Text>
            <Select
              style={{ width: 180 }}
              placeholder="全部科室"
              allowClear
              value={selectedDepartment}
              onChange={setSelectedDepartment}
              options={departmentOptions}
            />
          </Space>
          <Tag color="blue" icon={<BarChartOutlined />}>
            质控统计仪表盘
          </Tag>
        </Space>
      </Card>

      <Spin spinning={loading}>
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="结构化提取覆盖率"
                value={data?.overview?.overallCoverageRate || 0}
                precision={2}
                suffix="%"
                prefix={<BarChartOutlined style={{ color: '#1677ff' }} />}
                valueStyle={{ color: '#1677ff' }}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
                {data?.overview?.extractedRecords || 0} / {data?.overview?.totalRecords || 0} 条记录已完成提取
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="平均填充时间节省率"
                value={data?.overview?.overallTimeSavedRate || 0}
                precision={2}
                suffix="%"
                prefix={<ThunderboltOutlined style={{ color: '#52c41a' }} />}
                valueStyle={{ color: '#52c41a' }}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
                对比人工录入的效率提升
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="字段识别准确率"
                value={data?.overview?.overallAccuracyRate || 0}
                precision={2}
                suffix="%"
                prefix={<SafetyCertificateOutlined style={{ color: '#722ed1' }} />}
                valueStyle={{ color: '#722ed1' }}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
                高置信度 + 已审核字段占比
              </div>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="覆盖科室 / 医生"
                value={data?.overview?.totalDepartments || 0}
                prefix={<TeamOutlined style={{ color: '#fa8c16' }} />}
                valueStyle={{ color: '#fa8c16' }}
                suffix={
                  <span style={{ fontSize: 14, color: '#8c8c8c' }}>
                    {' '}
                    / {data?.overview?.totalSurgeons || 0} 位医生
                  </span>
                }
              />
              <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
                系统已接入范围
              </div>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} lg={8}>
            <Card
              title={
                <Space>
                  <BarChartOutlined style={{ color: '#1677ff' }} />
                  <span>覆盖率趋势</span>
                </Space>
              }
              size="small"
            >
              {coverageOption ? (
                <ReactECharts option={coverageOption} style={{ height: 280 }} />
              ) : (
                <Empty description="暂无数据" style={{ padding: '60px 0' }} />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              title={
                <Space>
                  <ThunderboltOutlined style={{ color: '#52c41a' }} />
                  <span>时间节省率趋势</span>
                </Space>
              }
              size="small"
            >
              {efficiencyOption ? (
                <ReactECharts option={efficiencyOption} style={{ height: 280 }} />
              ) : (
                <Empty description="暂无数据" style={{ padding: '60px 0' }} />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              title={
                <Space>
                  <SafetyCertificateOutlined style={{ color: '#722ed1' }} />
                  <span>字段识别准确率趋势</span>
                </Space>
              }
              size="small"
            >
              {accuracyOption ? (
                <ReactECharts option={accuracyOption} style={{ height: 280 }} />
              ) : (
                <Empty description="暂无数据" style={{ padding: '60px 0' }} />
              )}
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} lg={14}>
            <Card
              title={
                <Space>
                  <CloudOutlined style={{ color: '#13c2c2' }} />
                  <span>热门手术词云</span>
                </Space>
              }
              size="small"
            >
              {wordCloudOption ? (
                <ReactECharts option={wordCloudOption} style={{ height: 320 }} />
              ) : (
                <Empty description="暂无手术数据" style={{ padding: '80px 0' }} />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card
              title={
                <Space>
                  <WarningOutlined style={{ color: '#ff4d4f' }} />
                  <span>异常识别 - 低置信度字段分布</span>
                </Space>
              }
              size="small"
              extra={
                <Tooltip title="置信度低于0.6的字段被视为异常，需人工重点审核">
                  <Tag color="red">阈值 0.6</Tag>
                </Tooltip>
              }
            >
              {lowConfidenceOption ? (
                <ReactECharts option={lowConfidenceOption} style={{ height: 320 }} />
              ) : (
                <Empty description="暂无异常数据" style={{ padding: '80px 0' }} />
              )}
            </Card>
          </Col>
        </Row>

        <Card
          title={
            <Space>
              <PieChartOutlined style={{ color: '#eb2f96' }} />
              <span>维度下钻分析</span>
              <Select
                value={drillDimension}
                onChange={setDrillDimension}
                style={{ width: 160 }}
                size="small"
              >
                <Option value="department">
                  <Space>
                    <ApartmentOutlined />
                    科室维度
                  </Space>
                </Option>
                <Option value="surgeon">
                  <Space>
                    <UserOutlined />
                    医生维度
                  </Space>
                </Option>
                <Option value="surgeryType">
                  <Space>
                    <AppstoreOutlined />
                    手术类型维度
                  </Space>
                </Option>
              </Select>
            </Space>
          }
          size="small"
        >
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <Table
                size="small"
                columns={drillTableColumns}
                dataSource={drillDataSource as any[]}
                rowKey={(record: any) => record.department || record.surgeon || record.surgeryName}
                pagination={{ pageSize: 8, showSizeChanger: false }}
                scroll={{ x: 600 }}
              />
            </Col>
            <Col xs={24} lg={10}>
              {departmentBarOption && drillDimension === 'department' && (
                <ReactECharts option={departmentBarOption} style={{ height: 360 }} />
              )}
              {drillDimension === 'surgeon' && data?.surgeonStats?.length && (
                <ReactECharts
                  option={{
                    tooltip: { trigger: 'axis' },
                    legend: { data: ['手术数', '时间节省率'], top: 0 },
                    grid: { left: 100, right: 40, top: 40, bottom: 30 },
                    xAxis: { type: 'value' },
                    yAxis: {
                      type: 'category',
                      data: [...data.surgeonStats]
                        .sort((a, b) => b.recordCount - a.recordCount)
                        .slice(0, 10)
                        .map((s) => s.surgeon),
                    },
                    series: [
                      {
                        name: '手术数',
                        type: 'bar',
                        data: [...data.surgeonStats]
                          .sort((a, b) => b.recordCount - a.recordCount)
                          .slice(0, 10)
                          .map((s) => s.recordCount),
                        itemStyle: { color: '#1677ff' },
                      },
                      {
                        name: '时间节省率(%)',
                        type: 'bar',
                        data: [...data.surgeonStats]
                          .sort((a, b) => b.recordCount - a.recordCount)
                          .slice(0, 10)
                          .map((s) => s.avgTimeSavedRate || 0),
                        itemStyle: { color: '#52c41a' },
                      },
                    ],
                  }}
                  style={{ height: 360 }}
                />
              )}
              {drillDimension === 'surgeryType' && data?.surgeryTypeStats?.length && (
                <ReactECharts
                  option={{
                    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
                    grid: { left: 140, right: 40, top: 20, bottom: 30 },
                    xAxis: { type: 'value' },
                    yAxis: {
                      type: 'category',
                      data: [...data.surgeryTypeStats]
                        .sort((a, b) => b.recordCount - a.recordCount)
                        .slice(0, 10)
                        .map((s) => s.surgeryName),
                    },
                    series: [
                      {
                        type: 'bar',
                        data: [...data.surgeryTypeStats]
                          .sort((a, b) => b.recordCount - a.recordCount)
                          .slice(0, 10)
                          .map((s) => ({
                            value: s.recordCount,
                            itemStyle: {
                              color:
                                (s.coverageRate || 0) >= 80
                                  ? '#52c41a'
                                  : (s.coverageRate || 0) >= 60
                                    ? '#1677ff'
                                    : '#ff4d4f',
                            },
                          })),
                        label: {
                          show: true,
                          position: 'right',
                          formatter: (p: any) =>
                            `覆盖 ${(
                              data.surgeryTypeStats!.sort((a, b) => b.recordCount - a.recordCount)[p.dataIndex]
                                .coverageRate || 0
                            ).toFixed(0)}%`,
                        },
                      },
                    ],
                  }}
                  style={{ height: 360 }}
                />
              )}
            </Col>
          </Row>
        </Card>
      </Spin>
    </div>
  )
}

export default AnalyticsDashboard
