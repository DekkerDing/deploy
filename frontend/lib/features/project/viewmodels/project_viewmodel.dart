import 'package:flutter/foundation.dart';
import '../../../models/project.dart';
import '../../../services/http_client.dart';

class ProjectViewModel extends ChangeNotifier {
  final List<Project> _projects = [];
  bool _loading = false;
  String? _error;

  List<Project> get projects => List.unmodifiable(_projects);
  bool get loading => _loading;
  String? get error => _error;

  Future<void> loadProjects() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await HttpClient.get('/api/projects');
      _projects
        ..clear()
        ..addAll((data as List).map((e) => Project.fromJson(e as Map<String, dynamic>)));
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

  Future<Project?> createProject({
    required String name,
    required String buildType,
    required String sourcePath,
    String? description,
  }) async {
    _error = null;
    try {
      final data = await HttpClient.post(
        '/api/projects',
        body: {
          'name': name,
          'buildType': buildType,
          'sourcePath': sourcePath,
          if (description != null && description.isNotEmpty)
            'description': description,
        },
      );
      final project = Project.fromJson(data as Map<String, dynamic>);
      _projects.add(project);
      notifyListeners();
      return project;
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