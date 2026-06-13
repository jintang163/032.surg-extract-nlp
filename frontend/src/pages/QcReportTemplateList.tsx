import React, { useState, useEffect, useRef } from 'react'
import {
  Card,
  Button,
  Space,
  Table,
  Tag,
  Input,
  Select,
  Form,
  message,
  Modal,
  Tooltip,
  Empty,
  Popconfirm,
  Upload,
  Switch,
  Descriptions,
  Row,
  Col,
  Progress,
  Badge,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  HistoryOutlined,
  DownloadOutlined,
  UploadOutlined,
  EyeOutlined,
  StarOutlined,
  StarFilled,
  FileWordOutlined,
  FileExcelOutlined,
  SafetyCertificateOutlined,
  FilePdfOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { qcReportTemplateApi } from '@/services/api'
import type { QcReportTemplate, QcReportTemplateStatus, QcReportTemplateFileType, PageResult } from '@/types'
import { QcReportTemplateStatusMap, QcReportTemplateFileTypeMap } from '@/types'
import { useAuthStore } from '@/store/authStore'
import dayjs from 'dayjs'
import type { UploadProps } from 'antd'

const { Option } = Select

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const QcReportTemplateList: React.FC = () => {
  const navigate = useNavigate()
  const { userInfo } = useAuthStore()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<QcReportTemplate[]>([])
  const [total, setTotal] = useState(0)
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 20 })
  const [previewModal, setPreviewModal] = useState<{ open: boolean; template?: QcReportTemplate }>({
    open: false,
  })
  const [versionModal, setVersionModal] = useState<{ open: boolean; templateId?: number }>({
    open: false,
  })
  const [uploadModal, setUploadModal] = useState({ open: false })
  const [uploadProgress, setUploadProgress] = useState(0)
  const [previewLoading, setPreviewLoading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (userInfo?.role !== 'ADMIN') {
      message.warning('仅管理员可访问质控报告模板管理')
      navigate('/dashboard', { replace: true })
      return
    }
    loadData()
  }, [pagination, userInfo])

  const loadData = async () => {
    setLoading(true)
    try {
      const values = form.getFieldsValue()
      const result = (await qcReportTemplateApi.list({
        ...values,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      })) as PageResult<QcReportTemplate>
      setData(result.records || [])
      setTotal(result.total || 0)
    } catch (e) {
      console.error('加载模板列表失败', e)
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

  const handleDelete = async (id: number) => {
    try {
      await qcReportTemplateApi.delete(id)
      message.success('删除成功')
      loadData()
    } catch (e: any) {
      message.error(e?.message || '删除失败')
    }
  }

  const handleSetDefault = async (record: QcReportTemplate) => {
    try {
      await qcReportTemplateApi.setDefault(record.id)
      message.success('已设为默认模板')
      loadData()
    } catch (e: any) {
      message.error(e?.message || '设置失败')
    }
  }

  const handleToggleStatus = async (record: QcReportTemplate) => {
    try {
      await qcReportTemplateApi.toggleStatus(record.id)
      message.success('状态已更新')
      loadData()
    } catch (e: any) {
      message.error(e?.message || '更新失败')
    }
  }

  const handleDownload = async (record: QcReportTemplate) => {
    try {
      const blob = await qcReportTemplateApi.downloadTemplate(record.id)
      const url = window.URL.createObjectURL(new Blob([blob as any]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', record.originalFileName || `${record.templateCode}.docx`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      message.success('下载成功')
    } catch (e: any) {
      message.error(e?.message || '下载失败')
    }
  }

  const handleExportJson = async (record: QcReportTemplate) => {
    try {
      const data = await qcReportTemplateApi.exportTemplate(record.id)
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${record.templateCode}_v${record.currentVersion}.json`
      a.click()
      URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch (e: any) {
      message.error(e?.message || '导出失败')
    }
  }

  const handleImportClick = () => {
    fileInputRef.current?.click()
  }

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      await qcReportTemplateApi.importTemplate(file)
      message.success('导入成功')
      loadData()
    } catch (err: any) {
      message.error(err?.message || '导入失败，请检查JSON格式')
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const uploadProps: UploadProps = {
    name: 'file',
    accept: '.doc,.docx,.xls,.xlsx',
    showUploadList: false,
    customRequest: async (options) => {
      const file = options.file as File
      try {
        setUploadProgress(0)
        const result = await qcReportTemplateApi.uploadTemplate(
          file,
          { templateName: file.name.replace(/\.[^.]+$/, '') },
          (p) => setUploadProgress(p)
        )
        options.onSuccess?.(result)
        message.success('上传成功，请进入编辑页面配置字段绑定')
        setUploadModal({ open: false })
        loadData()
        setTimeout(() => {
          if (result?.id) {
            navigate(`/qc-report-templates/${result.id}/edit`)
          }
        }, 500)
      } catch (err: any) {
        options.onError?.(err)
        message.error(err?.message || '上传失败')
      } finally {
        setUploadProgress(0)
      }
    },
    beforeUpload: (file) => {
      const isWordOrExcel = /\.(doc|docx|xls|xlsx)$/i.test(file.name)
      if (!isWordOrExcel) {
        message.error('只能上传 Word 或 Excel 文件!')
        return Upload.LIST_IGNORE
      }
      const isLt20M = file.size / 1024 / 1024 < 20
      if (!isLt20M) {
        message.error('文件大小不能超过 20MB!')
        return Upload.LIST_IGNORE
      }
      return true
    },
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '模板编码',
      dataIndex: 'templateCode',
      width: 180,
      render: (text: string, record: QcReportTemplate) => (
        <Space>
          <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{text}</span>
          {record.isDefault === 1 && (
            <Tooltip title="默认模板">
              <StarFilled style={{ color: '#faad14' }} />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: '模板名称',
      dataIndex: 'templateName',
      width: 200,
    },
    {
      title: '文件类型',
      dataIndex: 'fileType',
      width: 100,
      render: (t: QcReportTemplateFileType) => {
        const info = QcReportTemplateFileTypeMap[t]
        return (
          <Tag icon={t === 'WORD' ? <FileWordOutlined /> : <FileExcelOutlined />} color={t === 'WORD' ? 'blue' : 'green'}>
            {info?.label || t}
          </Tag>
        )
      },
    },
    {
      title: '适用科室',
      dataIndex: 'department',
      width: 110,
      render: (text: string) => text || <Tag color="default">全院通用</Tag>,
    },
    {
      title: '字段绑定',
      dataIndex: 'fieldBindings',
      width: 90,
      render: (bindings: any[]) => (
        <Badge
          count={bindings?.length || 0}
          showZero
          color={bindings && bindings.length > 0 ? '#1677ff' : '#8c8c8c'}
        />
      ),
    },
    {
      title: '水印',
      dataIndex: 'enableWatermark',
      width: 80,
      align: 'center' as const,
      render: (v: number) => (v === 1 ? <Tag color="cyan">已启用</Tag> : <Tag>未启用</Tag>),
    },
    {
      title: '版本',
      dataIndex: 'currentVersion',
      width: 70,
      render: (v: number) => <Tag color="blue">v{v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status: QcReportTemplateStatus) => {
        const info = QcReportTemplateStatusMap[status]
        return <Tag color={info?.color}>{info?.label}</Tag>
      },
    },
    {
      title: '使用次数',
      dataIndex: 'useCount',
      width: 80,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedTime',
      width: 150,
      render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 340,
      fixed: 'right' as const,
      render: (_: any, record: QcReportTemplate) => (
        <Space size={2} wrap>
          <Tooltip title="预览效果">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setPreviewModal({ open: true, template: record })}
            />
          </Tooltip>
          <Tooltip title="编辑配置">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => navigate(`/qc-report-templates/${record.id}/edit`)}
            />
          </Tooltip>
          <Tooltip title="版本历史">
            <Button
              type="link"
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => setVersionModal({ open: true, templateId: record.id })}
            />
          </Tooltip>
          <Tooltip title="下载原始模板">
            <Button
              type="link"
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => handleDownload(record)}
            />
          </Tooltip>
          <Tooltip title="导出JSON配置">
            <Button
              type="link"
              size="small"
              onClick={() => handleExportJson(record)}
            >
              JSON
            </Button>
          </Tooltip>
          {record.isDefault !== 1 && (
            <Tooltip title="设为默认">
              <Button
                type="link"
                size="small"
                icon={<StarOutlined />}
                onClick={() => handleSetDefault(record)}
              />
            </Tooltip>
          )}
          <Tooltip title={record.status === 'ACTIVE' ? '停用' : '启用'}>
            <Switch
              size="small"
              checked={record.status === 'ACTIVE'}
              onChange={() => handleToggleStatus(record)}
            />
          </Tooltip>
          <Popconfirm title="确定删除此模板？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="page-container">
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        style={{ display: 'none' }}
        onChange={handleImport}
      />

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle" style={{ marginBottom: 12 }}>
          <Col span={24}>
            <Space size={12}>
              <SafetyCertificateOutlined style={{ fontSize: 20, color: '#1677ff' }} />
              <span style={{ fontSize: 18, fontWeight: 600 }}>质控报告模板管理</span>
              <Tag color="blue">管理员专用</Tag>
            </Space>
          </Col>
        </Row>
        <Form form={form} layout="inline" onFinish={handleSearch}>
          <Form.Item name="templateName" label="模板名称">
            <Input placeholder="请输入名称" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="fileType" label="文件类型">
            <Select placeholder="全部类型" allowClear style={{ width: 140 }}>
              <Option value="WORD">Word文档</Option>
              <Option value="EXCEL">Excel表格</Option>
            </Select>
          </Form.Item>
          <Form.Item name="department" label="科室">
            <Select placeholder="全部科室" allowClear style={{ width: 140 }}>
              <Option value="普外科">普外科</Option>
              <Option value="骨科">骨科</Option>
              <Option value="妇产科">妇产科</Option>
              <Option value="神经外科">神经外科</Option>
              <Option value="心胸外科">心胸外科</Option>
              <Option value="泌尿外科">泌尿外科</Option>
              <Option value="麻醉科">麻醉科</Option>
              <Option value="质控科">质控科</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部状态" allowClear style={{ width: 120 }}>
              {Object.entries(QcReportTemplateStatusMap).map(([key, val]) => (
                <Option key={key} value={key}>
                  {val.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={handleReset} icon={<ReloadOutlined />}>
                重置
              </Button>
              <Button type="primary" onClick={() => setUploadModal({ open: true })} icon={<PlusOutlined />}>
                上传新模板
              </Button>
              <Button icon={<UploadOutlined />} onClick={handleImportClick}>
                导入JSON
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
          scroll={{ x: 1500 }}
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
                    <span>暂无质控报告模板</span>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => setUploadModal({ open: true })}>
                      上传第一个模板
                    </Button>
                  </Space>
                }
              />
            ),
          }}
        />
      </Card>

      <Modal
        title={<Space><UploadOutlined />上传质控报告模板</Space>}
        open={uploadModal.open}
        onCancel={() => setUploadModal({ open: false })}
        footer={null}
        width={560}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <Alert
            type="info"
            showIcon
            message="模板要求"
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                <li>支持 Word (.doc/.docx) 和 Excel (.xls/.xlsx) 格式</li>
                <li>在模板中使用 {'${占位符名称}'} 格式标记需要动态填充的位置</li>
                <li>如：{'${患者姓名}'}、{'${综合评分}'}、{'${违规项表格}'}</li>
                <li>上传后在编辑页面进行质控字段绑定配置</li>
                <li>文件大小不超过 20MB</li>
              </ul>
            }
            style={{ marginBottom: 0 }}
          />
          <Upload.Dragger {...uploadProps} multiple={false}>
            <p className="ant-upload-drag-icon">
              <ThunderboltOutlined style={{ color: '#1677ff' }} />
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p className="ant-upload-hint">支持 .doc / .docx / .xls / .xlsx 格式</p>
            {uploadProgress > 0 && (
              <div style={{ marginTop: 12, padding: '0 24px' }}>
                <Progress percent={uploadProgress} size="small" />
              </div>
            )}
          </Upload.Dragger>
        </Space>
      </Modal>

      <PreviewModal
        open={previewModal.open}
        template={previewModal.template}
        onClose={() => setPreviewModal({ open: false })}
        loading={previewLoading}
        setLoading={setPreviewLoading}
      />

      <VersionHistoryModal
        open={versionModal.open}
        templateId={versionModal.templateId}
        onClose={() => setVersionModal({ open: false })}
      />
    </div>
  )
}

interface PreviewModalProps {
  open: boolean
  template?: QcReportTemplate
  onClose: () => void
  loading: boolean
  setLoading: (v: boolean) => void
}

const PreviewModal: React.FC<PreviewModalProps> = ({ open, template, onClose, loading, setLoading }) => {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [previewBlob, setPreviewBlob] = useState<Blob | null>(null)

  useEffect(() => {
    if (open && template) {
      loadPreview()
    } else {
      setPreviewUrl(null)
      setPreviewBlob(null)
    }
  }, [open, template])

  const loadPreview = async () => {
    setLoading(true)
    try {
      const blob = await qcReportTemplateApi.preview(template!.id, {
        enableWatermark: template!.enableWatermark === 1,
        watermarkText: template!.watermarkText,
      })
      const b = blob as unknown as Blob
      setPreviewBlob(b)
      const url = URL.createObjectURL(b)
      setPreviewUrl(url)
    } catch (e) {
      console.error('预览加载失败', e)
    } finally {
      setLoading(false)
    }
  }

  const handleDownloadPreview = () => {
    if (previewBlob) {
      const url = URL.createObjectURL(previewBlob)
      const a = document.createElement('a')
      a.href = url
      a.download = `预览_${template?.templateCode || 'template'}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    }
  }

  if (!template) return null

  return (
    <Modal
      title={
        <Space>
          <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
          <span>模板预览 - {template.templateName}</span>
          <Tag color="blue">v{template.currentVersion}</Tag>
          {template.enableWatermark === 1 && <Tag color="cyan">含水印</Tag>}
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={900}
      footer={
        <Space>
          <Button onClick={onClose}>关闭</Button>
          <Button icon={<DownloadOutlined />} onClick={handleDownloadPreview} disabled={!previewBlob}>
            下载预览PDF
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadPreview} loading={loading}>
            重新生成
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <Descriptions column={2} size="small" bordered>
          <Descriptions.Item label="模板编码">{template.templateCode}</Descriptions.Item>
          <Descriptions.Item label="文件类型">
            <Tag icon={template.fileType === 'WORD' ? <FileWordOutlined /> : <FileExcelOutlined />}>
              {QcReportTemplateFileTypeMap[template.fileType]?.label}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="科室">{template.department || '全院通用'}</Descriptions.Item>
          <Descriptions.Item label="字段绑定数">{template.fieldBindings?.length || 0} 个</Descriptions.Item>
        </Descriptions>
        <Card
          size="small"
          title={
            <Space>
              <FilePdfOutlined style={{ color: '#ff4d4f' }} />
              <span>PDF预览效果（含示例数据）</span>
            </Space>
          }
          bodyStyle={{ padding: 0 }}
          style={{ border: '1px solid #e5e7eb' }}
        >
          {loading ? (
            <div style={{ padding: 60, textAlign: 'center', color: '#8c8c8c' }}>
              <ReloadOutlined spin style={{ fontSize: 28, marginBottom: 12 }} />
              <div>正在生成预览...</div>
            </div>
          ) : previewUrl ? (
            <iframe
              src={previewUrl}
              style={{ width: '100%', height: 560, border: 'none' }}
              title="PDF预览"
            />
          ) : (
            <div style={{ padding: 60, textAlign: 'center' }}>
              <Empty description="预览加载失败，可点击下方按钮重新生成" />
            </div>
          )}
        </Card>
        {template.fieldBindings && template.fieldBindings.length > 0 && (
          <Card size="small" title="已绑定的质控字段" bodyStyle={{ padding: '8px 16px' }}>
            <Space wrap size={[8, 8]}>
              {template.fieldBindings.map((b, idx) => (
                <Tag key={idx} color={
                  b.fieldType === 'SCALAR' ? 'blue' :
                  b.fieldType === 'LIST' ? 'purple' :
                  b.fieldType === 'TABLE' ? 'geekblue' : 'cyan'
                }>
                  {b.placeholderLabel} → {b.qcFieldLabel}
                  <span style={{ marginLeft: 4, fontSize: 10, opacity: 0.7 }}>
                    [{b.fieldType === 'SCALAR' ? '标量' : b.fieldType === 'LIST' ? '列表' : b.fieldType === 'TABLE' ? '表格' : '表头'}]
                  </span>
                </Tag>
              ))}
            </Space>
          </Card>
        )}
      </Space>
    </Modal>
  )
}

interface VersionModalProps {
  open: boolean
  templateId?: number
  onClose: () => void
}

const VersionHistoryModal: React.FC<VersionModalProps> = ({ open, templateId, onClose }) => {
  const [loading, setLoading] = useState(false)
  const [versions, setVersions] = useState<any[]>([])

  useEffect(() => {
    if (open && templateId) {
      loadVersions()
    }
  }, [open, templateId])

  const loadVersions = async () => {
    setLoading(true)
    try {
      const data = await qcReportTemplateApi.getVersions(templateId!)
      setVersions(data || [])
    } catch (e) {
      console.error('加载版本历史失败', e)
    } finally {
      setLoading(false)
    }
  }

  const handleRevert = async (versionNo: number) => {
    Modal.confirm({
      title: '确定回退到此版本？',
      content: `回退到版本 v${versionNo}，将自动创建一个新版本`,
      onOk: async () => {
        try {
          await qcReportTemplateApi.revertVersion(templateId!, versionNo, `回退到版本 v${versionNo}`)
          message.success('回退成功')
          loadVersions()
        } catch (e: any) {
          message.error(e?.message || '回退失败')
        }
      },
    })
  }

  return (
    <Modal
      title={<Space><HistoryOutlined />版本历史</Space>}
      open={open}
      onCancel={onClose}
      onOk={onClose}
      okText="关闭"
      cancelButtonProps={{ style: { display: 'none' } }}
      width={720}
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={versions}
        pagination={false}
        columns={[
          {
            title: '版本',
            dataIndex: 'versionNo',
            width: 100,
            render: (v: number, record: any) => (
              <Space>
                <Tag color={record.isCurrent === 1 ? 'blue' : 'default'}>v{v}</Tag>
                {record.isCurrent === 1 && <Tag color="green">当前</Tag>}
              </Space>
            ),
          },
          {
            title: '原始文件',
            dataIndex: 'originalFileName',
            width: 180,
            render: (t: string) => t || '-',
          },
          {
            title: '字段数',
            dataIndex: 'fieldBindings',
            width: 70,
            render: (b: any[]) => b?.length || 0,
          },
          {
            title: '变更说明',
            dataIndex: 'changeLog',
            ellipsis: true,
            render: (t: string) => t || '-',
          },
          {
            title: '创建人',
            dataIndex: 'createdUserName',
            width: 100,
            render: (t: string) => t || '-',
          },
          {
            title: '创建时间',
            dataIndex: 'createdTime',
            width: 160,
            render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'),
          },
          {
            title: '操作',
            key: 'action',
            width: 100,
            render: (_: any, record: any) =>
              record.isCurrent !== 1 ? (
                <Button type="link" size="small" onClick={() => handleRevert(record.versionNo)}>
                  回退到此
                </Button>
              ) : null,
          },
        ]}
      />
    </Modal>
  )
}

export default QcReportTemplateList
