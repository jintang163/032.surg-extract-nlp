import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { message, Modal } from 'antd'
import type { Result, HisSyncLog } from '@/types'

const TOKEN_KEY = 'surg_extract_token'
const baseURL = '/api'

const service: AxiosInstance = axios.create({
  baseURL,
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data

    if (res.code === 401) {
      Modal.error({
        title: '登录已过期',
        content: '请重新登录',
        onOk: () => {
          localStorage.removeItem(TOKEN_KEY)
          localStorage.removeItem('user_info')
          window.location.href = '/login'
        },
      })
      return Promise.reject(new Error(res.message || '未登录'))
    }

    if (res.code !== 200) {
      message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res.data as any
  },
  (error) => {
    console.error('Response error:', error)
    if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
      message.error('请求超时，请稍后重试')
    } else if (error.response?.status === 404) {
      message.error('接口不存在')
    } else if (error.response?.status === 500) {
      message.error('服务器错误，请稍后重试')
    } else {
      message.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export const http = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config)
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config)
  },
  upload<T = any>(
    url: string,
    file: File | Blob,
    params?: Record<string, any>,
    onProgress?: (progress: number) => void
  ): Promise<T> {
    const formData = new FormData()
    formData.append('file', file instanceof Blob ? new File([file], 'upload', { type: file.type }) : file)
    if (params) {
      Object.keys(params).forEach((key) => {
        if (params[key] !== undefined && params[key] !== null) {
          formData.append(key, String(params[key]))
        }
      })
    }
    return service.post(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          onProgress(progress)
        }
      },
    })
  },

  uploadMulti<T = any>(
    url: string,
    mainFile: File,
    attachments: File[],
    params?: Record<string, any>,
    onProgress?: (progress: number) => void
  ): Promise<T> {
    const formData = new FormData()
    formData.append('mainFile', mainFile)
    attachments.forEach((f, idx) => {
      formData.append('attachments', f)
    })
    if (params) {
      Object.keys(params).forEach((key) => {
        if (params[key] !== undefined && params[key] !== null) {
          formData.append(key, String(params[key]))
        }
      })
    }
    return service.post(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          onProgress(progress)
        }
      },
    })
  },
}

export const authApi = {
  login: (username: string, password: string) =>
    http.post<any>('/auth/login', { username, password }),
  logout: () => http.post<void>('/auth/logout'),
  getUserInfo: () => http.get<any>('/auth/user-info'),
}

export const recordApi = {
  upload: (
    file: File,
    params: { patientName?: string; hospitalNo?: string; patientId?: string; department?: string },
    onProgress?: (p: number) => void
  ) => http.upload<any>('/records/upload', file, params, onProgress),

  uploadMulti: (
    mainFile: File,
    attachments: File[],
    params: { patientName?: string; hospitalNo?: string; patientId?: string; department?: string },
    onProgress?: (p: number) => void
  ) => http.uploadMulti<any>('/records/upload-multi', mainFile, attachments, params, onProgress),

  list: (params: {
    patientName?: string
    hospitalNo?: string
    status?: string
    startDate?: string
    endDate?: string
    pageNum: number
    pageSize: number
  }) => http.get<any>('/records/list', { params }),

  detail: (id: number) => http.get<any>(`/records/${id}`),

  getAttachments: (id: number) => http.get<any[]>(`/records/${id}/attachments`),

  getOcrText: (id: number) => http.get<string>(`/records/${id}/ocr-text`),

  updateOcrText: (id: number, ocrText: string) =>
    http.put<void>(`/records/${id}/ocr-text`, { ocrText }),

  getEntities: (id: number) => http.get<any[]>(`/records/${id}/entities`),

  updateEntities: (id: number, entities: any[]) =>
    http.put<void>(`/records/${id}/entities`, entities),

  getProcessingTime: (id: number) => http.get<number>(`/records/${id}/processing-time`),

  saveTemplateDraft: (id: number, templateId: number | null, draftContent: string) =>
    http.put<void>(`/records/${id}/template-draft`, { templateId, draftContent }),
}

export const homePageApi = {
  get: (recordId: number) => http.get<any>(`/homepage/${recordId}`),

  getFieldMappings: () => http.get<any[]>('/homepage/field-mappings'),

  update: (recordId: number, data: any) => http.put<any>(`/homepage/${recordId}`, data),

  saveDraft: (recordId: number, data: any) => http.post<any>(`/homepage/${recordId}/draft`, data),

  submit: (recordId: number) => http.post<void>(`/homepage/${recordId}/submit`),

  audit: (recordId: number, approved: boolean, remark?: string) =>
    http.post<void>(`/homepage/${recordId}/audit`, null, { params: { approved, remark } }),

  getEfficiencyStats: () => http.get<any>('/homepage/stats/efficiency'),
}

export const templateApi = {
  list: (params: {
    templateName?: string
    surgeryType?: string
    department?: string
    status?: string
    pageNum: number
    pageSize: number
  }) => http.get<any>('/templates/list', { params }),

  available: (params?: { surgeryType?: string; department?: string }) =>
    http.get<any[]>('/templates/available', { params }),

  detail: (id: number) => http.get<any>(`/templates/${id}`),

  create: (data: any) => http.post<any>('/templates', data),

  update: (id: number, data: any) => http.put<any>(`/templates/${id}`, data),

  delete: (id: number) => http.delete<void>(`/templates/${id}`),

  getVersions: (id: number) => http.get<any[]>(`/templates/${id}/versions`),

  getVersion: (id: number, versionNo: number) => http.get<any>(`/templates/${id}/versions/${versionNo}`),

  revertVersion: (id: number, versionNo: number, changeLog?: string) =>
    http.post<any>(`/templates/${id}/versions/revert/${versionNo}`, null, { params: { changeLog } }),

  fill: (id: number, values: Record<string, string>) =>
    http.post<string>(`/templates/${id}/fill`, values),

  fillFromRecord: (id: number, recordId: number) =>
    http.post<string>(`/templates/${id}/fill/record/${recordId}`),

  extractPlaceholders: (content: string) =>
    http.post<string[]>('/templates/extract-placeholders', { content }),

  exportTemplate: (id: number) => http.get<Record<string, any>>(`/templates/${id}/export`),

  importTemplate: (data: any) => http.post<any>('/templates/import', data),
}

export const voiceApi = {
  createSession: (params?: {
    recordId?: number
    language?: string
    enableAutoPunctuation?: boolean
    enableRealTimeNer?: boolean
  }) => http.post<any>('/voice/session', null, { params }),

  getSessionStatus: (sessionId: string) => http.get<any>(`/voice/session/${sessionId}`),

  stopSession: (sessionId: string) => http.post<any>(`/voice/session/${sessionId}/stop`),

  uploadChunk: (
    sessionId: string,
    seq: number,
    chunk: Blob,
    lastChunk = false
  ) => http.upload<any>('/voice/upload-chunk', chunk, { sessionId, seq, lastChunk }),

  submitTextChunk: (sessionId: string, text: string) =>
    http.post<any>('/voice/text-chunk', { text }, { params: { sessionId } }),

  addPunctuation: (text: string) =>
    http.post<string>('/voice/add-punctuation', { text }),
}

export const termApi = {
  getTermTypes: () => http.get<any[]>('/medical-term/types'),

  getAliasTypes: () => http.get<any[]>('/medical-term/alias-types'),

  getCategories: () => http.get<any[]>('/medical-term/categories'),

  list: (params: {
    keyword?: string
    termType?: string
    categoryId?: number
    reviewStatus?: string
    pageNum: number
    pageSize: number
  }) => http.get<any>('/medical-term', { params }),

  detail: (id: number) => http.get<any>(`/medical-term/${id}`),

  create: (data: {
    termCode: string
    standardName: string
    termType: string
    categoryId?: number
    icdCode?: string
    icdName?: string
    definition?: string
    confidence?: number
    enabled?: number
  }) => http.post<any>('/medical-term', data),

  update: (id: number, data: any) => http.put<any>(`/medical-term/${id}`, data),

  delete: (id: number) => http.delete<void>(`/medical-term/${id}`),

  review: (id: number, approved: boolean, remark?: string) =>
    http.post<void>(`/medical-term/${id}/review`, null, { params: { approved, remark } }),

  toggleEnabled: (id: number) => http.post<void>(`/medical-term/${id}/toggle`),

  merge: (data: {
    targetTermId: number
    sourceTermIds: number[]
    mergeAction: string
    remark?: string
  }) => http.post<void>('/medical-term/merge', data),

  batchImport: (data: {
    items: any[]
    categoryId?: number
    termType?: string
    skipDuplicate?: boolean
  }) => http.post<any>('/medical-term/batch-import', data),

  addAlias: (data: {
    termId: number
    aliasName: string
    aliasType: string
    similarityScore?: number
    source?: string
    enabled?: number
  }) => http.post<any>('/medical-term/alias', data),

  updateAlias: (id: number, data: any) => http.put<any>(`/medical-term/alias`, data),

  deleteAlias: (id: number) => http.delete<void>(`/medical-term/alias/${id}`),

  reviewAlias: (id: number, approved: boolean, remark?: string) =>
    http.post<void>(`/medical-term/alias/${id}/review`, null, { params: { approved, remark } }),

  getAliases: (termId: number) => http.get<any[]>(`/medical-term/${termId}/aliases`),

  mapTerm: (originalText: string, termType?: string, maxCandidates?: number) =>
    http.post<any>('/medical-term/map', null, { params: { originalText, termType, maxCandidates } }),

  mapTermByBody: (data: {
    text: string
    termType?: string
    maxResults?: number
    minSimilarity?: number
    useGraph?: boolean
  }) => http.post<any>('/medical-term/map', data),

  batchMapTerms: (data: any[]) =>
    http.post<any[]>('/medical-term/map/batch', data),

  searchInGraph: (keyword: string) => http.get<any[]>(`/medical-term/graph/search`, { params: { keyword } }),

  findSynonymsInGraph: (name: string, maxHops?: number) =>
    http.get<any[]>(`/medical-term/graph/synonyms`, { params: { name, maxHops } }),

  findPathInGraph: (startName: string, endName: string) =>
    http.get<any[]>(`/medical-term/graph/path`, { params: { startName, endName } }),

  getGraphStats: () => http.get<any>('/medical-term/graph/stats'),

  syncAllToGraph: () => http.post<void>('/medical-term/graph/sync'),

  clearGraph: () => http.delete<void>('/medical-term/graph/clear'),

  getMappingStats: (days?: number) => http.get<any>('/medical-term/mapping-stats', { params: { days } }),

  getTopTerms: (limit?: number) => http.get<any[]>('/medical-term/stats/top', { params: { limit } }),

  getMappingLog: (params: {
    pageNum?: number
    pageSize?: number
    recordId?: number
  }) => http.get<any>('/medical-term/mapping-log', { params }),
}

export const qcApi = {
  validate: (recordId: number) =>
    http.post<any>(`/qc/validate/${recordId}`),

  validateForm: (formData: any) =>
    http.post<any>('/qc/validate-form', formData),

  getScorecard: (recordId: number) =>
    http.get<any>(`/qc/scorecard/${recordId}`),

  exportReport: (recordId: number) =>
    http.get(`/qc/export/${recordId}`, { responseType: 'blob' }),

  exportReportWithTemplate: (
    recordId: number,
    templateId: number,
    params?: {
      enableWatermark?: boolean
      watermarkText?: string
      outputFormat?: 'PDF' | 'WORD' | 'EXCEL'
    }
  ) =>
    http.get(`/qc/export/${recordId}/template/${templateId}`, {
      responseType: 'blob',
      params,
    }),
}

export const qcReportTemplateApi = {
  uploadTemplate: (
    file: File,
    params: {
      templateName?: string
      fileType?: 'WORD' | 'EXCEL'
      department?: string
    },
    onProgress?: (p: number) => void
  ) => http.upload<any>('/qc-report-templates/upload', file, params, onProgress),

  list: (params: {
    templateName?: string
    fileType?: string
    department?: string
    status?: string
    pageNum: number
    pageSize: number
  }) => http.get<any>('/qc-report-templates/list', { params }),

  available: (params?: { department?: string }) =>
    http.get<any[]>('/qc-report-templates/available', { params }),

  detail: (id: number) => http.get<any>(`/qc-report-templates/${id}`),

  create: (data: any) => http.post<any>('/qc-report-templates', data),

  update: (id: number, data: any) => http.put<any>(`/qc-report-templates/${id}`, data),

  delete: (id: number) => http.delete<void>(`/qc-report-templates/${id}`),

  getVersions: (id: number) => http.get<any[]>(`/qc-report-templates/${id}/versions`),

  getVersion: (id: number, versionNo: number) =>
    http.get<any>(`/qc-report-templates/${id}/versions/${versionNo}`),

  revertVersion: (id: number, versionNo: number, changeLog?: string) =>
    http.post<any>(
      `/qc-report-templates/${id}/versions/revert/${versionNo}`,
      null,
      { params: { changeLog } }
    ),

  preview: (
    id: number,
    params?: {
      mockRecordId?: number
      enableWatermark?: boolean
      watermarkText?: string
    }
  ) => http.get(`/qc-report-templates/${id}/preview`, { responseType: 'blob', params }),

  parsePlaceholders: (id: number) =>
    http.get<string[]>(`/qc-report-templates/${id}/parse-placeholders`),

  setDefault: (id: number) =>
    http.post<void>(`/qc-report-templates/${id}/set-default`),

  toggleStatus: (id: number) =>
    http.post<void>(`/qc-report-templates/${id}/toggle-status`),

  exportTemplate: (id: number) => http.get<Record<string, any>>(`/qc-report-templates/${id}/export`),

  importTemplate: (file: File, onProgress?: (p: number) => void) =>
    http.upload<any>('/qc-report-templates/import', file, undefined, onProgress),

  downloadTemplate: (id: number) =>
    http.get(`/qc-report-templates/${id}/download`, { responseType: 'blob' }),
}

export const hisSyncApi = {
  syncToHis: (recordId: number) => http.post(`/his-sync/sync/${recordId}`),
  pullFromHis: (recordId: number, hospitalNo: string) =>
    http.post(`/his-sync/pull/${recordId}?hospitalNo=${encodeURIComponent(hospitalNo)}`),
  rollback: (recordId: number) => http.post(`/his-sync/rollback/${recordId}`),
  getLogs: (recordId: number) => http.get<HisSyncLog[]>(`/his-sync/logs/${recordId}`),
  getLatestLog: (recordId: number) => http.get<HisSyncLog>(`/his-sync/logs/latest/${recordId}`),
  getStatus: (recordId: number) =>
    http.get<{ hisEnabled: boolean; latestLog: HisSyncLog; synced: boolean }>(`/his-sync/status/${recordId}`),
  triggerBilling: (recordId: number) => http.post(`/his-sync/billing/${recordId}`),
  checkEnabled: () => http.get<{ enabled: boolean }>('/his-sync/enabled'),
}

export const caseCompareApi = {
  searchSimilar: (params: any) =>
    http.post<any[]>('/case-compare/similar/search', params),

  getStatsAnalysis: (params: any) =>
    http.post<any>('/case-compare/stats/analysis', params),

  getFullAnalysis: (recordId: number, params: any) =>
    http.post<any>(`/case-compare/${recordId}/full-analysis`, params),

  adoptTypicalValue: (recordId: number, params: any) =>
    http.post<any>(`/case-compare/${recordId}/adopt`, params),

  rebuildIndex: () =>
    http.post<any>('/case-compare/index/rebuild'),

  syncRecord: (recordId: number) =>
    http.post<void>(`/case-compare/index/sync/${recordId}`),

  syncAll: () =>
    http.post<any>('/case-compare/index/sync-all'),

  getIndexStatus: () =>
    http.get<any>('/case-compare/index/status'),
}
