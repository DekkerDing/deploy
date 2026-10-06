import 'dart:async';

import 'package:flutter/foundation.dart';
import '../../../models/release.dart';
import '../../../models/deployment.dart';
import '../../../services/http_client.dart';

class ReleaseViewModel extends ChangeNotifier {
  final List<Release> _releases = [];
  ReleaseDetail? _detail;
  List<Deployment> _deployments = [];
  bool _loading = false;
  String? _error;

  // ---- 构建日志轮询 ----
  static const Set<String> _logActiveStates = {'CREATED', 'BUILDING'};
  static const int _logMaxChars = 512 * 1024; // 只保留尾部，防超长日志撑爆视图
  static const Duration _logPollInterval = Duration(seconds: 1);

  Timer? _logTimer;
  String _logText = '';
  int _logOffset = 0;
  int _logSize = 0;
  bool _logExists = false;
  bool _logLive = true;

  String get logText => _logText;
  int get logSize => _logSize;
  bool get logExists => _logExists;
  bool get logLive => _logLive;

  List<Release> get releases => List.unmodifiable(_releases);
  ReleaseDetail? get detail => _detail;
  List<Deployment> get deployments => List.unmodifiable(_deployments);
  bool get loading => _loading;
  String? get error => _error;

  Future<void> loadByProject(int projectId) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await HttpClient.get('/api/projects/$projectId/releases');
      _releases
        ..clear()
        ..addAll((data as List).map((e) => Release.fromJson(e as Map<String, dynamic>)));
      _error = null;
    } on ApiException catch (e) {
      _error = e.message;
    } catch (e) {
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> loadDetail(int releaseId) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await HttpClient.get('/api/releases/$releaseId');
      _detail = ReleaseDetail.fromJson(data as Map<String, dynamic>);
      _error = null;
    } on ApiException catch (e) {
      _error = e.message;
    } catch (e) {
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> loadDeployments(int releaseId) async {
    try {
      final data = await HttpClient.get('/api/releases/$releaseId/deployments');
      _deployments = (data as List)
          .map((e) => Deployment.fromJson(e as Map<String, dynamic>))
          .toList();
      notifyListeners();
    } on ApiException catch (e) {
      _error = e.message;
    } catch (e) {
      _error = e.toString();
    }
  }

  Future<Release?> createRelease({
    required int projectId,
    required String version,
  }) async {
    _error = null;
    try {
      final data = await HttpClient.post(
        '/api/projects/$projectId/releases',
        body: {'version': version},
      );
      final release = Release.fromJson(data as Map<String, dynamic>);
      _releases.add(release);
      notifyListeners();
      return release;
    } on ApiException catch (e) {
      _error = e.message;
      notifyListeners();
      return null;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  Future<Release?> triggerBuild(int releaseId) async {
    _error = null;
    try {
      final data = await HttpClient.post('/api/releases/$releaseId/trigger');
      final release = Release.fromJson(data as Map<String, dynamic>);
      final idx = _releases.indexWhere((r) => r.id == releaseId);
      if (idx >= 0) _releases[idx] = release;
      if (_detail != null && _detail!.release.id == releaseId) {
        _detail = ReleaseDetail(
          release: release,
          artifacts: _detail!.artifacts,
          events: _detail!.events,
        );
      }
      notifyListeners();
      return release;
    } on ApiException catch (e) {
      _error = e.message;
      notifyListeners();
      return null;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  Future<Release?> deploy(int releaseId, int targetEnvId) async {
    _error = null;
    try {
      final data = await HttpClient.post(
        '/api/releases/$releaseId/deploy',
        body: {'targetEnvId': targetEnvId},
      );
      final release = Release.fromJson(data as Map<String, dynamic>);
      notifyListeners();
      return release;
    } on ApiException catch (e) {
      _error = e.message;
      notifyListeners();
      return null;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  Future<Release?> rollback(int releaseId, int targetEnvId) async {
    _error = null;
    try {
      final data = await HttpClient.post(
        '/api/releases/$releaseId/rollback',
        body: {'targetEnvId': targetEnvId},
      );
      final release = Release.fromJson(data as Map<String, dynamic>);
      notifyListeners();
      return release;
    } on ApiException catch (e) {
      _error = e.message;
      notifyListeners();
      return null;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    }
  }

  // ---- 构建日志滚动窗（1s 轮询 offset 续读，终态停止） ----

  /// 启动（或重启）日志轮询：重置累积文本，从 offset=0 全量拉起。
  void startLogPolling(int releaseId) {
    stopLogPolling();
    _logText = '';
    _logOffset = 0;
    _logSize = 0;
    _logExists = false;
    _logLive = true;
    _fetchLog(releaseId);
    _logTimer = Timer.periodic(_logPollInterval, (_) => _fetchLog(releaseId));
    notifyListeners();
  }

  void stopLogPolling() {
    _logTimer?.cancel();
    _logTimer = null;
  }

  Future<void> _fetchLog(int releaseId) async {
    try {
      final data = await HttpClient.get(
        '/api/releases/$releaseId/build-log',
        query: {'offset': '$_logOffset'},
      );
      final chunk = BuildLogChunk.fromJson(data as Map<String, dynamic>);
      _logExists = chunk.exists;
      _logSize = chunk.size;
      if (!_logActiveStates.contains(chunk.state)) {
        _logLive = false; // BUILT/FAILED 等后续状态：停止轮询
        stopLogPolling();
      }
      if (chunk.exists && chunk.content.isNotEmpty) {
        _logOffset = chunk.nextOffset;
        _logText += chunk.content;
        if (_logText.length > _logMaxChars) {
          _logText = _logText.substring(_logText.length - _logMaxChars);
        }
      }
      _error = null;
    } on ApiException catch (e) {
      _error = e.message;
    } catch (_) {
      // 单次轮询的瞬时失败不打断周期，下个 tick 重试
    }
    if (hasListeners) notifyListeners();
  }

  @override
  void dispose() {
    _logTimer?.cancel();
    super.dispose();
  }
}