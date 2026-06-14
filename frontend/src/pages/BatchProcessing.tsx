import React, { useEffect, useMemo, useState } from 'react'
import {
  Row,
  Col,
  Card,
  Table,
  Button,
  Upload,
  Form,
  Input,
  Select,
  InputNumber,
  Modal,
  Progress,
  Tag,
  Space,
  Typography,
  message,
  Popconfirm,
  DatePicker,
  Drawer,
  Descriptions,
  Tooltip,
  Empty,
  Spin,
  Alert,
  Statistic,
} from 'antd'
import {
  UploadOutlined,
  InboxOutlined,
  RetweetOutlined,
  DeleteOutlined,
  FileTextOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileImageOutlined,
  FileOutlined,
  HomeOutlined,
  HistoryOutlined,
} from '@ant-design/icons'
import type { UploadFile, UploadProps } from 'antd/es/upload/interface'
import dayjs, { Dayjs } from 'dayjs'
import { batchTaskApi, recordApi } from '@/services/api'
import type {
  BatchTask,
  BatchTaskItem,
  BatchTaskStatus,
  BatchItemStatus,
  NotifyType,
  FileType,
} from '@/types'
import {
  BatchTaskStatusMap,
  BatchItemStatusMap,
  NotifyTypeMap,
  FileTypeMap,
} from '@/types'

const { Text, Title } = Typography
const { Dragger } = Upload
const { RangePicker } = DatePicker
const { Option } = Select
const { TextArea } = Input

const getFileIcon = (fileType: FileType) => {
  switch (fileType) {
    case 'TEXT':
      return <FileTextOutlined />
    case 'WORD':
      return <FileWordOutlined />
    case 'PDF':
      return <FilePdfOutlined />
    case 'IMAGE':
      return <FileImageOutlined />
    default:
      return <FileOutlined />
  }
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(1)} ${units[unitIndex]}`
}

const formatDuration = (start?: string, end?: string) => {
  if (!start || !end) return '-'
  const diff = dayjs(end).diff(dayjs(start), 'second')
  if (diff < 60) return `${diff} 秒`
  if (diff < 3600) return `${Math.floor(diff / 60)} 分 ${diff % 60} 秒`
  return `${Math.floor(diff / 3600)} 时 ${Math.floor((diff % 3600) / 60)} 分`
}

const BatchProcessing: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [tasks, setTasks] = useState<BatchTask[]>([])
  const [total, setTotal] = useState(0)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 })
  const [filters, setFilters] = useState({
    status: undefined as string | undefined,
    department: undefined as string | undefined,
    dateRange: undefined as [Dayjs, Dayjs] | undefined,
  })
  const [selectedTask, setSelectedTask] = useState<BatchTask | null>(null)
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false)
  const [taskItems, setTaskItems] = useState<BatchTaskItem[]>([])
  const [itemsLoading, setItemsLoading] = useState(false)
  const [itemsPagination, setItemsPagination] = useState({ current: 1, pageSize: 20 })
  const [itemsTotal, setItemsTotal] = useState(0)
  const [itemStatusFilter, setItemStatusFilter] = useState<string | undefined>(undefined)
  const [uploadModalVisible, setUploadModalVisible] = useState(false)
  const [uploadForm] = Form.useForm()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [refreshTimer, setRefreshTimer] = useState<number | null>(null)
  const [viewRecordVisible, setViewRecordVisible] = useState(false)
  const [selectedRecordId, setSelectedRecordId] = useState<number | null>(null)
  const [recordDetail, setRecordDetail] = useState<any>(null)
  const [recordLoading, setRecordLoading] = useState(false)

  useEffect(() => {
    loadTasks()
    startAutoRefresh()
    return () => {
      if (refreshTimer) {
        clearInterval(refreshTimer)
      }
    }
  }, [pagination.current, pagination.pageSize, filters])

  const startAutoRefresh = () => {
    if (refreshTimer) {
      clearInterval(refreshTimer)
    }
    const timer = window.setInterval(() => {
      const hasProcessingTask = tasks.some((t) => t.status === 'PROCESSING' || t.status === 'PENDING')
      if (hasProcessingTask) {
        loadTasks(false)
      }
    }, 3000)
    setRefreshTimer(timer)
  }

  const loadTasks = async (showLoading = true) => {
    if (showLoading) setLoading(true)
    try {
      const params: Record<string, any> = {
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
      }
      if (filters.status) params.status = filters.status
      if (filters.department) params.department = filters.department
      if (filters.dateRange && filters.dateRange[0]) {
        params.startDate = filters.dateRange[0].format('YYYY-MM-DD')
      }
      if (filters.dateRange && filters.dateRange[1]) {
        params.endDate = filters.dateRange[1].format('YYYY-MM-DD')
      }
      const result = await batchTaskApi.list(params)
      setTasks(result.records || [])
      setTotal(result.total || 0)
    } catch (e) {
      console.error('加载任务列表失败', e)
      message.error('加载任务列表失败')
    } finally {
      if (showLoading) setLoading(false)
    }
  }

  const loadTaskDetail = async (taskId: number) => {
    try {
      const result = await batchTaskApi.detail(taskId)
      setSelectedTask(result)
      return result
    } catch (e) {
      console.error('加载任务详情失败', e)
      message.error('加载任务详情失败')
      return null
    }
  }

  const loadTaskItems = async (taskId: number, showLoading = true) => {
    if (showLoading) setItemsLoading(true)
    try {
      const params: Record<string, any> = {
        pageNum: itemsPagination.current,
        pageSize: itemsPagination.pageSize,
      }
      if (itemStatusFilter) params.status = itemStatusFilter
      const result = await batchTaskApi.getItems(taskId, params)
      setTaskItems(result.records || [])
      setItemsTotal(result.total || 0)
    } catch (e) {
      console.error('加载任务文件列表失败', e)
      message.error('加载任务文件列表失败')
    } finally {
      if (showLoading) setItemsLoading(false)
    }
  }

  const handleUpload = async () => {
    if (!selectedFile) {
      message.warning('请选择ZIP文件')
      return
    }

    try {
      const values = await uploadForm.validateFields()
      setUploading(true)
      setUploadProgress(0)

      const result = await batchTaskApi.create(
        selectedFile,
        {
          taskName: values.taskName,
          department: values.department,
          notifyTarget: values.notifyTarget,
          maxRetryCount: values.maxRetryCount,
        },
        (progress) => setUploadProgress(progress)
      )

      message.success('批量任务创建成功，正在后台处理')
      setUploadModalVisible(false)
      setSelectedFile(null)
      setFileList([])
      uploadForm.resetFields()
      loadTasks()
    } catch (e) {
      console.error('创建批量任务失败', e)
    } finally {
      setUploading(false)
      setUploadProgress(0)
    }
  }

  const handleRetry = async (taskId: number) => {
    try {
      await batchTaskApi.retry(taskId)
      message.success('重试任务已启动')
      loadTasks()
      if (selectedTask?.id === taskId) {
        loadTaskDetail(taskId)
      }
    } catch (e) {
      console.error('重试任务失败', e)
    }
  }

  const handleBatchFillHome = async (taskId: number) => {
    try {
      const result = await batchTaskApi.fillHomePages(taskId)
      message.success(result)
      loadTasks()
    } catch (e) {
      console.error('批量填充失败', e)
    }
  }

  const handleDelete = async (taskId: number) => {
    try {
      await batchTaskApi.delete(taskId)
      message.success('删除成功')
      loadTasks()
      if (selectedTask?.id === taskId) {
        setDetailDrawerVisible(false)
        setSelectedTask(null)
      }
    } catch (e) {
      console.error('删除任务失败', e)
    }
  }

  const handleViewRecord = async (recordId: number) => {
    setSelectedRecordId(recordId)
    setViewRecordVisible(true)
    setRecordLoading(true)
    try {
      const result = await recordApi.detail(recordId)
      setRecordDetail(result)
    } catch (e) {
      console.error('加载记录详情失败', e)
      message.error('加载记录详情失败')
    } finally {
      setRecordLoading(false)
    }
  }

  const openDetailDrawer = async (task: BatchTask) => {
    setSelectedTask(task)
    setDetailDrawerVisible(true)
    setItemsPagination({ current: 1, pageSize: 20 })
    setItemStatusFilter(undefined)
    await loadTaskDetail(task.id)
    await loadTaskItems(task.id)
  }

  const uploadProps: UploadProps = {
    fileList,
    multiple: false,
    accept: '.zip',
    beforeUpload: (file) => {
      if (!file.name.toLowerCase().endsWith('.zip')) {
        message.error('只能上传ZIP格式的压缩包')
        return Upload.LIST_IGNORE
      }
      if (file.size > 500 * 1024 * 1024) {
        message.error('文件大小不能超过500MB')
        return Upload.LIST_IGNORE
      }
      setSelectedFile(file)
      setFileList([file])
      return false
    },
    onRemove: () => {
      setSelectedFile(null)
      setFileList([])
    },
  }

  const taskColumns = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      key: 'taskName',
      width: 200,
      render: (text: string, record: BatchTask) => (
        <Space>
          <Text strong>{text}</Text>
          {record.retryCount > 0 && (
            <Tag color="orange" icon={<RetweetOutlined />}>
              重试 {record.retryCount}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: BatchTaskStatus) => (
        <Tag color={BatchTaskStatusMap[status].color}>
          {BatchTaskStatusMap[status].label}
        </Tag>
      ),
    },
    {
      title: '处理进度',
      key: 'progress',
      width: 250,
      render: (_: any, record: BatchTask) => (
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Progress
            percent={record.progress}
            status={
              record.status === 'FAILED'
                ? 'exception'
                : record.status === 'COMPLETED'
                  ? 'success'
                  : record.status === 'PARTIAL'
                    ? 'normal'
                    : 'active'
            }
            strokeColor={{
              '0%': '#1677ff',
              '100%': record.status === 'PARTIAL' ? '#faad14' : '#52c41a',
            }}
          />
          <Text type="secondary" style={{ fontSize: 12 }}>
            成功 {record.successCount} / 失败 {record.failedCount} / 待处理{' '}
            {record.pendingCount} / 共 {record.totalCount}
          </Text>
        </Space>
      ),
    },
    {
      title: '文件信息',
      key: 'fileInfo',
      width: 180,
      render: (_: any, record: BatchTask) => (
        <Space direction="vertical" size="small">
          <Text ellipsis style={{ maxWidth: 160 }} title={record.originalFileName}>
            {record.originalFileName}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {formatFileSize(record.fileSize)}
          </Text>
        </Space>
      ),
    },
    {
      title: '通知方式',
      dataIndex: 'notifyType',
      key: 'notifyType',
      width: 100,
      render: (type: NotifyType) => NotifyTypeMap[type]?.label || type,
    },
    {
      title: '创建人',
      dataIndex: 'createdByName',
      key: 'createdByName',
      width: 100,
    },
    {
      title: '创建时间',
      dataIndex: 'createdTime',
      key: 'createdTime',
      width: 160,
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      fixed: 'right',
      render: (_: any, record: BatchTask) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openDetailDrawer(record)}
          >
            查看
          </Button>
          {record.failedCount > 0 && (
            <Button
              type="link"
              size="small"
              icon={<RetweetOutlined />}
              onClick={() => handleRetry(record.id)}
              disabled={record.status === 'PROCESSING'}
            >
              重试失败
            </Button>
          )}
          {record.successCount > 0 && (
            <Button
              type="link"
              size="small"
              icon={<HomeOutlined />}
              onClick={() => handleBatchFillHome(record.id)}
              disabled={record.status === 'PROCESSING'}
            >
              批量填充
            </Button>
          )}
          <Popconfirm
            title="确定要删除这个任务吗？"
            description="删除后无法恢复"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              disabled={record.status === 'PROCESSING'}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const itemColumns = [
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      width: 200,
      render: (text: string, record: BatchTaskItem) => (
        <Space>
          {getFileIcon(record.fileType)}
          <Text>{text}</Text>
        </Space>
      ),
    },
    {
      title: '患者姓名',
      dataIndex: 'patientName',
      key: 'patientName',
      width: 100,
    },
    {
      title: '住院号',
      dataIndex: 'hospitalNo',
      key: 'hospitalNo',
      width: 120,
    },
    {
      title: '文件类型',
      dataIndex: 'fileType',
      key: 'fileType',
      width: 100,
      render: (type: FileType) => (
        <Tag>{FileTypeMap[type]?.label || type}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: BatchItemStatus) => (
        <Tag color={BatchItemStatusMap[status].color}>
          {BatchItemStatusMap[status].label}
        </Tag>
      ),
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      key: 'retryCount',
      width: 80,
      render: (count: number) => (count > 0 ? count : '-'),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      width: 200,
      ellipsis: true,
      render: (text: string) =>
        text ? (
          <Tooltip title={text}>
            <Text type="danger" style={{ fontSize: 12 }}>
              {text}
            </Text>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '处理时间',
      key: 'duration',
      width: 120,
      render: (_: any, record: BatchTaskItem) =>
        formatDuration(record.startTime, record.endTime),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_: any, record: BatchTaskItem) => (
        <Space>
          {record.recordId && (
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewRecord(record.recordId!)}
            >
              查看结果
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const stats = useMemo(() => {
    const allTasks = tasks
    return {
      total: total,
      processing: allTasks.filter((t) => t.status === 'PROCESSING').length,
      completed: allTasks.filter((t) => t.status === 'COMPLETED').length,
      failed: allTasks.filter((t) => t.status === 'FAILED').length,
    }
  }, [tasks, total])

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <Title level={3} style={{ margin: 0 }}>
              批量处理与任务队列
            </Title>
            <Text type="secondary">
              打包上传多份手术记录，系统异步处理，完成后通知您
            </Text>
          </div>
          <Button
            type="primary"
            size="large"
            icon={<UploadOutlined />}
            onClick={() => setUploadModalVisible(true)}
          >
            上传批量任务
          </Button>
        </div>

        <Row gutter={16}>
          <Col span={6}>
            <Card>
              <Statistic
                title="任务总数"
                value={stats.total}
                prefix={<HistoryOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="处理中"
                value={stats.processing}
                valueStyle={{ color: '#1677ff' }}
                prefix={<SyncOutlined spin={stats.processing > 0} />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="已完成"
                value={stats.completed}
                valueStyle={{ color: '#52c41a' }}
                prefix={<CheckCircleOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title="失败"
                value={stats.failed}
                valueStyle={{ color: '#ff4d4f' }}
                prefix={<CloseCircleOutlined />}
              />
            </Card>
          </Col>
        </Row>

        <Card>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space wrap>
              <Select
                placeholder="任务状态"
                style={{ width: 150 }}
                allowClear
                value={filters.status}
                onChange={(v) => setFilters({ ...filters, status: v })}
              >
                <Option value="PENDING">等待中</Option>
                <Option value="PROCESSING">处理中</Option>
                <Option value="COMPLETED">已完成</Option>
                <Option value="PARTIAL">部分完成</Option>
                <Option value="FAILED">全部失败</Option>
              </Select>
              <Input
                placeholder="科室"
                style={{ width: 150 }}
                allowClear
                value={filters.department}
                onChange={(e) => setFilters({ ...filters, department: e.target.value })}
              />
              <RangePicker
                value={filters.dateRange}
                onChange={(dates) =>
                  setFilters({ ...filters, dateRange: dates as [Dayjs, Dayjs] })
                }
              />
              <Button icon={<SyncOutlined />} onClick={() => loadTasks()}>
                刷新
              </Button>
            </Space>

            <Table
              rowKey="id"
              loading={loading}
              columns={taskColumns}
              dataSource={tasks}
              pagination={{
                ...pagination,
                total,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条`,
                onChange: (page, pageSize) =>
                  setPagination({ current: page, pageSize }),
              }}
              scroll={{ x: 1200 }}
              locale={{
                emptyText: <Empty description="暂无批量任务" />,
              }}
            />
          </Space>
        </Card>
      </Space>

      <Modal
        title="上传批量任务"
        open={uploadModalVisible}
        onCancel={() => {
          setUploadModalVisible(false)
          setSelectedFile(null)
          setFileList([])
          uploadForm.resetFields()
        }}
        footer={null}
        width={600}
        destroyOnClose
      >
        <Form form={uploadForm} layout="vertical">
          <Alert
            message="支持的文件格式"
            description="ZIP 压缩包内可包含：.txt, .doc, .docx, .pdf, .png, .jpg, .jpeg, .gif, .bmp, .tiff, .tif 格式的手术记录文件"
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />

          <Form.Item
            label="上传ZIP压缩包"
            name="file"
            rules={[{ required: true, message: '请上传ZIP文件' }]}
          >
            <Dragger {...uploadProps} maxCount={1}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽ZIP文件到此区域上传</p>
              <p className="ant-upload-hint">
                支持单个ZIP文件，大小不超过500MB
              </p>
            </Dragger>
          </Form.Item>

          {uploading && (
            <Progress percent={uploadProgress} status="active" style={{ marginBottom: 16 }} />
          )}

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="任务名称" name="taskName">
                <Input placeholder="自动生成，可选填" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="科室" name="department">
                <Input placeholder="可选，用于标识来源科室" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="通知邮箱"
                name="notifyTarget"
                rules={[{ required: true, message: '请输入通知邮箱' }, { type: 'email', message: '请输入有效的邮箱地址' }]}
              >
                <Input placeholder="任务完成后将通知到此邮箱" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="最大重试次数" name="maxRetryCount" initialValue={3}>
            <InputNumber min={0} max={10} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button
                onClick={() => {
                  setUploadModalVisible(false)
                  setSelectedFile(null)
                  setFileList([])
                  uploadForm.resetFields()
                }}
              >
                取消
              </Button>
              <Button
                type="primary"
                onClick={handleUpload}
                loading={uploading}
                disabled={!selectedFile}
              >
                开始处理
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="任务详情"
        placement="right"
        width={900}
        open={detailDrawerVisible}
        onClose={() => {
          setDetailDrawerVisible(false)
          setSelectedTask(null)
        }}
        extra={
          selectedTask && (
            <Space>
              {selectedTask.failedCount > 0 && (
                <Button
                  icon={<RetweetOutlined />}
                  onClick={() => handleRetry(selectedTask.id)}
                  disabled={selectedTask.status === 'PROCESSING'}
                >
                  重试失败项
                </Button>
              )}
              {selectedTask.successCount > 0 && (
                <Button
                  type="primary"
                  icon={<HomeOutlined />}
                  onClick={() => handleBatchFillHome(selectedTask.id)}
                  disabled={selectedTask.status === 'PROCESSING'}
                >
                  批量填充病案首页
                </Button>
              )}
            </Space>
          )
        }
      >
        {selectedTask && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card size="small">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="任务名称">
                  {selectedTask.taskName}
                </Descriptions.Item>
                <Descriptions.Item label="任务状态">
                  <Tag color={BatchTaskStatusMap[selectedTask.status].color}>
                    {BatchTaskStatusMap[selectedTask.status].label}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="原始文件">
                  {selectedTask.originalFileName}
                </Descriptions.Item>
                <Descriptions.Item label="文件大小">
                  {formatFileSize(selectedTask.fileSize)}
                </Descriptions.Item>
                <Descriptions.Item label="科室">
                  {selectedTask.department || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="通知方式">
                  {NotifyTypeMap[selectedTask.notifyType]?.label}
                </Descriptions.Item>
                <Descriptions.Item label="创建人">
                  {selectedTask.createdByName}
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">
                  {dayjs(selectedTask.createdTime).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
                <Descriptions.Item label="开始时间">
                  {selectedTask.startTime
                    ? dayjs(selectedTask.startTime).format('YYYY-MM-DD HH:mm:ss')
                    : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="结束时间">
                  {selectedTask.endTime
                    ? dayjs(selectedTask.endTime).format('YYYY-MM-DD HH:mm:ss')
                    : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="处理耗时" span={2}>
                  {formatDuration(selectedTask.startTime, selectedTask.endTime)}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Card size="small" title="处理统计">
              <Row gutter={16}>
                <Col span={6}>
                  <Statistic title="总文件数" value={selectedTask.totalCount} />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="成功"
                    value={selectedTask.successCount}
                    valueStyle={{ color: '#52c41a' }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="失败"
                    value={selectedTask.failedCount}
                    valueStyle={{ color: '#ff4d4f' }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="待处理"
                    value={selectedTask.pendingCount}
                    valueStyle={{ color: '#faad14' }}
                  />
                </Col>
              </Row>
              <Progress
                percent={selectedTask.progress}
                style={{ marginTop: 16 }}
                status={
                  selectedTask.status === 'FAILED'
                    ? 'exception'
                    : selectedTask.status === 'COMPLETED'
                      ? 'success'
                      : selectedTask.status === 'PARTIAL'
                        ? 'normal'
                        : 'active'
                }
                strokeColor={{
                  '0%': '#1677ff',
                  '100%': selectedTask.status === 'PARTIAL' ? '#faad14' : '#52c41a',
                }}
              />
            </Card>

            <Card
              size="small"
              title="文件处理列表"
              extra={
                <Select
                  placeholder="筛选状态"
                  style={{ width: 120 }}
                  allowClear
                  value={itemStatusFilter}
                  onChange={(v) => {
                    setItemStatusFilter(v)
                    setItemsPagination({ ...itemsPagination, current: 1 })
                    loadTaskItems(selectedTask.id)
                  }}
                >
                  <Option value="PENDING">待处理</Option>
                  <Option value="PROCESSING">处理中</Option>
                  <Option value="SUCCESS">成功</Option>
                  <Option value="FAILED">失败</Option>
                </Select>
              }
            >
              <Table
                rowKey="id"
                loading={itemsLoading}
                columns={itemColumns}
                dataSource={taskItems}
                pagination={{
                  ...itemsPagination,
                  total: itemsTotal,
                  showSizeChanger: true,
                  showQuickJumper: true,
                  showTotal: (total) => `共 ${total} 条`,
                  onChange: (page, pageSize) => {
                    setItemsPagination({ current: page, pageSize })
                    loadTaskItems(selectedTask.id, false)
                  },
                }}
                scroll={{ x: 1100 }}
                size="small"
              />
            </Card>
          </Space>
        )}
      </Drawer>

      <Modal
        title="查看手术记录详情"
        open={viewRecordVisible}
        onCancel={() => {
          setViewRecordVisible(false)
          setSelectedRecordId(null)
          setRecordDetail(null)
        }}
        footer={null}
        width={800}
      >
        <Spin spinning={recordLoading}>
          {recordDetail && (
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="记录编号">
                {recordDetail.recordNo}
              </Descriptions.Item>
              <Descriptions.Item label="患者姓名">
                {recordDetail.patientName}
              </Descriptions.Item>
              <Descriptions.Item label="住院号">
                {recordDetail.hospitalNo || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="性别">
                {recordDetail.gender || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="年龄">
                {recordDetail.age ? `${recordDetail.age} 岁` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="科室">
                {recordDetail.department || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="处理状态" span={2}>
                <Tag
                  color={
                    recordDetail.processStatus === 'COMPLETED'
                      ? 'success'
                      : recordDetail.processStatus === 'FAILED'
                        ? 'error'
                        : 'processing'
                  }
                >
                  {recordDetail.processStatus}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="原始文件" span={2}>
                {recordDetail.originalFileName}
              </Descriptions.Item>
              <Descriptions.Item label="上传时间" span={2}>
                {dayjs(recordDetail.uploadTime).format('YYYY-MM-DD HH:mm:ss')}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Spin>
        {selectedRecordId && (
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Button
              type="primary"
              onClick={() => {
                window.open(`/records/${selectedRecordId}`, '_blank')
              }}
            >
              打开完整页面
            </Button>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default BatchProcessing
