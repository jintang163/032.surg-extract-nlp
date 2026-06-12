import '../models/voice_message.dart';

class VoiceService {
  static const String defaultApiBase = 'http://10.0.2.2:8080/api';

  final String apiBase;
  VoiceService({this.apiBase = defaultApiBase});

  Uri wsUrl(String sessionId) {
    final base = apiBase.replaceFirst(RegExp(r'^https?://'), '');
    final scheme = apiBase.startsWith('https') ? 'wss' : 'ws';
    return Uri.parse('$scheme://$base/ws/voice/$sessionId');
  }

  Uri createSessionUrl({
    int? recordId,
    String language = 'zh',
    bool enableAutoPunctuation = true,
    bool enableRealTimeNer = true,
  }) {
    final uri = Uri.parse('$apiBase/voice/session').replace(queryParameters: {
      if (recordId != null) 'recordId': recordId.toString(),
      'language': language,
      'enableAutoPunctuation': enableAutoPunctuation.toString(),
      'enableRealTimeNer': enableRealTimeNer.toString(),
    });
    return uri;
  }

  Uri stopSessionUrl(String sessionId) =>
      Uri.parse('$apiBase/voice/session/$sessionId/stop');

  static VoiceStreamMessage parseMessage(dynamic raw) {
    if (raw is String) {
      return VoiceStreamMessage.fromRawJson(raw);
    }
    return VoiceStreamMessage.fromJson(raw as Map<String, dynamic>);
  }
}
