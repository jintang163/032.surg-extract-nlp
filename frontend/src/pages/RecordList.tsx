import React, { useState, useRef, useEffect } from 'react'
import {
  Card,
  Button,
  Space,
  Table,
  Tag,
  Input,
  Select,
  DatePicker,
  Form,
  Upload,
  message,
  Modal,
  Tooltip,
  Progress,
  Empty,
  Popconfirm,
} from 'antd'
import {
  PlusOutlined,
  InboxOutlined,
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  EditOutlined,
  FileWordOutlined,
  FilePdfOutlined,
  FileImageOutlined,
  FileTextOutlined,
  FileOutlined,
  AudioOutlined,
  VideoCameraOutlined,
  CheckCircleTwoTone,
  SyncOutlined,
} from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { recordApi } from '@/services/api'
import type { SurgeryRecord, ProcessStatus, FileType } from '@/types'
import { ProcessStatusMap, FileTypeMap } from '@/types'
import dayjs from 'dayjs'

const { RangePicker } = DatePicker
const { Dragger } = Upload
const { Option } = Select

const RecordList: React.FC = () => {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<SurgeryRecord[]>([])
  const [total, setTotal] = useState(0)
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 20 })
  const [uploadModal, setUploadModal] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    const status = searchParams.get('status')
    if (status) {
      form.setFieldsValue({ status })
    }
    loadData()
  }, [pagination])

  const loadData = async () => {
    setLoading(true)
    try {
      const values = form.getFieldsValue()
      const params = {
        ...values,
        startDate: values.dateRange?.[0]?.format('YYYY-MM-DD'),
        endDate: values.dateRange?.[1]?.format('YYYY-MM-DD'),
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      }
      delete params.dateRange
      const result = await recordApi.list(params)
      setData(result.records || [])
      setTotal(result.total || 0)
    } catch (e) {
      console.error('加载数据失败', e)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    setPagination({ ...pagination, pageNum: 1 })
    loadData()
  }

  const handleReset = () => {
    form.resetFields()
    setPagination({ ...pagination, pageNum: 1 })
    setTimeout(loadData, 100)
  }

  const handleUpload = async (file: File, patientInfo: any) => {
    setUploading(true)
    setUploadProgress(0)
    try {
      const result = await recordApi.upload(file, patientInfo, (progress) => {
        setUploadProgress(progress)
      })
      message.success('上传成功，系统正在后台处理...')
      setUploadModal(false)
      navigate(`/records/${result.id}`)
    } catch (e: any) {
      message.error(e?.message || '上传失败')
    } finally {
      setUploading(false)
      setUploadProgress(0)
    }
  }

  const getFileIcon = (type: FileType) => {
    const iconMap: Record<string, React.ReactNode> = {
      TEXT: <FileTextOutlined style={{ color: '#1677ff' }} />,
      WORD: <FileWordOutlined style={{ color: '#1890ff' }} />,
      PDF: <FilePdfOutlined style={{ color: '#f5222d' }} />,
      IMAGE: <FileImageOutlined style={{ color: '#52c41a' }} />,
      AUDIO: <AudioOutlined style={{ color: '#722ed1' }} />,
      VIDEO: <VideoCameraOutlined style={{ color: '#eb2f96' }} />,
      UNKNOWN: <FileOutlined style={{ color: '#8c8c8c' }} />,
    }
    return iconMap[type] || <FileOutlined />
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
    },
    {
      title: '患者信息',
      dataIndex: 'patientName',
      width: 180,
      render: (_: any, record: SurgeryRecord) => (
        <div>
          <div style={{ fontWeight: 500 }}>
            {record.patientName || '（未识别）'}
            {record.gender && (
              <Tag color={record.gender === '男' ? 'blue' : 'magenta'} style={{ marginLeft: 6 }}>
                {record.gender}
              </Tag>
            )}
            {record.age && <span style={{ color: '#8c8c8c' }}> {record.age}岁</span>}
          </div>
          <div style={{ fontSize: 12, color: '#8c8c8c' }}>
            住院号：{record.hospitalNo || '-'}
          </div>
        </div>
      ),
    },
    {
      title: '文件',
      dataIndex: 'originalFileName',
      width: 220,
      ellipsis: true,
      render: (text: string, record: SurgeryRecord) => (
        <Tooltip title={text}>
          <Space>
            {getFileIcon(record.fileType as FileType)}
            <span style={{ maxWidth: 180 }}>{text}</span>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: '科室',
      dataIndex: 'department',
      width: 120,
    },
    {
      title: '状态',
      dataIndex: 'processStatus',
      width: 120,
      render: (status: ProcessStatus) => {
        const info = ProcessStatusMap[status]
        return (
          <Tag
            color={info?.color}
            className={
              status === 'OCR_PROCESSING' || status === 'NER_PROCESSING'
                ? 'status-tag-processing'
                : ''
            }
            icon={
              status === 'OCR_PROCESSING' || status === 'NER_PROCESSING' ? (
                <SyncOutlined spin />
              ) : null
            }
          >
            {info?.label}
          </Tag>
        )
      },
    },
    {
      title: 'HIS同步',
      dataIndex: 'hisSynced',
      width: 100,
      render: (val: number) =>
        val === 1 ? (
          <Tag icon={<CheckCircleTwoTone twoToneColor="#52c41a" />} color="success">
            已同步
          </Tag>
        ) : (
          <Tag color="default">未同步</Tag>
        ),
    },
    {
      title: '上传时间',
      dataIndex: 'uploadTime',
      width: 160,
      render: (t: string) => dayjs(t).format('YYYY-MM-DD HH:mm'),
      sorter: (a: SurgeryRecord, b: SurgeryRecord) =>
        new Date(a.uploadTime).getTime() - new Date(b.uploadTime).getTime(),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right' as const,
      render: (_: any, record: SurgeryRecord) => (
        <Space size={4}>
          <Tooltip title="查看详情">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/records/${record.id}`)}
            >
              详情
            </Button>
          </Tooltip>
          {(record.processStatus === 'NER_DONE' || record.processStatus === 'COMPLETED') && (
            <Tooltip title="填写病案首页">
              <Button
                type="primary"
                size="small"
                icon={<EditOutlined />}
                onClick={() => navigate(`/homepage/${record.recordId || record.id}`)}
              >
                填首页
              </Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="page-container">
      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" onFinish={handleSearch}>
          <Form.Item name="patientName" label="患者姓名">
            <Input placeholder="请输入姓名" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="hospitalNo" label="住院号">
            <Input placeholder="请输入住院号" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部状态" allowClear style={{ width: 140 }}>
              {Object.entries(ProcessStatusMap).map(([key, val]) => (
                <Option key={key} value={key}>
                  {val.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="dateRange" label="上传时间">
            <RangePicker style={{ width: 260 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={handleReset} icon={<ReloadOutlined />}>
                重置
              </Button>
              <Button type="primary" onClick={() => setUploadModal(true)} icon={<PlusOutlined />}>
                上传记录
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={columns}
          scroll={{ x: 1300 }}
          pagination={{
            current: pagination.pageNum,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) => setPagination({ pageNum: page, pageSize }),
          }}
          locale={{
            emptyText: (
              <Empty
                description={
                  <Space direction="vertical">
                    <span>暂无手术记录</span>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => setUploadModal(true)}>
                      上传手术记录
                    </Button>
                  </Space>
                }
              />
            ),
          }}
        />
      </Card>

      <UploadModal
        open={uploadModal}
        onCancel={() => !uploading && setUploadModal(false)}
        uploading={uploading}
        uploadProgress={uploadProgress}
        onSubmit={handleUpload}
      />
    </div>
  )
}

interface UploadModalProps {
  open: boolean
  onCancel: () => void
  uploading: boolean
  uploadProgress: number
  onSubmit: (file: File, patientInfo: any) => void
}

const UploadModal: React.FC<UploadModalProps> = ({
  open,
  onCancel,
  uploading,
  uploadProgress,
  onSubmit,
}) => {
  const [file, setFile] = useState<File | null>(null)
  const [form] = Form.useForm()

  useEffect(() => {
    if (!open) {
      setFile(null)
      form.resetFields()
    }
  }, [open])

  const handleOk = async () => {
    if (!file) {
      message.warning('请上传手术记录文件')
      return
    }
    const values = await form.validateFields()
    onSubmit(file, values)
  }

  const beforeUpload = (f: File) => {
    const allowTypes = ['txt', 'doc', 'docx', 'pdf', 'png', 'jpg', 'jpeg', 'gif', 'bmp', 'tiff']
    const ext = f.name.split('.').pop()?.toLowerCase() || ''
    if (!allowTypes.includes(ext)) {
      message.error('仅支持 txt/word/pdf/图片 文件')
      return Upload.LIST_IGNORE
    }
    if (f.size > 50 * 1024 * 1024) {
      message.error('文件大小不能超过50MB')
      return Upload.LIST_IGNORE
    }
    setFile(f)
    return false
  }

  return (
    <Modal
      title="上传手术记录"
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={uploading}
      okText="上传并处理"
      cancelText="取消"
      width={600}
      maskClosable={!uploading}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={16}>
        <Dragger
          beforeUpload={beforeUpload}
          multiple={false}
          maxCount={1}
          showUploadList={false}
          disabled={uploading}
          fileList={file ? [{ name: file.name, size: file.size, uid: '1' }] : []}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p className="ant-upload-hint">
            支持 纯文本(txt)、Word(doc/docx)、PDF、图片(png/jpg等) 格式，单个文件不超过50MB
          </p>
          {file && (
            <div style={{ marginTop: 12 }}>
              <Tag color="blue">已选择：{file.name}</Tag>
              <span style={{ color: '#8c8c8c', marginLeft: 8 }}>
                {(file.size / 1024).toFixed(1)} KB
              </span>
            </div>
          )}
        </Dragger>

        {uploading && <Progress percent={uploadProgress} status="active" />}

        <Card size="small" title="患者信息（选填，若不填将从文件中自动识别）" variant="outlined">
          <Form form={form} layout="vertical">
            <Space size={12} wrap style={{ width: '100%' }}>
              <Form.Item
                name="patientName"
                label="患者姓名"
                style={{ flex: 1, minWidth: 150, marginBottom: 0 }}
              >
                <Input placeholder="请输入" />
              </Form.Item>
              <Form.Item
                name="hospitalNo"
                label="住院号"
                style={{ flex: 1, minWidth: 150, marginBottom: 0 }}
              >
                <Input placeholder="请输入" />
              </Form.Item>
            </Space>
            <Space size={12} wrap style={{ width: '100%', marginTop: 16 }}>
              <Form.Item
                name="patientId"
                label="患者ID"
                style={{ flex: 1, minWidth: 150, marginBottom: 0 }}
              >
                <Input placeholder="请输入" />
              </Form.Item>
              <Form.Item
                name="department"
                label="科室"
                style={{ flex: 1, minWidth: 150, marginBottom: 0 }}
              >
                <Select
                  placeholder="请选择"
                  allowClear
                  options={[
                    { label: '普外科', value: '普外科' },
                    { label: '骨科', value: '骨科' },
                    { label: '神经外科', value: '神经外科' },
                    { label: '心胸外科', value: '心胸外科' },
                    { label: '泌尿外科', value: '泌尿外科' },
                    { label: '妇产科', value: '妇产科' },
                  ]}
                />
              </Form.Item>
            </Space>
          </Form>
        </Card>
      </Space>
    </Modal>
  )
}

export default RecordList
