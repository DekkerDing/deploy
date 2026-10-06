import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';
import '../../../services/auth_session.dart';
import '../../../services/http_client.dart';

/// 令牌登录页（specs/console-auth）：成功后 AuthSession 通知路由 redirect 回首页。
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _tokenController = TextEditingController();
  bool _obscure = true;
  String? _error;
  bool _loading = false;

  @override
  void dispose() {
    _tokenController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final token = _tokenController.text.trim();
    if (token.isEmpty || _loading) {
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final res = await HttpClient.post('/api/auth/login', body: {'token': token});
      // onLogin 触发 go_router refresh → redirect 回首页（无需手动导航）
      AuthSession.instance.onLogin(res['sessionId'] as String);
    } on ApiException catch (e) {
      // 后端消息可读（"令牌不匹配"/"平台未启用认证…"）
      setState(() => _error = e.message);
    } catch (e) {
      setState(() => _error = '登录失败：$e');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Card(
            child: Container(
              constraints: const BoxConstraints(maxWidth: 380),
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.cloud_sync, size: 48, color: AppTheme.primary),
                  const SizedBox(height: 8),
                  const Text('Deploy Platform', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  const Text('请输入平台令牌登录', style: TextStyle(fontSize: 12, color: Colors.grey)),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _tokenController,
                    obscureText: _obscure,
                    onSubmitted: (_) => _submit(),
                    decoration: InputDecoration(
                      labelText: '平台令牌',
                      hintText: 'deploy.auth.token 配置的令牌',
                      prefixIcon: const Icon(Icons.key),
                      suffixIcon: IconButton(
                        icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                        onPressed: () => setState(() => _obscure = !_obscure),
                      ),
                    ),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(
                      _error!,
                      style: const TextStyle(color: Colors.red, fontSize: 12),
                    ),
                  ],
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: _loading ? null : _submit,
                      child: Text(_loading ? '登录中…' : '登录'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
