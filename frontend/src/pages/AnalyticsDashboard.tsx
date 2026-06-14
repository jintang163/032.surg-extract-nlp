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
  Button,
  message,
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
  DownloadOutlined,
  ReloadOutlined,
  FilterOutlined,
} from '@ant-design/icons'
import ReactECharts, { EChartsOption } from 'echarts-for-react'
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
  const [exportLoading, setExportLoading] = useState(false)
  const [data, setData] = useState<AnalyticsDashboardData | null>(null)
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(30, 'day'),
    dayjs(),
  ])
  const [selectedDepartment, setSelectedDepartment] = useState<string | undefined>(undefined)
  const [selectedEntityType, setSelectedEntityType] = useState<string | undefined>(undefined)
  const [drillDimension, setDrillDimension] = useState<'department' | 'surgeon' | 'surgeryType'>('department')
  const [selectedDrillItem, setSelectedDrillItem] = useState<string | null>(null)

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
      setSelectedDrillItem(null)
    } catch (e) {
      console.error('加载统计数据失败', e)
      message.error('加载统计数据失败')
    } finally {
      setLoading(false)
    }
  }

  const handleExport = async () => {
    setExportLoading(true)
    try {
      const params: Record<string, any> = {}
      if (dateRange && dateRange[0]) params.startDate = dateRange[0].format('YYYY-MM-DD')
      if (dateRange && dateRange[1]) params.endDate = dateRange[1].format('YYYY-MM-DD')
      if (selectedDepartment) params.department = selectedDepartment
      const blob = await analyticsApi.exportDashboard(params)
      const url = URL.createObjectURL(blob as Blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `质控统计仪表盘报表_${dayjs().format('YYYYMMDD')}.xlsx`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch (e) {
      console.error('导出失败', e)
      message.error('导出失败')
    } finally {
      setExportLoading(false)
    }
  }

  const departmentOptions = useMemo(() => {
    if (!data?.departmentStats) return []
    return data.departmentStats.map((d) => ({ value: d.department, label: d.department }))
  }, [data])

  const filteredCoverageTrend = useMemo(() => {
    if (!data?.coverageTrend?.length) return []
    if (drillDimension === 'department' && selectedDrillItem) {
      return data.coverageTrend.filter((d) => d.department === selectedDrillItem)
    }
    return data.coverageTrend
  }, [data, drillDimension, selectedDrillItem])

  const coverageOption: EChartsOption = useMemo(() => {
    if (!filteredCoverageTrend?.length) return {}
    const dates = Array.from(new Set(filteredCoverageTrend.map((d) => d.date))).sort()
    const deptMap = new Map<string, number[]>()
    filteredCoverageTrend.forEach((item) => {
      const key = item.department || '全部'
      if (!deptMap.has(key)) deptMap.set(key, [])
    })
    dates.forEach((date) => {
      deptMap.forEach((arr, key) => {
        const found = filteredCoverageTrend.find(
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
      legend: { data: series.map((s) => s.name), top: 0, type: 'scroll' },
      grid: { left: 40, right: 20, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        data: dates.map((d) => dayjs(d).format('MM-DD')),
        boundaryGap: false,
      },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series,
    } as EChartsOption
  }, [filteredCoverageTrend])

  const filteredEfficiencyTrend = useMemo(() => {
    if (!data?.efficiencyTrend?.length) return []
    let list = data.efficiencyTrend
    if (drillDimension === 'department' && selectedDrillItem) {
      list = list.filter((d) => d.department === selectedDrillItem)
    }
    if (drillDimension === 'surgeon' && selectedDrillItem) {
      list = list.filter((d) => d.surgeon === selectedDrillItem)
    }
    return list
  }, [data, drillDimension, selectedDrillItem])

  const efficiencyOption: EChartsOption = useMemo(() => {
    if (!filteredEfficiencyTrend?.length) return {}
    const dates = Array.from(new Set(filteredEfficiencyTrend.map((d) => d.date))).sort()
    const avgSaved = dates.map((date) => {
      const items = filteredEfficiencyTrend.filter((d) => d.date === date)
      if (!items.length) return 0
      const totalManual = items.reduce((s, i) => s + (i.avgManualDuration || 0) * (i.recordCount || 1), 0)
      const totalActual = items.reduce((s, i) => s + (i.avgActualDuration || 0) * (i.recordCount || 1), 0)
      return totalManual > 0 ? ((totalManual - totalActual) / totalManual) * 100 : 0
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
    } as EChartsOption
  }, [filteredEfficiencyTrend])

  const filteredAccuracyTrend = useMemo(() => {
    if (!data?.accuracyTrend?.length) return []
    let list = data.accuracyTrend
    if (drillDimension === 'department' && selectedDrillItem) {
      list = list.filter((d) => d.department === selectedDrillItem)
    }
    if (selectedEntityType) {
      list = list.filter((d) => d.entityType === selectedEntityType)
    }
    return list
  }, [data, drillDimension, selectedDrillItem, selectedEntityType])

  const accuracyOption: EChartsOption = useMemo(() => {
    if (!filteredAccuracyTrend?.length) return {}
    const dates = Array.from(new Set(filteredAccuracyTrend.map((d) => d.date))).sort()
    const types = Array.from(new Set(filteredAccuracyTrend.map((d) => d.entityType).filter(Boolean))).slice(0, 6)
    const series = types.map((type) => ({
      name: EntityTypeLabelMap[type as keyof typeof EntityTypeLabelMap]?.label || type,
      type: 'line',
      smooth: true,
      symbolSize: 5,
      data: dates.map((date) => {
        const found = filteredAccuracyTrend.find((d) => d.date === date && d.entityType === type)
        return found ? found.accuracyRate : null
      }),
    }))
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: series.map((s) => s.name), top: 0, type: 'scroll' },
      grid: { left: 40, right: 20, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        data: dates.map((d) => dayjs(d).format('MM-DD')),
        boundaryGap: false,
      },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series,
    } as EChartsOption
  }, [filteredAccuracyTrend])

  const entityTypeOptions = useMemo(() => {
    if (!data?.accuracyTrend?.length) return []
    const types = Array.from(new Set(data.accuracyTrend.map((d) => d.entityType).filter(Boolean)))
    return types.map((t) => ({
      value: t,
      label: EntityTypeLabelMap[t as keyof typeof EntityTypeLabelMap]?.label || t,
    }))
  }, [data])

  const departmentBarOption: EChartsOption = useMemo(() => {
    if (!data?.departmentStats?.length) return {}
    const list = selectedDrillItem
      ? data.departmentStats.filter((d) => d.department === selectedDrillItem)
      : [...data.departmentStats].sort((a, b) => b.totalRecords - a.totalRecords).slice(0, 10)
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['覆盖率', '时间节省率', '准确率'], top: 0 },
      grid: { left: 100, right: 40, top: 40, bottom: 30 },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', data: list.map((d) => d.department) },
      series: [
        {
          name: '覆盖率',
          type: 'bar',
          data: list.map((d) => d.coverageRate),
          itemStyle: { color: '#1677ff' },
        },
        {
          name: '时间节省率',
          type: 'bar',
          data: list.map((d) => d.avgTimeSavedRate || 0),
          itemStyle: { color: '#52c41a' },
        },
        {
          name: '准确率',
          type: 'bar',
          data: list.map((d) => d.avgAccuracyRate || 0),
          itemStyle: { color: '#722ed1' },
        },
      ],
    } as EChartsOption
  }, [data, selectedDrillItem])

  const wordCloudOption: EChartsOption = useMemo(() => {
    if (!data?.surgeryWordCloud?.length) return {}
    const list = data.surgeryWordCloud.filter((w) => w.name && w.value > 0).slice(0, 60)
    if (!list.length) return {}
    const maxValue = Math.max(...list.map((w) => w.value))
    const colors = ['#1677ff', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2', '#faad14']
    return {
      tooltip: { show: true, formatter: (p: any) => `${p.name}: ${p.value}例` },
      series: [
        {
          type: 'graph',
          layout: 'none',
          roam: false,
          label: {
            show: true,
            formatter: '{b}',
            position: 'inside',
            color: '#fff',
            fontSize: 12,
          },
          data: list.map((w, i) => {
            const size = 14 + (w.value / maxValue) * 32
            return {
              name: w.name,
              value: w.value,
              symbolSize: size,
              x: (i % 8) * 80 + 50 + Math.random() * 15,
              y: Math.floor(i / 8) * 50 + 40,
              itemStyle: { color: colors[i % colors.length] },
              label: { fontSize: Math.max(10, size * 0.45) },
            }
          }),
        },
      ],
    } as EChartsOption
  }, [data])

  const lowConfidenceOption: EChartsOption = useMemo(() => {
    if (!data?.lowConfidenceDistribution?.length) return {}
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
    } as EChartsOption
  }, [data])

  const handleDrillRowClick = (record: any) => {
    const key = record.department || record.surgeon || record.surgeryName
    if (selectedDrillItem === key) {
      setSelectedDrillItem(null)
    } else {
      setSelectedDrillItem(key)
    }
  }

  const drillTableColumns = useMemo(() => {
    if (drillDimension === 'department') {
      return [
        {
          title: '科室',
          dataIndex: 'department',
          key: 'department',
          width: 150,
          render: (v: string) => (
            <Tag color={selectedDrillItem === v ? 'blue' : 'default'}>
              <ApartmentOutlined /> {v}
            </Tag>
          ),
        },
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
        {
          title: '准确率',
          dataIndex: 'avgAccuracyRate',
          key: 'avgAccuracyRate',
          width: 180,
          render: (v: number) => (
            <Progress percent={v || 0} size="small" strokeColor="#722ed1" />
          ),
          sorter: (a: DepartmentStats, b: DepartmentStats) =>
            (a.avgAccuracyRate || 0) - (b.avgAccuracyRate || 0),
        },
      ]
    }
    if (drillDimension === 'surgeon') {
      return [
        {
          title: '手术医生',
          dataIndex: 'surgeon',
          key: 'surgeon',
          width: 150,
          render: (v: string) => (
            <Tag color={selectedDrillItem === v ? 'blue' : 'default'}>
              <UserOutlined /> {v}
            </Tag>
          ),
        },
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
      {
        title: '手术名称',
        dataIndex: 'surgeryName',
        key: 'surgeryName',
        ellipsis: true,
        render: (v: string) => (
          <Tag color={selectedDrillItem === v ? 'blue' : 'default'}>
            <AppstoreOutlined /> {v}
          </Tag>
        ),
      },
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
      {
        title: '准确率',
        dataIndex: 'avgAccuracyRate',
        key: 'avgAccuracyRate',
        width: 180,
        render: (v: number) => (
          <Progress percent={v || 0} size="small" strokeColor="#722ed1" />
        ),
        sorter: (a: SurgeryTypeStats, b: SurgeryTypeStats) =>
          (a.avgAccuracyRate || 0) - (b.avgAccuracyRate || 0),
      },
    ]
  }, [drillDimension, selectedDrillItem])

  const drillDataSource = useMemo(() => {
    if (!data) return []
    if (drillDimension === 'department') return data.departmentStats || []
    if (drillDimension === 'surgeon') return data.surgeonStats || []
    return data.surgeryTypeStats || []
  }, [data, drillDimension])

  const drillSideChart = useMemo(() => {
    if (!data) return null

    if (drillDimension === 'department') {
      return departmentBarOption
    }

    if (drillDimension === 'surgeon') {
      if (!data.surgeonStats?.length) return null
      const list = selectedDrillItem
        ? data.surgeonStats.filter((s) => s.surgeon === selectedDrillItem)
        : [...data.surgeonStats].sort((a, b) => b.recordCount - a.recordCount).slice(0, 10)
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['手术数', '时间节省率(%)'], top: 0 },
        grid: { left: 100, right: 40, top: 40, bottom: 30 },
        xAxis: { type: 'value' },
        yAxis: {
          type: 'category',
          data: list.map((s) => s.surgeon),
        },
        series: [
          {
            name: '手术数',
            type: 'bar',
            data: list.map((s) => s.recordCount),
            itemStyle: { color: '#1677ff' },
          },
          {
            name: '时间节省率(%)',
            type: 'bar',
            data: list.map((s) => s.avgTimeSavedRate || 0),
            itemStyle: { color: '#52c41a' },
          },
        ],
      } as EChartsOption
    }

    if (drillDimension === 'surgeryType') {
      if (!data.surgeryTypeStats?.length) return null
      const list = selectedDrillItem
        ? data.surgeryTypeStats.filter((s) => s.surgeryName === selectedDrillItem)
        : [...data.surgeryTypeStats].sort((a, b) => b.recordCount - a.recordCount).slice(0, 10)
      return {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { data: ['手术数', '覆盖率(%)', '准确率(%)'], top: 0 },
        grid: { left: 140, right: 40, top: 40, bottom: 30 },
        xAxis: { type: 'value' },
        yAxis: {
          type: 'category',
          data: list.map((s) => s.surgeryName),
        },
        series: [
          {
            name: '手术数',
            type: 'bar',
            data: list.map((s) => s.recordCount),
            itemStyle: { color: '#1677ff' },
          },
          {
            name: '覆盖率(%)',
            type: 'bar',
            data: list.map((s) => s.coverageRate || 0),
            itemStyle: { color: '#52c41a' },
          },
          {
            name: '准确率(%)',
            type: 'bar',
            data: list.map((s) => s.avgAccuracyRate || 0),
            itemStyle: { color: '#722ed1' },
          },
        ],
      } as EChartsOption
    }

    return null
  }, [data, drillDimension, selectedDrillItem, departmentBarOption])

  return (
    <div className="page-container">
      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={16} style={{ width: '100%', justifyContent: 'space-between' }}>
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
            <Space>
              <Text strong>字段类型：</Text>
              <Select
                style={{ width: 180 }}
                placeholder="全部字段"
                allowClear
                value={selectedEntityType}
                onChange={setSelectedEntityType}
                options={entityTypeOptions}
              />
            </Space>
            <Tag color="blue" icon={<BarChartOutlined />}>
              质控统计仪表盘
            </Tag>
          </Space>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadData}>
              刷新
            </Button>
            <Button type="primary" icon={<DownloadOutlined />} loading={exportLoading} onClick={handleExport}>
              导出报表
            </Button>
          </Space>
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
              {coverageOption && Object.keys(coverageOption).length ? (
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
              {efficiencyOption && Object.keys(efficiencyOption).length ? (
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
              {accuracyOption && Object.keys(accuracyOption).length ? (
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
              {wordCloudOption && Object.keys(wordCloudOption).length ? (
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
              {lowConfidenceOption && Object.keys(lowConfidenceOption).length ? (
                <ReactECharts option={lowConfidenceOption} style={{ height: 320 }} />
              ) : (
                <Empty description="暂无异常数据" style={{ padding: '80px 0' }} />
              )}
            </Card>
          </Col>
        </Row>

        <Card
          title={
            <Space wrap>
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
              {selectedDrillItem && (
                <Tag closable onClose={() => setSelectedDrillItem(null)} color="blue">
                  <FilterOutlined /> 已选中: {selectedDrillItem}
                </Tag>
              )}
            </Space>
          }
          size="small"
          extra={
            <Text type="secondary" style={{ fontSize: 12 }}>
              点击表格行可联动筛选右侧图表
            </Text>
          }
        >
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <Table
                size="small"
                columns={drillTableColumns}
                dataSource={drillDataSource as any[]}
                rowKey={(record: any) => record.department || record.surgeon || record.surgeryName}
                pagination={{ pageSize: 8, showSizeChanger: false }}
                scroll={{ x: 700 }}
                onRow={(record) => ({
                  onClick: () => handleDrillRowClick(record),
                  style: {
                    cursor: 'pointer',
                    backgroundColor:
                      selectedDrillItem === (record.department || record.surgeon || record.surgeryName)
                        ? '#e6f4ff'
                        : undefined,
                  },
                })}
              />
            </Col>
            <Col xs={24} lg={10}>
              {drillSideChart && Object.keys(drillSideChart).length ? (
                <ReactECharts option={drillSideChart} style={{ height: 360 }} />
              ) : (
                <Empty description="暂无数据" style={{ padding: '100px 0' }} />
              )}
            </Col>
          </Row>
        </Card>
      </Spin>
    </div>
  )
}

export default AnalyticsDashboard
