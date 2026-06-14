import React, { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  InputNumber,
  message,
  Popconfirm,
  Tabs,
  Row,
  Col,
  Typography,
  Tooltip,
  Divider,
  Empty,
  Spin,
  Draggable,
  Alert,
} from 'antd'
import {
  FileExcelOutlined,
  CodeOutlined,
  ApiOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SettingOutlined,
  GripOutlined,
  UpOutlined,
  DownOutlined,
  StarOutlined,
  StarFilled,
  SwapOutlined,
} from '@ant-design/icons'
import { exportApi } from '@/services/api'
import type {
  ExportTemplate,
  ExportFormat,
  ExportTemplateCreateForm,
  ExportFieldConfig,
  UnitConversion,
} from '@/types'
import { ExportFormatMap } from '@/types'
import dayjs from 'dayjs'

const { Title, Text } = Typography
const { Option } = Select
const { TextArea } = Input

const ExportTemplateManager: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const [templates, setTemplates] = useState<ExportTemplate[]>([])
  const [total, setTotal] = useState(0)
  const [formatFilter, setFormatFilter] = useState<ExportFormat | undefined>()
  const [enabledFilter, setEnabledFilter] = useState<number | undefined>(1)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [modalVisible, setModalVisible] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<ExportTemplate | null>(null)
  const [form] = Form.useForm<ExportTemplateCreateForm>()
  const [availableFields, setAvailableFields] = useState<ExportFieldConfig[]>([])
  const [selectedFields, setSelectedFields] = useState<ExportFieldConfig[]>([])
  const [unitConversions, setUnitConversions] = useState<UnitConversion[]>([])
  const [saving, setSaving] = useState(false)

  const [activeTab, setActiveTab] = useState<'list' | 'fields' | 'units'>('list')

  useEffect(() => {
    loadTemplates()
  }, [formatFilter, enabledFilter, pageNum, pageSize])

  useEffect(() => {
    loadAvailableFields()
  }, [])

  const loadTemplates = async () => {
    setLoading(true)
    try {
      const result = await exportApi.listTemplates({
        format: formatFilter,
        enabled: enabledFilter,
        pageNum,
        pageSize,
      })
      setTemplates(result.list || [])
      setTotal(result.total || 0)
    } catch (e) {
      message.error('加载模板列表失败')
    } finally {
      setLoading(false)
    }
  }

  const loadAvailableFields = async () => {
    try {
      const fields = await exportApi.getAvailableFields()
      setAvailableFields(fields)
    } catch (e) {
      message.error('加载可配置字段失败')
    }
  }

  const handleCreate = () => {
    setEditingTemplate(null)
    setSelectedFields(availableFields.map(f => ({ ...f, enabled: f.enabled || 1 })))
    setUnitConversions([])
    form.resetFields()
    form.setFieldsValue({ enabled: 1, isDefault: 0 })
    setActiveTab('list')
    setModalVisible(true)
  }

  const handleEdit = async (template: ExportTemplate) => {
    setEditingTemplate(template)
    setSelectedFields(template.fieldConfigs || availableFields.map(f => ({ ...f, enabled: 1 })))
    setUnitConversions(template.unitConversions || [])
    form.setFieldsValue({
      templateName: template.templateName,
      templateCode: template.templateCode,
      description: template.description,
      exportFormat: template.exportFormat,
      targetSystem: template.targetSystem,
      department: template.department,
      isDefault: template.isDefault || 0,
      enabled: template.enabled,
    })
    setActiveTab('list')
    setModalVisible(true)
  }

  const handleDelete = async (id: number) => {
    try {
      await exportApi.deleteTemplate(id)
      message.success('删除成功')
      loadTemplates()
    } catch (e) {
      message.error('删除失败')
    }
  }

  const handleToggleEnabled = async (template: ExportTemplate) => {
    try {
      const dto: ExportTemplateCreateForm = {
        templateName: template.templateName,
        exportFormat: template.exportFormat,
        fieldConfigs: template.fieldConfigs,
        enabled: template.enabled === 1 ? 0 : 1,
      }
      await exportApi.updateTemplate(template.id, dto)
      message.success('更新成功')
      loadTemplates()
    } catch (e) {
      message.error('更新失败')
    }
  }

  const handleMoveField = (index: number, direction: 'up' | 'down') => {
    const newFields = [...selectedFields]
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= newFields.length) return
    const temp = newFields[index]
    newFields[index] = newFields[targetIndex]
    newFields[targetIndex] = temp
    newFields.forEach((f, i) => f.sortOrder = i + 1)
    setSelectedFields(newFields)
  }

  const handleToggleField = (index: number, enabled: boolean) => {
    const newFields = [...selectedFields]
    newFields[index] = { ...newFields[index], enabled: enabled ? 1 : 0 }
    setSelectedFields(newFields)
  }

  const handleFieldChange = (index: number, field: keyof ExportFieldConfig, value: any) => {
    const newFields = [...selectedFields]
    newFields[index] = { ...newFields[index], [field]: value }
    setSelectedFields(newFields)
  }

  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)

      const dto: ExportTemplateCreateForm = {
        ...values,
        fieldConfigs: selectedFields.filter(f => f.enabled !== 0),
        unitConversions: unitConversions,
      }

      if (editingTemplate) {
        await exportApi.updateTemplate(editingTemplate.id, dto)
        message.success('模板更新成功')
      } else {
        await exportApi.createTemplate(dto)
        message.success('模板创建成功')
      }

      setModalVisible(false)
      loadTemplates()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const columns = [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      width: 180,
      render: (v: string, r: ExportTemplate) => (
        <Space>
          <Text strong>{v}</Text>
          {r.isDefault === 1 && <StarFilled style={{ color: '#faad14' }} />}
        </Space>
      ),
    },
    {
      title: '编码',
      dataIndex: 'templateCode',
      width: 150,
      render: (v: string) => v && <Text code>{v}</Text>,
    },
    {
      title: '导出格式',
      dataIndex: 'exportFormat',
      width: 120,
      render: (f: ExportFormat) => {
        const info = ExportFormatMap[f]
        const icons: Record<string, React.ReactNode> = {
          EXCEL: <FileExcelOutlined />,
          JSON: <CodeOutlined />,
          FHIR: <ApiOutlined />,
        }
        return (
          <Tag icon={icons[f]} color={info?.color}>
            {info?.label || f}
          </Tag>
        )
      },
    },
    {
      title: '目标系统',
      dataIndex: 'targetSystem',
      width: 100,
    },
    {
      title: '科室',
      dataIndex: 'department',
      width: 100,
    },
    {
      title: '字段数',
      width: 80,
      render: (_: any, r: ExportTemplate) =>
        r.fieldConfigs?.length ? `${r.fieldConfigs.filter(f => f.enabled !== 0).length}/${r.fieldConfigs.length}` : '-',
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (v: number, r: ExportTemplate) => (
        <Switch
          checked={v === 1}
          onChange={() => handleToggleEnabled(r)}
          size="small"
          checkedChildren="启用"
          unCheckedChildren="禁用"
        />
      ),
    },
    {
      title: '创建人',
      dataIndex: 'createUserName',
      width: 100,
    },
    {
      title: '创建时间',
      dataIndex: 'createdTime',
      width: 160,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      width: 140,
      fixed: 'right',
      render: (_: any, r: ExportTemplate) => (
        <Space size={4}>
          <Tooltip title="编辑">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(r)}
            >
              编辑
            </Button>
          </Tooltip>
          <Popconfirm
            title="确定删除该模板？"
            onConfirm={() => handleDelete(r.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const formatIcon = (f: ExportFormat) => {
    switch (f) {
      case 'EXCEL': return <FileExcelOutlined />
      case 'JSON': return <CodeOutlined />
      case 'FHIR': return <ApiOutlined />
    }
  }

  return (
    <div className="page-container">
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>
          <SettingOutlined style={{ color: '#1677ff' }} /> 导出模板管理
        </Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
        >
          新建模板
        </Button>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="按导出格式筛选"
            style={{ width: 150 }}
            allowClear
            value={formatFilter}
            onChange={setFormatFilter}
          >
            <Option value="EXCEL">
              <Space><FileExcelOutlined /> Excel</Space>
            </Option>
            <Option value="JSON">
              <Space><CodeOutlined /> JSON</Space>
            </Option>
            <Option value="FHIR">
              <Space><ApiOutlined /> HL7 FHIR</Space>
            </Option>
          </Select>
          <Select
            placeholder="按状态筛选"
            style={{ width: 130 }}
            allowClear
            value={enabledFilter}
            onChange={setEnabledFilter}
          >
            <Option value={1}>已启用</Option>
            <Option value={0}>已禁用</Option>
          </Select>
        </Space>

        <Table
          rowKey="id"
          loading={loading}
          dataSource={templates}
          columns={columns}
          pagination={{
            current: pageNum,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPageNum(p)
              setPageSize(s)
            },
          }}
          locale={{ emptyText: '暂无导出模板' }}
        />
      </Card>

      <Modal
        title={editingTemplate ? '编辑导出模板' : '新建导出模板'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        width={900}
        footer={
          <Space>
            <Button onClick={() => setModalVisible(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={handleSave}>
              保存
            </Button>
          </Space>
        }
      >
        <Tabs
          activeKey={activeTab}
          onChange={k => setActiveTab(k as typeof activeTab)}
          items={[
            {
              key: 'list',
              label: '基本信息',
              children: (
                <Form form={form} layout="vertical">
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item
                        label="模板名称"
                        name="templateName"
                        rules={[{ required: true, message: '请输入模板名称' }]}
                      >
                        <Input placeholder="如：病案首页-外科标准Excel" />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        label="模板编码"
                        name="templateCode"
                        rules={[{ pattern: /^[A-Z_]+$/, message: '仅支持大写字母和下划线' }]}
                      >
                        <Input placeholder="如：HOMEPAGE_EXCEL_SURG" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={8}>
                      <Form.Item
                        label="导出格式"
                        name="exportFormat"
                        rules={[{ required: true, message: '请选择导出格式' }]}
                      >
                        <Select>
                          {(Object.keys(ExportFormatMap) as ExportFormat[]).map(f => (
                            <Option key={f} value={f}>
                              <Space>
                                {formatIcon(f)} {ExportFormatMap[f].label}
                              </Space>
                            </Option>
                          ))}
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item label="目标系统" name="targetSystem">
                        <Select allowClear placeholder="如：HIS/EMR">
                          <Option value="HIS">HIS系统</Option>
                          <Option value="EMR">EMR系统</Option>
                          <Option value="API">API对接</Option>
                          <Option value="OTHER">其他</Option>
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item label="适用科室" name="department">
                        <Input placeholder="如：普外科" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Form.Item label="描述" name="description">
                    <TextArea rows={2} placeholder="模板用途说明" />
                  </Form.Item>
                  <Row gutter={16}>
                    <Col span={8}>
                      <Form.Item label="设为默认" name="isDefault" valuePropName="checked">
                        <Switch checkedChildren="是" unCheckedChildren="否" />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item label="启用状态" name="enabled" valuePropName="checked">
                        <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                      </Form.Item>
                    </Col>
                  </Row>
                </Form>
              ),
            },
            {
              key: 'fields',
              label: `字段配置 (${selectedFields.filter(f => f.enabled !== 0).length}个)`,
              children: (
                <div>
                  <Alert
                    type="info"
                    showIcon
                    message="拖拽调整顺序，开关控制是否导出，可配置单位转换"
                    style={{ marginBottom: 12 }}
                  />
                  <div style={{ maxHeight: 500, overflowY: 'auto' }}>
                    {selectedFields.map((field, index) => (
                      <Card
                        key={field.fieldCode}
                        size="small"
                        style={{
                          marginBottom: 8,
                          opacity: field.enabled === 0 ? 0.5 : 1,
                          borderColor: field.enabled === 0 ? '#d9d9d9' : undefined,
                        }}
                        title={
                          <Space>
                            <GripOutlined style={{ cursor: 'move', color: '#bfbfbf' }} />
                            <Switch
                              size="small"
                              checked={field.enabled !== 0}
                              onChange={checked => handleToggleField(index, checked)}
                            />
                            <Text strong>{field.fieldLabel}</Text>
                            <Tag color="blue">{field.fieldCode}</Tag>
                            <Tag>{field.dataType}</Tag>
                            {field.unit && <Tag color="orange">{field.unit}</Tag>}
                          </Space>
                        }
                        extra={
                          <Space size={4}>
                            <Tooltip title="上移">
                              <Button
                                size="small"
                                icon={<UpOutlined />}
                                disabled={index === 0}
                                onClick={() => handleMoveField(index, 'up')}
                              />
                            </Tooltip>
                            <Tooltip title="下移">
                              <Button
                                size="small"
                                icon={<DownOutlined />}
                                disabled={index === selectedFields.length - 1}
                                onClick={() => handleMoveField(index, 'down')}
                              />
                            </Tooltip>
                          </Space>
                        }
                      >
                        <Row gutter={16} align="middle">
                          <Col span={6}>
                            <Text type="secondary">排序:</Text> {field.sortOrder}
                          </Col>
                          {field.unit && (
                            <Col span={6}>
                              <Form.Item
                                label="转换目标单位"
                                style={{ margin: 0 }}
                              >
                                <Select
                                  size="small"
                                  allowClear
                                  value={field.targetUnit}
                                  onChange={v => handleFieldChange(index, 'targetUnit', v)}
                                  placeholder="如：L / 元"
                                >
                                  {field.unit === 'ml' && (
                                    <>
                                      <Option value="L">升 (L)</Option>
                                      <Option value="ml">毫升 (ml)</Option>
                                    </>
                                  )}
                                  {field.unit === '元' && (
                                    <>
                                      <Option value="元">元</Option>
                                      <Option value="万元">万元</Option>
                                    </>
                                  )}
                                </Select>
                              </Form.Item>
                            </Col>
                          )}
                          {field.targetUnit && (
                            <Col span={12}>
                              <Form.Item
                                label="转换公式"
                                style={{ margin: 0 }}
                              >
                                <Select
                                  size="small"
                                  value={field.conversionFormula}
                                  onChange={v => handleFieldChange(index, 'conversionFormula', v)}
                                  placeholder="选择转换公式"
                                >
                                  {field.unit === 'ml' && field.targetUnit === 'L' && (
                                    <Option value="value / 1000">毫升 → 升 (÷1000)</Option>
                                  )}
                                  {field.unit === 'L' && field.targetUnit === 'ml' && (
                                    <Option value="value * 1000">升 → 毫升 (×1000)</Option>
                                  )}
                                  {field.unit === '元' && field.targetUnit === '万元' && (
                                    <Option value="value / 10000">元 → 万元 (÷10000)</Option>
                                  )}
                                  {field.unit === '万元' && field.targetUnit === '元' && (
                                    <Option value="value * 10000">万元 → 元 (×10000)</Option>
                                  )}
                                </Select>
                              </Form.Item>
                            </Col>
                          )}
                        </Row>
                      </Card>
                    ))}
                  </div>
                </div>
              ),
            },
            {
              key: 'units',
              label: '单位转换 (高级)',
              children: (
                <div>
                  <Alert
                    type="warning"
                    showIcon
                    message="高级单位转换配置，支持自定义乘法因子和偏移量"
                    style={{ marginBottom: 12 }}
                  />
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={() => setUnitConversions([...unitConversions, {
                      fieldCode: '',
                      sourceUnit: '',
                      targetUnit: '',
                      multiplyFactor: 1,
                      decimalPlaces: 2,
                    }])}
                    style={{ marginBottom: 12 }}
                  >
                    添加转换规则
                  </Button>
                  {unitConversions.map((uc, index) => (
                    <Card key={index} size="small" style={{ marginBottom: 8 }}>
                      <Row gutter={8} align="middle">
                        <Col span={5}>
                          <Select
                            size="small"
                            value={uc.fieldCode}
                            onChange={v => {
                              const newList = [...unitConversions]
                              newList[index].fieldCode = v
                              setUnitConversions(newList)
                            }}
                            placeholder="选择字段"
                            style={{ width: '100%' }}
                          >
                            {availableFields.filter(f => f.unit).map(f => (
                              <Option key={f.fieldCode} value={f.fieldCode}>
                                {f.fieldLabel} ({f.unit})
                              </Option>
                            ))}
                          </Select>
                        </Col>
                        <Col span={2} style={{ textAlign: 'center' }}>
                          <SwapOutlined />
                        </Col>
                        <Col span={4}>
                          <Input
                            size="small"
                            placeholder="原单位"
                            value={uc.sourceUnit}
                            onChange={e => {
                              const newList = [...unitConversions]
                              newList[index].sourceUnit = e.target.value
                              setUnitConversions(newList)
                            }}
                          />
                        </Col>
                        <Col span={2} style={{ textAlign: 'center' }}>→</Col>
                        <Col span={4}>
                          <Input
                            size="small"
                            placeholder="目标单位"
                            value={uc.targetUnit}
                            onChange={e => {
                              const newList = [...unitConversions]
                              newList[index].targetUnit = e.target.value
                              setUnitConversions(newList)
                            }}
                          />
                        </Col>
                        <Col span={4}>
                          <InputNumber
                            size="small"
                            placeholder="倍率"
                            value={uc.multiplyFactor}
                            onChange={v => {
                              const newList = [...unitConversions]
                              newList[index].multiplyFactor = v ?? 1
                              setUnitConversions(newList)
                            }}
                            style={{ width: '100%' }}
                          />
                        </Col>
                        <Col span={3}>
                          <InputNumber
                            size="small"
                            placeholder="小数位"
                            value={uc.decimalPlaces}
                            onChange={v => {
                              const newList = [...unitConversions]
                              newList[index].decimalPlaces = v ?? 2
                              setUnitConversions(newList)
                            }}
                            style={{ width: '100%' }}
                          />
                        </Col>
                        <Col span={2}>
                          <Button
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => {
                              const newList = unitConversions.filter((_, i) => i !== index)
                              setUnitConversions(newList)
                            }}
                          />
                        </Col>
                      </Row>
                    </Card>
                  ))}
                  {unitConversions.length === 0 && (
                    <Empty description="暂无高级单位转换规则" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </div>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  )
}

export default ExportTemplateManager
