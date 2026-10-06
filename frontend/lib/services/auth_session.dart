import 'package:flutter/foundation.dart';

/// 平台会话（specs/console-auth）：
/// - 兼容模式（后端未配置令牌）：[onUnauthorized] 永不被触发，[_authRequired] 恒为 false，
///   路由从不重定向登录页（与 MVP 行为一致）
/// - 启用模式：任意请求收到 401 → needsLogin=true，go_router（refreshListenable）重定向 /login
class AuthSession extends ChangeNotifier {
  AuthSession._();

  static final AuthSession instance = AuthSession._();

  String? _sessionId;

  /// 收到过 401 才认定后端启用了认证，避免兼容模式下误跳登录页
  bool _authRequired = false;

  String? get sessionId => _sessionId;

  bool get needsLogin => _authRequired && _sessionId == null;

  /// 登录成功：记录会话并通知路由刷新（needsLogin → false，redirect 回首页）。
  void onLogin(String sessionId) {
    _sessionId = sessionId;
    _authRequired = true;
    notifyListeners();
  }

  /// 任一请求 401：要求登录（幂等；仅状态变化时通知，避免重复刷新）。
  void onUnauthorized() {
    final changed = !_authRequired || _sessionId != null;
    _authRequired = true;
    _sessionId = null;
    if (changed) {
      notifyListeners();
    }
  }

  /// 退出登录：清会话；启用认证时 redirect 回登录页，兼容模式下留在原页。
  void logout() {
    if (_sessionId == null) {
      return;
    }
    _sessionId = null;
    notifyListeners();
  }
}
