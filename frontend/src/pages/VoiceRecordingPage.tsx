import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react'
import {
  Card,
  Button,
  Space,
  Row,
  Col,
  Tag,
  Input,
  Select,
  Form,
  message,
  Modal,
  Badge,
  Statistic,
  Progress,
  Tooltip,
  Descriptions,
  Divider,
  Typography,
  Switch,
  Empty,
  List,
  Alert,
  Drawer,
} from 'antd'
import {
  AudioOutlined,
  AudioMutedOutlined,
  PlaySquareOutlined,
  StopOutlined,
  SaveOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  ThunderboltOutlined,
  FormOutlined,
  SettingOutlined,
  SendOutlined,
  SafetyCertificateOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { voiceApi, recordApi } from '@/services/api'
import type {
  VoiceStreamMessage,
  VoiceSession,
  HomePageField,
  SurgeryRecord,
  SurgeryEntity,
} from '@/types'
import { HOME_PAGE_FIELDS, HOME_PAGE_FIELD_LABEL_MAP, EntityTypeLabelMap } from '@/types'
import dayjs from 'dayjs'

const { Option } = Select
const { Title, Text, Paragraph } = Typography
const { TextArea } = Input

type RecordingState = 'idle' | 'connecting' | 'recording' | 'paused' | 'finalizing'

const VoiceRecordingPage: React.FC = () => {
  const navigate = useNavigate()
  const { recordId } = useParams<{ recordId?: string }>()

  const [form] = Form.useForm()
  const [manualForm] = Form.useForm()

  const [recordingState, setRecordingState] = useState<RecordingState>('idle')
  const [session, setSession] = useState<VoiceSession | null>(null)
  const [record, setRecord] = useState<SurgeryRecord | null>(null)

  const [partialText, setPartialText] = useState('')
  const [segments, setSegments] = useState<string[]>([])
  const [entities, setEntities] = useState<SurgeryEntity[]>([])
  const [homePageFields, setHomePageFields] = useState<Record<string, any>>({})
  const [fieldUpdateTimes, setFieldUpdateTimes] = useState<Record<string, string>>({})

  const [elapsed, setElapsed] = useState(0)
  const [volumeLevel, setVolumeLevel] = useState(0)
  const [audioLevel, setAudioLevel] = useState(0)

  const [useWebSocket, setUseWebSocket] = useState(true)
  const [enableAutoPunctuation, setEnableAutoPunctuation] = useState(true)
  const [enableRealTimeNer, setEnableRealTimeNer] = useState(true)
  const [wsConnected, setWsConnected] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  const [manualDrawer, setManualDrawer] = useState(false)

  const wsRef = useRef<WebSocket | null>(null)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)
  const animationRef = useRef<number | null>(null)
  const timerRef = useRef<number | null>(null)
  const seqRef = useRef(0)
  const heartbeatRef = useRef<number | null>(null)

  const fullText = useMemo(() => segments.join(' '), [segments])
  const filledCount = useMemo(
    () => Object.keys(homePageFields).filter((k) => homePageFields[k] != null && String(homePageFields[k]).trim() !== '').length,
    [homePageFields]
  )
  const fillProgress = useMemo(
    () => Math.round((filledCount / Math.max(HOME_PAGE_FIELDS.length, 1)) * 100),
    [filledCount]
  )

  useEffect(() => {
    if (recordId) {
      loadRecord()
    }
    return () => {
      cleanup()
    }
  }, [recordId])

  const loadRecord = async () => {
    try {
      const data = (await recordApi.detail(Number(recordId))) as SurgeryRecord
      setRecord(data)
      form.setFieldsValue({
        patientName: data.patientName,
        department: data.department,
      })
    } catch (e) {
      console.warn('加载记录失败', e)
    }
  }

  const cleanup = useCallback(() => {
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current)
      heartbeatRef.current = null
    }
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    if (animationRef.current) {
      cancelAnimationFrame(animationRef.current)
      animationRef.current = null
    }
    if (wsRef.current) {
      try {
        if (wsRef.current.readyState === WebSocket.OPEN) {
          wsRef.current.send('STOP')
        }
        wsRef.current.close()
      } catch (e) {}
      wsRef.current = null
    }
    if (mediaRecorderRef.current) {
      try {
        if (mediaRecorderRef.current.state !== 'inactive') {
          mediaRecorderRef.current.stop()
        }
      } catch (e) {}
      mediaRecorderRef.current = null
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop())
      streamRef.current = null
    }
    if (audioContextRef.current) {
      try {
        audioContextRef.current.close()
      } catch (e) {}
      audioContextRef.current = null
    }
    setWsConnected(false)
  }, [])

  const setupVolumeMeter = async (stream: MediaStream) => {
    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext
      if (!AudioCtx) return
      const ctx = new AudioCtx()
      audioContextRef.current = ctx
      const source = ctx.createMediaStreamSource(stream)
      const analyser = ctx.createAnalyser()
      analyser.fftSize = 512
      analyserRef.current = analyser
      source.connect(analyser)
      const dataArray = new Uint8Array(analyser.frequencyBinCount)
      const updateLevel = () => {
        if (analyserRef.current) {
          analyserRef.current.getByteTimeDomainData(dataArray)
          let sum = 0
          for (let i = 0; i < dataArray.length; i++) {
            const v = (dataArray[i] - 128) / 128
            sum += v * v
          }
          const rms = Math.sqrt(sum / dataArray.length)
          const level = Math.min(100, Math.round(rms * 400))
          setVolumeLevel(level)
          setAudioLevel(level)
        }
        animationRef.current = requestAnimationFrame(updateLevel)
      }
      updateLevel()
    } catch (e) {
      console.warn('音量检测启动失败', e)
    }
  }

  const startRecording = async () => {
    setErrorMsg('')
    try {
      const values = await form.validateFields()
      setRecordingState('connecting')

      const sess = (await voiceApi.createSession({
        recordId: recordId ? Number(recordId) : undefined,
        language: 'zh',
        enableAutoPunctuation,
        enableRealTimeNer,
      })) as VoiceSession
      setSession(sess)

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      streamRef.current = stream
      setupVolumeMeter(stream)

      if (useWebSocket) {
        const wsUrl = buildWsUrl(sess.wsUrl)
        const ws = new WebSocket(wsUrl)
        wsRef.current = ws

        ws.onopen = () => {
          setWsConnected(true)
          setRecordingState('recording')
          startTimer()
          startHeartbeat()
          message.success('连接成功，开始录音')
        }
        ws.onmessage = (ev) => handleWsMessage(ev.data)
        ws.onerror = (e) => {
          console.error('WS错误', e)
          setErrorMsg('WebSocket连接异常，尝试HTTP模式')
          setUseWebSocket(false)
        }
        ws.onclose = () => {
          setWsConnected(false)
          if (recordingState === 'recording') {
            setErrorMsg('连接断开')
          }
        }
      } else {
        setRecordingState('recording')
        startTimer()
        message.success('开始录音（HTTP模式）')
      }

      const mime = pickMimeType()
      const mr = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined)
      mediaRecorderRef.current = mr
      mr.ondataavailable = async (ev) => {
        if (ev.data && ev.data.size > 0) {
          await handleAudioChunk(ev.data)
        }
      }
      mr.start(500)
    } catch (e: any) {
      console.error('启动录音失败', e)
      setRecordingState('idle')
      setErrorMsg(e?.message || '无法访问麦克风，请检查权限')
      Modal.error({
        title: '无法启动录音',
        content: e?.message || '请检查浏览器是否授权麦克风访问，或尝试使用HTTPS环境',
      })
      cleanup()
    }
  }

  const pickMimeType = () => {
    const candidates = [
      'audio/webm;codecs=opus',
      'audio/webm',
      'audio/ogg;codecs=opus',
      'audio/mp4',
      'audio/wav',
    ]
    if (!(window as any).MediaRecorder?.isTypeSupported) return null
    for (const m of candidates) {
      if ((window as any).MediaRecorder.isTypeSupported(m)) return m
    }
    return null
  }

  const buildWsUrl = (path: string) => {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const base = `${proto}//${host}`
    const apiBase = (import.meta as any)?.env?.VITE_API_BASE || '/api'
    if (path.startsWith('ws://') || path.startsWith('wss://')) return path
    if (path.startsWith('/')) return `${base}${apiBase}${path}`
    return `${base}${apiBase}/${path}`
  }

  const startTimer = () => {
    const startTs = Date.now()
    timerRef.current = window.setInterval(() => {
      setElapsed(Math.floor((Date.now() - startTs) / 1000))
    }, 1000)
  }

  const startHeartbeat = () => {
    heartbeatRef.current = window.setInterval(() => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        try {
          wsRef.current.send('HEARTBEAT')
        } catch (e) {}
      }
    }, 15000)
  }

  const handleAudioChunk = async (blob: Blob) => {
    if (!session) return
    seqRef.current += 1
    const seq = seqRef.current

    if (useWebSocket && wsRef.current?.readyState === WebSocket.OPEN) {
      try {
        wsRef.current.send(blob)
        return
      } catch (e) {
        console.warn('WS发送失败，转HTTP', e)
      }
    }
    try {
      const resp = (await voiceApi.uploadChunk(
        session.sessionId,
        seq,
        blob,
        false
      )) as any
      if (resp && resp.code === 0 && resp.data) {
        handleWsMessage(JSON.stringify(resp.data))
      }
    } catch (e) {
      console.warn('上传分片失败', e)
    }
  }

  const applyMessage = (msg: VoiceStreamMessage) => {
    if (!msg || !msg.type) return
    switch (msg.type) {
      case 'PARTIAL':
        if (msg.text) setPartialText(msg.text)
        break
      case 'FINAL_SEGMENT':
        if (msg.text) {
          setSegments((prev) => [...prev, msg.text!])
          setPartialText('')
        }
        if (msg.data) {
          if (Array.isArray(msg.data.entities)) {
            setEntities((prev) => [...prev, ...(msg.data.entities as SurgeryEntity[])])
          }
          if (msg.data.homePageFields) {
            const fields = msg.data.homePageFields as Record<string, any>
            setHomePageFields((prev) => ({ ...prev, ...fields }))
            const now = new Date().toISOString()
            const tMap: Record<string, string> = {}
            Object.keys(fields).forEach((k) => (tMap[k] = now))
            setFieldUpdateTimes((prev) => ({ ...prev, ...tMap }))
          }
        }
        break
      case 'HOME_PAGE_UPDATE':
        if (msg.data) {
          const fields = msg.data as Record<string, any>
          setHomePageFields((prev) => ({ ...prev, ...fields }))
          const now = new Date().toISOString()
          const tMap: Record<string, string> = {}
          Object.keys(fields).forEach((k) => (tMap[k] = now))
          setFieldUpdateTimes((prev) => ({ ...prev, ...tMap }))
        }
        break
      case 'ENTITY_UPDATE':
        if (Array.isArray(msg.data)) {
          setEntities((prev) => [...prev, ...(msg.data as SurgeryEntity[])])
        }
        break
      case 'SESSION_STARTED':
        setWsConnected(true)
        break
      case 'SESSION_STOPPED':
        if (msg.text) setSegments((prev) => [...prev, msg.text!])
        setPartialText('')
        break
      case 'ERROR':
        setErrorMsg(msg.errorMsg || '转写服务异常')
        break
      case 'PONG':
      default:
        break
    }
  }

  const handleWsMessage = (raw: string | object) => {
    let msg: VoiceStreamMessage
    if (typeof raw === 'string') {
      try {
        msg = JSON.parse(raw)
      } catch {
        return
      }
    } else {
      msg = raw as VoiceStreamMessage
    }
    applyMessage(msg)
  }

  const pauseRecording = () => {
    if (mediaRecorderRef.current?.state === 'recording') {
      mediaRecorderRef.current.pause()
    }
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    setRecordingState('paused')
  }

  const resumeRecording = () => {
    if (mediaRecorderRef.current?.state === 'paused') {
      mediaRecorderRef.current.resume()
    }
    startTimer()
    setRecordingState('recording')
  }

  const stopRecording = async () => {
    if (!session) return
    setRecordingState('finalizing')
    try {
      if (mediaRecorderRef.current?.state !== 'inactive') {
        mediaRecorderRef.current.stop()
      }
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send('STOP')
      }
      const result = await voiceApi.stopSession(session.sessionId)
      if (result?.fullText && !segments.length) {
        setSegments([result.fullText])
      }
      if (result?.homePageFields) {
        setHomePageFields((prev) => ({ ...prev, ...result.homePageFields }))
      }
      if (result?.entities && Array.isArray(result.entities)) {
        setEntities((prev) => [...prev, ...(result.entities as SurgeryEntity[])])
      }
      message.success('录音结束，结构化数据已生成')
      setRecordingState('idle')
    } catch (e: any) {
      message.error(e?.message || '结束录音失败')
      setRecordingState('idle')
    } finally {
      cleanup()
    }
  }

  const submitManualText = async () => {
    try {
      const values = await manualForm.validateFields()
      if (!session) {
        const sess = (await voiceApi.createSession({
          recordId: recordId ? Number(recordId) : undefined,
        })) as VoiceSession
        setSession(sess)
        const payload = await voiceApi.submitTextChunk(sess.sessionId, values.manualText)
        applyChunkResult(payload)
      } else {
        const payload = await voiceApi.submitTextChunk(session.sessionId, values.manualText)
        applyChunkResult(payload)
      }
      message.success('文本已处理')
      manualForm.resetFields()
    } catch (e: any) {
      message.error(e?.message || '处理失败')
    }
  }

  const applyChunkResult = (payload: any) => {
    if (payload?.sentence) setSegments((prev) => [...prev, payload.sentence])
    if (payload?.entities && Array.isArray(payload.entities)) {
      setEntities((prev) => [...prev, ...(payload.entities as SurgeryEntity[])])
    }
    if (payload?.homePageFields) {
      setHomePageFields((prev) => ({ ...prev, ...payload.homePageFields }))
    }
  }

  const formatTime = (s: number) => {
    const h = Math.floor(s / 3600)
    const m = Math.floor((s % 3600) / 60)
    const sec = s % 60
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  }

  const isRecordingActive = recordingState === 'recording' || recordingState === 'paused'

  return (
    <div className="page-container">
      <Card style={{ marginBottom: 16 }}>
        <Space size={24} wrap>
          <Space>
            <Title level={4} style={{ margin: 0 }}>
              <AudioOutlined style={{ marginRight: 8, color: '#1677ff' }} />
              实时语音录入
            </Title>
            {recordingState === 'recording' && (
              <Badge status="processing" text="录音中" />
            )}
            {recordingState === 'paused' && <Badge status="warning" text="已暂停" />}
            {recordingState === 'connecting' && <Badge status="processing" text="连接中..." />}
            {recordingState === 'finalizing' && <Badge status="processing" text="正在结束..." />}
            {wsConnected && <Tag color="green">WebSocket 已连接</Tag>}
          </Space>
          <Space size={16}>
            <Statistic
              title="录制时长"
              value={formatTime(elapsed)}
              valueStyle={{ fontSize: 20 }}
              prefix={isRecordingActive ? <PlaySquareOutlined style={{ color: recordingState === 'paused' ? '#faad14' : '#1677ff' }} /> : undefined}
            />
            <Statistic
              title="已转写段数"
              value={segments.length}
              prefix={<FileTextOutlined />}
            />
            <Statistic
              title="实体抽取数"
              value={entities.length}
              prefix={<ThunderboltOutlined style={{ color: '#722ed1' }} />}
            />
            <Statistic
              title="首页填充进度"
              value={fillProgress}
              suffix="%"
              prefix={<FormOutlined style={{ color: '#13c2c2' }} />}
              valueStyle={{ color: fillProgress >= 80 ? '#52c41a' : fillProgress >= 50 ? '#faad14' : undefined }}
            />
          </Space>
        </Space>

        {errorMsg && (
          <Alert
            message={errorMsg}
            type="warning"
            showIcon
            style={{ marginTop: 12 }}
            closable
            onClose={() => setErrorMsg('')}
          />
        )}
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card
            title={
              <Space>
                <AudioOutlined style={{ color: '#1677ff' }} />
                <span>语音控制台</span>
              </Space>
            }
            style={{ marginBottom: 16 }}
            extra={
              <Tooltip title="录音设置">
                <Button icon={<SettingOutlined />} type="text" onClick={() => setManualDrawer(true)}>
                  设置 / 手动录入
                </Button>
              </Tooltip>
            }
          >
            {!isRecordingActive && recordingState !== 'finalizing' ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <div style={{ marginBottom: 24 }}>
                  <Form form={form} layout="inline" style={{ justifyContent: 'center' }}>
                    <Form.Item
                      name="patientName"
                      label="患者姓名"
                      initialValue={record?.patientName}
                    >
                      <Input placeholder="可选，关联患者" style={{ width: 160 }} />
                    </Form.Item>
                    <Form.Item
                      name="department"
                      label="科室"
                      initialValue={record?.department}
                    >
                      <Select placeholder="选择科室" allowClear style={{ width: 140 }}>
                        <Option value="普外科">普外科</Option>
                        <Option value="骨科">骨科</Option>
                        <Option value="妇产科">妇产科</Option>
                        <Option value="神经外科">神经外科</Option>
                        <Option value="心胸外科">心胸外科</Option>
                      </Select>
                    </Form.Item>
                  </Form>
                </div>

                <Space size={12} wrap style={{ justifyContent: 'center', marginBottom: 24 }}>
                  <Space>
                    <Text type="secondary">传输方式：</Text>
                    <Switch
                      checked={useWebSocket}
                      onChange={setUseWebSocket}
                      checkedChildren="WebSocket"
                      unCheckedChildren="HTTP分片"
                    />
                  </Space>
                  <Space>
                    <Text type="secondary">智能断句：</Text>
                    <Switch checked={enableAutoPunctuation} onChange={setEnableAutoPunctuation} />
                  </Space>
                  <Space>
                    <Text type="secondary">实时结构化：</Text>
                    <Switch checked={enableRealTimeNer} onChange={setEnableRealTimeNer} />
                  </Space>
                </Space>

                <div
                  style={{
                    position: 'relative',
                    display: 'inline-block',
                    marginBottom: 24,
                  }}
                >
                  <Button
                    type="primary"
                    shape="circle"
                    size="large"
                    icon={<AudioOutlined />}
                    onClick={startRecording}
                    style={{
                      width: 96,
                      height: 96,
                      fontSize: 36,
                      background: 'linear-gradient(135deg, #1677ff, #69b1ff)',
                      boxShadow: '0 8px 24px rgba(22,119,255,0.4)',
                    }}
                  />
                </div>
                <div style={{ marginTop: 12, color: '#8c8c8c' }}>
                  点击上方按钮开始语音录入手术记录
                </div>
                <div style={{ marginTop: 8, fontSize: 12, color: '#bfbfbf' }}>
                  系统将实时完成语音转写、自动断句、结构化抽取与病案首页填充
                </div>
              </div>
            ) : (
              <div>
                <div style={{ textAlign: 'center', marginBottom: 20 }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'flex-end',
                      justifyContent: 'center',
                      gap: 4,
                      height: 64,
                      marginBottom: 20,
                    }}
                  >
                    {Array.from({ length: 24 }).map((_, i) => {
                      const h = Math.max(8, (volumeLevel / 100) * 56 * (0.5 + 0.5 * Math.sin((i + elapsed) / 3)))
                      const opacity = 0.4 + (volumeLevel / 100) * 0.6
                      return (
                        <div
                          key={i}
                          style={{
                            width: 6,
                            height: h,
                            background: recordingState === 'paused'
                              ? '#d9d9d9'
                              : `linear-gradient(to top, #1677ff, #91caff)`,
                            borderRadius: 3,
                            opacity,
                            transition: 'height 0.1s',
                          }}
                        />
                      )
                    })}
                  </div>

                  <Space size={16} wrap style={{ justifyContent: 'center' }}>
                    {recordingState === 'recording' && (
                      <Button
                        type="primary"
                        danger
                        icon={<AudioMutedOutlined />}
                        onClick={pauseRecording}
                        size="large"
                      >
                        暂停
                      </Button>
                    )}
                    {recordingState === 'paused' && (
                      <Button
                        type="primary"
                        icon={<AudioOutlined />}
                        onClick={resumeRecording}
                        size="large"
                      >
                        继续
                      </Button>
                    )}
                    <Button
                      type="default"
                      icon={<StopOutlined />}
                      onClick={stopRecording}
                      size="large"
                      loading={recordingState === 'finalizing'}
                    >
                      结束录音
                    </Button>
                  </Space>

                  <div style={{ marginTop: 16, color: '#8c8c8c', fontSize: 13 }}>
                    音量级别：
                    <Progress
                      percent={volumeLevel}
                      showInfo={false}
                      size="small"
                      style={{ width: 200, display: 'inline-block', marginLeft: 8, verticalAlign: 'middle' }}
                    />
                  </div>
                </div>
              </div>
            )}
          </Card>

          <Card
            title={
              <Space>
                <FileTextOutlined />
                <span>实时转写文本</span>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  ({fullText.length} 字)
                </Text>
              </Space>
            }
            extra={
              <Space>
                <Tooltip title="复制全文">
                  <Button
                    type="text"
                    icon={<SafetyCertificateOutlined />}
                    onClick={() => {
                      navigator.clipboard?.writeText(fullText)
                      message.success('已复制')
                    }}
                    disabled={!fullText}
                  />
                </Tooltip>
                <Button
                  type="text"
                  icon={<ReloadOutlined />}
                  onClick={() => {
                    setSegments([])
                    setPartialText('')
                    message.success('已清空')
                  }}
                  disabled={!fullText && !partialText}
                />
              </Space>
            }
            style={{ marginBottom: 16 }}
          >
            {!fullText && !partialText && recordingState === 'idle' ? (
              <Empty description="暂无转写内容，开始说话后此处将实时显示" />
            ) : (
              <div
                style={{
                  background: '#f6f8fa',
                  padding: 20,
                  borderRadius: 8,
                  fontFamily: 'monospace',
                  whiteSpace: 'pre-wrap',
                  maxHeight: 360,
                  overflow: 'auto',
                  lineHeight: 2,
                  fontSize: 14,
                }}
              >
                {segments.map((seg, i) => (
                  <div key={i} style={{ marginBottom: 8 }}>
                    <Tag
                      color="geekblue"
                      style={{ marginRight: 8, fontSize: 11 }}
                    >
                      #{i + 1}
                    </Tag>
                    {seg}
                  </div>
                ))}
                {partialText && (
                  <div style={{ color: '#1677ff', fontStyle: 'italic' }}>
                    <Tag color="processing" style={{ marginRight: 8 }}>
                      识别中
                    </Tag>
                    {partialText}
                  </div>
                )}
              </div>
            )}
          </Card>

          {entities.length > 0 && (
            <Card
              title={
                <Space>
                  <ThunderboltOutlined style={{ color: '#722ed1' }} />
                  <span>实时抽取实体</span>
                  <Badge count={entities.length} color="#722ed1" />
                </Space>
              }
              size="small"
            >
              <Space wrap size={[6, 6]}>
                {entities.slice(-100).map((e, i) => {
                  const info = EntityTypeLabelMap[e.entityType as any] || {
                    label: e.entityType,
                    color: 'default',
                  }
                  return (
                    <Tooltip
                      key={`${e.id || i}-${e.entityType}`}
                      title={`来源: ${e.source || '实时抽取'}${typeof e.confidence === 'number' ? `，置信度: ${(e.confidence * 100).toFixed(0)}%` : ''}`}
                    >
                      <Tag color={info.color} style={{ padding: '4px 8px' }}>
                        <Text strong>{info.label}</Text>
                        <span style={{ marginLeft: 4, color: '#595959' }}>
                          {e.entityValue}
                          {e.entityUnit && <span style={{ color: '#8c8c8c' }}> {e.entityUnit}</span>}
                        </span>
                      </Tag>
                    </Tooltip>
                  )
                })}
              </Space>
            </Card>
          )}
        </Col>

        <Col xs={24} lg={10}>
          <Card
            title={
              <Space>
                <FormOutlined style={{ color: '#13c2c2' }} />
                <span>病案首页实时填充</span>
                <Tag color={fillProgress >= 80 ? 'success' : fillProgress >= 50 ? 'warning' : 'processing'}>
                  {filledCount}/{HOME_PAGE_FIELDS.length}
                </Tag>
              </Space>
            }
            extra={
              <Tooltip title="打开病案首页">
                <Button
                  icon={<EyeOutlined />}
                  type="text"
                  disabled={!recordId}
                  onClick={() => navigate(`/homepage/${recordId}`)}
                >
                  详情
                </Button>
              </Tooltip>
            }
            style={{ marginBottom: 16 }}
          >
            <Progress
              percent={fillProgress}
              status={fillProgress >= 100 ? 'success' : 'active'}
              style={{ marginBottom: 20 }}
            />
            <div
              style={{
                maxHeight: 520,
                overflow: 'auto',
                paddingRight: 8,
              }}
            >
              <Descriptions column={1} size="small" bordered>
                {HOME_PAGE_FIELDS.map((f) => {
                  const v = homePageFields[f.key]
                  const hasValue = v != null && String(v).trim() !== ''
                  const updated = fieldUpdateTimes[f.key]
                  return (
                    <Descriptions.Item
                      key={f.key}
                      label={
                        <Space>
                          <span>{f.label}</span>
                          {updated && (
                            <Tag color="success" style={{ fontSize: 10, padding: '0 4px' }}>
                              {new Date(updated).toLocaleTimeString()}
                            </Tag>
                          )}
                        </Space>
                      }
                      contentStyle={{
                        background: hasValue ? '#f6ffed' : undefined,
                        minWidth: 160,
                      }}
                    >
                      {hasValue ? (
                        <Space>
                          <CheckCircleOutlined style={{ color: '#52c41a' }} />
                          <Text strong>
                            {Array.isArray(v) ? v.join('，') : String(v)}
                          </Text>
                        </Space>
                      ) : (
                        <Text type="secondary">待填充...</Text>
                      )}
                    </Descriptions.Item>
                  )
                })}
              </Descriptions>
            </div>
          </Card>

          {record && (
            <Card title={<span>关联手术记录</span>} size="small">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="记录编号">{record.recordNo}</Descriptions.Item>
                <Descriptions.Item label="患者">{record.patientName || '-'}</Descriptions.Item>
                <Descriptions.Item label="科室">{record.department || '-'}</Descriptions.Item>
                <Descriptions.Item label="上传时间">
                  {dayjs(record.uploadTime).format('YYYY-MM-DD HH:mm')}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}
        </Col>
      </Row>

      <Drawer
        title="手动录入 / 设置"
        open={manualDrawer}
        onClose={() => setManualDrawer(false)}
        width={480}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={20}>
          <div>
            <Title level={5} style={{ marginBottom: 12 }}>
              <SendOutlined /> 手动输入文本段
            </Title>
            <Form form={manualForm} layout="vertical">
              <Form.Item
                name="manualText"
                label="口述文本"
                rules={[{ required: true, message: '请输入文本' }]}
                extra="系统将自动添加标点、抽取实体、填充首页"
              >
                <TextArea
                  rows={5}
                  placeholder="例如：患者取仰卧位，全身麻醉满意后常规消毒铺巾。于右下腹麦氏点做一长约3厘米斜切口..."
                />
              </Form.Item>
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={submitManualText}
                block
              >
                处理并写入
              </Button>
            </Form>
          </div>

          <Divider />

          <div>
            <Title level={5} style={{ marginBottom: 12 }}>
              <SettingOutlined /> 录音选项
            </Title>
            <List>
              <List.Item>
                <Space>
                  <span style={{ width: 120 }}>传输方式</span>
                  <Switch
                    checked={useWebSocket}
                    onChange={setUseWebSocket}
                    checkedChildren="WebSocket (实时)"
                    unCheckedChildren="HTTP (分片)"
                  />
                </Space>
              </List.Item>
              <List.Item>
                <Space>
                  <span style={{ width: 120 }}>自动断句标点</span>
                  <Switch
                    checked={enableAutoPunctuation}
                    onChange={setEnableAutoPunctuation}
                  />
                </Space>
              </List.Item>
              <List.Item>
                <Space>
                  <span style={{ width: 120 }}>实时NLP抽取</span>
                  <Switch
                    checked={enableRealTimeNer}
                    onChange={setEnableRealTimeNer}
                  />
                </Space>
              </List.Item>
            </List>
          </div>

          <Divider />

          <Alert
            message="提示"
            description="建议在安静环境下，标准普通话口述手术经过。系统会自动识别医学术语并填充病案首页。"
            type="info"
            showIcon
          />
        </Space>
      </Drawer>
    </div>
  )
}

export default VoiceRecordingPage
