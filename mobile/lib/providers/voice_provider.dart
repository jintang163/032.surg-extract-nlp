import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:record/record.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:dio/dio.dart';
import 'package:permission_handler/permission_handler.dart';
import '../models/voice_message.dart';
import '../services/voice_service.dart';

enum RecordingState { idle, connecting, recording, paused, finalizing, error }

class VoiceProvider extends ChangeNotifier {
  final VoiceService _service = VoiceService();
  final Dio _dio = Dio();

  RecordingState _state = RecordingState.idle;
  VoiceSession? _session;
  String? _recordId;
  int _elapsedSeconds = 0;
  Timer? _timer;

  String _partialText = '';
  final List<String> _segments = [];
  final List<SurgeryEntity> _entities = [];
  final Map<String, dynamic> _homePageFields = {};
  final Map<String, DateTime> _fieldUpdateTimes = {};

  WebSocketChannel? _wsChannel;
  final AudioRecorder _recorder = AudioRecorder();
  int _seq = 0;
  StreamSubscription? _audioStreamSubscription;
  String _errorMsg = '';

  bool _useWebSocket = true;
  bool _enableAutoPunctuation = true;
  bool _enableRealTimeNer = true;

  RecordingState get state => _state;
  VoiceSession? get session => _session;
  int get elapsedSeconds => _elapsedSeconds;
  String get partialText => _partialText;
  List<String> get segments => List.unmodifiable(_segments);
  List<SurgeryEntity> get entities => List.unmodifiable(_entities);
  Map<String, dynamic> get homePageFields => Map.unmodifiable(_homePageFields);
  Map<String, DateTime> get fieldUpdateTimes => Map.unmodifiable(_fieldUpdateTimes);
  String get errorMsg => _errorMsg;
  bool get useWebSocket => _useWebSocket;
  bool get enableAutoPunctuation => _enableAutoPunctuation;
  bool get enableRealTimeNer => _enableRealTimeNer;
  bool get isRecordingActive =>
      _state == RecordingState.recording || _state == RecordingState.paused;

  String get fullText => _segments.join(' ');
  int get filledCount => _homePageFields.entries
      .where((e) => e.value != null && e.value.toString().trim().isNotEmpty)
      .length;

  static const List<Map<String, String>> homePageFieldDefs = [
    {'key': 'patientName', 'label': '患者姓名'},
    {'key': 'gender', 'label': '性别'},
    {'key': 'age', 'label': '年龄'},
    {'key': 'hospitalNo', 'label': '住院号'},
    {'key': 'department', 'label': '科室'},
    {'key': 'admissionDiagnosis', 'label': '入院诊断'},
    {'key': 'dischargeDiagnosis', 'label': '出院诊断'},
    {'key': 'surgeryDate', 'label': '手术日期'},
    {'key': 'surgeryName', 'label': '手术名称'},
    {'key': 'surgeryCode', 'label': '手术编码'},
    {'key': 'incisionLevel', 'label': '切口等级'},
    {'key': 'incisionHealing', 'label': '切口愈合'},
    {'key': 'anesthesiaType', 'label': '麻醉方式'},
    {'key': 'anesthesiaCode', 'label': '麻醉编码'},
    {'key': 'bloodLoss', 'label': '失血量(ml)'},
    {'key': 'bloodTransfusion', 'label': '输血量(ml)'},
    {'key': 'fluidInfusion', 'label': '输液量(ml)'},
    {'key': 'surgeon', 'label': '术者'},
    {'key': 'assistant1', 'label': '第一助手'},
    {'key': 'anesthesiologist', 'label': '麻醉医师'},
    {'key': 'scrubNurse', 'label': '器械护士'},
    {'key': 'circulatingNurse', 'label': '巡回护士'},
    {'key': 'complications', 'label': '并发症'},
  ];

  void setUseWebSocket(bool v) {
    _useWebSocket = v;
    notifyListeners();
  }

  void setEnableAutoPunctuation(bool v) {
    _enableAutoPunctuation = v;
    notifyListeners();
  }

  void setEnableRealTimeNer(bool v) {
    _enableRealTimeNer = v;
    notifyListeners();
  }

  void setRecordId(String? id) {
    _recordId = id;
    notifyListeners();
  }

  Future<bool> requestPermissions() async {
    final micStatus = await Permission.microphone.request();
    return micStatus.isGranted;
  }

  Future<void> startRecording() async {
    if (_state == RecordingState.recording) return;

    final hasPermission = await requestPermissions();
    if (!hasPermission) {
      _errorMsg = '麦克风权限未授权';
      _state = RecordingState.error;
      notifyListeners();
      return;
    }

    try {
      _state = RecordingState.connecting;
      _errorMsg = '';
      _partialText = '';
      _segments.clear();
      _entities.clear();
      _homePageFields.clear();
      _fieldUpdateTimes.clear();
      _elapsedSeconds = 0;
      _seq = 0;
      notifyListeners();

      final uri = _service.createSessionUrl(
        recordId: _recordId != null ? int.tryParse(_recordId!) : null,
        language: 'zh',
        enableAutoPunctuation: _enableAutoPunctuation,
        enableRealTimeNer: _enableRealTimeNer,
      );

      final response = await _dio.postUri(uri);
      final data = response.data is Map
          ? Map<String, dynamic>.from(response.data)
          : json.decode(response.data.toString());

      if (data['code'] != 0) {
        throw Exception(data['msg'] ?? '创建会话失败');
      }
      final sessionData = Map<String, dynamic>.from(data['data']);
      _session = VoiceSession.fromJson(sessionData);

      if (_useWebSocket) {
        await _connectWebSocket();
      } else {
        _state = RecordingState.recording;
        _startTimer();
        notifyListeners();
      }

      await _startAudioCapture();
    } catch (e) {
      _errorMsg = e.toString();
      _state = RecordingState.error;
      notifyListeners();
      rethrow;
    }
  }

  Future<void> _connectWebSocket() async {
    if (_session == null) return;
    final wsUri = _service.wsUrl(_session!.sessionId);
    _wsChannel = WebSocketChannel.connect(wsUri);

    _wsChannel!.stream.listen(
      (dynamic raw) {
        _handleMessage(VoiceService.parseMessage(raw));
      },
      onError: (e) {
        _errorMsg = 'WebSocket连接异常：$e';
        _useWebSocket = false;
        if (_state == RecordingState.recording) {
          notifyListeners();
        }
      },
      onDone: () {
        if (_state == RecordingState.recording) {
          _errorMsg = '连接断开';
          notifyListeners();
        }
      },
    );

    _state = RecordingState.recording;
    _startTimer();
    notifyListeners();
  }

  Future<void> _startAudioCapture() async {
    const config = RecordConfig(
      encoder: AudioEncoder.opus,
      sampleRate: 16000,
      numChannels: 1,
      bitRate: 32000,
    );

    final stream = await _recorder.startStream(config);

    _audioStreamSubscription = stream.listen((chunk) async {
      if (_state != RecordingState.recording) return;
      _seq++;
      final seq = _seq;

      if (_useWebSocket && _wsChannel != null) {
        try {
          _wsChannel!.sink.add(chunk);
        } catch (e) {
          _useWebSocket = false;
          _uploadChunkHttp(seq, chunk);
        }
      } else {
        _uploadChunkHttp(seq, chunk);
      }
    });
  }

  Future<void> _uploadChunkHttp(int seq, List<int> data) async {
    if (_session == null) return;
    try {
      final formData = FormData.fromMap({
        'sessionId': _session!.sessionId,
        'seq': seq,
        'lastChunk': false,
        'chunk': MultipartFile.fromBytes(data, filename: 'chunk.webm'),
      });

      final response = await _dio.post(
        '${_service.apiBase}/voice/upload-chunk',
        data: formData,
        options: Options(contentType: 'multipart/form-data'),
      );

      final data = response.data is Map
          ? Map<String, dynamic>.from(response.data)
          : json.decode(response.data.toString());

      if (data['code'] == 0 && data['data'] != null) {
        _handleMessage(VoiceStreamMessage.fromJson(
            Map<String, dynamic>.from(data['data'])));
      }
    } catch (e) {
      debugPrint('上传分片失败: $e');
    }
  }

  void _handleMessage(VoiceStreamMessage msg) {
    switch (msg.type) {
      case 'PARTIAL':
        if (msg.text != null) {
          _partialText = msg.text!;
          notifyListeners();
        }
        break;
      case 'FINAL_SEGMENT':
        if (msg.text != null && msg.text!.isNotEmpty) {
          _segments.add(msg.text!);
          _partialText = '';
        }
        if (msg.data is Map) {
          final payload = Map<String, dynamic>.from(msg.data as Map);
          if (payload['entities'] is List) {
            for (var e in payload['entities']) {
              _entities.add(SurgeryEntity.fromJson(Map<String, dynamic>.from(e)));
            }
          }
          if (payload['homePageFields'] is Map) {
            final fields = Map<String, dynamic>.from(payload['homePageFields']);
            _homePageFields.addAll(fields);
            final now = DateTime.now();
            for (var k in fields.keys) {
              _fieldUpdateTimes[k] = now;
            }
          }
        }
        notifyListeners();
        break;
      case 'HOME_PAGE_UPDATE':
        if (msg.data is Map) {
          final fields = Map<String, dynamic>.from(msg.data as Map);
          _homePageFields.addAll(fields);
          final now = DateTime.now();
          for (var k in fields.keys) {
            _fieldUpdateTimes[k] = now;
          }
          notifyListeners();
        }
        break;
      case 'ENTITY_UPDATE':
        if (msg.data is List) {
          for (var e in msg.data as List) {
            _entities.add(SurgeryEntity.fromJson(Map<String, dynamic>.from(e)));
          }
          notifyListeners();
        }
        break;
      case 'SESSION_STARTED':
        break;
      case 'SESSION_STOPPED':
        if (msg.text != null && msg.text!.isNotEmpty) {
          _segments.add(msg.text!);
        }
        _partialText = '';
        notifyListeners();
        break;
      case 'ERROR':
        _errorMsg = msg.errorMsg ?? '转写服务异常';
        notifyListeners();
        break;
    }
  }

  void _startTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      _elapsedSeconds++;
      notifyListeners();
    });
  }

  Future<void> pauseRecording() async {
    if (_state != RecordingState.recording) return;
    await _recorder.pause();
    _timer?.cancel();
    _state = RecordingState.paused;
    notifyListeners();
  }

  Future<void> resumeRecording() async {
    if (_state != RecordingState.paused) return;
    await _recorder.resume();
    _startTimer();
    _state = RecordingState.recording;
    notifyListeners();
  }

  Future<void> stopRecording() async {
    if (_session == null) return;
    _state = RecordingState.finalizing;
    _timer?.cancel();
    notifyListeners();

    try {
      await _audioStreamSubscription?.cancel();
      _audioStreamSubscription = null;

      try {
        await _recorder.stop();
      } catch (_) {}

      if (_useWebSocket && _wsChannel != null) {
        _wsChannel!.sink.add('STOP');
        await Future.delayed(const Duration(milliseconds: 500));
      }

      final uri = _service.stopSessionUrl(_session!.sessionId);
      final response = await _dio.postUri(uri);
      final data = response.data is Map
          ? Map<String, dynamic>.from(response.data)
          : json.decode(response.data.toString());

      if (data['code'] == 0 && data['data'] != null) {
        final payload = Map<String, dynamic>.from(data['data']);
        if (payload['fullText'] != null && _segments.isEmpty) {
          _segments.add(payload['fullText'].toString());
        }
        if (payload['homePageFields'] is Map) {
          _homePageFields
              .addAll(Map<String, dynamic>.from(payload['homePageFields']));
        }
        if (payload['entities'] is List) {
          for (var e in payload['entities']) {
            _entities.add(SurgeryEntity.fromJson(Map<String, dynamic>.from(e)));
          }
        }
      }

      _wsChannel?.sink.close();
      _wsChannel = null;

      _state = RecordingState.idle;
      notifyListeners();
    } catch (e) {
      _errorMsg = e.toString();
      _state = RecordingState.idle;
      notifyListeners();
    }
  }

  Future<void> submitManualText(String text) async {
    if (_session == null) {
      final uri = _service.createSessionUrl();
      final response = await _dio.postUri(uri);
      final data = response.data is Map
          ? Map<String, dynamic>.from(response.data)
          : json.decode(response.data.toString());
      if (data['code'] != 0) throw Exception(data['msg']);
      _session =
          VoiceSession.fromJson(Map<String, dynamic>.from(data['data']));
    }

    final uri = Uri.parse('${_service.apiBase}/voice/text-chunk')
        .replace(queryParameters: {'sessionId': _session!.sessionId});

    final response = await _dio.postUri(uri, data: {'text': text});
    final data = response.data is Map
        ? Map<String, dynamic>.from(response.data)
        : json.decode(response.data.toString());

    if (data['code'] == 0 && data['data'] != null) {
      final payload = Map<String, dynamic>.from(data['data']);
      if (payload['sentence'] != null) {
        _segments.add(payload['sentence'].toString());
      }
      if (payload['entities'] is List) {
        for (var e in payload['entities']) {
          _entities.add(SurgeryEntity.fromJson(Map<String, dynamic>.from(e)));
        }
      }
      if (payload['homePageFields'] is Map) {
        final fields = Map<String, dynamic>.from(payload['homePageFields']);
        _homePageFields.addAll(fields);
        final now = DateTime.now();
        for (var k in fields.keys) {
          _fieldUpdateTimes[k] = now;
        }
      }
      notifyListeners();
    }
  }

  void clearText() {
    _segments.clear();
    _partialText = '';
    notifyListeners();
  }

  String formatDuration(int seconds) {
    final h = (seconds ~/ 3600).toString().padLeft(2, '0');
    final m = ((seconds % 3600) ~/ 60).toString().padLeft(2, '0');
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$h:$m:$s';
  }

  @override
  void dispose() {
    _timer?.cancel();
    _audioStreamSubscription?.cancel();
    _wsChannel?.sink.close();
    _recorder.dispose();
    super.dispose();
  }
}
