import React, { useState, useEffect, useRef, useMemo } from 'react'
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
  Popconfirm,
  Empty,
} from 'antd'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  EyeOutlined,
  PlusOutlined,
  DeleteOutlined,
  SyncOutlined,
  CopyOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import Editor from '@monaco-editor/react'
import { templateApi } from '@/services/api'
import type { SurgeryTemplate, Placeholder, TemplateStatus } from '@/types'
import { TemplateStatusMap, EntityTypeLabelMap } from '@/types'

const { Option } = Select
const { TextArea } = Input

const DEFAULT_TEMPLATE_CONTENT = `术前诊断：\${术前诊断}
术后诊断：\${术后诊断}
手术名称：\${手术名称}
手术日期：\${手术日期}
手术医生：\${手术医生}
第一助手：\${第一助手}
麻醉方式：\${麻醉方式}
麻醉医生：\${麻醉医生}
器械护士：\${器械护士}
巡回护士：\${巡回护士}
切口等级：\${切口等级}

手术经过：
患者取仰卧位，\${麻醉方式}满意后，常规消毒铺巾。

术中出血约\${失血量}ml，输液\${输液量}ml，输血\${输血量}ml。
术中患者生命体征平稳，术毕安返病房。

术中并发症：\${术中并发症}`

const SurgeryTemplateEditor: React.FC = () => {
  const navigate = useNavigate()
  const params = useParams<{ id?: string }>()
  const isEdit = !!params.id
  const templateId = params.id ? parseInt(params.id) : undefined

  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [template, setTemplate] = useState<SurgeryTemplate | null>(null)
  const [content, setContent] = useState(DEFAULT_TEMPLATE_CONTENT)
  const [placeholders, setPlaceholders] = useState<Placeholder[]>([])
  const [extractedNames, setExtractedNames] = useState<string[]>([])
  const [previewVisible, setPreviewVisible] = useState(false)
  const [previewFilled, setPreviewFilled] = useState('')
  const editorRef = useRef<any>(null)

  useEffect(() => {
    if (isEdit && templateId) {
      loadTemplate()
    } else {
      extractPlaceholdersFromContent(DEFAULT_TEMPLATE_CONTENT)
    }
  }, [])

  useEffect(() => {
    extractPlaceholdersFromContent(content)
  }, [content])

  const loadTemplate = async () => {
    setLoading(true)
    try {
      const data = (await templateApi.detail(templateId!)) as SurgeryTemplate
      setTemplate(data)
      form.setFieldsValue({
        templateCode: data.templateCode,
        templateName: data.templateName,
        surgeryType: data.surgeryType,
        surgeryCode: data.surgeryCode,
        department: data.department,
        status: data.status,
        isDefault: data.isDefault === 1,
        description: data.description,
        tags: data.tags,
        sortOrder: data.sortOrder,
      })
      setContent(data.templateContent)
      setPlaceholders(data.placeholders || [])
    } catch (e: any) {
      message.error(e?.message || '加载模板失败')
    } finally {
      setLoading(false)
    }
  }

  const extractPlaceholdersFromContent = async (text: string) => {
    try {
      const names = await templateApi.extractPlaceholders(text)
      setExtractedNames(names || [])
    } catch (e) {
      console.error('提取占位符失败', e)
    }
  }

  const syncPlaceholdersFromContent = () => {
    const existingMap = new Map(placeholders.map((p) => [p.name, p]))
    const synced: Placeholder[] = extractedNames.map((name) => {
      const existing = existingMap.get(name)
      if (existing) return existing
      return {
        name,
        label: name,
        required: false,
      }
    })
    setPlaceholders(synced)
    message.success(`已同步 ${synced.length} 个占位符`)
  }

  const addPlaceholder = () => {
    setPlaceholders([
      ...placeholders,
      {
        name: `new_placeholder_${Date.now()}`,
        label: '新占位符',
        required: false,
      },
    ])
  }

  const updatePlaceholder = (index: number, field: keyof Placeholder, value: any) => {
    const updated = [...placeholders]
    updated[index] = { ...updated[index], [field]: value }
    setPlaceholders(updated)
  }

  const removePlaceholder = (index: number) => {
    const updated = placeholders.filter((_, i) => i !== index)
    setPlaceholders(updated)
  }

  const handleSave = async (status?: TemplateStatus) => {
    setSaving(true)
    try {
      const values = await form.validateFields()
      const payload: any = {
        ...values,
        isDefault: values.isDefault ? 1 : 0,
        templateContent: content,
        placeholders,
        changeLog: values.changeLog || (isEdit ? '更新模板' : '创建模板'),
      }
      delete payload.changeLog
      if (status) payload.status = status

      let result: SurgeryTemplate
      if (isEdit && templateId) {
        payload.changeLog = values.changeLog || '更新模板内容'
        result = (await templateApi.update(templateId, payload)) as SurgeryTemplate
      } else {
        if (!values.templateCode) {
          payload.templateCode = `TPL-${Date.now().toString().slice(-8)}`
        }
        payload.changeLog = values.changeLog || '初始版本'
        result = (await templateApi.create(payload)) as SurgeryTemplate
      }
      message.success('保存成功')
      if (!isEdit) {
        navigate(`/templates/${result.id}/edit`, { replace: true })
      } else {
        setTemplate(result)
      }
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handlePreview = async () => {
    const fillValues: Record<string, string> = {}
    placeholders.forEach((p) => {
      fillValues[p.name] = p.defaultValue || `【${p.label || p.name}】`
    })
    try {
      const filled = await templateApi.fill(0, fillValues)
      setPreviewFilled(filled)
    } catch {
      let temp = content
      placeholders.forEach((p) => {
        temp = temp.replace(new RegExp(`\\$\\{${p.name}\\}`, 'g'), `【${p.label || p.name}】`)
      })
      setPreviewFilled(temp)
    }
    setPreviewVisible(true)
  }

  const insertPlaceholder = (name: string) => {
    if (editorRef.current) {
      const editor = editorRef.current
      const selection = editor.getSelection()
      const range = selection
        ? new (window as any).monaco.Range(
            selection.startLineNumber,
            selection.startColumn,
            selection.endLineNumber,
            selection.endColumn
          )
        : null
      editor.executeEdits('insert-placeholder', [
        {
          range: range || editor.getSelection(),
          text: `\${${name}}`,
          forceMoveMarkers: true,
        },
      ])
      editor.focus()
    }
  }

  const placeholderColumns = [
    {
      title: '占位符名称',
      dataIndex: 'name',
      width: 180,
      render: (_: string, record: Placeholder, index: number) => (
        <Input
          value={record.name}
          size="small"
          onChange={(e) => updatePlaceholder(index, 'name', e.target.value)}
          placeholder="如: 手术日期"
        />
      ),
    },
    {
      title: '显示标签',
      dataIndex: 'label',
      width: 150,
      render: (_: string, record: Placeholder, index: number) => (
        <Input
          value={record.label}
          size="small"
          onChange={(e) => updatePlaceholder(index, 'label', e.target.value)}
          placeholder="显示名"
        />
      ),
    },
    {
      title: '关联实体',
      dataIndex: 'entityType',
      width: 180,
      render: (_: string, record: Placeholder, index: number) => (
        <Select
          value={record.entityType}
          size="small"
          allowClear
          placeholder="选择实体类型"
          onChange={(v) => updatePlaceholder(index, 'entityType', v)}
          style={{ width: '100%' }}
        >
          {Object.entries(EntityTypeLabelMap).map(([key, val]) => (
            <Option key={key} value={key}>
              {val.label}
            </Option>
          ))}
        </Select>
      ),
    },
    {
      title: '默认值',
      dataIndex: 'defaultValue',
      width: 150,
      render: (_: string, record: Placeholder, index: number) => (
        <Input
          value={record.defaultValue}
          size="small"
          onChange={(e) => updatePlaceholder(index, 'defaultValue', e.target.value)}
          placeholder="默认值"
        />
      ),
    },
    {
      title: '必填',
      dataIndex: 'required',
      width: 80,
      align: 'center' as const,
      render: (_: any, record: Placeholder, index: number) => (
        <Switch
          size="small"
          checked={record.required}
          onChange={(v) => updatePlaceholder(index, 'required', v)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      align: 'center' as const,
      render: (_: any, __: any, index: number) => (
        <Popconfirm title="删除此占位符？" onConfirm={() => removePlaceholder(index)}>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ]

  const tabItems = [
    {
      key: 'editor',
      label: (
        <Space>
          <ThunderboltOutlined />
          模板编辑
          {extractedNames.length > 0 && (
            <Tag color="geekblue" style={{ marginLeft: 4 }}>
              {extractedNames.length} 占位符
            </Tag>
          )}
        </Space>
      ),
      children: (
        <div style={{ display: 'flex', height: 'calc(100vh - 340px)', minHeight: 500 }}>
          <div style={{ flex: 1, border: '1px solid #e5e7eb', borderRadius: 8, overflow: 'hidden' }}>
            <Editor
              height="100%"
              defaultLanguage="plaintext"
              theme="vs-light"
              value={content}
              onChange={(v) => setContent(v || '')}
              onMount={(editor) => {
                editorRef.current = editor
              }}
              options={{
                fontSize: 14,
                lineNumbers: 'on',
                minimap: { enabled: false },
                wordWrap: 'on',
                wrappingIndent: 'same',
                tabSize: 2,
                scrollBeyondLastLine: false,
                automaticLayout: true,
                readOnly: loading,
              }}
            />
          </div>
          <Card
            title={
              <Space>
                <span>快捷插入</span>
                <Tooltip title="从模板内容同步占位符">
                  <Button
                    type="text"
                    size="small"
                    icon={<SyncOutlined />}
                    onClick={syncPlaceholdersFromContent}
                  >
                    同步
                  </Button>
                </Tooltip>
              </Space>
            }
            style={{ width: 280, marginLeft: 16 }}
            bodyStyle={{ padding: 12, overflow: 'auto', maxHeight: '100%' }}
            size="small"
          >
            <div style={{ marginBottom: 12 }}>
              <Button type="dashed" block icon={<PlusOutlined />} onClick={addPlaceholder} size="small">
                自定义占位符
              </Button>
            </div>
            {extractedNames.length === 0 && placeholders.length === 0 ? (
              <Empty description="暂无占位符" style={{ marginTop: 20 }} />
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size={4}>
                {extractedNames.map((name) => {
                  const configured = placeholders.find((p) => p.name === name)
                  return (
                    <Tooltip key={name} title={`点击插入 \${${name}}`}>
                      <Button
                        block
                        size="small"
                        style={{
                          justifyContent: 'flex-start',
                          background: configured ? '#e6f4ff' : undefined,
                        }}
                        onClick={() => insertPlaceholder(name)}
                      >
                        <Tag color={configured ? 'geekblue' : 'default'} style={{ marginRight: 8 }}>
                          {configured?.label || name}
                          {configured?.required && <span style={{ color: '#ff4d4f' }}>*</span>}
                        </Tag>
                        <code style={{ fontSize: 12, color: '#8c8c8c' }}>${name}</code>
                      </Button>
                    </Tooltip>
                  )
                })}
                {placeholders
                  .filter((p) => !extractedNames.includes(p.name))
                  .map((p) => (
                    <Tooltip key={p.name} title={`点击插入 \${${p.name}}`}>
                      <Button block size="small" style={{ justifyContent: 'flex-start' }} onClick={() => insertPlaceholder(p.name)}>
                        <Tag color="geekblue" style={{ marginRight: 8 }}>
                          {p.label || p.name}
                          {p.required && <span style={{ color: '#ff4d4f' }}>*</span>}
                        </Tag>
                        <code style={{ fontSize: 12, color: '#8c8c8c' }}>${p.name}</code>
                      </Button>
                    </Tooltip>
                  ))}
              </Space>
            )}
            <Divider style={{ margin: '12px 0' }} />
            <div style={{ fontSize: 12, color: '#8c8c8c' }}>
              提示：在编辑框中使用 <code style={{ background: '#f0f0f0', padding: '0 4px', borderRadius: 3 }}>${'{名称}'}</code> 格式定义占位符
            </div>
          </Card>
        </div>
      ),
    },
    {
      key: 'placeholders',
      label: (
        <Space>
          <CopyOutlined />
          占位符配置
          {placeholders.length > 0 && (
            <Tag color="geekblue" style={{ marginLeft: 4 }}>
              {placeholders.length}
            </Tag>
          )}
        </Space>
      ),
      children: (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <Space>
              <Button icon={<SyncOutlined />} onClick={syncPlaceholdersFromContent}>
                从模板内容同步
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={addPlaceholder}>
                添加占位符
              </Button>
            </Space>
          </div>
          <Table
            rowKey="name"
            size="small"
            dataSource={placeholders}
            columns={placeholderColumns}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无占位符配置，点击上方按钮添加或同步" /> }}
          />
        </Space>
      ),
    },
  ]

  return (
    <div className="page-container">
      <Card
        style={{ marginBottom: 16 }}
        title={
          <Space>
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/templates')} />
            <span>{isEdit ? '编辑模板' : '新建模板'}</span>
            {template && <Tag color="blue">v{template.currentVersion}</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Button icon={<EyeOutlined />} onClick={handlePreview}>
              预览
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
                extra="全局唯一，建议使用 TPL-前缀"
              >
                <Input placeholder="如: TPL-LAP-APPENDECTOMY" disabled={isEdit} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item
                name="templateName"
                label="模板名称"
                rules={[{ required: true, message: '请输入模板名称' }]}
              >
                <Input placeholder="如: 腹腔镜阑尾切除术模板" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item
                name="surgeryType"
                label="手术类型"
                rules={[{ required: true, message: '请输入手术类型' }]}
              >
                <Input placeholder="如: 腹腔镜阑尾切除术" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="surgeryCode" label="手术编码">
                <Input placeholder="ICD-9-CM-3, 如: 47.01" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="department" label="适用科室" extra="不选表示全院通用">
                <Select placeholder="选择科室" allowClear>
                  <Option value="普外科">普外科</Option>
                  <Option value="骨科">骨科</Option>
                  <Option value="妇产科">妇产科</Option>
                  <Option value="神经外科">神经外科</Option>
                  <Option value="心胸外科">心胸外科</Option>
                  <Option value="泌尿外科">泌尿外科</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="status" label="状态" initialValue="DRAFT">
                <Select>
                  {Object.entries(TemplateStatusMap).map(([key, val]) => (
                    <Option key={key} value={key}>
                      {val.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="isDefault" label="设为默认" valuePropName="checked" initialValue={false}>
                <Switch />
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
                <TextArea rows={2} placeholder="简要说明模板适用场景" />
              </Form.Item>
            </Col>
          </Row>
          {isEdit && (
            <Row>
              <Col span={24}>
                <Form.Item name="changeLog" label="版本变更说明">
                  <TextArea rows={2} placeholder="描述本次修改内容，将作为版本记录" />
                </Form.Item>
              </Col>
            </Row>
          )}
        </Form>
      </Card>

      <Card bodyStyle={{ padding: 0 }}>
        <Tabs defaultActiveKey="editor" items={tabItems} style={{ padding: '0 16px' }} />
      </Card>

      <Modal
        title="模板预览"
        open={previewVisible}
        onCancel={() => setPreviewVisible(false)}
        onOk={() => setPreviewVisible(false)}
        okText="关闭"
        cancelButtonProps={{ style: { display: 'none' } }}
        width={800}
      >
        <div
          style={{
            background: '#f6f8fa',
            padding: 16,
            borderRadius: 8,
            fontFamily: 'monospace',
            whiteSpace: 'pre-wrap',
            maxHeight: 500,
            overflow: 'auto',
            fontSize: 13,
            lineHeight: 1.8,
          }}
        >
          {previewFilled || content}
        </div>
      </Modal>
    </div>
  )
}

export default SurgeryTemplateEditor
