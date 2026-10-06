import 'package:flutter/foundation.dart';
import '../../../models/target_env.dart';
import '../../../models/deployment.dart';
import '../../../services/http_client.dart';

class TargetEnvViewModel extends ChangeNotifier {
  final List<TargetEnv> _envs = [];
  List<Deployment> _deployments = [];
  /// 每环境最近一次部署（时间倒序取首个）——健康灯数据源；无记录 = 未部署
  final Map<int, Deployment> _envHealth = {};
  bool _loading = false;
  String? _error;

  List<TargetEnv> get envs => List.unmodifiable(_envs);
  List<Deployment> get deployments => List.unmodifiable(_deployments);
  Map<int, Deployment> get envHealth => Map.unmodifiable(_envHealth);
  bool get loading => _loading;
  String? get error => _error;

  Future<void> loadEnvs() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await HttpClient.get('/api/target-envs');
      _envs
        ..clear()
        ..addAll((data as List).map((e) => TargetEnv.fromJson(e as Map<String, dynamic>)));
      _error = null;
      await loadEnvHealth();
    } on ApiException catch (e) {
      _error = e.message;
    } catch (e) {
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  /// 并行拉每环境最近部署（列表已按时间倒序，取首个即最近一次）
  Future<void> loadEnvHealth() async {
    await Future.wait(_envs.map((e) async {
      try {
        final data = await HttpClient.get('/api/target-envs/${e.id}/deployments');
        final list = data as List;
        if (list.isNotEmpty) {
          _envHealth[e.id] = Deployment.fromJson(list.first as Map<String, dynamic>);
        } else {
          _envHealth.remove(e.id);
        }
      } catch (_) {
        // 单环境失败不影响其余环境的灯
      }
    }));
    notifyListeners();
  }

  Future<void> loadDeployments(int envId) async {
    try {
      final data = await HttpClient.get('/api/target-envs/$envId/deployments');
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

  Future<TargetEnv?> register({
    required String name,
    required String os,
    required String arch,
    String? libc,
    required String runtimeType,
    required String reach,
    String? host,
    int? port,
    String? username,
    String? credential,
    int? jvmVersion,
    int? healthCheckPort,
  }) async {
    _error = null;
    try {
      final data = await HttpClient.post(
        '/api/target-envs',
        body: {
          'name': name,
          'os': os,
          'arch': arch,
          'libc': libc,
          'runtimeType': runtimeType,
          'reach': reach,
          'host': host,
          'port': port,
          'username': username,
          'credential': credential,
          'jvmVersion': jvmVersion,
          'healthCheckPort': healthCheckPort,
        },
      );
      final env = TargetEnv.fromJson(data as Map<String, dynamic>);
      _envs.add(env);
      notifyListeners();
      return env;
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