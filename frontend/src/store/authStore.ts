import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserInfo, LoginResponse } from '@/types'
import { authApi } from '@/services/api'

interface AuthState {
  token: string | null
  userInfo: UserInfo | null
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  setUserInfo: (info: UserInfo) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userInfo: null,
      isAuthenticated: false,

      login: async (username: string, password: string) => {
        const res: LoginResponse = await authApi.login(username, password)
        const userInfo: UserInfo = {
          userId: res.userId,
          username: res.username,
          realName: res.realName,
          role: res.role as any,
          department: res.department,
          title: res.title,
        }
        localStorage.setItem('surg_extract_token', res.token)
        set({
          token: res.token,
          userInfo,
          isAuthenticated: true,
        })
      },

      logout: () => {
        localStorage.removeItem('surg_extract_token')
        set({
          token: null,
          userInfo: null,
          isAuthenticated: false,
        })
      },

      setUserInfo: (info: UserInfo) => {
        set({ userInfo: info })
      },
    }),
    {
      name: 'surg_extract_auth',
    }
  )
)
