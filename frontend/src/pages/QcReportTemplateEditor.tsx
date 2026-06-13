import React, { useState, useEffect, useMemo } from 'react'
import {
  Card,
  Button,
  Space,
  Form,
  Input,
  Select,
  InputNumber,
  Switch,
  message,
  Tabs,
  Tag,
  Row,
  Col,
  Divider,
  Tooltip,
  Modal,
  Table,
  Empty,
  Upload,
  Alert,
  Progress,
  Descriptions,
  Cascader,
  Badge,
  List,
  Avatar,
} from 'antd'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  EyeOutlined,
  PlusOutlined,
  DeleteOutlined,
  SyncOutlined,
  SafetyCertificateOutlined,
  FileWordOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
  UploadOutlined,
  DownloadOutlined,
  ReloadOutlined,
  DatabaseOutlined,
  LinkOutlined,
  UnlinkOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined,
  SettingOutlined,
  BorderOutlined,
  BarsOutlined,
  FieldStringOutlined,
  FormOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { qcReportTemplateApi } from '@/services/api'
import type {
  QcReportTemplate,
  QcFieldBinding,
  QcReportTemplateStatus,
  QcReportTemplateFileType,
} from '@/types'
import {
  QcReportTemplateStatusMap,
  QcReportTemplateFileTypeMap,
  QC_AVAILABLE_FIELDS,
  getAllQcFieldOptions,
} from '@/types'
import { useAuthStore } from '@/store/authStore'
import dayjs from 'dayjs'
import type { UploadProps } from 'antd'

const { Option } = Select
const { TextArea } = Input

const buildCascaderOptions = () => {
  return QC_AVAILABLE_FIELDS.map((g) => ({
    value: g.group,
    label: g.groupLabel,
    children: g.fields.map((f) => ({
      value: f.key,
      label: (
        <Space size={6}>
          <span>{f.label}</span>
          <Tag
            style={{ margin: 0, fontSize: 10, padding: '0 4px', lineHeight: '14px' }}
            color={
              f.type === 'SCALAR' ? 'blue' :
              f.type === 'LIST' ? 'purple' : 'geekblue'
            }
          >
            {f.type === 'SCALAR' ? '标量' : f.type === 'LIST' ? '列表' : '表格'}
          </Tag>
        </Space>
      ),
      rawType: f.type,
      rawLabel: f.label,
      rawDesc: f.description,
    })),
  }))
}

const getFieldIcon = (type: string) => {
  switch (type) {
    case 'SCALAR': return <FieldStringOutlined style={{ color: '#1677ff' }} />
    case 'LIST': return <BarsOutlined style={{ color: '#722ed1' }} />
    case 'TABLE': return <BorderOutlined style={{ color: '#13c2c2' }} />
    case 'HEADER': return <FormOutlined style={{ color: '#fa8c16' }} />
    default: return <InfoCircleOutlined />
  }
}

const QcReportTemplateEditor: React.FC = () => {
  const navigate = useNavigate()
  const params = useParams<{ id?: string }>()
  const isEdit = !!params.id
  const templateId = params.id ? parseInt(params.id) : undefined
  const { userInfo } = useAuthStore()

  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [template, setTemplate] = useState<QcReportTemplate | null>(null)
  const [placeholders, setPlaceholders] = useState<string[]>([])
  const [fieldBindings, setFieldBindings] = useState<QcFieldBinding[]>([])
  const [parseLoading, setParseLoading] = useState(false)
  const [previewVisible, setPreviewVisible] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [previewBlob, setPreviewBlob] = useState<Blob | null>(null)
  const [reUploadModal, setReUploadModal] = useState({ open: false })
  const [uploadProgress, setUploadProgress] = useState(0)
  const [autoBindTip, setAutoBindTip] = useState<string | null>(null)

  const allFieldOptions = useMemo(() => getAllQcFieldOptions(), [])
  const cascaderOptions = useMemo(() => buildCascaderOptions(), [])

  useEffect(() => {
    if (userInfo?.role !== 'ADMIN') {
      message.warning('仅管理员可编辑模板')
      navigate('/dashboard', { replace: true })
      return
    }
    if (isEdit && templateId) {
      loadTemplate()
    }
  }, [userInfo])

  const loadTemplate = async () => {
    setLoading(true)
    try {
      const data = (await qcReportTemplateApi.detail(templateId!)) as QcReportTemplate
      setTemplate(data)
      form.setFieldsValue({
        templateCode: data.templateCode,
        templateName: data.templateName,
        fileType: data.fileType,
        department: data.department,
        status: data.status,
        isDefault: data.isDefault === 1,
        enableWatermark: data.enableWatermark === 1,
        watermarkText: data.watermarkText,
        description: data.description,
        tags: data.tags,
        sortOrder: data.sortOrder,
      })
      setPlaceholders(data.placeholders || [])
      setFieldBindings(data.fieldBindings || [])
    } catch (e: any) {
      message.error(e?.message || '加载模板失败')
    } finally {
      setLoading(false)
    }
  }

  const handleParsePlaceholders = async () => {
    if (!templateId) return
    setParseLoading(true)
    try {
      const names = await qcReportTemplateApi.parsePlaceholders(templateId)
      setPlaceholders(names || [])
      if ((names?.length || 0) > 0) {
        message.success(`解析成功，发现 ${names!.length} 个占位符`)
      } else {
        message.warning('未发现占位符，请检查模板中是否使用了${占位符名称}格式')
      }
    } catch (e) {
      message.error('解析占位符失败')
    } finally {
      setParseLoading(false)
    }
  }

  const addBinding = (placeholderKey?: string) => {
    const idx = fieldBindings.length
    const defaultPlaceholder = placeholderKey || `placeholder_${Date.now()}`
    setFieldBindings([
      ...fieldBindings,
      {
        placeholderKey: defaultPlaceholder,
        placeholderLabel: defaultPlaceholder,
        qcFieldKey: '',
        qcFieldLabel: '',
        fieldType: 'SCALAR',
        sortOrder: idx,
      },
    ])
  }

  const updateBinding = (index: number, field: keyof QcFieldBinding, value: any) => {
    const updated = [...fieldBindings]
    updated[index] = { ...updated[index], [field]: value }
    setFieldBindings(updated)
  }

  const removeBinding = (index: number) => {
    const updated = fieldBindings.filter((_, i) => i !== index)
    setFieldBindings(updated)
  }

  const handleAutoBind = () => {
    if (placeholders.length === 0) {
      message.warning('请先解析占位符')
      return
    }
    const existingMap = new Map(fieldBindings.map((b) => [b.placeholderKey, b]))
    const optionMap = new Map(allFieldOptions.map((f) => [f.label, f]))
    const keyMap = new Map(allFieldOptions.map((f) => [f.key, f]))

    const newBindings: QcFieldBinding[] = []
    let autoMatched = 0

    placeholders.forEach((ph, idx) => {
      if (existingMap.has(ph)) {
        newBindings.push(existingMap.get(ph)!)
        return
      }
      let matched: any = null
      if (optionMap.has(ph)) {
        matched = optionMap.get(ph)
      } else {
        const cleanPh = ph.replace(/[\s_\-（）()【】\[\]]/g, '').toLowerCase()
        for (const opt of allFieldOptions) {
          const cleanLabel = opt.label.replace(/[\s_\-]/g, '').toLowerCase()
          if (cleanLabel === cleanPh || cleanLabel.includes(cleanPh) || cleanPh.includes(cleanLabel)) {
            matched = opt
            break
          }
        }
      }
      if (matched) {
        autoMatched++
        newBindings.push({
          placeholderKey: ph,
          placeholderLabel: ph,
          qcFieldKey: matched.key,
          qcFieldLabel: matched.label,
          fieldType: matched.type,
          sortOrder: idx,
          description: matched.description,
        })
      } else {
        newBindings.push({
          placeholderKey: ph,
          placeholderLabel: ph,
          qcFieldKey: '',
          qcFieldLabel: '',
          fieldType: 'SCALAR',
          sortOrder: idx,
        })
      }
    })

    setFieldBindings(newBindings)
    setAutoBindTip(`自动匹配完成：${autoMatched}/${placeholders.length} 个占位符已自动绑定`)
    message.success(`自动匹配 ${autoMatched} 个字段，请检查并手动补充未匹配项`)
    setTimeout(() => setAutoBindTip(null), 8000)
  }

  const handleSave = async (status?: QcReportTemplateStatus) => {
    setSaving(true)
    try {
      const values = await form.validateFields()
      const unbound = fieldBindings.filter((b) => !b.qcFieldKey)
      if (status === 'ACTIVE' && unbound.length > 0) {
        Modal.confirm({
          title: '存在未绑定字段',
          content: `有 ${unbound.length} 个占位符尚未绑定质控字段，启用后这些位置将显示为空。是否继续？`,
          okText: '继续启用',
          cancelText: '返回编辑',
          onOk: async () => {
            await doSave(values, status)
          },
        })
        setSaving(false)
        return
      }
      await doSave(values, status)
    } catch (e: any) {
      if (e?.errorFields) {
        setSaving(false)
        return
      }
      message.error(e?.message || '保存失败')
      setSaving(false)
    }
  }

  const doSave = async (values: any, status?: QcReportTemplateStatus) => {
    const payload: any = {
      ...values,
      isDefault: values.isDefault ? 1 : 0,
      enableWatermark: values.enableWatermark ? 1 : 0,
      fieldBindings,
      changeLog: values.changeLog || (isEdit ? '更新模板配置' : '创建模板'),
    }
    delete payload.changeLog
    if (status) payload.status = status

    try {
      let result: QcReportTemplate
      if (isEdit && templateId) {
        payload.changeLog = values.changeLog || '更新字段绑定或配置'
        result = (await qcReportTemplateApi.update(templateId, payload)) as QcReportTemplate
      } else {
        if (!values.templateCode) {
          payload.templateCode = `QC-RPT-${Date.now().toString().slice(-8)}`
        }
        payload.status = status || 'ACTIVE'
        payload.changeLog = values.changeLog || '初始版本'
        result = (await qcReportTemplateApi.create(payload)) as QcReportTemplate
      }
      message.success('保存成功')
      if (!isEdit) {
        navigate(`/qc-report-templates/${result.id}/edit`, { replace: true })
      } else {
        setTemplate(result)
      }
    } catch (e: any) {
      message.error(e?.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handlePreview = async () => {
    if (!templateId) {
      message.warning('请先保存模板后再预览')
      return
    }
    setPreviewVisible(true)
    setPreviewLoading(true)
    setPreviewUrl(null)
    setPreviewBlob(null)
    try {
      const values = form.getFieldsValue()
      const blob = await qcReportTemplateApi.preview(templateId, {
        enableWatermark: values.enableWatermark,
        watermarkText: values.watermarkText,
      })
      const b = blob as unknown as Blob
      setPreviewBlob(b)
      const url = URL.createObjectURL(b)
      setPreviewUrl(url)
    } catch (e) {
      console.error('预览加载失败', e)
    } finally {
      setPreviewLoading(false)
    }
  }

  const handleDownloadTemplate = () => {
    if (!templateId) return
    qcReportTemplateApi
      .downloadTemplate(templateId)
      .then((blob) => {
        const url = window.URL.createObjectURL(new Blob([blob as any]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', template?.originalFileName || `${form.getFieldValue('templateCode')}.docx`)
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
        message.success('下载成功')
      })
      .catch(() => message.error('下载失败'))
  }

  const reUploadProps: UploadProps = {
    name: 'file',
    accept: '.doc,.docx,.xls,.xlsx',
    showUploadList: false,
    customRequest: async (options) => {
      const file = options.file as File
      try {
        setUploadProgress(0)
        await qcReportTemplateApi.uploadTemplate(
          file,
          {
            templateName: form.getFieldValue('templateName'),
            fileType: form.getFieldValue('fileType'),
            department: form.getFieldValue('department'),
          },
          (p) => setUploadProgress(p)
        )
        options.onSuccess?.(null)
        message.success('模板文件已更新，请重新解析占位符')
        setReUploadModal({ open: false })
        if (templateId) {
          loadTemplate()
        }
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
      return true
    },
  }

  const bindingColumns = [
    {
      title: '序号',
      key: 'idx',
      width: 50,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '模板占位符',
      key: 'placeholder',
      width: 200,
      render: (_: any, record: QcFieldBinding, index: number) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Input
            size="small"
            value={record.placeholderKey}
            placeholder="占位符标识"
            onChange={(e) => updateBinding(index, 'placeholderKey', e.target.value)}
          />
          <Input
            size="small"
            value={record.placeholderLabel}
            placeholder="显示名称"
            onChange={(e) => updateBinding(index, 'placeholderLabel', e.target.value)}
          />
        </Space>
      ),
    },
    {
      title: '字段类型',
      key: 'fieldType',
      width: 100,
      align: 'center' as const,
      render: (_: any, record: QcFieldBinding, index: number) => (
        <Select
          size="small"
          value={record.fieldType}
          onChange={(v) => updateBinding(index, 'fieldType', v)}
          style={{ width: '100%' }}
        >
          <Option value="SCALAR">
            <Space size={4}><FieldStringOutlined />标量值</Space>
          </Option>
          <Option value="LIST">
            <Space size={4}><BarsOutlined />列表</Space>
          </Option>
          <Option value="TABLE">
            <Space size={4}><BorderOutlined />表格</Space>
          </Option>
          <Option value="HEADER">
            <Space size={4}><FormOutlined />表头/页脚</Space>
          </Option>
        </Select>
      ),
    },
    {
      title: '绑定质控字段',
      key: 'qcField',
      render: (_: any, record: QcFieldBinding, index: number) => {
        const matched = allFieldOptions.find((f) => f.key === record.qcFieldKey)
        const defaultValue = matched ? [matched.group, matched.key] : undefined
        return (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Cascader
              size="small"
              options={cascaderOptions as any}
              value={defaultValue}
              onChange={(val, selectedOptions: any) => {
                if (val && val.length === 2 && selectedOptions?.[1]) {
                  const opt = selectedOptions[1] as any
                  updateBinding(index, 'qcFieldKey', val[1] as string)
                  updateBinding(index, 'qcFieldLabel', opt.rawLabel || opt.label)
                  updateBinding(index, 'fieldType', opt.rawType || 'SCALAR')
                  if (opt.rawDesc) {
                    updateBinding(index, 'description', opt.rawDesc)
                  }
                } else {
                  updateBinding(index, 'qcFieldKey', '')
                  updateBinding(index, 'qcFieldLabel', '')
                }
              }}
              placeholder="选择质控字段..."
              style={{ width: '100%' }}
              expandTrigger="hover"
              showSearch={{ filter: (input, path) =>
                path.some(o => (o.label as string).toString().toLowerCase().includes(input.toLowerCase()))
              }}
            />
            {record.qcFieldKey && (
              <Space size={6} style={{ fontSize: 11, color: '#8c8c8c' }}>
                {getFieldIcon(record.fieldType)}
                <code style={{ fontSize: 11 }}>${record.qcFieldKey}</code>
                {record.description && (
                  <Tooltip title={record.description}>
                    <InfoCircleOutlined />
                  </Tooltip>
                )}
                <LinkOutlined style={{ color: '#52c41a' }} />
                <span style={{ color: '#52c41a' }}>已绑定: {record.qcFieldLabel}</span>
              </Space>
            )}
            {!record.qcFieldKey && (
              <Space size={6} style={{ fontSize: 11, color: '#fa8c16' }}>
                <UnlinkOutlined />
                <span>未绑定 - 此字段将留空</span>
              </Space>
            )}
          </Space>
        )
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 60,
      align: 'center' as const,
      render: (_: any, __: any, index: number) => (
        <Tooltip title="删除此行">
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeBinding(index)} />
        </Tooltip>
      ),
    },
  ]

  const bindingStats = useMemo(() => {
    const total = fieldBindings.length
    const bound = fieldBindings.filter((b) => b.qcFieldKey).length
    return { total, bound, unbound: total - bound }
  }, [fieldBindings])

  const placeholderStats = useMemo(() => {
    const placeholderSet = new Set(fieldBindings.map((b) => b.placeholderKey))
    const used = placeholders.filter((p) => placeholderSet.has(p)).length
    const unused = placeholders.filter((p) => !placeholderSet.has(p))
    return { total: placeholders.length, used, unused }
  }, [placeholders, fieldBindings])

  const basicInfoCard = (
    <Card
      style={{ marginBottom: 16 }}
      title={
        <Space>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/qc-report-templates')} />
          <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
          <span>{isEdit ? '编辑质控报告模板' : '新建质控报告模板'}</span>
          {template && <Tag color="blue">v{template.currentVersion}</Tag>}
        </Space>
      }
      extra={
        <Space>
          {isEdit && (
            <>
              <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
                下载模板文件
              </Button>
              <Button icon={<UploadOutlined />} onClick={() => setReUploadModal({ open: true })}>
                重新上传文件
              </Button>
            </>
          )}
          <Button icon={<EyeOutlined />} onClick={handlePreview}>
            预览效果
          </Button>
          <Button icon={<SaveOutlined />} onClick={() => handleSave('DRAFT')} loading={saving}>
            保存草稿
          </Button>
          <Button type="primary" icon={<SaveOutlined />} onClick={() => handleSave('ACTIVE')} loading={saving}>
            保存并启用
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Row gutter={16}>
          <Col span={6}>
            <Form.Item
              name="templateCode"
              label="模板编码"
              rules={[{ required: !isEdit, message: '请输入模板编码' }]}
              extra="全局唯一，建议使用 QC-RPT- 前缀"
            >
              <Input placeholder="如: QC-RPT-GENERAL-STANDARD" disabled={isEdit} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item
              name="templateName"
              label="模板名称"
              rules={[{ required: true, message: '请输入模板名称' }]}
            >
              <Input placeholder="如: 普外科手术质控报告标准版" />
            </Form.Item>
          </Col>
          <Col span={4}>
            <Form.Item
              name="fileType"
              label="文件类型"
              rules={[{ required: true, message: '请选择文件类型' }]}
            >
              <Select disabled={isEdit}>
                <Option value="WORD">
                  <Space size={4}><FileWordOutlined />Word文档</Space>
                </Option>
                <Option value="EXCEL">
                  <Space size={4}><FileExcelOutlined />Excel表格</Space>
                </Option>
              </Select>
            </Form.Item>
          </Col>
          <Col span={4}>
            <Form.Item name="department" label="适用科室" extra="不选表示全院通用">
              <Select placeholder="选择科室" allowClear>
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
          </Col>
          <Col span={4}>
            <Form.Item name="status" label="状态" initialValue="DRAFT">
              <Select>
                {Object.entries(QcReportTemplateStatusMap).map(([key, val]) => (
                  <Option key={key} value={key}>
                    {val.label}
                  </Option>
                ))}
              </Select>
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={4}>
            <Form.Item name="isDefault" label="设为默认" valuePropName="checked" initialValue={false}>
              <Switch />
            </Form.Item>
          </Col>
          <Col span={4}>
            <Form.Item name="enableWatermark" label="启用科室水印" valuePropName="checked" initialValue={true}>
              <Switch />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="watermarkText" label="水印文本" extra="默认为科室名称+质控科">
              <Input placeholder="如: 普外科质控科" />
            </Form.Item>
          </Col>
          <Col span={4}>
            <Form.Item name="sortOrder" label="排序号" initialValue={0}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item name="tags" label="标签">
              <Input placeholder="多个标签用逗号分隔" />
            </Form.Item>
          </Col>
        </Row>
        <Row>
          <Col span={24}>
            <Form.Item name="description" label="模板说明">
              <TextArea rows={2} placeholder="简要说明模板适用场景、格式特点等" />
            </Form.Item>
          </Col>
        </Row>
        {isEdit && (
          <Row>
            <Col span={24}>
              <Form.Item name="changeLog" label="版本变更说明">
                <TextArea rows={2} placeholder="描述本次修改内容，将作为版本记录保存" />
              </Form.Item>
            </Col>
          </Row>
        )}
      </Form>

      {isEdit && template && (
        <Descriptions column={3} size="small" bordered style={{ marginTop: 4 }}>
          <Descriptions.Item label="原始文件">
            <Space>
              <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{template.originalFileName || '-'}</span>
              {template.fileSize && (
                <Tag color="default" style={{ margin: 0 }}>{formatSize(template.fileSize)}</Tag>
              )}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="创建人">{template.createdUserName || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{template.createdTime ? dayjs(template.createdTime).format('YYYY-MM-DD HH:mm') : '-'}</Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  )

  const placeholderPanel = (
    <Card
      size="small"
      title={
        <Space>
          <SettingOutlined />
          <span>模板占位符解析</span>
          {placeholderStats.total > 0 && (
            <Badge count={placeholderStats.total} style={{ backgroundColor: '#1677ff' }} offset={[0, 0]} />
          )}
        </Space>
      }
      extra={
        <Space>
          <Tooltip title="从已上传的模板文件中自动解析 ${占位符} 标记">
            <Button
              size="small"
              icon={<SyncOutlined />}
              onClick={handleParsePlaceholders}
              loading={parseLoading}
              disabled={!isEdit}
            >
              解析占位符
            </Button>
          </Tooltip>
        </Space>
      }
      style={{ marginBottom: 16 }}
    >
      {placeholders.length === 0 ? (
        <Empty
          description={
            <Space direction="vertical">
              <span>{isEdit ? '尚未解析占位符' : '请先保存并上传模板文件'}</span>
              {isEdit && (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginTop: 8, textAlign: 'left' }}
                  message="使用说明"
                  description={
                    <ul style={{ margin: 0, paddingLeft: 18 }}>
                      <li>在 Word/Excel 模板中使用 {'${占位符名称}'} 格式标记需要填充的位置</li>
                      <li>例如：{'${患者姓名}'} 将在生成报告时被实际患者姓名替换</li>
                      <li>常用占位符命名建议与质控字段一致，便于自动匹配</li>
                      <li>点击上方"解析占位符"按钮自动提取所有标记</li>
                    </ul>
                  }
                />
              )}
            </Space>
          }
          style={{ padding: '24px 0' }}
        />
      ) : (
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <Row gutter={12} align="middle">
            <Col>
              <Space size={12}>
                <Tag color="blue">共 {placeholderStats.total} 个</Tag>
                <Tag color="green">已纳入 {placeholderStats.used} 个</Tag>
                {placeholderStats.unused.length > 0 && (
                  <Tag color="orange">未绑定 {placeholderStats.unused.length} 个</Tag>
                )}
              </Space>
            </Col>
            {placeholderStats.unused.length > 0 && (
              <Col flex="auto" style={{ textAlign: 'right' }}>
                <Space wrap size={[4, 4]}>
                  <span style={{ fontSize: 12, color: '#8c8c8c' }}>尚未纳入绑定的：</span>
                  {placeholderStats.unused.slice(0, 8).map((p) => (
                    <Tooltip key={p} title="点击添加到绑定表">
                      <Tag
                        color="orange"
                        style={{ cursor: 'pointer' }}
                        onClick={() => addBinding(p)}
                      >
                        + {'${' + p + '}'}
                      </Tag>
                    </Tooltip>
                  ))}
                  {placeholderStats.unused.length > 8 && (
                    <Tag color="orange">+{placeholderStats.unused.length - 8} 更多</Tag>
                  )}
                </Space>
              </Col>
            )}
          </Row>
          <List
            size="small"
            grid={{ gutter: 8, xs: 1, sm: 2, md: 3, lg: 4, xl: 6 }}
            dataSource={placeholders}
            renderItem={(item) => {
              const isUsed = !placeholderStats.unused.includes(item)
              return (
                <List.Item>
                  <Card
                    size="small"
                    style={{
                      borderColor: isUsed ? '#b7eb8f' : '#ffe58f',
                      background: isUsed ? '#f6ffed' : '#fffbe6',
                    }}
                    bodyStyle={{ padding: '8px 12px' }}
                  >
                    <Space direction="vertical" size={0} style={{ width: '100%' }}>
                      <Space size={4}>
                        <Avatar size={20} icon={isUsed ? <LinkOutlined /> : <UnlinkOutlined />} style={{
                          backgroundColor: isUsed ? '#52c41a' : '#fa8c16',
                        }} />
                        <span style={{ fontSize: 12, fontWeight: 500 }}>{item}</span>
                      </Space>
                      <code style={{ fontSize: 10, color: '#8c8c8c', marginTop: 4 }}>
                        {'${' + item + '}'}
                      </code>
                    </Space>
                  </Card>
                </List.Item>
              )
            }}
          />
        </Space>
      )}
    </Card>
  )

  const bindingPanel = (
    <Card
      size="small"
      title={
        <Space>
          <DatabaseOutlined style={{ color: '#1677ff' }} />
          <span>质控字段绑定配置</span>
          {bindingStats.total > 0 && (
            <Space size={6}>
              <Tag color="blue">{bindingStats.bound}/{bindingStats.total}</Tag>
              <Progress
                type="circle"
                size={24}
                percent={bindingStats.total === 0 ? 0 : Math.round((bindingStats.bound / bindingStats.total) * 100)}
                strokeColor={bindingStats.bound === bindingStats.total ? '#52c41a' : '#1677ff'}
                format={(p) => <span style={{ fontSize: 8, fontWeight: 600 }}>{p}%</span>}
              />
            </Space>
          )}
        </Space>
      }
      extra={
        <Space>
          {autoBindTip && (
            <Alert type="success" showIcon message={autoBindTip} style={{ padding: '4px 12px', marginRight: 8 }} />
          )}
          <Tooltip title="根据占位符名称自动匹配质控字段">
            <Button
              size="small"
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={handleAutoBind}
              disabled={placeholders.length === 0}
            >
              智能自动绑定
            </Button>
          </Tooltip>
          <Tooltip title="根据已解析占位符创建空的绑定行">
            <Button
              size="small"
              icon={<SyncOutlined />}
              onClick={() => {
                if (placeholders.length === 0) {
                  message.warning('请先解析占位符')
                  return
                }
                const existingKeys = new Set(fieldBindings.map((b) => b.placeholderKey))
                const toAdd = placeholders.filter((p) => !existingKeys.has(p))
                if (toAdd.length === 0) {
                  message.info('所有占位符已在绑定表中')
                  return
                }
                const currentLen = fieldBindings.length
                const newRows = toAdd.map((p, i) => ({
                  placeholderKey: p,
                  placeholderLabel: p,
                  qcFieldKey: '',
                  qcFieldLabel: '',
                  fieldType: 'SCALAR' as const,
                  sortOrder: currentLen + i,
                }))
                setFieldBindings([...fieldBindings, ...newRows])
                message.success(`已添加 ${toAdd.length} 行绑定记录`)
              }}
            >
              同步占位符
            </Button>
          </Tooltip>
          <Button size="small" icon={<PlusOutlined />} onClick={() => addBinding()}>
            添加行
          </Button>
        </Space>
      }
    >
      <Table
        size="small"
        rowKey={(_, idx) => String(idx)}
        dataSource={fieldBindings}
        columns={bindingColumns}
        pagination={false}
        scroll={{ y: 420 }}
        rowClassName={(_, idx) =>
          fieldBindings[idx]?.qcFieldKey ? '' : 'qc-row-unbound'
        }
        locale={{
          emptyText: (
            <Empty
              description={
                <Space direction="vertical">
                  <span>暂无字段绑定配置</span>
                  <Space>
                    <Button size="small" onClick={handleAutoBind} disabled={placeholders.length === 0}>
                      智能自动绑定
                    </Button>
                    <Button size="small" onClick={() => addBinding()} type="primary">
                      手动添加
                    </Button>
                  </Space>
                </Space>
              }
            }
          }}
        }}
      />
      <style>{`
        .qc-row-unbound td { background: #fffbe6 !important; }
        .qc-row-unbound:hover td { background: #fff7cc !important; }
      `}</style>
    </Card>
  )

  const fieldLibPanel = (
    <Card
      size="small"
      title={
        <Space>
          <InfoCircleOutlined style={{ color: '#722ed1' }} />
          <span>可用质控字段参考</span>
          <Tag color="purple">{allFieldOptions.length} 个</Tag>
        </Space>
      }
      bodyStyle={{ padding: 8, maxHeight: 520, overflow: 'auto' }}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        {QC_AVAILABLE_FIELDS.map((g) => (
          <div key={g.group}>
            <div style={{
              fontSize: 12,
              fontWeight: 600,
              color: '#1677ff',
              marginBottom: 6,
              paddingLeft: 6,
              borderLeft: '3px solid #1677ff',
            }}>
              {g.groupLabel}（{g.fields.length}）
            </div>
            <Space wrap size={[6, 6]}>
              {g.fields.map((f) => {
                const boundCount = fieldBindings.filter((b) => b.qcFieldKey === f.key).length
                return (
                  <Tooltip
                    key={f.key}
                    title={
                      <Space direction="vertical" size={2} style={{ fontSize: 12 }}>
                        <span><b>字段标识：</b><code>{f.key}</code></span>
                        <span><b>数据类型：</b>{f.type === 'SCALAR' ? '标量值' : f.type === 'LIST' ? '列表' : '表格'}</span>
                        {f.description && <span><b>说明：</b>{f.description}</span>}
                        <span><b>已绑定次数：</b>{boundCount}</span>
                      </Space>
                    }
                  >
                    <Tag
                      icon={getFieldIcon(f.type)}
                      color={
                        boundCount > 0 ? (
                          f.type === 'SCALAR' ? 'blue' :
                          f.type === 'LIST' ? 'purple' : 'geekblue'
                        ) : 'default'
                      }
                      style={{
                        margin: 0,
                        fontWeight: boundCount > 0 ? 600 : 400,
                      }}
                    >
                      {f.label}
                      {boundCount > 0 && <span style={{ opacity: 0.7 }}> ×{boundCount}</span>}
                    </Tag>
                  </Tooltip>
                )
              })}
            </Space>
          </div>
        ))}
      </Space>
    </Card>
  )

  return (
    <div className="page-container">
      {loading ? (
        <Card style={{ textAlign: 'center', padding: '80px 0' }}>
          <SyncOutlined spin style={{ fontSize: 28, color: '#1677ff' }} />
          <div style={{ marginTop: 12, color: '#8c8c8c' }}>正在加载模板数据...</div>
        </Card>
      ) : (
        <>
          {basicInfoCard}

          <Row gutter={16}>
            <Col xs={24} lg={16}>
              {placeholderPanel}
              {bindingPanel}
            </Col>
            <Col xs={24} lg={8}>
              {fieldLibPanel}
            </Col>
          </Row>
        </>
      )}

      <Modal
        title={<Space><UploadOutlined />重新上传模板文件</Space>}
        open={reUploadModal.open}
        onCancel={() => setReUploadModal({ open: false })}
        footer={null}
        width={520}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <Alert
            type="warning"
            showIcon
            message="注意事项"
            description="重新上传后，原有的字段绑定配置将保留。如果新模板中占位符发生变化，请重新解析并检查绑定关系。"
          />
          <Upload.Dragger {...reUploadProps} multiple={false}>
            <p className="ant-upload-drag-icon">
              <UploadOutlined style={{ color: '#1677ff' }} />
            </p>
            <p className="ant-upload-text">点击或拖拽新文件到此处</p>
            <p className="ant-upload-hint">替换现有模板文件，绑定配置将保留</p>
            {uploadProgress > 0 && (
              <div style={{ marginTop: 12, padding: '0 24px' }}>
                <Progress percent={uploadProgress} size="small" />
              </div>
            )}
          </Upload.Dragger>
        </Space>
      </Modal>

      <Modal
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
            <span>模板预览</span>
            {form.getFieldValue('enableWatermark') && <Tag color="cyan">含水印</Tag>}
          </Space>
        }
        open={previewVisible}
        onCancel={() => setPreviewVisible(false)}
        onOk={() => setPreviewVisible(false)}
        okText="关闭"
        cancelButtonProps={{ style: { display: 'none' } }}
        width={920}
        footer={
          <Space>
            <Button onClick={() => setPreviewVisible(false)}>关闭</Button>
            {previewBlob && (
              <Button
                icon={<DownloadOutlined />}
                onClick={() => {
                  const url = URL.createObjectURL(previewBlob)
                  const a = document.createElement('a')
                  a.href = url
                  a.download = `预览_${form.getFieldValue('templateCode') || 'template'}.pdf`
                  a.click()
                  URL.revokeObjectURL(url)
                }}
              >
                下载预览PDF
              </Button>
            )}
            <Button icon={<ReloadOutlined />} onClick={handlePreview} loading={previewLoading}>
              重新生成
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Alert
            type="info"
            showIcon
            message="预览使用示例数据生成"
            description="预览报告中的数据为系统生成的示例数据，实际导出时将使用真实质控数据填充"
          />
          <Card
            size="small"
            bodyStyle={{ padding: 0 }}
            style={{ border: '1px solid #e5e7eb' }}
          >
            {previewLoading ? (
              <div style={{ padding: 80, textAlign: 'center', color: '#8c8c8c' }}>
                <ReloadOutlined spin style={{ fontSize: 28, marginBottom: 12 }} />
                <div>正在根据模板生成预览PDF，请稍候...</div>
              </div>
            ) : previewUrl ? (
              <iframe
                src={previewUrl}
                style={{ width: '100%', height: 600, border: 'none' }}
                title="PDF预览"
              />
            ) : (
              <div style={{ padding: 80, textAlign: 'center' }}>
                <FilePdfOutlined style={{ fontSize: 48, color: '#ff4d4f', marginBottom: 12 }} />
                <Empty description="预览加载失败，请点击右上角重新生成" />
              </div>
            )}
          </Card>
        </Space>
      </Modal>
    </div>
  )
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default QcReportTemplateEditor
