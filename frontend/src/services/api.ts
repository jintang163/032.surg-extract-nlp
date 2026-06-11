import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { message, Modal } from 'antd'
import type { Result } from '@/types'

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
