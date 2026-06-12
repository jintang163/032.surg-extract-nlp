import 'dart:convert';

class VoiceStreamMessage {
  final String type;
  final String sessionId;
  final String? text;
  final bool? isFinal;
  final String? errorMsg;
  final dynamic data;
  final String? timestamp;

  VoiceStreamMessage({
    required this.type,
    required this.sessionId,
    this.text,
    this.isFinal,
    this.errorMsg,
    this.data,
    this.timestamp,
  });

  factory VoiceStreamMessage.fromRawJson(String str) =>
      VoiceStreamMessage.fromJson(json.decode(str));

  factory VoiceStreamMessage.fromJson(Map<String, dynamic> json) =>
      VoiceStreamMessage(
        type: json['type'] as String? ?? 'UNKNOWN',
        sessionId: json['sessionId'] as String? ?? '',
        text: json['text'] as String?,
        isFinal: json['isFinal'] as bool?,
        errorMsg: json['errorMsg'] as String?,
        data: json['data'],
        timestamp: json['timestamp'] as String?,
      );

  bool get isPartial => type == 'PARTIAL';
  bool get isFinalSegment => type == 'FINAL_SEGMENT';
  bool get isHomePageUpdate => type == 'HOME_PAGE_UPDATE';
  bool get isEntityUpdate => type == 'ENTITY_UPDATE';
  bool get isSessionStarted => type == 'SESSION_STARTED';
  bool get isSessionStopped => type == 'SESSION_STOPPED';
  bool get isError => type == 'ERROR';

  List<Map<String, dynamic>> get entityList {
    final d = data;
    if (d is List) {
      return d.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    }
    if (d is Map && d['entities'] is List) {
      return (d['entities'] as List)
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
    }
    return [];
  }

  Map<String, dynamic> get homePageFields {
    final d = data;
    if (d is Map) {
      if (d['homePageFields'] is Map) {
        return Map<String, dynamic>.from(d['homePageFields'] as Map);
      }
      if (isHomePageUpdate) {
        return Map<String, dynamic>.from(d);
      }
    }
    return {};
  }
}

class VoiceSession {
  final String sessionId;
  final int? recordId;
  final String wsUrl;
  final String language;
  final bool enableAutoPunctuation;
  final bool enableRealTimeNer;
  final String startTime;

  VoiceSession({
    required this.sessionId,
    this.recordId,
    required this.wsUrl,
    required this.language,
    required this.enableAutoPunctuation,
    required this.enableRealTimeNer,
    required this.startTime,
  });

  factory VoiceSession.fromJson(Map<String, dynamic> json) => VoiceSession(
        sessionId: json['sessionId'] as String,
        recordId: json['recordId'] as int?,
        wsUrl: json['wsUrl'] as String,
        language: json['language'] as String? ?? 'zh',
        enableAutoPunctuation:
            json['enableAutoPunctuation'] as bool? ?? true,
        enableRealTimeNer: json['enableRealTimeNer'] as bool? ?? true,
        startTime: json['startTime'] as String,
      );
}

class SurgeryEntity {
  final String entityType;
  final String entityValue;
  final String? entityUnit;
  final double? confidence;
  final String? source;

  SurgeryEntity({
    required this.entityType,
    required this.entityValue,
    this.entityUnit,
    this.confidence,
    this.source,
  });

  factory SurgeryEntity.fromJson(Map<String, dynamic> json) => SurgeryEntity(
        entityType: json['entityType'] as String? ?? '',
        entityValue: json['entityValue'] as String? ?? '',
        entityUnit: json['entityUnit'] as String?,
        confidence: (json['confidence'] as num?)?.toDouble(),
        source: json['source'] as String?,
      );
}
