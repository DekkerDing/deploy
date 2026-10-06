class Deployment {
  final int id;
  final int releaseId;
  final int artifactId;
  final int targetEnvId;
  final String result;
  final String? message;
  final DateTime startedAt;
  final DateTime? finishedAt;

  Deployment({
    required this.id,
    required this.releaseId,
    required this.artifactId,
    required this.targetEnvId,
    required this.result,
    this.message,
    required this.startedAt,
    this.finishedAt,
  });

  factory Deployment.fromJson(Map<String, dynamic> json) {
    return Deployment(
      id: json['id'] as int,
      releaseId: json['releaseId'] as int,
      artifactId: json['artifactId'] as int,
      targetEnvId: json['targetEnvId'] as int,
      result: json['result'] as String,
      message: json['message'] as String?,
      startedAt: DateTime.parse(json['startedAt'] as String),
      finishedAt: json['finishedAt'] != null
          ? DateTime.parse(json['finishedAt'] as String)
          : null,
    );
  }
}