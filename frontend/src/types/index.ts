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
