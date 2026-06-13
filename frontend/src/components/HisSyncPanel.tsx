import React, { useEffect, useState } from 'react'
import {
  Card,
  Tag,
  Button,
  Space,
  List,
  Typography,
  Tooltip,
  Modal,
  message,
  Divider,
  Descriptions,
  Badge,
  Empty,
} from 'antd'
import {
  CloudUploadOutlined,
  CloudDownloadOutlined,
  RollbackOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { hisSyncApi } from '@/services/api'
import type { HisSyncLog } from '@/types'
import { SyncTypeMap, SyncDirectionMap, SyncStatusMap } from '@/types'

const { Text } = Typography

interface HisSyncPanelProps {
  recordId: number
  hospitalNo?: string
  onSyncSuccess?: () => void
}

const HisSyncPanel: React.FC<HisSyncPanelProps> = ({ recordId, hospitalNo, onSyncSuccess }) => {
  const [logs, setLogs] = useState<HisSyncLog[]>([])
  const [loading, setLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [rollingBack, setRollingBack] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [selectedLog, setSelectedLog] = useState<HisSyncLog | null>(null)
  const [hisEnabled, setHisEnabled] = useState(true)
  const [synced, setSynced] = useState(false)

  useEffect(() => {
    if (recordId && recordId > 0) {
      loadSyncData()
    }
  }, [recordId])

  const loadSyncData = async () => {
    setLoading(true)
    try {
      const [statusRes, logsRes] = await Promise.all([
        hisSyncApi.getStatus(recordId).catch(() => null),
        hisSyncApi.getLogs(recordId).catch(() => null),
      ])
      if (statusRes) {
        setHisEnabled(statusRes.hisEnabled)
        setSynced(statusRes.synced)
      }
      if (logsRes) {
        setLogs(logsRes)
      }
    } catch (e) {
      console.error('加载同步数据失败', e)
    } finally {
      setLoading(false)
    }
  }

  const handleSyncToHis = async () => {
    Modal.confirm({
      title: '确认同步到HIS？',
      icon: <CloudUploadOutlined style={{ color: '#1677ff' }} />,
      content: '同步后病案首页数据将写入医院HIS系统，并自动触发计费项目。',
      okText: '确认同步',
      cancelText: '取消',
      onOk: async () => {
        setSyncing(true)
        try {
          await hisSyncApi.syncToHis(recordId)
          message.success('同步成功')
          onSyncSuccess?.()
          loadSyncData()
        } catch (e: any) {
          message.error(e?.message || '同步失败')
        } finally {
          setSyncing(false)
        }
      },
    })
  }

  const handlePullFromHis = async () => {
    if (!hospitalNo) {
      message.warning('缺少住院号，无法从HIS拉取数据')
      return
    }
    Modal.confirm({
      title: '确认从HIS拉取数据？',
      icon: <CloudDownloadOutlined style={{ color: '#722ed1' }} />,
      content: '将从HIS系统拉取该患者的病案首页数据，用于核对补充。',
      okText: '确认拉取',
      cancelText: '取消',
      onOk: async () => {
        setSyncing(true)
        try {
          await hisSyncApi.pullFromHis(recordId, hospitalNo)
          message.success('拉取成功')
          onSyncSuccess?.()
          loadSyncData()
        } catch (e: any) {
          message.error(e?.message || '拉取失败')
        } finally {
          setSyncing(false)
        }
      },
    })
  }

  const handleRollback = async () => {
    Modal.confirm({
      title: '确认回滚同步？',
      icon: <RollbackOutlined style={{ color: '#faad14' }} />,
      content: (
        <div>
          <p>回滚将取消在HIS系统中创建的病案首页记录及相关计费项目。</p>
          <p style={{ color: '#faad14', marginBottom: 0 }}>
            <strong>注意：此操作不可撤销，请谨慎操作！</strong>
          </p>
        </div>
      ),
      okText: '确认回滚',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setRollingBack(true)
        try {
          await hisSyncApi.rollback(recordId)
          message.success('回滚成功')
          onSyncSuccess?.()
          loadSyncData()
        } catch (e: any) {
          message.error(e?.message || '回滚失败')
        } finally {
          setRollingBack(false)
        }
      },
    })
  }

  const showLogDetail = (log: HisSyncLog) => {
    setSelectedLog(log)
    setDetailModalVisible(true)
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />
      case 'FAILED':
        return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
      case 'PENDING':
        return <ClockCircleOutlined style={{ color: '#1677ff' }} />
      default:
        return <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
    }
  }

  const formatDuration = (ms?: number) => {
    if (!ms) return '-'
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(2)}s`
  }

  return (
    <>
      <Card
        title={
          <Space>
            <CloudUploadOutlined style={{ color: '#1677ff' }} />
            <span>HIS同步管理</span>
            <Badge
              status={synced ? 'success' : 'default'}
              text={synced ? '已同步' : '未同步'}
              style={{ marginLeft: 8 }}
            />
          </Space>
        }
        extra={
          <Tooltip title="刷新">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              onClick={loadSyncData}
              loading={loading}
            />
          </Tooltip>
        }
        size="small"
      >
        {!hisEnabled ? (
          <Empty
            description="HIS同步未启用"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ padding: '20px 0' }}
          />
        ) : (
          <>
            <Space style={{ marginBottom: 16 }} wrap>
              <Button
                type="primary"
                size="small"
                icon={<CloudUploadOutlined />}
                onClick={handleSyncToHis}
                loading={syncing}
              >
                写入HIS
              </Button>
              <Button
                size="small"
                icon={<CloudDownloadOutlined />}
                onClick={handlePullFromHis}
                loading={syncing}
                disabled={!hospitalNo}
              >
                从HIS读取
              </Button>
              <Button
                size="small"
                danger
                icon={<RollbackOutlined />}
                onClick={handleRollback}
                loading={rollingBack}
                disabled={!synced}
              >
                回滚
              </Button>
            </Space>

            <Divider style={{ margin: '8px 0 12px' }} />

            <div style={{ marginBottom: 8 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                同步日志（最近5条）
              </Text>
            </div>

            {logs.length === 0 ? (
              <Empty
                description="暂无同步记录"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: '16px 0' }}
              />
            ) : (
              <List
                size="small"
                dataSource={logs.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item
                    style={{ padding: '6px 0', cursor: 'pointer' }}
                    onClick={() => showLogDetail(item)}
                  >
                    <List.Item.Meta
                      avatar={getStatusIcon(item.syncStatus)}
                      title={
                        <Space size={6}>
                          <Tag
                            color={SyncTypeMap[item.syncType]?.color || 'default'}
                            style={{ fontSize: 10, padding: '0 6px', margin: 0 }}
                          >
                            {SyncTypeMap[item.syncType]?.label || item.syncType}
                          </Tag>
                          <Tag
                            color={SyncDirectionMap[item.syncDirection]?.color || 'default'}
                            style={{ fontSize: 10, padding: '0 6px', margin: 0 }}
                          >
                            {SyncDirectionMap[item.syncDirection]?.label || item.syncDirection}
                          </Tag>
                          <Tag
                            color={SyncStatusMap[item.syncStatus]?.color || 'default'}
                            style={{ fontSize: 10, padding: '0 6px', margin: 0 }}
                          >
                            {SyncStatusMap[item.syncStatus]?.label || item.syncStatus}
                          </Tag>
                        </Space>
                      }
                      description={
                        <Space size={8} style={{ fontSize: 11 }}>
                          <span style={{ color: '#8c8c8c' }}>
                            {dayjs(item.createdTime).format('MM-DD HH:mm:ss')}
                          </span>
                          <span style={{ color: '#8c8c8c' }}>
                            耗时: {formatDuration(item.duration)}
                          </span>
                          {item.retryCount && item.retryCount > 0 && (
                            <Tag color="orange" style={{ fontSize: 10, margin: 0 }}>
                              重试{item.retryCount}次
                            </Tag>
                          )}
                        </Space>
                      }
                    />
                    <InfoCircleOutlined style={{ color: '#bfbfbf' }} />
                  </List.Item>
                )}
              />
            )}

            {logs.length > 0 && (
              <div style={{ textAlign: 'center', marginTop: 8 }}>
                <Button type="link" size="small" onClick={() => showLogDetail(logs[0])}>
                  查看详情
                </Button>
              </div>
            )}
          </>
        )}
      </Card>

      <Modal
        title={
          <Space>
            <FileTextOutlined style={{ color: '#1677ff' }} />
            <span>同步详情</span>
          </Space>
        }
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={720}
      >
        {selectedLog && (
          <div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="同步类型">
                <Tag color={SyncTypeMap[selectedLog.syncType]?.color || 'default'}>
                  {SyncTypeMap[selectedLog.syncType]?.label || selectedLog.syncType}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="同步方向">
                <Tag color={SyncDirectionMap[selectedLog.syncDirection]?.color || 'default'}>
                  {SyncDirectionMap[selectedLog.syncDirection]?.label || selectedLog.syncDirection}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="同步状态">
                <Tag color={SyncStatusMap[selectedLog.syncStatus]?.color || 'default'}>
                  {SyncStatusMap[selectedLog.syncStatus]?.label || selectedLog.syncStatus}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="重试次数">
                {selectedLog.retryCount || 0} 次
              </Descriptions.Item>
              <Descriptions.Item label="开始时间">
                {dayjs(selectedLog.syncStartTime).format('YYYY-MM-DD HH:mm:ss')}
              </Descriptions.Item>
              <Descriptions.Item label="结束时间">
                {dayjs(selectedLog.syncEndTime).format('YYYY-MM-DD HH:mm:ss')}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">
                {formatDuration(selectedLog.duration)}
              </Descriptions.Item>
              <Descriptions.Item label="操作人">
                {selectedLog.createdUserName || '-'}
              </Descriptions.Item>
            </Descriptions>

            {selectedLog.errorMessage && (
              <>
                <Divider style={{ margin: '16px 0' }} />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8, color: '#ff4d4f' }}>
                    错误信息
                  </div>
                  <div
                    style={{
                      padding: 12,
                      background: '#fff2f0',
                      borderRadius: 6,
                      fontSize: 12,
                      color: '#cf1322',
                      wordBreak: 'break-all',
                    }}
                  >
                    {selectedLog.errorMessage}
                  </div>
                </div>
              </>
            )}

            {selectedLog.syncData && (
              <>
                <Divider style={{ margin: '16px 0' }} />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                    请求数据
                  </div>
                  <div
                    style={{
                      padding: 12,
                      background: '#f6ffed',
                      borderRadius: 6,
                      fontSize: 11,
                      color: '#52c41a',
                      wordBreak: 'break-all',
                      maxHeight: 200,
                      overflow: 'auto',
                      fontFamily: 'monospace',
                    }}
                  >
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(JSON.parse(selectedLog.syncData), null, 2)}
                    </pre>
                  </div>
                </div>
              </>
            )}

            {selectedLog.responseData && (
              <>
                <Divider style={{ margin: '16px 0' }} />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                    响应数据
                  </div>
                  <div
                    style={{
                      padding: 12,
                      background: '#e6f7ff',
                      borderRadius: 6,
                      fontSize: 11,
                      color: '#0958d9',
                      wordBreak: 'break-all',
                      maxHeight: 200,
                      overflow: 'auto',
                      fontFamily: 'monospace',
                    }}
                  >
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(JSON.parse(selectedLog.responseData), null, 2)}
                    </pre>
                  </div>
                </div>
              </>
            )}
          </div>
        )}
      </Modal>
    </>
  )
}

export default HisSyncPanel
