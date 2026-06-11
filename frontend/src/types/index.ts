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
  | 'NER_PROCESSING'
  | 'NER_DONE'
  | 'COMPLETED'
  | 'FAILED'

export const ProcessStatusMap: Record<ProcessStatus, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'default' },
  OCR_PROCESSING: { label: 'OCR处理中', color: 'processing' },
  OCR_DONE: { label: 'OCR完成', color: 'blue' },
  NER_PROCESSING: { label: '实体抽取中', color: 'processing' },
  NER_DONE: { label: '抽取完成', color: 'cyan' },
  COMPLETED: { label: '已完成', color: 'success' },
  FAILED: { label: '处理失败', color: 'error' },
}

export type FileType = 'TEXT' | 'WORD' | 'PDF' | 'IMAGE' | 'UNKNOWN'

export const FileTypeMap: Record<FileType, { label: string; icon: string }> = {
  TEXT: { label: '纯文本', icon: 'FileTextOutlined' },
  WORD: { label: 'Word文档', icon: 'FileWordOutlined' },
  PDF: { label: 'PDF文档', icon: 'FilePdfOutlined' },
  IMAGE: { label: '图片', icon: 'FileImageOutlined' },
  UNKNOWN: { label: '未知', icon: 'FileOutlined' },
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
