import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from '@/layouts/MainLayout'
import Login from '@/pages/Login'
import Dashboard from '@/pages/Dashboard'
import RecordList from '@/pages/RecordList'
import RecordDetail from '@/pages/RecordDetail'
import HomePageFill from '@/pages/HomePageFill'
import SurgeryTemplateList from '@/pages/SurgeryTemplateList'
import SurgeryTemplateEditor from '@/pages/SurgeryTemplateEditor'
import ProtectedRoute from '@/components/ProtectedRoute'

const App: React.FC = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="records" element={<RecordList />} />
        <Route path="records/:id" element={<RecordDetail />} />
        <Route path="homepage/:recordId" element={<HomePageFill />} />
        <Route path="templates" element={<SurgeryTemplateList />} />
        <Route path="templates/new" element={<SurgeryTemplateEditor />} />
        <Route path="templates/:id/edit" element={<SurgeryTemplateEditor />} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
