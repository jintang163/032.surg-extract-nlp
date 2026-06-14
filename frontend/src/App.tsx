import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from '@/layouts/MainLayout'
import Login from '@/pages/Login'
import Dashboard from '@/pages/Dashboard'
import AnalyticsDashboard from '@/pages/AnalyticsDashboard'
import BatchProcessing from '@/pages/BatchProcessing'
import RecordList from '@/pages/RecordList'
import RecordDetail from '@/pages/RecordDetail'
import HomePageFill from '@/pages/HomePageFill'
import SurgeryTemplateList from '@/pages/SurgeryTemplateList'
import SurgeryTemplateEditor from '@/pages/SurgeryTemplateEditor'
import VoiceRecordingPage from '@/pages/VoiceRecordingPage'
import MedicalTermManagement from '@/pages/MedicalTermManagement'
import QualityControlPage from '@/pages/QualityControlPage'
import QcReportTemplateList from '@/pages/QcReportTemplateList'
import QcReportTemplateEditor from '@/pages/QcReportTemplateEditor'
import FeedbackDashboard from '@/pages/FeedbackDashboard'
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
        <Route path="analytics" element={<AnalyticsDashboard />} />
        <Route path="batch" element={<BatchProcessing />} />
        <Route path="records" element={<RecordList />} />
        <Route path="records/:id" element={<RecordDetail />} />
        <Route path="homepage/:recordId" element={<HomePageFill />} />
        <Route path="templates" element={<SurgeryTemplateList />} />
        <Route path="templates/new" element={<SurgeryTemplateEditor />} />
        <Route path="templates/:id/edit" element={<SurgeryTemplateEditor />} />
        <Route path="voice" element={<VoiceRecordingPage />} />
        <Route path="voice/:recordId" element={<VoiceRecordingPage />} />
        <Route path="medical-term" element={<MedicalTermManagement />} />
        <Route path="quality-control" element={<QualityControlPage />} />
        <Route path="qc-report-templates" element={<QcReportTemplateList />} />
        <Route path="qc-report-templates/new" element={<QcReportTemplateEditor />} />
        <Route path="qc-report-templates/:id/edit" element={<QcReportTemplateEditor />} />
        <Route path="feedback" element={<FeedbackDashboard />} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
