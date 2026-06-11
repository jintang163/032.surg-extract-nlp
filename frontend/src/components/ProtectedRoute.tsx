import React from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

interface Props {
  children: React.ReactNode
}

const ProtectedRoute: React.FC<Props> = ({ children }) => {
  const { isAuthenticated } = useAuthStore()
  const location = useLocation()

  const token = localStorage.getItem('surg_extract_token')
  const authenticated = isAuthenticated || !!token

  if (!authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}

export default ProtectedRoute
