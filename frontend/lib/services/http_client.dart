import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/config/app_config.dart';
import 'auth_session.dart';

class ApiException implements Exception {
  final int code;
  final String message;

  ApiException({required this.code, required this.message});

  @override
  String toString() => 'ApiException($code): $message';
}

class HttpClient {
  static final http.Client _client = http.Client();
  static const Duration _timeout = Duration(seconds: 30);

  static String get _baseUrl => AppConfig.resolvedApiBaseUrl;

  static Uri _uri(String path) {
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return Uri.parse(path);
    }
    final base = _baseUrl.endsWith('/')
        ? _baseUrl.substring(0, _baseUrl.length - 1)
        : _baseUrl;
    return Uri.parse('$base$path');
  }

  static Future<Map<String, String>> _buildHeaders() async {
    final headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    // 启用认证时携带会话标记；兼容模式（无会话）不受影响
    final session = AuthSession.instance.sessionId;
    if (session != null) {
      headers['X-Auth-Session'] = session;
    }
    return headers;
  }

  static Future<dynamic> get(
    String path, {
    Map<String, String>? query,
  }) async {
    final uri = query != null ? _uri(path).replace(queryParameters: query) : _uri(path);
    final response = await _client
        .get(uri, headers: await _buildHeaders())
        .timeout(_timeout);
    return _handleResponse(response);
  }

  static Future<dynamic> post(
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final uri = _uri(path);
    final response = await _client
        .post(
          uri,
          headers: await _buildHeaders(),
          body: body != null ? jsonEncode(body) : null,
        )
        .timeout(_timeout);
    return _handleResponse(response);
  }

  static Future<dynamic> put(
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final uri = _uri(path);
    final response = await _client
        .put(
          uri,
          headers: await _buildHeaders(),
          body: body != null ? jsonEncode(body) : null,
        )
        .timeout(_timeout);
    return _handleResponse(response);
  }

  static Future<dynamic> delete(String path) async {
    final uri = _uri(path);
    final response = await _client
        .delete(uri, headers: await _buildHeaders())
        .timeout(_timeout);
    return _handleResponse(response);
  }

  static dynamic _handleResponse(http.Response response) {
    final statusCode = response.statusCode;
    final body = response.body;

    if (statusCode >= 200 && statusCode < 300) {
      if (body.isEmpty) return null;
      try {
        return jsonDecode(body);
      } catch (_) {
        return body;
      }
    }

    // 会话缺失/失效：通知路由跳登录页（登录请求自身的 401 由登录页呈现错误）
    if (statusCode == 401) {
      AuthSession.instance.onUnauthorized();
    }
    String message;
    try {
      final decoded = jsonDecode(body);
      message = decoded['message'] ?? decoded['msg'] ?? body;
    } catch (_) {
      message = body.isEmpty ? '请求失败' : body;
    }
    throw ApiException(code: statusCode, message: message);
  }
}