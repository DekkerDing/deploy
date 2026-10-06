import 'package:flutter/foundation.dart';
import '../../../models/target_env.dart';
import '../../../models/deployment.dart';
import '../../../services/http_client.dart';

class TargetEnvViewModel extends ChangeNotifier {
  final List<TargetEnv> _envs = [];
  List<Deployment> _deployments = [];
  bool _loading = false;
  String? _error;

  List<TargetEnv> get envs => List.unmodifiable(_envs);
  List<Deployment> get deployments => List.unmodifiable(_deployments);
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
    } on ApiException catch (e) {
      _error = e.message;
    } catch (e) {
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
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