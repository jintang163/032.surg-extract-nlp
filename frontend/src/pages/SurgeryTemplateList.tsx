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
  CopyOutlined,
  StarOutlined,
  StarFilled,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { templateApi } from '@/services/api'
import type { SurgeryTemplate, TemplateStatus } from '@/types'
import { TemplateStatusMap, PageResult } from '@/types'
import { useAuthStore } from '@/store/authStore'
import dayjs from 'dayjs'

const { Option } = Select

const SurgeryTemplateList: React.FC = () => {
  const navigate = useNavigate()
  const { userInfo } = useAuthStore()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<SurgeryTemplate[]>([])
  const [total, setTotal] = useState(0)
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 20 })
  const [previewModal, setPreviewModal] = useState<{ open: boolean; template?: SurgeryTemplate }>({
    open: false,
  })
  const [versionModal, setVersionModal] = useState<{ open: boolean; templateId?: number }>({
    open: false,
  })
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (userInfo?.role !== 'ADMIN') {
      message.warning('仅管理员可访问模板管理')
      navigate('/dashboard', { replace: true })
      return
    }
    loadData()
  }, [pagination, userInfo])

  const loadData = async () => {
    setLoading(true)
    try {
      const values = form.getFieldsValue()
      const result = (await templateApi.list({
        ...values,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      })) as PageResult<SurgeryTemplate>
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
      await templateApi.delete(id)
      message.success('删除成功')
      loadData()
    } catch (e: any) {
      message.error(e?.message || '删除失败')
    }
  }

  const handleExport = async (template: SurgeryTemplate) => {
    try {
      const data = await templateApi.exportTemplate(template.id)
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${template.templateCode}_v${template.currentVersion}.json`
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
      const text = await file.text()
      const json = JSON.parse(text)
      await templateApi.importTemplate(json)
      message.success('导入成功')
      loadData()
    } catch (err: any) {
      message.error(err?.message || '导入失败，请检查JSON格式')
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
    },
    {
      title: '模板编码',
      dataIndex: 'templateCode',
      width: 180,
      render: (text: string, record: SurgeryTemplate) => (
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
      title: '手术类型',
      dataIndex: 'surgeryType',
      width: 180,
      render: (text: string, record: SurgeryTemplate) => (
        <div>
          <div>{text}</div>
          {record.surgeryCode && (
            <div style={{ fontSize: 12, color: '#8c8c8c' }}>编码: {record.surgeryCode}</div>
          )}
        </div>
      ),
    },
    {
      title: '适用科室',
      dataIndex: 'department',
      width: 120,
      render: (text: string) => text || <Tag color="default">全院通用</Tag>,
    },
    {
      title: '版本',
      dataIndex: 'currentVersion',
      width: 80,
      render: (v: number) => <Tag color="blue">v{v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: TemplateStatus) => {
        const info = TemplateStatusMap[status]
        return <Tag color={info?.color}>{info?.label}</Tag>
      },
    },
    {
      title: '使用次数',
      dataIndex: 'useCount',
      width: 90,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedTime',
      width: 160,
      render: (t: string) => dayjs(t).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      fixed: 'right' as const,
      render: (_: any, record: SurgeryTemplate) => (
        <Space size={2} wrap>
          <Tooltip title="查看">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setPreviewModal({ open: true, template: record })}
            >
              查看
            </Button>
          </Tooltip>
          <Tooltip title="编辑">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => navigate(`/templates/${record.id}/edit`)}
            >
              编辑
            </Button>
          </Tooltip>
          <Tooltip title="版本历史">
            <Button
              type="link"
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => setVersionModal({ open: true, templateId: record.id })}
            >
              版本
            </Button>
          </Tooltip>
          <Tooltip title="导出">
            <Button
              type="link"
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => handleExport(record)}
            >
              导出
            </Button>
          </Tooltip>
          <Popconfirm title="确定删除此模板？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
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
        <Form form={form} layout="inline" onFinish={handleSearch}>
          <Form.Item name="templateName" label="模板名称">
            <Input placeholder="请输入名称" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="surgeryType" label="手术类型">
            <Input placeholder="请输入手术类型" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="department" label="科室">
            <Select placeholder="全部科室" allowClear style={{ width: 140 }}>
              <Option value="普外科">普外科</Option>
              <Option value="骨科">骨科</Option>
              <Option value="妇产科">妇产科</Option>
              <Option value="神经外科">神经外科</Option>
              <Option value="心胸外科">心胸外科</Option>
              <Option value="泌尿外科">泌尿外科</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部状态" allowClear style={{ width: 120 }}>
              {Object.entries(TemplateStatusMap).map(([key, val]) => (
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
              <Button type="primary" onClick={() => navigate('/templates/new')} icon={<PlusOutlined />}>
                新建模板
              </Button>
              <Button icon={<UploadOutlined />} onClick={handleImportClick}>
                导入
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
          scroll={{ x: 1400 }}
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
                    <span>暂无手术模板</span>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/templates/new')}>
                      创建第一个模板
                    </Button>
                  </Space>
                }
              />
            ),
          }}
        />
      </Card>

      <PreviewModal
        open={previewModal.open}
        template={previewModal.template}
        onClose={() => setPreviewModal({ open: false })}
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
  template?: SurgeryTemplate
  onClose: () => void
}

const PreviewModal: React.FC<PreviewModalProps> = ({ open, template, onClose }) => {
  if (!template) return null
  return (
    <Modal
      title={
        <Space>
          <span>{template.templateName}</span>
          <Tag color="blue">v{template.currentVersion}</Tag>
        </Space>
      }
      open={open}
      onCancel={onClose}
      onOk={onClose}
      okText="关闭"
      cancelButtonProps={{ style: { display: 'none' } }}
      width={800}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <span style={{ color: '#8c8c8c' }}>编码：</span>
            <span style={{ fontFamily: 'monospace' }}>{template.templateCode}</span>
          </div>
          <div>
            <span style={{ color: '#8c8c8c' }}>手术类型：</span>
            <span>{template.surgeryType}</span>
          </div>
          <div>
            <span style={{ color: '#8c8c8c' }}>科室：</span>
            <span>{template.department || '全院通用'}</span>
          </div>
          <div>
            <span style={{ color: '#8c8c8c' }}>状态：</span>
            <Tag color={TemplateStatusMap[template.status]?.color}>
              {TemplateStatusMap[template.status]?.label}
            </Tag>
          </div>
        </div>
        {template.description && (
          <div>
            <div style={{ color: '#8c8c8c', marginBottom: 4 }}>说明：</div>
            <div>{template.description}</div>
          </div>
        )}
        <div>
          <div style={{ color: '#8c8c8c', marginBottom: 4 }}>模板内容：</div>
          <div
            style={{
              background: '#f6f8fa',
              padding: 16,
              borderRadius: 8,
              fontFamily: 'monospace',
              whiteSpace: 'pre-wrap',
              maxHeight: 400,
              overflow: 'auto',
              fontSize: 13,
              lineHeight: 1.8,
            }}
          >
            {template.templateContent}
          </div>
        </div>
        {template.placeholders && template.placeholders.length > 0 && (
          <div>
            <div style={{ color: '#8c8c8c', marginBottom: 4 }}>占位符（{template.placeholders.length}个）：</div>
            <Space wrap>
              {template.placeholders.map((p) => (
                <Tag key={p.name} color="geekblue">
                  {p.label || p.name}
                  {p.required && <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>}
                </Tag>
              ))}
            </Space>
          </div>
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
      const data = await templateApi.getVersions(templateId!)
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
          await templateApi.revertVersion(templateId!, versionNo, `回退到版本 v${versionNo}`)
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
      title="版本历史"
      open={open}
      onCancel={onClose}
      onOk={onClose}
      okText="关闭"
      cancelButtonProps={{ style: { display: 'none' } }}
      width={700}
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
            width: 80,
            render: (v: number, record: any) => (
              <Space>
                <Tag color={record.isCurrent === 1 ? 'blue' : 'default'}>v{v}</Tag>
                {record.isCurrent === 1 && <Tag color="green">当前</Tag>}
              </Space>
            ),
          },
          {
            title: '变更说明',
            dataIndex: 'changeLog',
            ellipsis: true,
          },
          {
            title: '创建人',
            dataIndex: 'createdUserName',
            width: 100,
          },
          {
            title: '创建时间',
            dataIndex: 'createdTime',
            width: 160,
            render: (t: string) => dayjs(t).format('YYYY-MM-DD HH:mm'),
          },
          {
            title: '操作',
            key: 'action',
            width: 100,
            render: (_: any, record: any) =>
              record.isCurrent !== 1 ? (
                <Button type="link" size="small" onClick={() => handleRevert(record.versionNo)}>
                  回退到此版本
                </Button>
              ) : null,
          },
        ]}
      />
    </Modal>
  )
}

export default SurgeryTemplateList
