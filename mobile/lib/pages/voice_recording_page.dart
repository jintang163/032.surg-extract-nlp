import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../providers/voice_provider.dart';
import '../models/voice_message.dart';

class VoiceRecordingPage extends StatefulWidget {
  final String? recordId;
  const VoiceRecordingPage({super.key, this.recordId});

  @override
  State<VoiceRecordingPage> createState() => _VoiceRecordingPageState();
}

class _VoiceRecordingPageState extends State<VoiceRecordingPage>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  final TextEditingController _manualController = TextEditingController();
  final ScrollController _textScrollController = ScrollController();
  bool _showManualInput = false;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final provider = context.read<VoiceProvider>();
      if (widget.recordId != null) {
        provider.setRecordId(widget.recordId!);
      }
    });
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _manualController.dispose();
    _textScrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<VoiceProvider>();
    final colorScheme = Theme.of(context).colorScheme;
    final isRecording = provider.state == RecordingState.recording;
    final isPaused = provider.state == RecordingState.paused;
    final isActive = provider.isRecordingActive;

    if (isRecording) {
      _pulseController.repeat();
    } else {
      _pulseController.stop();
      _pulseController.reset();
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('实时语音录入'),
        centerTitle: true,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.more_vert),
            onPressed: () => _showSettingsSheet(context),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            _buildStatsBar(context, provider),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildTranscriptionCard(context, provider),
                    const SizedBox(height: 12),
                    _buildHomePageCard(context, provider),
                    const SizedBox(height: 12),
                    _buildEntitiesCard(context, provider),
                  ],
                ),
              ),
            ),
            _buildControlPanel(context, provider),
            if (_showManualInput) _buildManualInputBar(context, provider),
          ],
        ),
      ),
    );
  }

  Widget _buildStatsBar(BuildContext context, VoiceProvider provider) {
    final colorScheme = Theme.of(context).colorScheme;
    final isRecording = provider.state == RecordingState.recording;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(
          bottom: BorderSide(color: colorScheme.outlineVariant.withOpacity(0.5)),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: isRecording ? Colors.red : Colors.grey.shade400,
              boxShadow: isRecording
                  ? [
                      BoxShadow(
                        color: Colors.red.withOpacity(0.6),
                        blurRadius: 8,
                      )
                    ]
                  : null,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            _getStateText(provider.state),
            style: TextStyle(
              fontSize: 13,
              color: isRecording ? Colors.red : Colors.grey.shade600,
              fontWeight: FontWeight.w500,
            ),
          ),
          const Spacer(),
          _buildStatChip(
            provider.formatDuration(provider.elapsedSeconds),
            Icons.timer_outlined,
            colorScheme.primary,
          ),
          const SizedBox(width: 8),
          _buildStatChip(
            '${provider.segments.length}段',
            Icons.text_snippet_outlined,
            Colors.green,
          ),
          const SizedBox(width: 8),
          _buildStatChip(
            '${provider.filledCount}/${VoiceProvider.homePageFieldDefs.length}',
            Icons.assignment_turned_in_outlined,
            Colors.teal,
          ),
        ],
      ),
    );
  }

  Widget _buildStatChip(String text, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 4),
          Text(
            text,
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: color),
          ),
        ],
      ),
    );
  }

  String _getStateText(RecordingState state) {
    switch (state) {
      case RecordingState.idle:
        return '准备就绪';
      case RecordingState.connecting:
        return '连接中...';
      case RecordingState.recording:
        return '录音中';
      case RecordingState.paused:
        return '已暂停';
      case RecordingState.finalizing:
        return '正在保存...';
      case RecordingState.error:
        return '出错';
    }
  }

  Widget _buildTranscriptionCard(BuildContext context, VoiceProvider provider) {
    final colorScheme = Theme.of(context).colorScheme;

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.text_fields_rounded, size: 18, color: colorScheme.primary),
                const SizedBox(width: 8),
                const Text(
                  '实时转写',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                ),
                const Spacer(),
                Text(
                  '${provider.fullText.length} 字',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
                ),
                const SizedBox(width: 8),
                if (provider.fullText.isNotEmpty)
                  GestureDetector(
                    onTap: () {
                      Clipboard.setData(ClipboardData(text: provider.fullText));
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('已复制到剪贴板'), duration: Duration(seconds: 1)),
                      );
                    },
                    child: Icon(Icons.copy, size: 18, color: Colors.grey.shade500),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              constraints: const BoxConstraints(minHeight: 120, maxHeight: 200),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey.shade50,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: provider.segments.isEmpty && provider.partialText.isEmpty
                  ? Center(
                      child: Text(
                        '开始说话后此处将实时显示转写文本...',
                        style: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                      ),
                    )
                  : SingleChildScrollView(
                      controller: _textScrollController,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          ...provider.segments.asMap().entries.map(
                                (entry) => Padding(
                                  padding: const EdgeInsets.only(bottom: 8),
                                  child: Row(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: colorScheme.primary.withOpacity(0.1),
                                          borderRadius: BorderRadius.circular(4),
                                        ),
                                        child: Text(
                                          '#${entry.key + 1}',
                                          style: TextStyle(
                                            fontSize: 10,
                                            color: colorScheme.primary,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                      ),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text(
                                          entry.value,
                                          style: const TextStyle(
                                            fontSize: 14,
                                            height: 1.6,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                          if (provider.partialText.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                        horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: Colors.blue.shade50,
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: const Text(
                                      '识别中',
                                      style: TextStyle(
                                        fontSize: 10,
                                        color: Colors.blue,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      provider.partialText,
                                      style: TextStyle(
                                        fontSize: 14,
                                        height: 1.6,
                                        color: Colors.blue.shade700,
                                        fontStyle: FontStyle.italic,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                        ],
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHomePageCard(BuildContext context, VoiceProvider provider) {
    final colorScheme = Theme.of(context).colorScheme;
    final totalFields = VoiceProvider.homePageFieldDefs.length;
    final filledCount = provider.filledCount;
    final progress = (filledCount / totalFields * 100).toInt();

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ExpansionTile(
        tilePadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
        childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 12),
        title: Row(
          children: [
            Icon(Icons.assignment_rounded, size: 18, color: Colors.teal.shade600),
            const SizedBox(width: 8),
            const Text(
              '病案首页实时填充',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
            ),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: progress >= 80
                    ? Colors.green.shade100
                    : progress >= 50
                        ? Colors.orange.shade100
                        : Colors.blue.shade100,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                '$filledCount/$totalFields',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: progress >= 80
                      ? Colors.green.shade700
                      : progress >= 50
                          ? Colors.orange.shade700
                          : Colors.blue.shade700,
                ),
              ),
            ),
          ],
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 6),
          child: LinearProgressIndicator(
            value: progress / 100,
            minHeight: 4,
            backgroundColor: Colors.grey.shade200,
            valueColor: AlwaysStoppedAnimation<Color>(
              progress >= 80
                  ? Colors.green
                  : progress >= 50
                      ? Colors.orange
                      : colorScheme.primary,
            ),
          ),
        ),
        children: [
          const SizedBox(height: 12),
          ...VoiceProvider.homePageFieldDefs.map(
            (field) {
              final key = field['key']!;
              final label = field['label']!;
              final value = provider.homePageFields[key];
              final hasValue = value != null && value.toString().trim().isNotEmpty;
              final updateTime = provider.fieldUpdateTimes[key];
              final isRecent = updateTime != null &&
                  DateTime.now().difference(updateTime).inSeconds < 3;

              return Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  color: hasValue ? Colors.green.shade50 : Colors.grey.shade50,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                    color: isRecent
                        ? Colors.green.shade300
                        : hasValue
                            ? Colors.green.shade200
                            : Colors.grey.shade200,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      hasValue ? Icons.check_circle : Icons.circle_outlined,
                      size: 14,
                      color: hasValue ? Colors.green : Colors.grey.shade400,
                    ),
                    const SizedBox(width: 8),
                    SizedBox(
                      width: 80,
                      child: Text(
                        label,
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey.shade600,
                        ),
                      ),
                    ),
                    Expanded(
                      child: Text(
                        hasValue ? value.toString() : '待填充...',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: hasValue ? FontWeight.w500 : FontWeight.normal,
                          color: hasValue ? Colors.black87 : Colors.grey.shade400,
                        ),
                        textAlign: TextAlign.right,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildEntitiesCard(BuildContext context, VoiceProvider provider) {
    if (provider.entities.isEmpty) {
      return const SizedBox.shrink();
    }

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.psychology_alt_rounded,
                    size: 18, color: Colors.purple),
                const SizedBox(width: 8),
                const Text(
                  '抽取实体',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                ),
                const Spacer(),
                Text(
                  '${provider.entities.length} 个',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: provider.entities.reversed.take(30).map((entity) {
                final color = _entityColor(entity.entityType);
                return Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: color.withOpacity(0.3)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _entityTypeLabel(entity.entityType),
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: color,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        entity.entityValue +
                            (entity.entityUnit ?? ''),
                        style: const TextStyle(fontSize: 12, color: Colors.black87),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ],
        ),
      ),
    );
  }

  Color _entityColor(String type) {
    final map = {
      'SURGERY_NAME': Colors.blue.shade700,
      'SURGERY_CODE': Colors.blue.shade500,
      'PATIENT_NAME': Colors.pink.shade600,
      'BLOOD_LOSS': Colors.red.shade600,
      'BLOOD_TRANSFUSION': Colors.red.shade400,
      'ANESTHESIA_TYPE': Colors.purple.shade600,
      'ANESTHESIA_CODE': Colors.purple.shade400,
      'INCISION_LEVEL': Colors.orange.shade600,
      'INCISION_HEALING': Colors.orange.shade400,
      'SURGEON': Colors.teal.shade600,
      'ASSISTANT1': Colors.teal.shade400,
      'ANESTHESIOLOGIST': Colors.deepPurple.shade600,
      'DIAGNOSIS': Colors.teal.shade700,
      'DEPARTMENT': Colors.indigo.shade600,
      'SURGERY_DATE': Colors.brown.shade600,
      'FLUID_INFUSION': Colors.cyan.shade600,
      'OPERATION_DURATION': Colors.lime.shade700,
      'COMPLICATIONS': Colors.red.shade700,
    };
    return map[type] ?? Colors.grey.shade600;
  }

  String _entityTypeLabel(String type) {
    final map = {
      'SURGERY_NAME': '手术名',
      'SURGERY_CODE': '手术编码',
      'PATIENT_NAME': '姓名',
      'BLOOD_LOSS': '出血量',
      'BLOOD_TRANSFUSION': '输血量',
      'ANESTHESIA_TYPE': '麻醉',
      'ANESTHESIA_CODE': '麻醉编码',
      'INCISION_LEVEL': '切口等级',
      'INCISION_HEALING': '切口愈合',
      'SURGEON': '术者',
      'ASSISTANT1': '一助',
      'ANESTHESIOLOGIST': '麻醉师',
      'DIAGNOSIS': '诊断',
      'DEPARTMENT': '科室',
      'SURGERY_DATE': '手术日期',
      'FLUID_INFUSION': '输液量',
      'OPERATION_DURATION': '时长',
      'COMPLICATIONS': '并发症',
    };
    return map[type] ?? type;
  }

  Widget _buildControlPanel(BuildContext context, VoiceProvider provider) {
    final colorScheme = Theme.of(context).colorScheme;
    final isRecording = provider.state == RecordingState.recording;
    final isPaused = provider.state == RecordingState.paused;
    final isActive = provider.isRecordingActive;
    final isFinalizing = provider.state == RecordingState.finalizing;
    final isConnecting = provider.state == RecordingState.connecting;

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(
          top: BorderSide(color: colorScheme.outlineVariant.withOpacity(0.5)),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (isActive) ...[
            _buildWaveform(context, isRecording),
            const SizedBox(height: 16),
          ],
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (isActive) ...[
                IconButton.filled(
                  onPressed: isFinalizing
                      ? null
                      : () {
                          if (isRecording) {
                            provider.pauseRecording();
                          } else if (isPaused) {
                            provider.resumeRecording();
                          }
                        },
                  icon: Icon(
                    isRecording ? Icons.pause : Icons.play_arrow,
                    size: 28,
                  ),
                  style: IconButton.styleFrom(
                    backgroundColor: Colors.blueGrey.shade100,
                    foregroundColor: Colors.blueGrey.shade700,
                    padding: const EdgeInsets.all(14),
                  ),
                ),
                const SizedBox(width: 24),
              ],
              GestureDetector(
                onTap: isConnecting || isFinalizing
                    ? null
                    : () {
                        if (isActive) {
                          _confirmStop(context, provider);
                        } else {
                          provider.startRecording();
                        }
                      },
                child: AnimatedBuilder(
                  animation: _pulseController,
                  builder: (context, child) {
                    final scale = 1.0 + (_pulseController.value * 0.08);
                    return Transform.scale(
                      scale: isRecording ? scale : 1.0,
                      child: Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                            colors: isActive
                                ? [Colors.red.shade400, Colors.red.shade600]
                                : [colorScheme.primary, colorScheme.primaryContainer],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: isActive
                                  ? Colors.red.withOpacity(0.4)
                                  : colorScheme.primary.withOpacity(0.4),
                              blurRadius: 16,
                              offset: const Offset(0, 6),
                            ),
                          ],
                        ),
                        child: Icon(
                          isActive ? Icons.stop_rounded : Icons.mic_none_rounded,
                          color: Colors.white,
                          size: 36,
                        ),
                      ),
                    );
                  },
                ),
              ),
              if (isActive) ...[
                const SizedBox(width: 24),
                IconButton.filled(
                  onPressed: () {
                    setState(() => _showManualInput = !_showManualInput);
                  },
                  icon: const Icon(Icons.edit_note_rounded, size: 26),
                  style: IconButton.styleFrom(
                    backgroundColor: Colors.teal.shade100,
                    foregroundColor: Colors.teal.shade700,
                    padding: const EdgeInsets.all(14),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 10),
          if (!isActive)
            Text(
              '点击麦克风开始语音录入',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
            ),
          if (provider.errorMsg.isNotEmpty && provider.state == RecordingState.error)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                provider.errorMsg,
                style: const TextStyle(color: Colors.red, fontSize: 12),
                textAlign: TextAlign.center,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildWaveform(BuildContext context, bool isActive) {
    final bars = <double>[];
    for (int i = 0; i < 30; i++) {
      final base = 4.0 + ((i * 1.7) % 12);
      final wave = isActive
          ? 8 + (i % 5) * 4 + ((DateTime.now().millisecondsSinceEpoch / (200 + i * 30)) % 1) * 20
          : base;
      bars.add(wave.clamp(4.0, 36.0));
    }

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: bars.asMap().entries.map((entry) {
        return Container(
          width: 4,
          height: entry.value,
          margin: const EdgeInsets.symmetric(horizontal: 1.5),
          decoration: BoxDecoration(
            color: isActive ? Colors.blue.shade300 : Colors.grey.shade300,
            borderRadius: BorderRadius.circular(2),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildManualInputBar(BuildContext context, VoiceProvider provider) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
      decoration: BoxDecoration(
        color: Colors.teal.shade50,
        border: Border(
          top: BorderSide(color: Colors.teal.shade200),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _manualController,
              maxLines: 2,
              minLines: 1,
              decoration: InputDecoration(
                hintText: '手动输入手术描述文本...',
                hintStyle: TextStyle(fontSize: 13, color: Colors.grey.shade500),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                fillColor: Colors.white,
                filled: true,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide(color: Colors.teal.shade200),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide(color: Colors.teal.shade200),
                ),
              ),
              style: const TextStyle(fontSize: 14),
            ),
          ),
          const SizedBox(width: 8),
          IconButton.filled(
            onPressed: () async {
              final text = _manualController.text.trim();
              if (text.isEmpty) return;
              try {
                await provider.submitManualText(text);
                _manualController.clear();
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                        content: Text('文本已处理'), duration: Duration(seconds: 1)),
                  );
                }
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('处理失败：$e')),
                  );
                }
              }
            },
            icon: const Icon(Icons.send_rounded),
            style: IconButton.styleFrom(
              backgroundColor: Colors.teal,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.all(12),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmStop(BuildContext context, VoiceProvider provider) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('结束录音'),
        content: const Text('确认结束本次语音录入？系统将保存结构化结果。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              provider.stopRecording();
            },
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            child: const Text('结束'),
          ),
        ],
      ),
    );
  }

  void _showSettingsSheet(BuildContext context) {
    final provider = context.read<VoiceProvider>();
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '录音设置',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              _settingRow(
                'WebSocket 实时传输',
                '使用WebSocket实现低延迟实时转写',
                provider.useWebSocket,
                (v) {
                  provider.setUseWebSocket(v);
                  setSheetState(() {});
                },
              ),
              _settingRow(
                '智能断句标点',
                '自动识别句末并添加标点符号',
                provider.enableAutoPunctuation,
                (v) {
                  provider.setEnableAutoPunctuation(v);
                  setSheetState(() {});
                },
              ),
              _settingRow(
                '实时 NLP 抽取',
                '边转写边抽取实体并填充首页',
                provider.enableRealTimeNer,
                (v) {
                  provider.setEnableRealTimeNer(v);
                  setSheetState(() {});
                },
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('完成'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _settingRow(
      String title, String desc, bool value, ValueChanged<bool> onChanged) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(
                        fontSize: 14, fontWeight: FontWeight.w500)),
                const SizedBox(height: 2),
                Text(desc,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade500)),
              ],
            ),
          ),
          Switch(value: value, onChanged: onChanged),
        ],
      ),
    );
  }
}
