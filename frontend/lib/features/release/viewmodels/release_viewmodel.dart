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
}