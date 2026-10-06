class Release {
  final int id;
  final int projectId;
  final String version;
  final String state;
  final String? failReason;
  final DateTime createdAt;
  final DateTime updatedAt;

  Release({
    required this.id,
    required this.projectId,
    required this.version,
    required this.state,
    this.failReason,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Release.fromJson(Map<String, dynamic> json) {
    return Release(
      id: json['id'] as int,
      projectId: json['projectId'] as int,
      version: json['version'] as String,
      state: json['state'] as String,
      failReason: json['failReason'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'projectId': projectId,
        'version': version,
        'state': state,
        'failReason': failReason,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };
}

class ReleaseDetail {
  final Release release;
  final List<Artifact> artifacts;
  final List<ReleaseEvent> events;

  ReleaseDetail({
    required this.release,
    required this.artifacts,
    required this.events,
  });

  factory ReleaseDetail.fromJson(Map<String, dynamic> json) {
    return ReleaseDetail(
      release: Release.fromJson(json['release'] as Map<String, dynamic>),
      artifacts: (json['artifacts'] as List<dynamic>?)
              ?.map((e) => Artifact.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      // 后端字段名为 timeline（状态事件时间线）
      events: (json['timeline'] as List<dynamic>?)
              ?.map((e) => ReleaseEvent.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}

class Artifact {
  final int id;
  final int releaseId;
  final int projectId;
  final String fileName;
  final String storagePath;
  final int sizeBytes;
  final String sha256;
  final String? platformOs;
  final String? platformArch;
  final String? platformLibc;
  final bool portable;
  final DateTime createdAt;

  Artifact({
    required this.id,
    required this.releaseId,
    required this.projectId,
    required this.fileName,
    required this.storagePath,
    required this.sizeBytes,
    required this.sha256,
    this.platformOs,
    this.platformArch,
    this.platformLibc,
    required this.portable,
    required this.createdAt,
  });

  factory Artifact.fromJson(Map<String, dynamic> json) {
    return Artifact(
      id: json['id'] as int,
      releaseId: json['releaseId'] as int,
      projectId: json['projectId'] as int,
      fileName: json['fileName'] as String,
      storagePath: json['storagePath'] as String,
      sizeBytes: json['sizeBytes'] as int,
      sha256: json['sha256'] as String,
      platformOs: json['platformOs'] as String?,
      platformArch: json['platformArch'] as String?,
      platformLibc: json['platformLibc'] as String?,
      portable: json['portable'] as bool? ?? false,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

class ReleaseEvent {
  final int id;
  final int releaseId;
  final String? fromState;
  final String toState;
  final String? message;
  final DateTime createdAt;

  ReleaseEvent({
    required this.id,
    required this.releaseId,
    this.fromState,
    required this.toState,
    this.message,
    required this.createdAt,
  });

  factory ReleaseEvent.fromJson(Map<String, dynamic> json) {
    return ReleaseEvent(
      id: json['id'] as int,
      releaseId: json['releaseId'] as int,
      fromState: json['fromState'] as String?,
      toState: json['toState'] as String,
      message: json['message'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

/// 构建日志增量块（GET /api/releases/{id}/build-log?offset=N）。
class BuildLogChunk {
  final int releaseId;
  final String state;
  final bool exists;
  final int offset;
  final int nextOffset;
  final int size;
  final bool truncated;
  final String content;

  BuildLogChunk({
    required this.releaseId,
    required this.state,
    required this.exists,
    required this.offset,
    required this.nextOffset,
    required this.size,
    required this.truncated,
    required this.content,
  });

  factory BuildLogChunk.fromJson(Map<String, dynamic> json) {
    return BuildLogChunk(
      releaseId: json['releaseId'] as int,
      state: json['state'] as String,
      exists: json['exists'] as bool? ?? false,
      offset: json['offset'] as int? ?? 0,
      nextOffset: json['nextOffset'] as int? ?? 0,
      size: json['size'] as int? ?? 0,
      truncated: json['truncated'] as bool? ?? false,
      content: json['content'] as String? ?? '',
    );
  }
}