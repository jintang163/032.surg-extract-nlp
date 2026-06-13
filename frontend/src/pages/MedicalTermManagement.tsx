import React, { useState, useEffect, useMemo } from 'react'
import {
  Card,
  Button,
  Space,
  Table,
  Tag,
  Input,
  Select,
  Form,
  Modal,
  message,
  Tooltip,
  Popconfirm,
  Tabs,
  Statistic,
  Row,
  Col,
  Descriptions,
  Divider,
  List,
  Progress,
  InputNumber,
  Upload,
  Alert,
  Tree,
} from 'antd'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  MergeCellsOutlined,
  ApiOutlined,
  BarChartOutlined,
  CloudSyncOutlined,
  BulbOutlined,
  ImportOutlined,
  DownloadOutlined,
  ExperimentOutlined,
} from '@ant-design/icons'
import { termApi } from '@/services/api'
import type { MedicalTerm, MedicalTermAlias, TermMappingResult, TermGraphStats } from '@/types'
import { TermTypeMap, AliasTypeMap, ReviewStatusMap } from '@/types'
import type { Key } from 'antd/es/table/interface'

const { Option } = Select
const { TextArea } = Input
const { TabPane } = Tabs
const { Search } = Input
const { DirectoryTree } = Tree

const MedicalTermManagement: React.FC = () => {
  const [form] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [aliasForm] = Form.useForm()
  const [mergeForm] = Form.useForm()
  const [testForm] = Form.useForm()

  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<MedicalTerm[]>([])
  const [total, setTotal] = useState(0)
  const [pagination, setPagination] = useState({ pageNum: 1, pageSize: 20 })

  const [modalVisible, setModalVisible] = useState(false)
  const [aliasModalVisible, setAliasModalVisible] = useState(false)
  const [mergeModalVisible, setMergeModalVisible] = useState(false)
  const [testModalVisible, setTestModalVisible] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)

  const [editingTerm, setEditingTerm] = useState<MedicalTerm | null>(null)
  const [currentTermId, setCurrentTermId] = useState<number | null>(null)
  const [aliases, setAliases] = useState<MedicalTermAlias[]>([])
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([])
  const [graphStats, setGraphStats] = useState<TermGraphStats | null>(null)
  const [mappingResult, setMappingResult] = useState<TermMappingResult | null>(null)
  const [categories, setCategories] = useState<any[]>([])
  const [termTypes, setTermTypes] = useState<any[]>([])
  const [aliasTypes, setAliasTypes] = useState<any[]>([])

  const [activeTab, setActiveTab] = useState('terms')
  const [testLoading, setTestLoading] = useState(false)

  useEffect(() => {
    loadCategories()
    loadMeta()
    loadData()
    loadGraphStats()
  }, [pagination, activeTab])

  const loadMeta = async () => {
    try {
      const [types, aliasTypeList] = await Promise.all([termApi.getTermTypes(), termApi.getAliasTypes()])
      setTermTypes(types || [])
      setAliasTypes(aliasTypeList || [])
    } catch (e) {
      console.error('加载元数据失败', e)
    }
  }

  const loadCategories = async () => {
    try {
      const result = await termApi.getCategories()
      setCategories(result || [])
    } catch (e) {
      console.error('加载分类失败', e)
    }
  }

  const loadData = async () => {
    setLoading(true)
    try {
      const values = searchForm.getFieldsValue()
      const params = {
        ...values,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      }
      const result = await termApi.list(params)
      setData(result.records || [])
      setTotal(result.total || 0)
    } catch (e) {
      console.error('加载数据失败', e)
    } finally {
      setLoading(false)
    }
  }

  const loadGraphStats = async () => {
    try {
      const stats = await termApi.getGraphStats()
      setGraphStats(stats)
    } catch (e) {
      console.error('加载图谱统计失败', e)
    }
  }

  const loadAliases = async (termId: number) => {
    try {
      const result = await termApi.getAliases(termId)
      setAliases(result || [])
    } catch (e) {
      console.error('加载别名失败', e)
    }
  }

  const handleSearch = () => {
    setPagination({ ...pagination, pageNum: 1 })
    loadData()
  }

  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, pageNum: 1 })
    setTimeout(loadData, 100)
  }

  const handleAdd = () => {
    setEditingTerm(null)
    form.resetFields()
    form.setFieldsValue({
      termType: 'SURGERY',
      confidence: 1.0,
      enabled: 1,
      reviewStatus: 'APPROVED',
    })
    setModalVisible(true)
  }

  const handleEdit = (record: MedicalTerm) => {
    setEditingTerm(record)
    form.setFieldsValue({
      ...record,
    })
    setModalVisible(true)
  }

  const handleDelete = async (id: number) => {
    try {
      await termApi.delete(id)
      message.success('删除成功')
      loadData()
    } catch (e) {
      console.error('删除失败', e)
    }
  }

  const handleReview = async (id: number, approved: boolean) => {
    try {
      await termApi.review(id, approved)
      message.success(approved ? '审核通过' : '审核拒绝')
      loadData()
    } catch (e) {
      console.error('审核失败', e)
    }
  }

  const handleToggleEnabled = async (id: number) => {
    try {
      await termApi.toggleEnabled(id)
      message.success('状态已更新')
      loadData()
    } catch (e) {
      console.error('更新状态失败', e)
    }
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      if (editingTerm) {
        await termApi.update(editingTerm.id, values)
        message.success('更新成功')
      } else {
        await termApi.create(values)
        message.success('创建成功')
      }
      setModalVisible(false)
      loadData()
    } catch (e) {
      console.error('提交失败', e)
    }
  }

  const handleViewDetail = async (record: MedicalTerm) => {
    setCurrentTermId(record.id)
    await loadAliases(record.id)
    setDetailModalVisible(true)
  }

  const handleAddAlias = (termId: number) => {
    setCurrentTermId(termId)
    aliasForm.resetFields()
    aliasForm.setFieldsValue({
      termId,
      aliasType: 'SYNONYM',
      similarityScore: 0.9,
      source: 'MANUAL',
      reviewStatus: 'APPROVED',
      enabled: 1,
    })
    setAliasModalVisible(true)
  }

  const handleSubmitAlias = async () => {
    try {
      const values = await aliasForm.validateFields()
      await termApi.addAlias(values)
      message.success('添加别名成功')
      setAliasModalVisible(false)
      if (currentTermId) {
        loadAliases(currentTermId)
      }
    } catch (e) {
      console.error('添加别名失败', e)
    }
  }

  const handleDeleteAlias = async (id: number) => {
    try {
      await termApi.deleteAlias(id)
      message.success('删除别名成功')
      if (currentTermId) {
        loadAliases(currentTermId)
      }
    } catch (e) {
      console.error('删除别名失败', e)
    }
  }

  const handleMerge = () => {
    if (selectedRowKeys.length < 2) {
      message.warning('请至少选择两个术语进行合并')
      return
    }
    mergeForm.resetFields()
    mergeForm.setFieldsValue({
      sourceTermIds: selectedRowKeys.filter((k) => Number(k) !== selectedRowKeys[0]),
      targetTermId: Number(selectedRowKeys[0]),
      mergeAction: 'MOVE_ALIASES',
    })
    setMergeModalVisible(true)
  }

  const handleSubmitMerge = async () => {
    try {
      const values = await mergeForm.validateFields()
      await termApi.merge(values)
      message.success('合并成功')
      setMergeModalVisible(false)
      setSelectedRowKeys([])
      loadData()
    } catch (e) {
      console.error('合并失败', e)
    }
  }

  const handleTestMapping = async () => {
    try {
      const values = await testForm.validateFields()
      setTestLoading(true)
      const result = await termApi.mapTerm(values.originalText, values.termType)
      setMappingResult(result)
    } catch (e) {
      console.error('映射测试失败', e)
    } finally {
      setTestLoading(false)
    }
  }

  const handleSyncGraph = async () => {
    try {
      await termApi.syncAllToGraph()
      message.success('同步成功')
      loadGraphStats()
    } catch (e) {
      console.error('同步图谱失败', e)
    }
  }

  const handleImport = () => {
    message.info('请使用批量导入API或准备好JSON数据')
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
    },
    {
      title: '术语编码',
      dataIndex: 'termCode',
      width: 130,
    },
    {
      title: '标准名称',
      dataIndex: 'standardName',
      width: 180,
      render: (text: string, record: MedicalTerm) => (
        <a onClick={() => handleViewDetail(record)}>{text}</a>
      ),
    },
    {
      title: '类型',
      dataIndex: 'termType',
      width: 100,
      render: (type: string) => {
        const info = TermTypeMap[type as keyof typeof TermTypeMap]
        return info ? <Tag color={info.color}>{info.label}</Tag> : type
      },
    },
    {
      title: 'ICD编码',
      dataIndex: 'icdCode',
      width: 120,
      render: (code: string, record: MedicalTerm) =>
        code ? (
          <Tooltip title={record.icdName}>
            <Tag color="geekblue">{code}</Tag>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '拼音',
      dataIndex: 'pinyinAbbr',
      width: 100,
      render: (abbr: string, record: MedicalTerm) => (
        <Tooltip title={record.pinyin}>{abbr || '-'}</Tooltip>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 100,
      render: (val: number) => (
        <Progress percent={Math.round((val || 0) * 100)} size="small" showInfo={false} />
      ),
    },
    {
      title: '匹配次数',
      dataIndex: 'matchCount',
      width: 90,
    },
    {
      title: '审核状态',
      dataIndex: 'reviewStatus',
      width: 100,
      render: (status: string) => {
        const info = ReviewStatusMap[status as keyof typeof ReviewStatusMap]
        return info ? <Tag color={info.color}>{info.label}</Tag> : status
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (val: number) => (
        <Tag color={val === 1 ? 'success' : 'default'}>{val === 1 ? '启用' : '禁用'}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdTime',
      width: 170,
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      fixed: 'right',
      render: (_: any, record: MedicalTerm) => (
        <Space size="small">
          <Tooltip title="编辑">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          </Tooltip>
          <Tooltip title="管理别名">
            <Button
              type="link"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => {
                setCurrentTermId(record.id)
                handleAddAlias(record.id)
              }}
            />
          </Tooltip>
          {record.reviewStatus === 'PENDING' && (
            <>
              <Tooltip title="通过">
                <Button
                  type="link"
                  size="small"
                  icon={<CheckCircleOutlined />}
                  onClick={() => handleReview(record.id, true)}
                />
              </Tooltip>
              <Tooltip title="拒绝">
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => handleReview(record.id, false)}
                />
              </Tooltip>
            </>
          )}
          <Tooltip title={record.enabled === 1 ? '禁用' : '启用'}>
            <Button
              type="link"
              size="small"
              onClick={() => handleToggleEnabled(record.id)}
            >
              {record.enabled === 1 ? '禁用' : '启用'}
            </Button>
          </Tooltip>
          <Popconfirm title="确定删除该术语？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const aliasColumns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '别名',
      dataIndex: 'aliasName',
      width: 180,
    },
    {
      title: '类型',
      dataIndex: 'aliasType',
      width: 100,
      render: (type: string) => {
        const info = AliasTypeMap[type as keyof typeof AliasTypeMap]
        return info ? <Tag color={info.color}>{info.label}</Tag> : type
      },
    },
    {
      title: '相似度',
      dataIndex: 'similarityScore',
      width: 100,
      render: (val: number) =>
        val ? <Progress percent={Math.round(val * 100)} size="small" showInfo={false} /> : '-',
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 100,
    },
    {
      title: '审核状态',
      dataIndex: 'reviewStatus',
      width: 100,
      render: (status: string) => {
        const info = ReviewStatusMap[status as keyof typeof ReviewStatusMap]
        return info ? <Tag color={info.color}>{info.label}</Tag> : status
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (val: number) => (
        <Tag color={val === 1 ? 'success' : 'default'}>{val === 1 ? '启用' : '禁用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: any, record: MedicalTermAlias) => (
        <Popconfirm title="确定删除该别名？" onConfirm={() => handleDeleteAlias(record.id)}>
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ]

  const categoryTreeData = useMemo(() => {
    const buildTree = (items: any[], parentId: number | null = null): any[] => {
      return items
        .filter((item) => item.parentId === parentId)
        .map((item) => ({
          title: item.categoryName,
          key: item.id,
          children: buildTree(items, item.id),
        }))
    }
    return buildTree(categories)
  }, [categories])

  const StatsCards = () => (
    <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
      <Col xs={12} sm={6}>
        <Card>
          <Statistic
            title="术语总数"
            value={graphStats?.totalNodes || 0}
            prefix={<BarChartOutlined style={{ color: '#1677ff' }} />}
          />
        </Card>
      </Col>
      <Col xs={12} sm={6}>
        <Card>
          <Statistic
            title="关联关系"
            value={graphStats?.totalRelationships || 0}
            prefix={<ApiOutlined style={{ color: '#52c41a' }} />}
          />
        </Card>
      </Col>
      <Col xs={12} sm={6}>
        <Card>
          <Statistic
            title="本月映射"
            value={1280}
            prefix={<ExperimentOutlined style={{ color: '#722ed1' }} />}
          />
        </Card>
      </Col>
      <Col xs={12} sm={6}>
        <Card>
          <Statistic
            title="匹配准确率"
            value={96.8}
            precision={1}
            suffix="%"
            prefix={<BulbOutlined style={{ color: '#fa8c16' }} />}
          />
        </Card>
      </Col>
    </Row>
  )

  return (
    <div style={{ padding: 16 }}>
      <StatsCards />

      <Card>
        <Tabs activeKey={activeTab} onChange={setActiveTab}>
          <TabPane tab="术语管理" key="terms">
            <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
              <Form.Item name="keyword" label="关键词">
                <Input placeholder="名称/拼音/编码" allowClear style={{ width: 200 }} />
              </Form.Item>
              <Form.Item name="termType" label="类型">
                <Select placeholder="全部" allowClear style={{ width: 140 }}>
                  {termTypes.map((t) => (
                    <Option key={t.code} value={t.code}>
                      {t.name}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item name="reviewStatus" label="审核状态">
                <Select placeholder="全部" allowClear style={{ width: 120 }}>
                  <Option value="PENDING">待审核</Option>
                  <Option value="APPROVED">已通过</Option>
                  <Option value="REJECTED">已拒绝</Option>
                </Select>
              </Form.Item>
              <Space>
                <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                  搜索
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset}>
                  重置
                </Button>
              </Space>
            </Form>

            <Space style={{ marginBottom: 16 }} wrap>
              <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
                新增术语
              </Button>
              <Button
                icon={<MergeCellsOutlined />}
                onClick={handleMerge}
                disabled={selectedRowKeys.length < 2}
              >
                合并选中 ({selectedRowKeys.length})
              </Button>
              <Button icon={<ExperimentOutlined />} onClick={() => setTestModalVisible(true)}>
                映射测试
              </Button>
              <Button icon={<ImportOutlined />} onClick={handleImport}>
                批量导入
              </Button>
              <Button icon={<CloudSyncOutlined />} onClick={handleSyncGraph}>
                同步图谱
              </Button>
            </Space>

            <Table
              rowKey="id"
              loading={loading}
              dataSource={data}
              columns={columns}
              rowSelection={{
                selectedRowKeys,
                onChange: (keys) => setSelectedRowKeys(keys),
              }}
              pagination={{
                current: pagination.pageNum,
                pageSize: pagination.pageSize,
                total,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (page, size) => setPagination({ pageNum: page, pageSize: size }),
              }}
              scroll={{ x: 1600 }}
            />
          </TabPane>

          <TabPane tab="分类管理" key="categories">
            <Row gutter={16}>
              <Col xs={24} sm={8}>
                <Card title="分类树" extra={<Button size="small" icon={<PlusOutlined />}>新增</Button>}>
                  <DirectoryTree
                    treeData={categoryTreeData}
                    defaultExpandAll
                    onSelect={(keys) => {
                      if (keys.length > 0) {
                        searchForm.setFieldsValue({ categoryId: keys[0] })
                        setActiveTab('terms')
                        handleSearch()
                      }
                    }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={16}>
                <Card title="分类说明">
                  <Alert
                    type="info"
                    showIcon
                    message="术语分类用于组织医学术语，支持多级分类结构"
                    description="通过分类可以更好地管理不同科室、不同类型的医学术语，提高检索效率。"
                    style={{ marginBottom: 16 }}
                  />
                  <List
                    dataSource={categories.slice(0, 5)}
                    renderItem={(item) => (
                      <List.Item
                        actions={[
                          <Button type="link" size="small" icon={<EditOutlined />}>
                            编辑
                          </Button>,
                          <Popconfirm title="确定删除？">
                            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                              删除
                            </Button>
                          </Popconfirm>,
                        ]}
                      >
                        <List.Item.Meta title={item.categoryName} description={item.description || '无描述'} />
                      </List.Item>
                    )}
                  />
                </Card>
              </Col>
            </Row>
          </TabPane>

          <TabPane tab="图谱统计" key="graph">
            <Row gutter={16}>
              <Col xs={24} sm={12}>
                <Card title="类型分布">
                  {graphStats?.termTypeStats &&
                    Object.entries(graphStats.termTypeStats).map(([type, count]) => {
                      const info = TermTypeMap[type as keyof typeof TermTypeMap]
                      return (
                        <div key={type} style={{ marginBottom: 12 }}>
                          <div style={{ marginBottom: 4 }}>
                            <Space>
                              <Tag color={info?.color || 'default'}>{info?.label || type}</Tag>
                              <span>{count} 个</span>
                            </Space>
                          </div>
                          <Progress
                            percent={Math.round(
                              ((count as number) / (graphStats?.totalNodes || 1)) * 100
                            )}
                            size="small"
                          />
                        </div>
                      )
                    })}
                </Card>
              </Col>
              <Col xs={24} sm={12}>
                <Card
                  title="热门术语"
                  extra={
                    <Button size="small" icon={<ReloadOutlined />} onClick={loadGraphStats}>
                      刷新
                    </Button>
                  }
                >
                  <List
                    dataSource={graphStats?.topMatchedTerms || []}
                    renderItem={(item, index) => (
                      <List.Item>
                        <List.Item.Meta
                          title={
                            <Space>
                              <Tag color={index < 3 ? 'gold' : 'default'}>#{index + 1}</Tag>
                              {item.standardName}
                            </Space>
                          }
                          description={
                            <Space>
                              <span>匹配 {item.matchCount} 次</span>
                              {item.icdCode && <Tag color="geekblue">ICD: {item.icdCode}</Tag>}
                            </Space>
                          }
                        />
                      </List.Item>
                    )}
                  />
                </Card>
              </Col>
            </Row>
          </TabPane>
        </Tabs>
      </Card>

      <Modal
        title={editingTerm ? '编辑术语' : '新增术语'}
        open={modalVisible}
        width={600}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="termCode"
                label="术语编码"
                rules={[{ required: true, message: '请输入术语编码' }]}
              >
                <Input placeholder="如：ST-OP-001" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="termType"
                label="术语类型"
                rules={[{ required: true, message: '请选择术语类型' }]}
              >
                <Select>
                  {termTypes.map((t) => (
                    <Option key={t.code} value={t.code}>
                      {t.name}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="standardName"
            label="标准名称"
            rules={[{ required: true, message: '请输入标准名称' }]}
          >
            <Input placeholder="如：胆囊切除术" />
          </Form.Item>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="categoryId" label="所属分类">
                <Select placeholder="请选择分类" allowClear>
                  {categories.map((c) => (
                    <Option key={c.id} value={c.id}>
                      {c.categoryName}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="confidence" label="置信度">
                <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="icdCode" label="ICD编码">
                <Input placeholder="如：K80.1" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="icdName" label="ICD名称">
                <Input placeholder="如：慢性胆囊炎" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="definition" label="术语定义">
            <TextArea rows={3} placeholder="请输入术语的医学定义说明" />
          </Form.Item>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="reviewStatus" label="审核状态">
                <Select>
                  <Option value="PENDING">待审核</Option>
                  <Option value="APPROVED">已通过</Option>
                  <Option value="REJECTED">已拒绝</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="enabled" label="状态">
                <Select>
                  <Option value={1}>启用</Option>
                  <Option value={0}>禁用</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        title="添加别名"
        open={aliasModalVisible}
        onOk={handleSubmitAlias}
        onCancel={() => setAliasModalVisible(false)}
        okText="添加"
        cancelText="取消"
      >
        <Form form={aliasForm} layout="vertical">
          <Form.Item
            name="aliasName"
            label="别名名称"
            rules={[{ required: true, message: '请输入别名' }]}
          >
            <Input placeholder="如：胆囊摘除" />
          </Form.Item>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="aliasType"
                label="别名类型"
                rules={[{ required: true, message: '请选择类型' }]}
              >
                <Select>
                  {aliasTypes.map((t) => (
                    <Option key={t.code} value={t.code}>
                      {t.name}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="similarityScore" label="相似度">
                <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="source" label="来源">
                <Input placeholder="如：MANUAL" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="reviewStatus" label="审核状态">
                <Select>
                  <Option value="PENDING">待审核</Option>
                  <Option value="APPROVED">已通过</Option>
                  <Option value="REJECTED">已拒绝</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="enabled" label="状态">
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
          <Form.Item name="termId" hidden>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="合并术语"
        open={mergeModalVisible}
        onOk={handleSubmitMerge}
        onCancel={() => setMergeModalVisible(false)}
        okText="合并"
        cancelText="取消"
        width={500}
      >
        <Alert
          type="warning"
          showIcon
          message="合并后源术语将被删除，其别名将被移动到目标术语"
          style={{ marginBottom: 16 }}
        />
        <Form form={mergeForm} layout="vertical">
          <Form.Item
            name="targetTermId"
            label="目标术语"
            rules={[{ required: true, message: '请选择目标术语' }]}
          >
            <Select placeholder="该术语将保留">
              {data
                .filter((d) => selectedRowKeys.includes(String(d.id)))
                .map((d) => (
                  <Option key={d.id} value={d.id}>
                    {d.standardName} ({d.termCode})
                  </Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="sourceTermIds"
            label="源术语"
            rules={[{ required: true, message: '请选择源术语' }]}
          >
            <Select mode="multiple" placeholder="这些术语将被合并">
              {data
                .filter((d) => selectedRowKeys.includes(String(d.id)))
                .map((d) => (
                  <Option key={d.id} value={d.id}>
                    {d.standardName} ({d.termCode})
                  </Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item name="mergeAction" label="合并方式">
            <Select>
              <Option value="MOVE_ALIASES">仅移动别名</Option>
              <Option value="MOVE_ALL">移动所有数据</Option>
            </Select>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea rows={2} placeholder="请输入合并备注" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="术语映射测试"
        open={testModalVisible}
        onCancel={() => setTestModalVisible(false)}
        footer={null}
        width={700}
      >
        <Form form={testForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item
            name="originalText"
            label="输入文本"
            rules={[{ required: true, message: '请输入要映射的文本' }]}
          >
            <Input placeholder="如：胆囊摘除" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="termType" label="类型">
            <Select placeholder="不限" allowClear style={{ width: 140 }}>
              {termTypes.map((t) => (
                <Option key={t.code} value={t.code}>
                  {t.name}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Button type="primary" loading={testLoading} onClick={handleTestMapping}>
            测试映射
          </Button>
        </Form>

        {mappingResult && (
          <div>
            <Divider orientation="left">映射结果</Divider>
            {mappingResult.matched ? (
              <Alert
                type="success"
                showIcon
                message="映射成功"
                description={
                  <div>
                    <Descriptions column={2} size="small">
                      <Descriptions.Item label="原始文本">{mappingResult.originalText}</Descriptions.Item>
                      <Descriptions.Item label="标准名称">
                        <strong>{mappingResult.standardName}</strong>
                      </Descriptions.Item>
                      <Descriptions.Item label="术语编码">{mappingResult.termCode}</Descriptions.Item>
                      <Descriptions.Item label="ICD编码">{mappingResult.icdCode || '-'}</Descriptions.Item>
                      <Descriptions.Item label="匹配方式">{mappingResult.matchMethod}</Descriptions.Item>
                      <Descriptions.Item label="置信度">
                        <Progress
                          percent={Math.round((mappingResult.confidence || 0) * 100)}
                          size="small"
                        />
                      </Descriptions.Item>
                    </Descriptions>
                  </div>
                }
              />
            ) : (
              <Alert type="warning" showIcon message="未找到匹配的术语" description="请考虑添加该术语到映射库" />
            )}

            {mappingResult.candidates && mappingResult.candidates.length > 0 && (
              <>
                <Divider orientation="left">候选术语 ({mappingResult.candidates.length})</Divider>
                <List
                  size="small"
                  dataSource={mappingResult.candidates}
                  renderItem={(item, index) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space>
                            <Tag color="geekblue">#{index + 1}</Tag>
                            <strong>{item.standardName}</strong>
                            {item.aliasName && (
                              <Tag color="blue">别名：{item.aliasName}</Tag>
                            )}
                          </Space>
                        }
                        description={
                          <Space>
                            <span>
                              匹配方式：{item.matchMethod}，置信度：
                              {Math.round((item.confidence || 0) * 100)}%
                            </span>
                          </Space>
                        }
                      />
                      <Progress
                        percent={Math.round((item.confidence || 0) * 100)}
                        size="small"
                        style={{ width: 150 }}
                      />
                    </List.Item>
                  )}
                />
              </>
            )}
          </div>
        )}
      </Modal>

      <Modal
        title="术语详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={
          <Space>
            <Button onClick={() => handleAddAlias(currentTermId!)}>添加别名</Button>
            <Button type="primary" onClick={() => setDetailModalVisible(false)}>
              关闭
            </Button>
          </Space>
        }
        width={800}
      >
        {editingTerm && (
          <Descriptions column={2} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="术语编码">{editingTerm.termCode}</Descriptions.Item>
            <Descriptions.Item label="类型">
              {TermTypeMap[editingTerm.termType as keyof typeof TermTypeMap]?.label || editingTerm.termType}
            </Descriptions.Item>
            <Descriptions.Item label="标准名称">{editingTerm.standardName}</Descriptions.Item>
            <Descriptions.Item label="拼音">{editingTerm.pinyin || '-'}</Descriptions.Item>
            <Descriptions.Item label="ICD编码">{editingTerm.icdCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="ICD名称">{editingTerm.icdName || '-'}</Descriptions.Item>
            <Descriptions.Item label="匹配次数">{editingTerm.matchCount}</Descriptions.Item>
            <Descriptions.Item label="使用次数">{editingTerm.usageCount}</Descriptions.Item>
            <Descriptions.Item label="定义" span={2}>
              {editingTerm.definition || '无'}
            </Descriptions.Item>
          </Descriptions>
        )}
        <Divider orientation="left">别名列表 ({aliases.length})</Divider>
        <Table
          rowKey="id"
          dataSource={aliases}
          columns={aliasColumns}
          pagination={false}
          size="small"
        />
      </Modal>
    </div>
  )
}

export default MedicalTermManagement
