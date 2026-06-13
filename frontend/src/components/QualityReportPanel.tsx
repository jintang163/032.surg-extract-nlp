import React, { useState, useEffect, useMemo } from 'react'
import {
  Card,
  Button,
  Tag,
  Space,
  Progress,
  Collapse,
  Alert,
  Descriptions,
  Tooltip,
  Empty,
  Spin,
  Badge,
} from 'antd'
import {
  SafetyCertificateOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  ExclamationCircleOutlined,
  ExportOutlined,
  ReloadOutlined,
  FileExcelOutlined,
} from '@ant-design/icons'
import { qcApi } from '@/services/api'
import type { QcCheckResult, QcScorecard, QcViolation, QcFieldCheck } from '@/types'
import { QcSeverityMap, QcCategoryMap } from '@/types'

interface QualityReportPanelProps {
  recordId?: number
  checkResult?: QcCheckResult | null
  scorecard?: QcScorecard | null
  loading?: boolean
  onViolationFieldsChange?: (fields: string[]) => void
  onRefresh?: () => void
}

const QualityReportPanel: React.FC<QualityReportPanelProps> = ({
  recordId,
  checkResult: externalCheckResult,
  scorecard: externalScorecard,
  loading: externalLoading,
  onViolationFieldsChange,
  onRefresh,
}) => {
  const [internalLoading, setInternalLoading] = useState(false)
  const [internalScorecard, setInternalScorecard] = useState<QcScorecard | null>(null)
  const [internalCheckResult, setInternalCheckResult] = useState<QcCheckResult | null>(null)
  const [exporting, setExporting] = useState(false)

  const loading = externalLoading !== undefined ? externalLoading : internalLoading
  const scorecard = externalScorecard !== undefined ? externalScorecard : internalScorecard
  const checkResult = externalCheckResult !== undefined ? externalCheckResult : internalCheckResult
  const isExternalMode = externalCheckResult !== undefined || externalScorecard !== undefined

  useEffect(() => {
    if (!isExternalMode && recordId) {
      loadQcData()
    }
  }, [recordId, isExternalMode])

  useEffect(() => {
    if (checkResult?.violations) {
      const fields = new Set<string>()
      checkResult.violations.forEach((v) => {
        v.relatedFields?.forEach((f) => fields.add(f))
      })
      onViolationFieldsChange?.(Array.from(fields))
    }
  }, [checkResult, onViolationFieldsChange])

  const loadQcData = async () => {
    setInternalLoading(true)
    try {
      const [scorecardData, checkData] = await Promise.all([
        qcApi.getScorecard(recordId!).catch(() => null),
        qcApi.validate(recordId!).catch(() => null),
      ])
      setInternalScorecard(scorecardData)
      setInternalCheckResult(checkData)
    } catch (e) {
      console.error('加载质控数据失败', e)
    } finally {
      setInternalLoading(false)
    }
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      const blob = await qcApi.exportReport(recordId) as any
      const url = window.URL.createObjectURL(new Blob([blob]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `质控报告_${recordId}.xlsx`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (e) {
      console.error('导出失败', e)
    } finally {
      setExporting(false)
    }
  }

  const errorCount = useMemo(
    () => checkResult?.violations?.filter((v) => v.severity === 'ERROR').length || 0,
    [checkResult]
  )
  const warningCount = useMemo(
    () => checkResult?.violations?.filter((v) => v.severity === 'WARNING').length || 0,
    [checkResult]
  )

  const getScoreColor = (score: number) => {
    if (score >= 90) return '#52c41a'
    if (score >= 80) return '#1677ff'
    if (score >= 70) return '#fa8c16'
    if (score >= 60) return '#faad14'
    return '#ff4d4f'
  }

  const getGradeColor = (grade: string) => {
    if (grade.startsWith('A')) return 'green'
    if (grade.startsWith('B')) return 'blue'
    if (grade.startsWith('C')) return 'orange'
    if (grade.startsWith('D')) return 'gold'
    return 'red'
  }

  return (
    <Card
      title={
        <Space>
          <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
          <span>质控报告</span>
          {checkResult && !checkResult.passed && (
            <Badge count={errorCount} size="small" />
          )}
        </Space>
      }
      extra={
        <Space size={4}>
          <Tooltip title="刷新">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => {
                if (isExternalMode && onRefresh) {
                  onRefresh()
                } else if (!isExternalMode && recordId) {
                  loadQcData()
                }
              }}
              loading={loading}
            />
          </Tooltip>
          {recordId && (
            <Tooltip title="导出报告">
              <Button
                type="text"
                size="small"
                icon={<FileExcelOutlined />}
                onClick={handleExport}
                loading={exporting}
              />
            </Tooltip>
          )}
        </Space>
      }
      loading={loading}
    >
      {scorecard ? (
        <>
          <div style={{ textAlign: 'center', marginBottom: 16 }}>
            <Progress
              type="dashboard"
              size={120}
              percent={Math.round(scorecard.overallScore)}
              strokeColor={getScoreColor(scorecard.overallScore)}
              format={(p) => (
                <div>
                  <div style={{ fontSize: 22, fontWeight: 700, color: getScoreColor(scorecard.overallScore) }}>
                    {p}
                  </div>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>综合评分</div>
                </div>
              )}
            />
            <div style={{ marginTop: 8 }}>
              <Tag color={getGradeColor(scorecard.grade)} style={{ fontSize: 13, padding: '2px 12px' }}>
                {scorecard.grade}
              </Tag>
            </div>
          </div>

          <Descriptions column={2} size="small" style={{ marginBottom: 12 }}>
            <Descriptions.Item label="完整性">
              <span style={{ color: getScoreColor(scorecard.completenessScore), fontWeight: 600 }}>
                {scorecard.completenessScore.toFixed(1)}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="逻辑性">
              <span style={{ color: getScoreColor(scorecard.logicConsistencyScore), fontWeight: 600 }}>
                {scorecard.logicConsistencyScore.toFixed(1)}
              </span>
            </Descriptions.Item>
          </Descriptions>

          <div style={{ marginBottom: 12, fontSize: 12, color: '#8c8c8c' }}>
            <div style={{ marginBottom: 4 }}>
              字段填写：{scorecard.filledFields}/{scorecard.totalFields}
            </div>
            <Progress
              percent={scorecard.totalFields > 0 ? Math.round((scorecard.filledFields / scorecard.totalFields) * 100) : 0}
              size="small"
              showInfo={false}
              strokeColor="#1677ff"
            />
            <div style={{ marginTop: 4 }}>
              必填项：{scorecard.requiredFilled}/{scorecard.requiredFields}
            </div>
            <Progress
              percent={scorecard.requiredFields > 0 ? Math.round((scorecard.requiredFilled / scorecard.requiredFields) * 100) : 0}
              size="small"
              showInfo={false}
              strokeColor="#52c41a"
            />
            <div style={{ marginTop: 4 }}>
              逻辑校验：通过 {scorecard.logicPassed}/{scorecard.logicRuleCount}
            </div>
            <Progress
              percent={scorecard.logicRuleCount > 0 ? Math.round((scorecard.logicPassed / scorecard.logicRuleCount) * 100) : 100}
              size="small"
              showInfo={false}
              strokeColor={scorecard.logicFailed > 0 ? '#fa8c16' : '#52c41a'}
            />
          </div>

          {checkResult && !checkResult.passed && (
            <Alert
              type={errorCount > 0 ? 'error' : 'warning'}
              showIcon
              icon={errorCount > 0 ? <CloseCircleOutlined /> : <WarningOutlined />}
              message={
                errorCount > 0
                  ? `${errorCount} 个错误，${warningCount} 个警告`
                  : `${warningCount} 个警告`
              }
              style={{ marginBottom: 12 }}
            />
          )}

          {checkResult?.violations && checkResult.violations.length > 0 && (
            <Collapse
              size="small"
              defaultActiveKey={errorCount > 0 ? ['violations'] : []}
              items={[
                {
                  key: 'violations',
                  label: (
                    <Space>
                      <ExclamationCircleOutlined />
                      <span>质控问题</span>
                      <Badge
                        count={checkResult.violations.length}
                        size="small"
                        style={{ backgroundColor: errorCount > 0 ? '#ff4d4f' : '#fa8c16' }}
                      />
                    </Space>
                  ),
                  children: (
                    <div>
                      {checkResult.violations.map((v, idx) => (
                        <div
                          key={idx}
                          style={{
                            padding: '6px 8px',
                            marginBottom: 4,
                            background: v.severity === 'ERROR' ? '#fff2f0' : '#fffbe6',
                            borderRadius: 4,
                            borderLeft: `3px solid ${v.severity === 'ERROR' ? '#ff4d4f' : '#faad14'}`,
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <Space size={4}>
                              <Tag
                                color={QcSeverityMap[v.severity]?.color || 'default'}
                                style={{ fontSize: 10, padding: '0 4px', lineHeight: '16px', margin: 0 }}
                              >
                                {QcSeverityMap[v.severity]?.label || v.severity}
                              </Tag>
                              <Tag
                                color={QcCategoryMap[v.category]?.color || 'default'}
                                style={{ fontSize: 10, padding: '0 4px', lineHeight: '16px', margin: 0 }}
                              >
                                {QcCategoryMap[v.category]?.label || v.category}
                              </Tag>
                              <span style={{ fontSize: 12, fontWeight: 500 }}>{v.ruleName}</span>
                            </Space>
                          </div>
                          <div style={{ fontSize: 11, color: '#595959', marginTop: 4 }}>
                            {v.message}
                          </div>
                          {v.relatedFields && v.relatedFields.length > 0 && (
                            <div style={{ marginTop: 4 }}>
                              {v.relatedFields.map((f) => (
                                <Tag key={f} style={{ fontSize: 10, margin: '0 2px' }} color="default">
                                  {f}
                                </Tag>
                              ))}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  ),
                },
              ]}
            />
          )}

          {checkResult?.passed && (
            <Alert
              type="success"
              showIcon
              icon={<CheckCircleOutlined />}
              message="所有质控规则校验通过"
              style={{ marginTop: 8 }}
            />
          )}
        </>
      ) : !loading ? (
        <Empty description="暂无质控数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : null}
    </Card>
  )
}

export default QualityReportPanel
