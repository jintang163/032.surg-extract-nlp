import React, { useEffect, useState, useRef, useMemo, useCallback } from 'react'
import {
  Card,
  Button,
  Row,
  Col,
  Form,
  Input,
  Select,
  InputNumber,
  DatePicker,
  Space,
  Divider,
  Tag,
  Steps,
  message,
  Modal,
  Tooltip,
  Typography,
  Radio,
  Progress,
  Statistic,
  Drawer,
  Alert,
  Descriptions,
  Result,
  Badge,
  Collapse,
} from 'antd'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  RocketOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  CheckCircleTwoTone,
  CheckCircleOutlined,
  CloseCircleOutlined,
  InfoCircleOutlined,
  UserOutlined,
  FileTextOutlined,
  MedicineBoxOutlined,
  TeamOutlined,
  AuditOutlined,
  FileDoneOutlined,
  SyncOutlined,
  SafetyCertificateOutlined,
  ExclamationCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { homePageApi, recordApi, qcApi } from '@/services/api'
import type { MedicalRecordHome, FieldMapping, SurgeryRecord, QcCheckResult, QcViolation } from '@/types'
import { HomePageStatusMap, QcSeverityMap, QcCategoryMap, HOME_PAGE_FIELD_LABEL_MAP } from '@/types'
import QualityReportPanel from '@/components/QualityReportPanel'
import dayjs from 'dayjs'

const { RangePicker } = DatePicker
const { Title, Text } = Typography
const { Option } = Select
const { TextArea } = Input

const HomePageFill: React.FC = () => {
  const navigate = useNavigate()
  const { recordId } = useParams<{ recordId: string }>()
  const recId = Number(recordId)

  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [homeData, setHomeData] = useState<MedicalRecordHome | null>(null)
  const [recordInfo, setRecordInfo] = useState<SurgeryRecord | null>(null)
  const [fieldMappings, setFieldMappings] = useState<FieldMapping[]>([])

  const [fillStartTime] = useState(dayjs())
  const [elapsedTime, setElapsedTime] = useState(0)
  const timerRef = useRef<NodeJS.Timeout | null>(null)

  const [showSubmitModal, setShowSubmitModal] = useState(false)
  const [requiredFields, setRequiredFields] = useState<string[]>([])
  const [missingFields, setMissingFields] = useState<string[]>([])
  const [qcViolationFields, setQcViolationFields] = useState<string[]>([])
  const [qcCheckResult, setQcCheckResult] = useState<QcCheckResult | null>(null)
  const [showQcModal, setShowQcModal] = useState(false)
  const [qcValidating, setQcValidating] = useState(false)
  const qcDebounceRef = useRef<NodeJS.Timeout | null>(null)
  const qcAbortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    timerRef.current = setInterval(() => {
      setElapsedTime(dayjs().diff(fillStartTime, 'second'))
    }, 1000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [fillStartTime])

  useEffect(() => {
    loadData()
  }, [recId])

  const loadData = async () => {
    setLoading(true)
    try {
      const [home, record, mappings] = await Promise.all([
        homePageApi.get(recId),
        recordApi.detail(recId),
        homePageApi.getFieldMappings(),
      ])
      setHomeData(home)
      setRecordInfo(record)
      setFieldMappings(mappings)

      const formData: any = { ...home }
      if (home.surgeryDate) formData.surgeryDate = dayjs(home.surgeryDate)
      if (home.admissionDate) formData.admissionDate = dayjs(home.admissionDate)
      if (home.dischargeDate) formData.dischargeDate = dayjs(home.dischargeDate)
      if (home.fillStartTime) {
        const st = dayjs(home.fillStartTime)
        setElapsedTime(dayjs().diff(st, 'second'))
      }
      form.setFieldsValue(formData)

      const required = mappings.filter((m) => m.required === 1).map((m) => m.targetField)
      setRequiredFields(required)

      setTimeout(() => {
        validateFormQc()
      }, 300)
    } catch (e) {
      console.error('加载数据失败', e)
      message.error('加载数据失败')
    } finally {
      setLoading(false)
    }
  }

  const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = seconds % 60
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s
      .toString()
      .padStart(2, '0')}`
  }

  const handleSaveDraft = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const data = transformFormData(values)
      data.fillStartTime = fillStartTime.format('YYYY-MM-DDTHH:mm:ss')
      data.fillEndTime = dayjs().format('YYYY-MM-DDTHH:mm:ss')
      data.fillDuration = elapsedTime
      await homePageApi.saveDraft(recId, data)
      message.success('草稿已保存')
    } catch (e: any) {
      if (!e?.errorFields) {
        message.error(e?.message || '保存失败')
      }
    } finally {
      setSaving(false)
    }
  }

  const handleSubmit = async () => {
    try {
      const values = form.getFieldsValue()
      const missing: string[] = []
      requiredFields.forEach((field) => {
        const v = values[field]
        if (v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0)) {
          const mapping = fieldMappings.find((m) => m.targetField === field)
          missing.push(mapping?.fieldLabel || field)
        }
      })
      setMissingFields(missing)
      if (missing.length > 0) {
        setShowSubmitModal(true)
        return
      }

      setQcValidating(true)
      try {
        const data = transformFormData(values)
        const qcResult = await qcApi.validateForm(data)
        setQcCheckResult(qcResult)

        const violationFields = new Set<string>()
        qcResult.violations?.forEach((v: QcViolation) => {
          v.relatedFields?.forEach((f: string) => violationFields.add(f))
        })
        setQcViolationFields(Array.from(violationFields))

        const hasErrors = qcResult.violations?.some((v: QcViolation) => v.severity === 'ERROR')
        if (hasErrors) {
          setShowQcModal(true)
          return
        }
      } catch (e) {
        console.warn('质控校验失败，继续提交流程', e)
      } finally {
        setQcValidating(false)
      }

      setSubmitting(true)
      const data = transformFormData(values)
      data.fillStartTime = fillStartTime.format('YYYY-MM-DDTHH:mm:ss')
      data.fillEndTime = dayjs().format('YYYY-MM-DDTHH:mm:ss')
      data.fillDuration = elapsedTime
      await homePageApi.update(recId, data)
      await homePageApi.submit(recId)
      if (timerRef.current) clearInterval(timerRef.current)
      message.success('提交成功，等待审核')
      setShowSubmitModal(true)
      setTimeout(() => {
        loadData()
      }, 500)
    } catch (e: any) {
      message.error(e?.message || '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const forceSubmitAfterQc = async () => {
    setShowQcModal(false)
    setSubmitting(true)
    try {
      const values = form.getFieldsValue()
      const data = transformFormData(values)
      data.fillStartTime = fillStartTime.format('YYYY-MM-DDTHH:mm:ss')
      data.fillEndTime = dayjs().format('YYYY-MM-DDTHH:mm:ss')
      data.fillDuration = elapsedTime
      await homePageApi.update(recId, data)
      await homePageApi.submit(recId)
      if (timerRef.current) clearInterval(timerRef.current)
      message.success('已强制提交')
      setTimeout(() => loadData(), 500)
    } catch (e: any) {
      message.error(e?.message || '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleViolationFieldsChange = useCallback((fields: string[]) => {
    setQcViolationFields(fields)
  }, [])

  const validateFormQc = useCallback(async () => {
    if (recId <= 0) return
    try {
      if (qcAbortRef.current) {
        qcAbortRef.current.abort()
      }
      const abortController = new AbortController()
      qcAbortRef.current = abortController

      setQcValidating(true)
      const values = form.getFieldsValue()
      const data = transformFormData(values)

      const result = await qcApi.validateForm(data)
      if (abortController.signal.aborted) return

      setQcCheckResult(result)

      const violationFields = new Set<string>()
      result.violations?.forEach((v: QcViolation) => {
        v.relatedFields?.forEach((f: string) => violationFields.add(f))
      })
      setQcViolationFields(Array.from(violationFields))
    } catch (e: any) {
      if (e?.name !== 'AbortError') {
        console.warn('实时质控校验失败', e)
      }
    } finally {
      setQcValidating(false)
    }
  }, [form, recId, transformFormData])

  const debouncedValidateQc = useCallback(() => {
    if (qcDebounceRef.current) {
      clearTimeout(qcDebounceRef.current)
    }
    qcDebounceRef.current = setTimeout(() => {
      validateFormQc()
    }, 500)
  }, [validateFormQc])

  const getFieldViolationInfo = useCallback((fieldName: string) => {
    if (!qcCheckResult?.violations) return null
    const violations = qcCheckResult.violations.filter(
      (v) => v.relatedFields?.includes(fieldName)
    )
    if (violations.length === 0) return null
    const hasError = violations.some((v) => v.severity === 'ERROR')
    const messages = violations.map((v) => v.message).join('；')
    return { hasError, messages, violations }
  }, [qcCheckResult])

  const getFormItemStatus = useCallback((fieldName: string) => {
    const info = getFieldViolationInfo(fieldName)
    if (!info) return ''
    return info.hasError ? 'error' : 'warning'
  }, [getFieldViolationInfo])

  const getFormItemHelp = useCallback((fieldName: string) => {
    const info = getFieldViolationInfo(fieldName)
    return info?.messages
  }, [getFieldViolationInfo])

  const getFormItemClassName = useCallback((fieldName: string) => {
    const info = getFieldViolationInfo(fieldName)
    if (!info) return ''
    return info.hasError ? 'qc-field-error' : 'qc-field-warning'
  }, [getFieldViolationInfo])

  const forceSubmit = async () => {
    try {
      setSubmitting(true)
      const values = form.getFieldsValue()
      const data = transformFormData(values)
      data.fillStartTime = fillStartTime.format('YYYY-MM-DDTHH:mm:ss')
      data.fillEndTime = dayjs().format('YYYY-MM-DDTHH:mm:ss')
      data.fillDuration = elapsedTime
      await homePageApi.update(recId, data)
      await homePageApi.submit(recId)
      if (timerRef.current) clearInterval(timerRef.current)
      setShowSubmitModal(false)
      message.success('已强制提交')
      setTimeout(() => loadData(), 500)
    } catch (e: any) {
      message.error(e?.message || '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const transformFormData = (values: any) => {
    const data: any = { ...values }
    if (values.surgeryDate) {
      data.surgeryDate = dayjs(values.surgeryDate).format('YYYY-MM-DDTHH:mm:ss')
    }
    if (values.admissionDate) {
      data.admissionDate = dayjs(values.admissionDate).format('YYYY-MM-DD')
    }
    if (values.dischargeDate) {
      data.dischargeDate = dayjs(values.dischargeDate).format('YYYY-MM-DD')
    }
    if (values.admissionDays === undefined && values.admissionDate && values.dischargeDate) {
      data.admissionDays = dayjs(values.dischargeDate).diff(dayjs(values.admissionDate), 'day')
    }
    return data
  }

  const isApproved = homeData?.status === 'APPROVED'
  const isPending = homeData?.status === 'PENDING'
  const isRejected = homeData?.status === 'REJECTED'
  const readOnly = isApproved || isPending

  const requiredFormItems = useMemo(() => {
    const filled = requiredFields.filter((f) => {
      const v = form.getFieldValue(f)
      return v !== undefined && v !== null && v !== '' && !(Array.isArray(v) && v.length === 0)
    })
    return {
      total: requiredFields.length,
      filled: filled.length,
      percent:
        requiredFields.length > 0 ? Math.round((filled.length / requiredFields.length) * 100) : 0,
    }
  }, [requiredFields, form])

  const estimatedMinutes = (recordInfo?.manualDurationEst || 600) / 60
  const actualMinutes = Math.round(elapsedTime / 60)
  const savedMinutes = Math.max(0, estimatedMinutes - actualMinutes)
  const efficiencyPercent =
    estimatedMinutes > 0 ? Math.min(100, Math.round((savedMinutes / estimatedMinutes) * 100)) : 0

  const steps = [
    {
      title: '草稿',
      status: homeData?.status === 'DRAFT' ? 'process' : isApproved || isPending || isRejected ? 'finish' : 'wait',
    },
    {
      title: '待审核',
      status: isPending ? 'process' : isApproved || isRejected ? 'finish' : 'wait',
    },
    {
      title: '审核通过',
      status: isApproved ? 'finish' : isRejected ? 'error' : 'wait',
    },
  ]

  return (
    <div className="page-container">
      <div style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
            返回
          </Button>
          <Title level={4} style={{ margin: 0 }}>
            病案首页填写
          </Title>
          {homeData?.status && (
            <Tag color={HomePageStatusMap[homeData.status as any]?.color}>
              {HomePageStatusMap[homeData.status as any]?.label}
            </Tag>
          )}
          {recordInfo?.hisSynced === 1 && (
            <Tag color="green" icon={<CheckCircleTwoTone twoToneColor="#52c41a" />}>
              HIS已同步
            </Tag>
          )}
        </Space>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={17}>
          <Card
            title={
              <Space>
                <FileTextOutlined />
                <span>病案首页信息</span>
              </Space>
            }
            loading={loading}
            extra={
              <Space>
                <Badge
                  count={`${requiredFormItems.percent}%`}
                  showZero
                  color={
                    requiredFormItems.percent === 100
                      ? '#52c41a'
                      : requiredFormItems.percent >= 60
                        ? '#fa8c16'
                        : '#ff4d4f'
                  }
                  style={{ backgroundColor: '#fff' }}
                >
                  <span style={{ color: '#8c8c8c', fontSize: 13 }}>必填项完成度</span>
                </Badge>
              </Space>
            }
          >
            {isApproved && (
              <Alert
                type="success"
                showIcon
                icon={<CheckCircleOutlined />}
                message="病案首页已审核通过并同步至HIS系统"
                style={{ marginBottom: 16 }}
              />
            )}
            {isPending && (
              <Alert
                type="info"
                showIcon
                icon={<SyncOutlined spin />}
                message="病案首页正在审核中，请耐心等待"
                style={{ marginBottom: 16 }}
              />
            )}
            {isRejected && homeData?.auditRemark && (
              <Alert
                type="error"
                showIcon
                message={`已驳回：${homeData.auditRemark}`}
                style={{ marginBottom: 16 }}
              />
            )}

            <Steps
              size="small"
              current={steps.findIndex((s) => s.status === 'process') + 1}
              items={steps}
              style={{ marginBottom: 24 }}
            />

            <Form
              form={form}
              layout="vertical"
              disabled={readOnly}
              requiredMark={(label, info) => (
                <span>
                  {label}
                  {info.required && <span style={{ color: '#ff4d4f', marginLeft: 2 }}>*</span>}
                </span>
              )}
              onValuesChange={() => {
                setRequiredFields([...requiredFields])
                if (!readOnly) {
                  debouncedValidateQc()
                }
              }}
            >
              <Divider orientation="left" orientationMargin={0}>
                <Space size={6}>
                  <UserOutlined />
                  <span>患者基本信息</span>
                </Space>
              </Divider>

              <Row gutter={[16, 8]}>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="患者姓名"
                    name="patientName"
                    rules={[{ required: requiredFields.includes('patient_name'), message: '请输入患者姓名' }]}
                  >
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="性别"
                    name="gender"
                    rules={[{ required: requiredFields.includes('gender'), message: '请选择性别' }]}
                  >
                    <Radio.Group>
                      <Radio value="男">男</Radio>
                      <Radio value="女">女</Radio>
                    </Radio.Group>
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="年龄"
                    name="age"
                    rules={[{ required: requiredFields.includes('age'), message: '请输入年龄' }]}
                    className={getFormItemClassName('age')}
                    help={getFormItemHelp('age')}
                    validateStatus={getFormItemStatus('age') as any}
                  >
                    <InputNumber min={0} max={150} addonAfter="岁" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="住院号"
                    name="hospitalNo"
                    rules={[{ required: requiredFields.includes('hospital_no'), message: '请输入住院号' }]}
                  >
                    <Input placeholder="请输入住院号" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="身份证号" name="idCardNo">
                    <Input placeholder="请输入18位身份证号" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="床号" name="bedNo">
                    <Input placeholder="如：1203" maxLength={16} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="科室" name="department">
                    <Select
                      placeholder="请选择"
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      options={[
                        { label: '普外科', value: '普外科' },
                        { label: '骨科', value: '骨科' },
                        { label: '神经外科', value: '神经外科' },
                        { label: '心胸外科', value: '心胸外科' },
                        { label: '泌尿外科', value: '泌尿外科' },
                        { label: '妇产科', value: '妇产科' },
                        { label: '眼科', value: '眼科' },
                        { label: '耳鼻喉科', value: '耳鼻喉科' },
                        { label: '口腔科', value: '口腔科' },
                        { label: '整形外科', value: '整形外科' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="入院日期" name="admissionDate">
                    <DatePicker style={{ width: '100%' }} placeholder="请选择" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="出院日期" name="dischargeDate">
                    <DatePicker style={{ width: '100%' }} placeholder="请选择" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="入院诊断"
                    name="admissionDiagnosis"
                    className={getFormItemClassName('admissionDiagnosis')}
                    help={getFormItemHelp('admissionDiagnosis')}
                    validateStatus={getFormItemStatus('admissionDiagnosis') as any}
                  >
                    <Input placeholder="ICD-10编码或诊断名称" maxLength={128} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="出院诊断"
                    name="dischargeDiagnosis"
                    className={getFormItemClassName('dischargeDiagnosis')}
                    help={getFormItemHelp('dischargeDiagnosis')}
                    validateStatus={getFormItemStatus('dischargeDiagnosis') as any}
                  >
                    <Input placeholder="ICD-10编码或诊断名称" maxLength={128} />
                  </Form.Item>
                </Col>
              </Row>

              <Divider orientation="left" orientationMargin={0}>
                <Space size={6}>
                  <MedicineBoxOutlined />
                  <span>手术信息</span>
                </Space>
              </Divider>

              <Row gutter={[16, 8]}>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="手术日期"
                    name="surgeryDate"
                    rules={[{ required: requiredFields.includes('surgery_date'), message: '请选择手术日期' }]}
                  >
                    <DatePicker showTime style={{ width: '100%' }} placeholder="请选择" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={12}>
                  <Form.Item
                    label="手术名称"
                    name="surgeryName"
                    rules={[{ required: requiredFields.includes('surgery_name'), message: '请输入手术名称' }]}
                  >
                    <Input placeholder="ICD-9-CM-3或手术名称" maxLength={256} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={4}>
                  <Form.Item label="手术编码" name="surgeryCode">
                    <Input placeholder="ICD-9编码" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="手术等级"
                    name="surgeryLevel"
                    className={getFormItemClassName('surgeryLevel')}
                    help={getFormItemHelp('surgeryLevel')}
                    validateStatus={getFormItemStatus('surgeryLevel') as any}
                  >
                    <Select placeholder="请选择">
                      <Option value="一级">一级</Option>
                      <Option value="二级">二级</Option>
                      <Option value="三级">三级</Option>
                      <Option value="四级">四级</Option>
                    </Select>
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="切口等级"
                    name="incisionLevel"
                    rules={[{ required: requiredFields.includes('incision_level'), message: '请选择切口等级' }]}
                    className={getFormItemClassName('incisionLevel')}
                    help={getFormItemHelp('incisionLevel')}
                    validateStatus={getFormItemStatus('incisionLevel') as any}
                  >
                    <Select placeholder="请选择">
                      <Option value="Ⅰ">Ⅰ类（清洁切口）</Option>
                      <Option value="Ⅱ">Ⅱ类（清洁-污染切口）</Option>
                      <Option value="Ⅲ">Ⅲ类（污染切口）</Option>
                    </Select>
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="切口愈合"
                    name="incisionHealing"
                    className={getFormItemClassName('incisionHealing')}
                    help={getFormItemHelp('incisionHealing')}
                    validateStatus={getFormItemStatus('incisionHealing') as any}
                  >
                    <Select placeholder="请选择">
                      <Option value="甲">甲级（优良）</Option>
                      <Option value="乙">乙级（欠佳）</Option>
                      <Option value="丙">丙级（化脓）</Option>
                    </Select>
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={12}>
                  <Form.Item
                    label="麻醉方式"
                    name="anesthesiaType"
                    rules={[{ required: requiredFields.includes('anesthesia_type'), message: '请输入麻醉方式' }]}
                    className={getFormItemClassName('anesthesiaType')}
                    help={getFormItemHelp('anesthesiaType')}
                    validateStatus={getFormItemStatus('anesthesiaType') as any}
                  >
                    <Select
                      placeholder="请选择或输入"
                      allowClear
                      showSearch
                      mode="tags"
                      maxTagCount={1}
                      options={[
                        { label: '全身麻醉', value: '全身麻醉' },
                        { label: '全身麻醉（气管插管）', value: '全身麻醉（气管插管）' },
                        { label: '椎管内麻醉', value: '椎管内麻醉' },
                        { label: '硬膜外麻醉', value: '硬膜外麻醉' },
                        { label: '腰硬联合麻醉', value: '腰硬联合麻醉' },
                        { label: '腰麻', value: '腰麻' },
                        { label: '局部浸润麻醉', value: '局部浸润麻醉' },
                        { label: '颈丛神经阻滞', value: '颈丛神经阻滞' },
                        { label: '臂丛神经阻滞', value: '臂丛神经阻滞' },
                        { label: '静脉麻醉', value: '静脉麻醉' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="麻醉编码" name="anesthesiaCode">
                    <Input placeholder="请输入麻醉编码" maxLength={32} />
                  </Form.Item>
                </Col>
              </Row>

              <Divider orientation="left" orientationMargin={0}>
                <Space size={6}>
                  <ThunderboltOutlined />
                  <span>术中数据</span>
                </Space>
              </Divider>

              <Row gutter={[16, 8]}>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="失血量"
                    name="bloodLoss"
                    className={getFormItemClassName('bloodLoss')}
                    help={getFormItemHelp('bloodLoss')}
                    validateStatus={getFormItemStatus('bloodLoss') as any}
                  >
                    <InputNumber
                      min={0}
                      addonAfter="ml"
                      style={{ width: '100%' }}
                      placeholder="请输入"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="输血量"
                    name="bloodTransfusion"
                    className={getFormItemClassName('bloodTransfusion')}
                    help={getFormItemHelp('bloodTransfusion')}
                    validateStatus={getFormItemStatus('bloodTransfusion') as any}
                  >
                    <InputNumber
                      min={0}
                      addonAfter="ml"
                      style={{ width: '100%' }}
                      placeholder="请输入"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="输液量" name="fluidInfusion">
                    <InputNumber
                      min={0}
                      addonAfter="ml"
                      style={{ width: '100%' }}
                      placeholder="请输入"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item
                    label="术中并发症"
                    name="complications"
                    className={getFormItemClassName('complications')}
                    help={getFormItemHelp('complications')}
                    validateStatus={getFormItemStatus('complications') as any}
                  >
                    <Select
                      mode="tags"
                      placeholder="选择或输入并发症，多个可添加"
                      style={{ width: '100%' }}
                      options={[
                        { label: '无', value: '无' },
                        { label: '心律失常', value: '心律失常' },
                        { label: '低血压', value: '低血压' },
                        { label: '高血压', value: '高血压' },
                        { label: '低氧血症', value: '低氧血症' },
                        { label: '大出血', value: '大出血' },
                        { label: '过敏性反应', value: '过敏性反应' },
                        { label: '皮下气肿', value: '皮下气肿' },
                        { label: '气胸', value: '气胸' },
                        { label: '感染', value: '感染' },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Divider orientation="left" orientationMargin={0}>
                <Space size={6}>
                  <TeamOutlined />
                  <span>手术人员</span>
                </Space>
              </Divider>

              <Row gutter={[16, 8]}>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="手术医生"
                    name="surgeon"
                    rules={[{ required: requiredFields.includes('surgeon'), message: '请输入手术医生姓名' }]}
                    className={getFormItemClassName('surgeon')}
                    help={getFormItemHelp('surgeon')}
                    validateStatus={getFormItemStatus('surgeon') as any}
                  >
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="主刀医生"
                    name="chiefSurgeon"
                    rules={[{ required: requiredFields.includes('chiefSurgeon'), message: '请输入主刀医生姓名' }]}
                    className={getFormItemClassName('chiefSurgeon')}
                    help={getFormItemHelp('chiefSurgeon')}
                    validateStatus={getFormItemStatus('chiefSurgeon') as any}
                  >
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="第一助手"
                    name="assistant1"
                    className={getFormItemClassName('assistant1')}
                    help={getFormItemHelp('assistant1')}
                    validateStatus={getFormItemStatus('assistant1') as any}
                  >
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="第二助手" name="assistant2">
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="麻醉医生"
                    name="anesthesiologist"
                    rules={[{ required: requiredFields.includes('anesthesiologist'), message: '请输入麻醉医生姓名' }]}
                    className={getFormItemClassName('anesthesiologist')}
                    help={getFormItemHelp('anesthesiologist')}
                    validateStatus={getFormItemStatus('anesthesiologist') as any}
                  >
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="器械护士" name="scrubNurse">
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item label="巡回护士" name="circulatingNurse">
                    <Input placeholder="请输入" maxLength={32} />
                  </Form.Item>
                </Col>
              </Row>

              <Divider orientation="left" orientationMargin={0}>
                <Space size={6}>
                  <InfoCircleOutlined />
                  <span>其他信息</span>
                </Space>
              </Divider>

              <Row gutter={[16, 8]}>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item
                    label="是否危重患者"
                    name="criticalPatient"
                    className={getFormItemClassName('criticalPatient')}
                    help={getFormItemHelp('criticalPatient')}
                    validateStatus={getFormItemStatus('criticalPatient') as any}
                  >
                    <Radio.Group>
                      <Radio value={0}>否</Radio>
                      <Radio value={1}>是</Radio>
                    </Radio.Group>
                  </Form.Item>
                </Col>
              </Row>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={7}>
          <div className="quality-report-sticky">
            {homeData && recId > 0 && (
              <div style={{ marginBottom: 16 }}>
                <QualityReportPanel
                  recordId={recId}
                  checkResult={qcCheckResult}
                  loading={qcValidating}
                  onViolationFieldsChange={handleViolationFieldsChange}
                  onRefresh={validateFormQc}
                />
              </div>
            )}

            <Card
              style={{ marginBottom: 16 }}
              title={
                <Space>
                  <ClockCircleOutlined style={{ color: '#1677ff' }} />
                  <span>填写用时</span>
                </Space>
              }
            >
            <div style={{ textAlign: 'center', padding: '8px 0 16px' }}>
              <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>
                实际用时
              </div>
              <div
                style={{
                  fontSize: 36,
                  fontWeight: 700,
                  color: '#1677ff',
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {formatTime(elapsedTime)}
              </div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                开始时间：{fillStartTime.format('HH:mm:ss')}
              </div>
            </div>

            <Divider style={{ margin: '12px 0' }} />

            <Row gutter={[12, 12]}>
              <Col xs={12}>
                <div className="stat-card" style={{ padding: 12, textAlign: 'center' }}>
                  <div className="stat-label">人工预估</div>
                  <div className="stat-value" style={{ fontSize: 18, color: '#8c8c8c' }}>
                    {estimatedMinutes.toFixed(0)}
                    <span style={{ fontSize: 12 }}>分钟</span>
                  </div>
                </div>
              </Col>
              <Col xs={12}>
                <div className="stat-card" style={{ padding: 12, textAlign: 'center' }}>
                  <div className="stat-label">节省时间</div>
                  <div className="stat-value" style={{ fontSize: 18, color: '#52c41a' }}>
                    {savedMinutes}
                    <span style={{ fontSize: 12 }}>分钟</span>
                  </div>
                </div>
              </Col>
            </Row>

            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>效率提升</span>
                <span
                  style={{
                    fontSize: 12,
                    color: '#52c41a',
                    fontWeight: 600,
                  }}
                >
                  {efficiencyPercent}%
                </span>
              </div>
              <Progress
                percent={efficiencyPercent}
                showInfo={false}
                strokeColor={{ '0%': '#95de64', '100%': '#52c41a' }}
                size="small"
              />
            </div>
          </Card>

          <Card
            style={{ marginBottom: 16 }}
            title={
              <Space>
                <CheckCircleOutlined style={{ color: '#52c41a' }} />
                <span>必填项检查</span>
              </Space>
            }
          >
            <div style={{ marginBottom: 12 }}>
              <Progress
                type="circle"
                size={100}
                percent={requiredFormItems.percent}
                format={(p) => `${requiredFormItems.filled}/${requiredFormItems.total}`}
                status={requiredFormItems.percent === 100 ? 'success' : 'active'}
              />
            </div>
            {requiredFields.length > 0 && (
              <div>
                {fieldMappings
                  .filter((m) => m.required === 1)
                  .map((m) => {
                    const filled = form.getFieldValue(m.targetField)
                    const hasValue =
                      filled !== undefined &&
                      filled !== null &&
                      filled !== '' &&
                      !(Array.isArray(filled) && filled.length === 0)
                    return (
                      <div
                        key={m.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '6px 0',
                          fontSize: 13,
                          borderBottom: '1px solid #f0f0f0',
                        }}
                      >
                        <span>
                          {hasValue ? (
                            <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 4 }} />
                          ) : (
                            <span style={{ color: '#ff4d4f', marginRight: 4 }}>*</span>
                          )}
                          {m.fieldLabel}
                        </span>
                        <Tag color={hasValue ? 'green' : 'red'} style={{ margin: 0 }}>
                          {hasValue ? '已填' : '未填'}
                        </Tag>
                      </div>
                    )
                  })}
              </div>
            )}
          </Card>

          {homeData && (
            <Card
              title={
                <Space>
                  <FileDoneOutlined style={{ color: '#722ed1' }} />
                  <span>审核记录</span>
                </Space>
              }
            >
              <Descriptions column={1} size="small">
                <Descriptions.Item label="当前状态">
                  {HomePageStatusMap[homeData.status as any] ? (
                    <Tag color={HomePageStatusMap[homeData.status as any].color}>
                      {HomePageStatusMap[homeData.status as any].label}
                    </Tag>
                  ) : (
                    homeData.status
                  )}
                </Descriptions.Item>
                {homeData.auditUserName && (
                  <Descriptions.Item label="审核人">{homeData.auditUserName}</Descriptions.Item>
                )}
                {homeData.auditTime && (
                  <Descriptions.Item label="审核时间">
                    {dayjs(homeData.auditTime).format('YYYY-MM-DD HH:mm')}
                  </Descriptions.Item>
                )}
                {homeData.auditRemark && (
                  <Descriptions.Item label="审核意见">{homeData.auditRemark}</Descriptions.Item>
                )}
                {homeData.fillUserName && (
                  <Descriptions.Item label="填写人">{homeData.fillUserName}</Descriptions.Item>
                )}
                {homeData.fillDuration && (
                  <Descriptions.Item label="实际填写时长">
                    {Math.round(homeData.fillDuration / 60)} 分 {homeData.fillDuration % 60} 秒
                  </Descriptions.Item>
                )}
              </Descriptions>
            </Card>
          )}

          <div style={{ marginTop: 16, position: 'sticky', top: 20, zIndex: 1 }}>
            {!readOnly ? (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <Button
                  block
                  icon={<SaveOutlined />}
                  size="large"
                  loading={saving}
                  onClick={handleSaveDraft}
                >
                  保存草稿
                </Button>
                <Button
                  type="primary"
                  block
                  icon={<RocketOutlined />}
                  size="large"
                  loading={submitting}
                  onClick={handleSubmit}
                >
                  提交审核
                </Button>
                <Button
                  block
                  icon={<FileTextOutlined />}
                  size="large"
                  onClick={() => navigate(`/records/${recId}`)}
                >
                  查看原始记录
                </Button>
              </Space>
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <Button block icon={<FileTextOutlined />} size="large" onClick={() => navigate(`/records/${recId}`)}>
                  查看原始记录
                </Button>
                {isRejected && (
                  <Button
                    type="primary"
                    block
                    icon={<AuditOutlined />}
                    size="large"
                    onClick={() => {
                      setSubmitting(true)
                      homePageApi
                        .update(recId, { status: 'DRAFT' })
                        .then(() => {
                          message.success('已取消提交，请重新修改后再提交')
                          loadData()
                        })
                        .catch((e) => message.error(e?.message || '操作失败'))
                        .finally(() => setSubmitting(false))
                    }}
                  >
                    重新编辑
                  </Button>
                )}
              </Space>
            )}
          </div>
          </div>
        </Col>
      </Row>

      <Modal
        title={missingFields.length > 0 ? '存在未填写的必填项' : '确认提交审核？'}
        open={showSubmitModal}
        onCancel={() => !submitting && setShowSubmitModal(false)}
        width={480}
        footer={
          missingFields.length > 0 ? (
            <Space>
              <Button onClick={() => setShowSubmitModal(false)}>返回修改</Button>
              <Button
                type="primary"
                danger
                loading={submitting}
                icon={<RocketOutlined />}
                onClick={forceSubmit}
              >
                仍然提交
              </Button>
            </Space>
          ) : (
            <Space>
              <Button onClick={() => setShowSubmitModal(false)}>取消</Button>
              <Button
                type="primary"
                loading={submitting}
                icon={<CheckCircleOutlined />}
                onClick={() => {
                  setShowSubmitModal(false)
                  message.success('已提交审核')
                  loadData()
                }}
              >
                确认提交
              </Button>
            </Space>
          )
        }
      >
        {missingFields.length > 0 ? (
          <>
            <Alert
              type="warning"
              showIcon
              message={`共有 ${missingFields.length} 项必填项未填写，提交后将无法再次编辑`}
              style={{ marginBottom: 16 }}
            />
            <div>
              <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 8 }}>
                未填写字段：
              </div>
              <Space wrap>
                {missingFields.map((f) => (
                  <Tag key={f} color="red">
                    {f}
                  </Tag>
                ))}
              </Space>
            </div>
          </>
        ) : (
          <Result
            status="success"
            title="所有必填项已填写完成"
            subTitle="提交后病案首页将进入审核流程，请确认信息无误"
          />
        )}
      </Modal>

      <Modal
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#ff4d4f' }} />
            <span>质控校验不通过</span>
          </Space>
        }
        open={showQcModal}
        onCancel={() => setShowQcModal(false)}
        width={720}
        footer={
          <Space>
            <Button onClick={() => setShowQcModal(false)}>返回修改</Button>
            <Button
              type="primary"
              danger
              loading={submitting}
              icon={<RocketOutlined />}
              onClick={forceSubmitAfterQc}
            >
              强制提交
            </Button>
          </Space>
        }
      >
        {qcCheckResult && (
          <>
            <Alert
              type="error"
              showIcon
              icon={<CloseCircleOutlined />}
              message={`质控校验发现 ${qcCheckResult.violations?.filter((v) => v.severity === 'ERROR').length || 0} 个错误，${qcCheckResult.violations?.filter((v) => v.severity === 'WARNING').length || 0} 个警告`}
              description="请修复以下问题后再提交，或选择强制提交"
              style={{ marginBottom: 16 }}
            />

            <div style={{ maxHeight: 400, overflow: 'auto' }}>
              {qcCheckResult.violations?.map((v, idx) => (
                <div
                  key={idx}
                  style={{
                    padding: '10px 12px',
                    marginBottom: 8,
                    background: v.severity === 'ERROR' ? '#fff2f0' : '#fffbe6',
                    borderRadius: 6,
                    borderLeft: `4px solid ${v.severity === 'ERROR' ? '#ff4d4f' : '#faad14'}`,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                    {v.severity === 'ERROR' ? (
                      <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                    ) : (
                      <WarningOutlined style={{ color: '#faad14' }} />
                    )}
                    <Tag
                      color={QcSeverityMap[v.severity]?.color || 'default'}
                      style={{ fontSize: 11, padding: '0 6px', lineHeight: '18px', margin: 0 }}
                    >
                      {QcSeverityMap[v.severity]?.label}
                    </Tag>
                    <Tag
                      color={QcCategoryMap[v.category]?.color || 'default'}
                      style={{ fontSize: 11, padding: '0 6px', lineHeight: '18px', margin: 0 }}
                    >
                      {QcCategoryMap[v.category]?.label}
                    </Tag>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>{v.ruleCode}: {v.ruleName}</span>
                  </div>
                  <div style={{ fontSize: 13, color: '#595959', marginBottom: 6 }}>{v.message}</div>
                  {v.relatedFields && v.relatedFields.length > 0 && (
                    <div>
                      <span style={{ fontSize: 11, color: '#8c8c8c' }}>关联字段：</span>
                      {v.relatedFields.map((f) => (
                        <Tag key={f} style={{ fontSize: 10, margin: '0 2px' }} color="default">
                          {HOME_PAGE_FIELD_LABEL_MAP[f] || f}
                        </Tag>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
      </Modal>
    </div>
  )
}

export default HomePageFill
