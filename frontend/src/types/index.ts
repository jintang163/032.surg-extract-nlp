export interface Result<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface UserInfo {
  userId: number
  username: string
  realName: string
  role: 'ADMIN' | 'DOCTOR' | 'NURSE'
  department: string
  title: string
  phone?: string
  email?: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  realName: string
  role: string
  department: string
  title: string
}

export type ProcessStatus =
  | 'PENDING'
  | 'OCR_PROCESSING'
  | 'OCR_DONE'
  | 'ASR_PROCESSING'
  | 'ASR_DONE'
  | 'NER_PROCESSING'
  | 'NER_DONE'
  | 'COMPLETED'
  | 'FAILED'

export const ProcessStatusMap: Record<ProcessStatus, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'default' },
  OCR_PROCESSING: { label: 'OCR处理中', color: 'processing' },
  OCR_DONE: { label: 'OCR完成', color: 'blue' },
  ASR_PROCESSING: { label: '语音识别中', color: 'processing' },
  ASR_DONE: { label: '语音识别完成', color: 'purple' },
  NER_PROCESSING: { label: '实体抽取中', color: 'processing' },
  NER_DONE: { label: '抽取完成', color: 'cyan' },
  COMPLETED: { label: '已完成', color: 'success' },
  FAILED: { label: '处理失败', color: 'error' },
}

export type FileType = 'TEXT' | 'WORD' | 'PDF' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'UNKNOWN'

export const FileTypeMap: Record<FileType, { label: string; icon: string }> = {
  TEXT: { label: '纯文本', icon: 'FileTextOutlined' },
  WORD: { label: 'Word文档', icon: 'FileWordOutlined' },
  PDF: { label: 'PDF文档', icon: 'FilePdfOutlined' },
  IMAGE: { label: '图片', icon: 'FileImageOutlined' },
  AUDIO: { label: '音频', icon: 'AudioOutlined' },
  VIDEO: { label: '视频', icon: 'VideoCameraOutlined' },
  UNKNOWN: { label: '未知', icon: 'FileOutlined' },
}

export type TermType = 'SURGERY' | 'DIAGNOSIS' | 'ANESTHESIA' | 'INSTRUMENT' | 'DRUG' | 'OTHER'

export const TermTypeMap: Record<TermType, { label: string; color: string }> = {
  SURGERY: { label: '手术名称', color: 'blue' },
  DIAGNOSIS: { label: '诊断', color: 'green' },
  ANESTHESIA: { label: '麻醉', color: 'purple' },
  INSTRUMENT: { label: '器械', color: 'cyan' },
  DRUG: { label: '药品', color: 'orange' },
  OTHER: { label: '其他', color: 'default' },
}

export type AliasType = 'SYNONYM' | 'ABBREVIATION' | 'MISTAKE' | 'TRANSLATION' | 'REGIONAL' | 'MERGED'

export const AliasTypeMap: Record<AliasType, { label: string; color: string }> = {
  SYNONYM: { label: '同义词', color: 'blue' },
  ABBREVIATION: { label: '缩写', color: 'green' },
  MISTAKE: { label: '常见误写', color: 'red' },
  TRANSLATION: { label: '译名', color: 'purple' },
  REGIONAL: { label: '地域说法', color: 'cyan' },
  MERGED: { label: '合并来源', color: 'orange' },
}

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export const ReviewStatusMap: Record<ReviewStatus, { label: string; color: string }> = {
  PENDING: { label: '待审核', color: 'warning' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
}

export interface MedicalTerm {
  id: number
  termCode: string
  standardName: string
  termType: TermType
  categoryId?: number
  categoryName?: string
  pinyin?: string
  pinyinAbbr?: string
  icdCode?: string
  icdName?: string
  definition?: string
  confidence: number
  matchCount: number
  usageCount: number
  reviewStatus: ReviewStatus
  reviewRemark?: string
  reviewedBy?: number
  reviewedTime?: string
  enabled: number
  createdTime: string
  updatedTime: string
  aliases?: MedicalTermAlias[]
}

export interface MedicalTermAlias {
  id: number
  termId: number
  aliasName: string
  aliasType: AliasType
  pinyin?: string
  pinyinAbbr?: string
  similarityScore?: number
  source?: string
  reviewStatus: ReviewStatus
  reviewedBy?: number
  reviewedTime?: string
  enabled: number
  createdTime: string
}

export interface TermMappingResult {
  mappingSuccess: boolean
  sourceText: string
  standardTermId?: number
  standardTermCode?: string
  standardTermName?: string
  termType?: TermType
  icdCode?: string
  icdName?: string
  matchMethod?: string
  similarityScore?: number
  candidates?: TermMappingCandidate[]
}

export interface TermMappingCandidate {
  termId: number
  termCode: string
  termName: string
  matchedText?: string
  matchMethod: string
  similarityScore: number
  rank?: number
}

export interface TermGraphStats {
  totalNodes: number
  totalRelationships: number
  termTypeStats: Record<string, number>
  topMatchedTerms: MedicalTerm[]
}

export interface TermGraphNode {
  id: string
  termId: number
  termCode: string
  name: string
  standardName: string
  termType: TermType
  pinyin?: string
  pinyinAbbr?: string
  icdCode?: string
  icdName?: string
  isStandard?: boolean
  enabled?: boolean
}

export interface SurgeryRecord {
  id: number
  recordNo: string
  patientId?: string
  patientName?: string
  hospitalNo?: string
  gender?: '男' | '女'
  age?: number
  department?: string
  originalFileName: string
  fileType: FileType
  filePath?: string
  fileSize?: number
  ocrText?: string
  processedText?: string
  asrText?: string
  audioDuration?: number
  asrSegments?: any[]
  enhancedText?: string
  instruments?: DetectedInstrument[]
  fusionStats?: Record<string, any>
  multimodalStatus?: 'NONE' | 'ASR_DONE' | 'INSTRUMENT_DONE' | 'FUSED' | 'FUSION_FAILED' | 'FUSION_ERROR'
  uploadUserId?: number
  uploadUserName?: string
  uploadTime: string
  processStatus: ProcessStatus
  processMessage?: string
  ocrStartTime?: string
  ocrEndTime?: string
  nerStartTime?: string
  nerEndTime?: string
  patientConfirmed?: number
  confirmUserId?: number
  confirmTime?: string
  fillDuration?: number
  manualDurationEst?: number
  hisSynced?: number
  hisSyncTime?: string
  hisSyncMessage?: string
}

export interface DetectedInstrument {
  instrumentName: string
  instrumentCode?: string
  category?: string
  confidence?: number
  count?: number
  position?: { x?: number; y?: number; width?: number; height?: number }
}

export interface SurgeryEntity {
  id?: number
  recordId: number
  entityType: EntityType
  entityValue: string
  entityUnit?: string
  confidence?: number
  source?: 'MODEL' | 'REGEX' | 'RULE' | 'MANUAL'
  startPos?: number
  endPos?: number
  originalText?: string
  verified?: number
  verifiedBy?: number
  verifiedTime?: string
  remark?: string
}

export type EntityType =
  | 'PATIENT_NAME'
  | 'HOSPITAL_NO'
  | 'GENDER'
  | 'AGE'
  | 'DEPARTMENT'
  | 'SURGERY_DATE'
  | 'SURGERY_NAME'
  | 'SURGERY_LEVEL'
  | 'INCISION_LEVEL'
  | 'INCISION_HEALING'
  | 'ANESTHESIA_TYPE'
  | 'BLOOD_LOSS'
  | 'BLOOD_TRANSFUSION'
  | 'FLUID_INFUSION'
  | 'URINE_OUTPUT'
  | 'COMPLICATION'
  | 'SURGEON'
  | 'ASSISTANT'
  | 'ANESTHESIOLOGIST'
  | 'SCRUB_NURSE'
  | 'CIRCULATING_NURSE'
  | 'PREOP_DIAGNOSIS'
  | 'POSTOP_DIAGNOSIS'
  | 'BED_NO'
  | 'ADMISSION_DATE'

export const EntityTypeLabelMap: Record<EntityType, { label: string; color: string; required?: boolean }> = {
  PATIENT_NAME: { label: '患者姓名', color: 'magenta', required: true },
  HOSPITAL_NO: { label: '住院号', color: 'purple', required: true },
  GENDER: { label: '性别', color: 'blue', required: true },
  AGE: { label: '年龄', color: 'geekblue', required: true },
  DEPARTMENT: { label: '科室', color: 'cyan' },
  SURGERY_DATE: { label: '手术日期', color: 'green', required: true },
  SURGERY_NAME: { label: '手术名称', color: 'volcano', required: true },
  SURGERY_LEVEL: { label: '手术等级', color: 'orange' },
  INCISION_LEVEL: { label: '切口等级', color: 'red', required: true },
  INCISION_HEALING: { label: '切口愈合', color: 'lime' },
  ANESTHESIA_TYPE: { label: '麻醉方式', color: 'gold', required: true },
  BLOOD_LOSS: { label: '失血量', color: 'pink' },
  BLOOD_TRANSFUSION: { label: '输血量', color: 'maroon' },
  FLUID_INFUSION: { label: '输液量', color: 'teal' },
  URINE_OUTPUT: { label: '尿量', color: 'blue' },
  COMPLICATION: { label: '术中并发症', color: 'red' },
  SURGEON: { label: '手术医生', color: 'violet', required: true },
  ASSISTANT: { label: '助手', color: 'purple' },
  ANESTHESIOLOGIST: { label: '麻醉医生', color: 'indigo', required: true },
  SCRUB_NURSE: { label: '器械护士', color: 'cyan' },
  CIRCULATING_NURSE: { label: '巡回护士', color: 'teal' },
  PREOP_DIAGNOSIS: { label: '术前诊断', color: 'orange' },
  POSTOP_DIAGNOSIS: { label: '术后诊断', color: 'brown' },
  BED_NO: { label: '床号', color: 'gray' },
  ADMISSION_DATE: { label: '入院日期', color: 'green' },
}

export const SourceMap: Record<string, { label: string; color: string }> = {
  MODEL: { label: '模型识别', color: 'blue' },
  REGEX: { label: '正则匹配', color: 'green' },
  RULE: { label: '规则引擎', color: 'orange' },
  MANUAL: { label: '人工修正', color: 'purple' },
}

export type HomePageStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED'

export const HomePageStatusMap: Record<HomePageStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  PENDING: { label: '待审核', color: 'processing' },
  APPROVED: { label: '已审核', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
}

export interface MedicalRecordHome {
  id?: number
  recordId: number
  patientId?: string
  patientName?: string
  gender?: '男' | '女'
  age?: number
  idCardNo?: string
  hospitalNo?: string
  admissionDate?: string
  dischargeDate?: string
  admissionDays?: number
  department?: string
  bedNo?: string
  admissionDiagnosis?: string
  dischargeDiagnosis?: string
  surgeryDate?: string
  surgeryName?: string
  surgeryCode?: string
  surgeryLevel?: '一级' | '二级' | '三级' | '四级'
  incisionLevel?: 'Ⅰ' | 'Ⅱ' | 'Ⅲ'
  incisionHealing?: '甲' | '乙' | '丙'
  anesthesiaType?: string
  anesthesiaCode?: string
  bloodLoss?: number
  bloodTransfusion?: number
  fluidInfusion?: number
  complications?: string[]
  surgeon?: string
  chiefSurgeon?: string
  assistant1?: string
  assistant2?: string
  anesthesiologist?: string
  scrubNurse?: string
  circulatingNurse?: string
  criticalPatient?: number
  hospitalizationFee?: number
  surgeryFee?: number
  anesthesiaFee?: number
  drugFee?: number
  examFee?: number
  treatmentFee?: number
  bedFee?: number
  otherFee?: number
  fillUserId?: number
  fillUserName?: string
  fillStartTime?: string
  fillEndTime?: string
  fillDuration?: number
  manualDurationEst?: number
  status?: HomePageStatus
  auditUserId?: number
  auditUserName?: string
  auditTime?: string
  auditRemark?: string
}

export interface FieldMapping {
  id: number
  entityType: EntityType
  targetTable: string
  targetField: string
  fieldLabel: string
  required?: number
  dataType?: 'STRING' | 'INT' | 'DECIMAL' | 'DATE' | 'DATETIME' | 'ENUM'
  unit?: string
  enumValues?: string[]
  sortOrder: number
  enabled: number
}

export interface EfficiencyStats {
  totalRecords: number
  autoFilledCount: number
  confirmedCount: number
  totalManualEstSeconds: number
  totalActualSeconds: number
  savedSeconds: number
  savedMinutes: number
  savedHours: number
  efficiencyRate: string
  avgFillSeconds: number
}

export interface Placeholder {
  name: string
  label: string
  entityType?: EntityType | string
  description?: string
  required?: boolean
  defaultValue?: string
}

export type TemplateStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE'

export const TemplateStatusMap: Record<TemplateStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '启用', color: 'success' },
  INACTIVE: { label: '停用', color: 'warning' },
}

export interface SurgeryTemplate {
  id: number
  templateCode: string
  templateName: string
  surgeryType: string
  surgeryCode?: string
  department?: string
  templateContent: string
  placeholders: Placeholder[]
  currentVersion: number
  status: TemplateStatus
  isDefault: number
  description?: string
  tags?: string
  sortOrder: number
  useCount: number
  createdUserName?: string
  updatedUserName?: string
  createdTime: string
  updatedTime: string
}

export interface SurgeryTemplateVersion {
  id: number
  templateId: number
  versionNo: number
  templateContent: string
  placeholders: Placeholder[]
  changeLog?: string
  isCurrent: number
  createdUserName?: string
  createdTime: string
}

export interface SurgeryTemplateCreateForm {
  templateCode: string
  templateName: string
  surgeryType: string
  surgeryCode?: string
  department?: string
  templateContent: string
  placeholders: Placeholder[]
  description?: string
  tags?: string
  sortOrder?: number
  changeLog?: string
}

export interface SurgeryTemplateUpdateForm {
  templateName?: string
  surgeryType?: string
  surgeryCode?: string
  department?: string
  templateContent?: string
  placeholders?: Placeholder[]
  status?: TemplateStatus
  isDefault?: number
  description?: string
  tags?: string
  sortOrder?: number
  changeLog?: string
}

export interface TemplateImportData {
  templateCode: string
  templateName: string
  surgeryType: string
  surgeryCode?: string
  department?: string
  templateContent: string
  placeholders: Placeholder[]
  description?: string
  tags?: string
  sortOrder?: number
}

export type VoiceMessageType =
  | 'SESSION_STARTED'
  | 'SESSION_STOPPED'
  | 'PARTIAL'
  | 'FINAL_SEGMENT'
  | 'HOME_PAGE_UPDATE'
  | 'ENTITY_UPDATE'
  | 'ERROR'
  | 'PONG'

export interface VoiceStreamMessage {
  type: VoiceMessageType
  sessionId: string
  text?: string
  isFinal?: boolean
  errorMsg?: string
  data?: any
  timestamp?: string
}

export interface VoiceSession {
  sessionId: string
  recordId?: number
  wsUrl: string
  language: string
  enableAutoPunctuation: boolean
  enableRealTimeNer: boolean
  startTime: string
}

export interface HomePageField {
  key: string
  label: string
  value: any
  source?: string
  updatedAt?: string
}

export const HOME_PAGE_FIELDS: HomePageField[] = [
  { key: 'patientName', label: '患者姓名' },
  { key: 'gender', label: '性别' },
  { key: 'age', label: '年龄' },
  { key: 'hospitalNo', label: '住院号' },
  { key: 'department', label: '科室' },
  { key: 'admissionDiagnosis', label: '入院诊断' },
  { key: 'dischargeDiagnosis', label: '出院诊断' },
  { key: 'surgeryDate', label: '手术日期' },
  { key: 'surgeryName', label: '手术名称' },
  { key: 'surgeryCode', label: '手术编码' },
  { key: 'incisionLevel', label: '切口等级' },
  { key: 'incisionHealing', label: '切口愈合' },
  { key: 'anesthesiaType', label: '麻醉方式' },
  { key: 'anesthesiaCode', label: '麻醉编码' },
  { key: 'bloodLoss', label: '失血量(ml)' },
  { key: 'bloodTransfusion', label: '输血量(ml)' },
  { key: 'fluidInfusion', label: '输液量(ml)' },
  { key: 'surgeon', label: '术者' },
  { key: 'chiefSurgeon', label: '主刀医生' },
  { key: 'assistant1', label: '第一助手' },
  { key: 'anesthesiologist', label: '麻醉医师' },
  { key: 'scrubNurse', label: '器械护士' },
  { key: 'circulatingNurse', label: '巡回护士' },
  { key: 'complications', label: '并发症' },
]

export const HOME_PAGE_FIELD_LABEL_MAP: Record<string, string> =
  HOME_PAGE_FIELDS.reduce((acc, f) => {
    acc[f.key] = f.label
    return acc
  }, {} as Record<string, string>)

export interface QcViolation {
  ruleCode: string
  ruleName: string
  category: 'COMPLETENESS' | 'LOGIC_CONSISTENCY'
  severity: 'ERROR' | 'WARNING'
  message: string
  relatedFields: string[]
}

export interface QcCheckResult {
  violations: QcViolation[]
  totalChecks: number
  passedChecks: number
  failedChecks: number
  passed: boolean
  passRate: number
}

export interface QcFieldCheck {
  fieldName: string
  fieldLabel: string
  filled: boolean
  required: boolean
  valid: boolean
  issue?: string
}

export interface QcScorecard {
  recordId: number
  recordNo: string
  patientName: string
  surgeryName: string
  completenessScore: number
  logicConsistencyScore: number
  overallScore: number
  grade: string
  fieldChecks: QcFieldCheck[]
  violations: QcViolation[]
  totalFields: number
  filledFields: number
  requiredFields: number
  requiredFilled: number
  logicRuleCount: number
  logicPassed: number
  logicFailed: number
}

export const QcSeverityMap: Record<string, { label: string; color: string }> = {
  ERROR: { label: '错误', color: 'red' },
  WARNING: { label: '警告', color: 'orange' },
}

export const QcCategoryMap: Record<string, { label: string; color: string }> = {
  COMPLETENESS: { label: '完整性', color: 'blue' },
  LOGIC_CONSISTENCY: { label: '逻辑一致性', color: 'purple' },
}

export interface HisSyncLog {
  id: number
  recordId: number
  syncType: string
  syncDirection: 'TO_HIS' | 'FROM_HIS'
  syncStatus: 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'PENDING'
  syncData?: string
  responseData?: string
  errorMessage?: string
  retryCount?: number
  syncStartTime?: string
  syncEndTime?: string
  duration?: number
  createdUserId?: number
  createdUserName?: string
  createdTime?: string
  updatedTime?: string
}

export const SyncTypeMap: Record<string, { label: string; color: string }> = {
  HOME_PAGE: { label: '病案首页', color: 'blue' },
  ROLLBACK: { label: '首页回滚', color: 'orange' },
  BILLING: { label: '计费', color: 'green' },
  BILLING_ROLLBACK: { label: '计费回滚', color: 'gold' },
}

export const SyncDirectionMap: Record<string, { label: string; color: string }> = {
  TO_HIS: { label: '写入HIS', color: 'geekblue' },
  FROM_HIS: { label: '从HIS读取', color: 'purple' },
}

export const SyncStatusMap: Record<string, { label: string; color: string }> = {
  SUCCESS: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  SKIPPED: { label: '跳过', color: 'default' },
  PENDING: { label: '处理中', color: 'processing' },
}

export interface HisSyncStatus {
  status: number
  label: string
  color: string
  text: string
}

export const HisSyncedMap: Record<number, { label: string; status: string; color: string }> = {
  0: { label: '未同步', status: 'default', color: '#8c8c8c' },
  1: { label: '已同步', status: 'success', color: '#52c41a' },
  2: { label: '同步失败', status: 'error', color: '#ff4d4f' },
  3: { label: '回滚失败', status: 'warning', color: '#faad14' },
}

export type QcReportTemplateFileType = 'WORD' | 'EXCEL'

export const QcReportTemplateFileTypeMap: Record<QcReportTemplateFileType, { label: string; icon: string; accept: string }> = {
  WORD: { label: 'Word文档', icon: 'FileWordOutlined', accept: '.doc,.docx' },
  EXCEL: { label: 'Excel表格', icon: 'FileExcelOutlined', accept: '.xls,.xlsx' },
}

export type QcReportTemplateStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE'

export const QcReportTemplateStatusMap: Record<QcReportTemplateStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '启用', color: 'success' },
  INACTIVE: { label: '停用', color: 'warning' },
}

export interface QcFieldBinding {
  id?: number
  templateId?: number
  placeholderKey: string
  placeholderLabel: string
  qcFieldKey: string
  qcFieldLabel: string
  fieldType: 'SCALAR' | 'LIST' | 'TABLE' | 'HEADER'
  description?: string
  sortOrder?: number
}

export interface QcReportTemplate {
  id: number
  templateCode: string
  templateName: string
  fileType: QcReportTemplateFileType
  department?: string
  originalFileName?: string
  fileUrl?: string
  fileSize?: number
  placeholders?: string[]
  fieldBindings: QcFieldBinding[]
  currentVersion: number
  status: QcReportTemplateStatus
  isDefault: number
  enableWatermark: number
  watermarkText?: string
  description?: string
  tags?: string
  sortOrder: number
  useCount: number
  createdUserName?: string
  updatedUserName?: string
  createdTime: string
  updatedTime: string
}

export interface QcReportTemplateVersion {
  id: number
  templateId: number
  versionNo: number
  originalFileName?: string
  fileUrl?: string
  fieldBindings: QcFieldBinding[]
  changeLog?: string
  isCurrent: number
  createdUserName?: string
  createdTime: string
}

export interface QcReportTemplateCreateForm {
  templateCode: string
  templateName: string
  fileType: QcReportTemplateFileType
  department?: string
  fieldBindings: QcFieldBinding[]
  status?: QcReportTemplateStatus
  isDefault?: number
  enableWatermark?: number
  watermarkText?: string
  description?: string
  tags?: string
  sortOrder?: number
  changeLog?: string
}

export interface QcReportTemplateUpdateForm {
  templateName?: string
  fileType?: QcReportTemplateFileType
  department?: string
  fieldBindings?: QcFieldBinding[]
  status?: QcReportTemplateStatus
  isDefault?: number
  enableWatermark?: number
  watermarkText?: string
  description?: string
  tags?: string
  sortOrder?: number
  changeLog?: string
}

export interface QcExportConfig {
  templateId: number
  recordId: number
  enableWatermark?: boolean
  watermarkText?: string
  outputFormat: 'PDF' | 'WORD' | 'EXCEL'
}

export const QC_AVAILABLE_FIELDS: {
  group: string
  groupLabel: string
  fields: { key: string; label: string; type: 'SCALAR' | 'LIST' | 'TABLE'; description?: string }[]
}[] = [
  {
    group: 'BASIC',
    groupLabel: '基本信息',
    fields: [
      { key: 'recordNo', label: '记录编号', type: 'SCALAR', description: '质控记录唯一编号' },
      { key: 'patientName', label: '患者姓名', type: 'SCALAR' },
      { key: 'patientGender', label: '患者性别', type: 'SCALAR' },
      { key: 'patientAge', label: '患者年龄', type: 'SCALAR' },
      { key: 'hospitalNo', label: '住院号', type: 'SCALAR' },
      { key: 'department', label: '科室', type: 'SCALAR' },
      { key: 'surgeryName', label: '手术名称', type: 'SCALAR' },
      { key: 'surgeryDate', label: '手术日期', type: 'SCALAR' },
      { key: 'surgeon', label: '术者', type: 'SCALAR' },
      { key: 'chiefSurgeon', label: '主刀医生', type: 'SCALAR' },
      { key: 'anesthesiologist', label: '麻醉医师', type: 'SCALAR' },
    ],
  },
  {
    group: 'SCORE',
    groupLabel: '质控评分',
    fields: [
      { key: 'overallScore', label: '综合评分', type: 'SCALAR', description: '0-100分' },
      { key: 'overallGrade', label: '质量等级', type: 'SCALAR', description: 'A/B/C/D级' },
      { key: 'completenessScore', label: '完整性评分', type: 'SCALAR' },
      { key: 'logicConsistencyScore', label: '逻辑一致性评分', type: 'SCALAR' },
      { key: 'totalFields', label: '总字段数', type: 'SCALAR' },
      { key: 'filledFields', label: '已填字段数', type: 'SCALAR' },
      { key: 'requiredFields', label: '必填字段数', type: 'SCALAR' },
      { key: 'requiredFilled', label: '必填已填数', type: 'SCALAR' },
      { key: 'completenessRate', label: '完整率', type: 'SCALAR', description: '百分比' },
      { key: 'logicRuleCount', label: '逻辑规则总数', type: 'SCALAR' },
      { key: 'logicPassed', label: '逻辑通过数', type: 'SCALAR' },
      { key: 'logicFailed', label: '逻辑失败数', type: 'SCALAR' },
      { key: 'logicPassRate', label: '逻辑通过率', type: 'SCALAR' },
    ],
  },
  {
    group: 'VIOLATION',
    groupLabel: '质控违规项',
    fields: [
      { key: 'violationErrorCount', label: '错误数量', type: 'SCALAR' },
      { key: 'violationWarningCount', label: '警告数量', type: 'SCALAR' },
      { key: 'violationTotalCount', label: '问题总数', type: 'SCALAR' },
      { key: 'violationList', label: '违规项列表', type: 'LIST', description: '包含所有错误和警告的列表' },
      { key: 'errorList', label: '错误项列表', type: 'LIST', description: '仅包含严重错误' },
      { key: 'warningList', label: '警告项列表', type: 'LIST', description: '仅包含警告' },
      { key: 'violationTable', label: '违规项表格', type: 'TABLE', description: '含规则编码、名称、类型、级别、消息的完整表格' },
    ],
  },
  {
    group: 'FIELD_CHECK',
    groupLabel: '字段检查明细',
    fields: [
      { key: 'fieldCheckTable', label: '字段检查表格', type: 'TABLE', description: '包含所有字段的检查结果' },
      { key: 'missingRequiredFields', label: '缺失必填字段列表', type: 'LIST' },
      { key: 'invalidFields', label: '不合规字段列表', type: 'LIST' },
    ],
  },
  {
    group: 'META',
    groupLabel: '报告元信息',
    fields: [
      { key: 'reportDate', label: '报告生成日期', type: 'SCALAR' },
      { key: 'reportGenerator', label: '报告生成人', type: 'SCALAR' },
      { key: 'reportDepartment', label: '报告科室', type: 'SCALAR' },
      { key: 'reviewerName', label: '审核人', type: 'SCALAR' },
      { key: 'watermark', label: '水印文本', type: 'SCALAR', description: '科室水印，默认为"科室质控科"' },
    ],
  },
]

export const getAllQcFieldOptions = () => {
  const options: { key: string; label: string; type: 'SCALAR' | 'LIST' | 'TABLE'; group: string; description?: string }[] = []
  QC_AVAILABLE_FIELDS.forEach((g) => {
    g.fields.forEach((f) => {
      options.push({ ...f, group: g.group })
    })
  })
  return options
}

export interface SimilarCaseSearchRequest {
  excludeRecordId?: number
  surgeryName: string
  preopDiagnosis?: string
  postopDiagnosis?: string
  department?: string
  timeRangeMonths?: number
  topN?: number
  minScore?: number
}

export interface SimilarCaseResult {
  recordId: number
  recordNo: string
  score: number
  department?: string
  surgeryName?: string
  preopDiagnosis?: string
  postopDiagnosis?: string
  surgeryLevel?: string
  incisionLevel?: string
  anesthesiaType?: string
  bloodLoss?: number
  bloodTransfusion?: number
  fluidInfusion?: number
  surgeon?: string
  surgeryDate?: string
  uploadTime?: string
}

export interface NumericFieldStats {
  fieldLabel: string
  count?: number
  avg?: number
  median?: number
  min?: number
  max?: number
  stdDev?: number
  percentile25?: number
  percentile75?: number
  typicalRange?: string
  unit?: string
}

export interface CategoryBucket {
  value: string
  count: number
  percentage: number
  isMostFrequent?: boolean
}

export type FieldType = 'NUMERIC' | 'CATEGORY'
export type DeviationDirection = 'HIGHER' | 'LOWER' | 'WITHIN_RANGE' | 'DIFFERENT' | 'UNKNOWN'
export type DeviationLevel = 'NORMAL' | 'MILD' | 'MODERATE' | 'SEVERE'

export interface FieldComparison {
  fieldType: FieldType
  fieldLabel: string
  currentValue?: string
  typicalValue?: string
  typicalRange?: string
  deviationDirection?: DeviationDirection
  deviationPercent?: number
  deviationLevel?: DeviationLevel
  unit?: string
  tip?: string
}

export const DeviationLevelMap: Record<DeviationLevel, { label: string; color: string }> = {
  NORMAL: { label: '正常', color: 'green' },
  MILD: { label: '轻微偏差', color: 'blue' },
  MODERATE: { label: '明显偏差', color: 'orange' },
  SEVERE: { label: '严重偏差', color: 'red' },
}

export const DeviationDirectionMap: Record<DeviationDirection, { label: string; icon: string; color: string }> = {
  HIGHER: { label: '偏高', icon: '↑', color: '#ff4d4f' },
  LOWER: { label: '偏低', icon: '↓', color: '#faad14' },
  WITHIN_RANGE: { label: '在区间内', icon: '✓', color: '#52c41a' },
  DIFFERENT: { label: '非常规', icon: '!', color: '#faad14' },
  UNKNOWN: { label: '未知', icon: '?', color: '#8c8c8c' },
}

export interface CaseStatsAnalysis {
  totalCases: number
  timeRangeDescription: string
  numericStats: Record<string, NumericFieldStats>
  categoryStats: Record<string, CategoryBucket[]>
  departmentTotalCases?: number
  departmentNumericStats?: Record<string, NumericFieldStats>
  departmentCategoryStats?: Record<string, CategoryBucket[]>
  fieldComparisons: Record<string, FieldComparison>
}

export interface AdoptTypicalValueRequest {
  fieldKey: string
  fieldType: FieldType
  adoptedValue: string
  unit?: string
}

export interface CaseFullAnalysis {
  similarCases: SimilarCaseResult[]
  stats: CaseStatsAnalysis
}

export interface NextStepRecommend {
  templateId: number
  templateCode?: string
  templateName: string
  documentType: string
  description?: string
  tags?: string[]
  department?: string
  surgeryType?: string
  useCount?: number
  isDefault?: boolean
  score: number
  collaborativeScore: number
  contentScore: number
  popularityScore: number
  rank: number
  placeholdersCount: number
  recommendedReason: string
  expectedDurationMinutes?: number
}

export interface GeneratedDraftResult {
  recordId: number
  templateId: number
  templateName: string
  documentType: string
  draftPreview: string
  draftLength: number
  message: string
}

export interface CoverageTrend {
  date: string
  department?: string
  totalRecords: number
  extractedRecords: number
  coverageRate: number
}

export interface EfficiencyTrend {
  date: string
  department?: string
  surgeon?: string
  avgManualDuration: number
  avgActualDuration: number
  timeSavedRate: number
  recordCount: number
}

export interface AccuracyTrend {
  date: string
  department?: string
  entityType?: string
  totalEntities: number
  verifiedEntities: number
  highConfidenceEntities: number
  accuracyRate: number
}

export interface SurgeryWordCloudItem {
  name: string
  value: number
}

export interface LowConfidenceDistribution {
  entityType: string
  entityLabel: string
  count: number
  avgConfidence: number
}

export interface DepartmentStats {
  department: string
  totalRecords: number
  extractedRecords: number
  coverageRate: number
  avgTimeSavedRate: number
  avgAccuracyRate?: number
}

export interface AnalyticsOverview {
  totalRecords: number
  extractedRecords: number
  overallCoverageRate: number
  overallTimeSavedRate: number
  overallAccuracyRate: number
  totalDepartments: number
  totalSurgeons: number
}

export interface SurgeonStats {
  surgeon: string
  recordCount: number
  avgTimeSavedRate: number
  coverageRate: number
}

export interface SurgeryTypeStats {
  surgeryName: string
  recordCount: number
  coverageRate: number
  avgAccuracyRate?: number
}

export interface AnalyticsDashboardData {
  overview: AnalyticsOverview
  coverageTrend: CoverageTrend[]
  efficiencyTrend: EfficiencyTrend[]
  accuracyTrend: AccuracyTrend[]
  departmentStats: DepartmentStats[]
  surgeonStats: SurgeonStats[]
  surgeryTypeStats: SurgeryTypeStats[]
  surgeryWordCloud: SurgeryWordCloudItem[]
  lowConfidenceDistribution: LowConfidenceDistribution[]
}

export type BatchTaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'

export const BatchTaskStatusMap: Record<BatchTaskStatus, { label: string; color: string }> = {
  PENDING: { label: '等待中', color: 'default' },
  PROCESSING: { label: '处理中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'success' },
  PARTIAL: { label: '部分完成', color: 'warning' },
  FAILED: { label: '全部失败', color: 'error' },
}

export type BatchItemStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export const BatchItemStatusMap: Record<BatchItemStatus, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'default' },
  PROCESSING: { label: '处理中', color: 'processing' },
  SUCCESS: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
}

export type NotifyType = 'EMAIL'

export const NotifyTypeMap: Record<NotifyType, { label: string }> = {
  EMAIL: { label: '邮件通知' },
}

export interface BatchTask {
  id: number
  taskName: string
  department?: string
  taskType: string
  originalFileName?: string
  fileSize?: number
  totalCount: number
  successCount: number
  failedCount: number
  pendingCount: number
  status: BatchTaskStatus
  errorMessage?: string
  progress: number
  notifyType: NotifyType
  notifyTarget?: string
  retryCount: number
  startTime?: string
  endTime?: string
  createdByName?: string
  createdTime: string
}

export interface BatchTaskItem {
  id: number
  taskId: number
  fileName: string
  filePath?: string
  fileType: FileType
  patientName?: string
  hospitalNo?: string
  recordId?: number
  status: BatchItemStatus
  errorMessage?: string
  retryCount: number
  startTime?: string
  endTime?: string
  createdTime: string
}

export interface BatchTaskCreateForm {
  file: File
  taskName?: string
  department?: string
  notifyType: NotifyType
  notifyTarget?: string
  maxRetryCount?: number
}

export type CorrectionType = 'CORRECTION' | 'ADDITION' | 'DELETION'

export const CorrectionTypeMap: Record<CorrectionType, { label: string; color: string }> = {
  CORRECTION: { label: '修改', color: 'orange' },
  ADDITION: { label: '新增', color: 'green' },
  DELETION: { label: '删除', color: 'red' },
}

export type TrainStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export const TrainStatusMap: Record<TrainStatus, { label: string; color: string }> = {
  PENDING: { label: '待开始', color: 'default' },
  RUNNING: { label: '训练中', color: 'processing' },
  SUCCESS: { label: '成功', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
}

export type TrainType = 'FULL' | 'INCREMENTAL' | 'WEEKLY'

export const TrainTypeMap: Record<TrainType, { label: string; color: string }> = {
  FULL: { label: '全量训练', color: 'blue' },
  INCREMENTAL: { label: '增量微调', color: 'green' },
  WEEKLY: { label: '周度训练', color: 'purple' },
}

export interface DoctorFeedback {
  id: number
  recordId: number
  recordNo?: string
  entityId?: number
  entityType: EntityType
  entityTypeLabel?: string
  originalValue?: string
  originalUnit?: string
  originalConfidence?: number
  originalSource?: string
  originalSourceLabel?: string
  correctedValue?: string
  correctedUnit?: string
  correctionType: CorrectionType
  correctionTypeLabel?: string
  department?: string
  feedbackUserId?: number
  feedbackUserName?: string
  feedbackRemark?: string
  qualityScore?: number
  usedForTraining: number
  trainBatchNo?: string
  createdTime: string
}

export interface DoctorFeedbackCreateRequest {
  recordId: number
  entityId?: number
  entityType: EntityType
  originalValue?: string
  originalUnit?: string
  originalConfidence?: number
  originalSource?: string
  originalStartPos?: number
  originalEndPos?: number
  originalText?: string
  correctedValue?: string
  correctedUnit?: string
  correctionType: CorrectionType
  feedbackRemark?: string
  qualityScore?: number
}

export interface FeedbackOverview {
  totalFeedbackCount: number
  usedForTrainingCount: number
  pendingTrainingCount: number
  averageQualityScore: number
  correctionCount: number
  additionCount: number
  deletionCount: number
  totalTrainCount: number
  latestF1Score: number
  f1Improvement: number
  activeDoctorCount: number
}

export interface FeedbackTrendItem {
  date: string
  department?: string
  feedbackCount: number
  correctionCount: number
  additionCount: number
  deletionCount: number
  usedForTrainingCount: number
  avgQualityScore: number
  activeDoctorCount: number
}

export interface EntityFeedbackStats {
  entityType: EntityType
  entityTypeLabel: string
  feedbackCount: number
  correctionCount: number
  additionCount: number
  deletionCount: number
  avgOriginalConfidence: number
  usedForTrainingRate: number
}

export interface CorrectionTypeStats {
  correctionType: CorrectionType
  correctionTypeLabel: string
  count: number
  percentage: number
}

export interface DoctorFeedbackStats {
  feedbackUserId: number
  feedbackUserName: string
  department?: string
  feedbackCount: number
  correctionCount: number
  avgQualityScore: number
  contributionScore: number
}

export interface ModelTrainLog {
  id: number
  trainBatchNo: string
  modelName?: string
  modelVersion?: string
  previousVersion?: string
  trainType: TrainType
  feedbackCount: number
  newSampleCount: number
  totalSampleCount: number
  trainLoss?: number
  devLoss?: number
  precisionScore?: number
  recallScore?: number
  f1Score?: number
  previousF1Score?: number
  f1Improvement?: number
  entityTypeBreakdown?: string
  trainStatus: TrainStatus
  failReason?: string
  trainStartTime?: string
  trainEndTime?: string
  trainDurationSec?: number
  trainParams?: string
  triggeredByName?: string
  modelPath?: string
  remark?: string
  createdTime: string
}

export interface ModelTrainRequest {
  trainType?: TrainType
  maxFeedbackCount?: number
  minQualityScore?: number
  epochs?: number
  batchSize?: number
  learningRate?: number
  remark?: string
}

export interface FeedbackDashboardData {
  overview: FeedbackOverview
  feedbackTrend: FeedbackTrendItem[]
  entityTypeStats: EntityFeedbackStats[]
  correctionTypeStats: CorrectionTypeStats[]
  topDoctors: DoctorFeedbackStats[]
  recentTrainLogs: ModelTrainLog[]
}

export type ExportFormat = 'EXCEL' | 'JSON' | 'FHIR'

export const ExportFormatMap: Record<ExportFormat, { label: string; color: string; icon: string; mimeType: string }> = {
  EXCEL: { label: 'Excel', color: 'green', icon: 'FileExcelOutlined', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
  JSON: { label: 'JSON', color: 'blue', icon: 'CodeOutlined', mimeType: 'application/json' },
  FHIR: { label: 'HL7 FHIR', color: 'purple', icon: 'ApiOutlined', mimeType: 'application/json' },
}

export interface ExportFieldConfig {
  fieldCode: string
  fieldLabel: string
  sourceTable?: string
  sourceField?: string
  fhirPath?: string
  dataType: 'STRING' | 'INTEGER' | 'DECIMAL' | 'DATE' | 'DATETIME' | 'BOOLEAN'
  unit?: string
  targetUnit?: string
  conversionFormula?: string
  sortOrder: number
  enabled: number
  required?: number
  defaultValue?: string
  valueMapping?: string
}

export interface UnitConversion {
  fieldCode: string
  sourceUnit: string
  targetUnit: string
  formula?: string
  multiplyFactor?: number
  addOffset?: number
  decimalPlaces?: number
}

export interface ExportTemplate {
  id: number
  templateName: string
  templateCode?: string
  description?: string
  exportFormat: ExportFormat
  exportFormatLabel?: string
  targetSystem?: string
  department?: string
  fieldConfigs: ExportFieldConfig[]
  unitConversions?: UnitConversion[]
  sortOrder?: number
  isDefault?: number
  enabled: number
  createUserId?: number
  createUserName?: string
  createdTime: string
  updatedTime: string
}

export interface ExportTemplateCreateForm {
  templateName: string
  templateCode?: string
  description?: string
  exportFormat: ExportFormat
  targetSystem?: string
  department?: string
  fieldConfigs: ExportFieldConfig[]
  unitConversions?: UnitConversion[]
  sortOrder?: number
  isDefault?: number
  enabled?: number
}

export interface StandardHomePage {
  patientId?: string
  patientName?: string
  gender?: string
  age?: number
  idCardNo?: string
  hospitalNo?: string
  admissionDate?: string
  dischargeDate?: string
  admissionDays?: number
  department?: string
  bedNo?: string
  admissionDiagnosis?: string
  dischargeDiagnosis?: string
  surgeryDate?: string
  surgeryName?: string
  surgeryCode?: string
  surgeryLevel?: string
  incisionLevel?: string
  incisionHealing?: string
  anesthesiaType?: string
  anesthesiaCode?: string
  bloodLoss?: number
  bloodLossUnit?: string
  bloodTransfusion?: number
  bloodTransfusionUnit?: string
  fluidInfusion?: number
  fluidInfusionUnit?: string
  surgeon?: string
  chiefSurgeon?: string
  assistant1?: string
  assistant2?: string
  anesthesiologist?: string
  scrubNurse?: string
  circulatingNurse?: string
  criticalPatient?: number
  hospitalizationFee?: number
  surgeryFee?: number
  anesthesiaFee?: number
  drugFee?: number
  examFee?: number
  treatmentFee?: number
  bedFee?: number
  otherFee?: number
  status?: string
  recordNo?: string
  extractTime?: string
}

export interface FhirIdentifier {
  system?: string
  value?: string
  use?: string
}

export interface FhirCoding {
  system?: string
  code?: string
  display?: string
}

export interface FhirCodeableConcept {
  coding?: FhirCoding[]
  text?: string
}

export interface FhirReference {
  reference?: string
  display?: string
}

export interface FhirPeriod {
  start?: string
  end?: string
}

export interface FhirHumanName {
  use?: string
  family?: string
  given?: string[]
}

export interface FhirPatient {
  resourceType: 'Patient'
  id?: string
  identifier?: FhirIdentifier[]
  name?: FhirHumanName[]
  gender?: string
  birthDate?: string
  active?: boolean
}

export interface FhirEncounter {
  resourceType: 'Encounter'
  id?: string
  identifier?: FhirIdentifier[]
  status?: string
  class?: FhirCodeableConcept
  subject?: FhirReference
  period?: FhirPeriod
}

export interface FhirProcedurePerformer {
  function?: FhirCodeableConcept
  actor?: FhirReference
}

export interface FhirProcedure {
  resourceType: 'Procedure'
  id?: string
  identifier?: FhirIdentifier[]
  status?: string
  code?: FhirCodeableConcept
  subject?: FhirReference
  encounter?: FhirReference
  performedPeriod?: FhirPeriod
  performer?: FhirProcedurePerformer[]
}

export type FhirResource = FhirPatient | FhirEncounter | FhirProcedure

export interface FhirBundleEntry {
  fullUrl?: string
  resource: FhirResource
}

export interface FhirBundle {
  resourceType: 'Bundle'
  type?: string
  id?: string
  timestamp?: string
  entry?: FhirBundleEntry[]
}

export type IndicatorCategory = 'EFFICIENCY' | 'SAFETY' | 'COST' | 'QUALITY' | 'CLINICAL'

export const IndicatorCategoryMap: Record<IndicatorCategory, string> = {
  EFFICIENCY: '效率指标',
  SAFETY: '安全指标',
  COST: '费用指标',
  QUALITY: '质量指标',
  CLINICAL: '临床指标',
}

export type BenchmarkDirection = 'LOWER_BETTER' | 'HIGHER_BETTER' | 'RANGE'

export const BenchmarkDirectionMap: Record<BenchmarkDirection, string> = {
  LOWER_BETTER: '越低越好',
  HIGHER_BETTER: '越高越好',
  RANGE: '范围适宜',
}

export type DeviationLevel = 'PASS' | 'WARNING' | 'CRITICAL'

export const DeviationLevelMap: Record<DeviationLevel, string> = {
  PASS: '达标',
  WARNING: '预警',
  CRITICAL: '严重偏离',
}

export const DeviationLevelColorMap: Record<DeviationLevel, string> = {
  PASS: '#52c41a',
  WARNING: '#faad14',
  CRITICAL: '#ff4d4f',
}

export interface QualityBenchmark {
  id: number
  indicatorCode: string
  indicatorName: string
  indicatorCategory: IndicatorCategory
  unit?: string
  benchmarkValue: number
  warningThreshold?: number
  criticalThreshold?: number
  direction: BenchmarkDirection
  directionLabel?: string
  source?: string
  region?: string
  department?: string
  benchmarkYear: number
  benchmarkQuarter?: number
  description?: string
  sortOrder?: number
  enabled?: number
  createUserName?: string
  createdTime?: string
  updatedTime?: string
}

export interface QualityBenchmarkCreateForm {
  id?: number
  indicatorCode: string
  indicatorName: string
  indicatorCategory: IndicatorCategory
  unit?: string
  benchmarkValue: number
  warningThreshold?: number
  criticalThreshold?: number
  direction: BenchmarkDirection
  source?: string
  region?: string
  department?: string
  benchmarkYear: number
  benchmarkQuarter?: number
  description?: string
  sortOrder?: number
  enabled?: number
}

export interface IndicatorDeviation {
  indicatorCode: string
  indicatorName: string
  indicatorCategory: IndicatorCategory
  unit?: string
  actualValue: number
  benchmarkValue: number
  warningThreshold?: number
  criticalThreshold?: number
  direction: BenchmarkDirection
  deviationValue: number
  deviationRate: number
  deviationLevel: DeviationLevel
  deviationLevelLabel: string
  department?: string
  dataCount: number
}

export interface DepartmentRanking {
  department: string
  totalIndicators: number
  passedIndicators: number
  warningIndicators: number
  criticalIndicators: number
  passRate: number
  compositeScore: number
  ranking: number
  indicatorDeviations: IndicatorDeviation[]
}

export interface QualityRadarSeries {
  name: string
  values: number[]
}

export interface QualityRadarData {
  indicatorNames: string[]
  series: QualityRadarSeries[]
}

export interface QualityBenchmarkDashboard {
  totalIndicators: number
  passedCount: number
  warningCount: number
  criticalCount: number
  overallPassRate: number
  compositeScore: number
  evaluatedDepartments: number
  topDeviations: IndicatorDeviation[]
  departmentRankings: DepartmentRanking[]
}
