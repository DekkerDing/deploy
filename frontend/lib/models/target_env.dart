class TargetEnv {
  final int id;
  final String name;
  final String os;
  final String arch;
  final String? libc;
  final String runtime;
  final String reach;
  final String? host;
  final int? port;
  final String? username;
  final String? credential;
  final int? jvmVersion;
  final int? healthCheckPort;
  final String probeStatus;
  final DateTime createdAt;
  final DateTime updatedAt;

  TargetEnv({
    required this.id,
    required this.name,
    required this.os,
    required this.arch,
    this.libc,
    required this.runtime,
    required this.reach,
    this.host,
    this.port,
    this.username,
    this.credential,
    this.jvmVersion,
    this.healthCheckPort,
    required this.probeStatus,
    required this.createdAt,
    required this.updatedAt,
  });

  factory TargetEnv.fromJson(Map<String, dynamic> json) {
    return TargetEnv(
      id: json['id'] as int,
      name: json['name'] as String,
      os: json['os'] as String,
      arch: json['arch'] as String,
      libc: json['libc'] as String?,
      runtime: json['runtimeType'] as String,
      reach: json['reach'] as String,
      host: json['host'] as String?,
      port: json['port'] as int?,
      username: json['username'] as String?,
      credential: json['credential'] as String?,
      jvmVersion: json['jvmVersion'] as int?,
      healthCheckPort: json['healthCheckPort'] as int?,
      probeStatus: json['probeStatus'] as String? ?? 'UNKNOWN',
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'os': os,
        'arch': arch,
        'libc': libc,
        'runtimeType': runtime,
        'reach': reach,
        'host': host,
        'port': port,
        'username': username,
        'credential': credential,
        'jvmVersion': jvmVersion,
        'healthCheckPort': healthCheckPort,
        'probeStatus': probeStatus,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };
}