import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../app.dart';
import '../../../core/theme/app_theme.dart';
import '../../target_env/viewmodels/target_env_viewmodel.dart';

class TargetEnvListPage extends StatefulWidget {
  const TargetEnvListPage({super.key});

  @override
  State<TargetEnvListPage> createState() => _TargetEnvListPageState();
}

class _TargetEnvListPageState extends State<TargetEnvListPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<TargetEnvViewModel>().loadEnvs();
    });
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<TargetEnvViewModel>();

    return AppScaffold(
      title: '目标环境',
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showRegisterDialog(context),
        icon: const Icon(Icons.add),
        label: const Text('注册环境'),
      ),
      body: RefreshIndicator(
        onRefresh: () => vm.loadEnvs(),
        child: vm.loading
            ? const Center(child: CircularProgressIndicator())
            : vm.error != null
                ? Center(child: Text('加载失败: ${vm.error}'))
                : vm.envs.isEmpty
                    ? const Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.computer, size: 64, color: Colors.grey),
                            SizedBox(height: 12),
                            Text('暂无目标环境，点击右下角按钮注册'),
                          ],
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.all(16),
                        itemCount: vm.envs.length,
                        itemBuilder: (ctx, i) {
                          final env = vm.envs[i];
                          return Card(
                            margin: const EdgeInsets.only(bottom: 12),
                            child: Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Container(
                                        padding: const EdgeInsets.all(10),
                                        decoration: BoxDecoration(
                                          color: AppTheme.accent.withOpacity(0.1),
                                          borderRadius: BorderRadius.circular(8),
                                        ),
                                        child: const Icon(Icons.computer, color: AppTheme.accent),
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(env.name,
                                                style: const TextStyle(
                                                    fontSize: 16, fontWeight: FontWeight.w600)),
                                            const SizedBox(height: 2),
                                            Text(
                                              '${env.os}/${env.arch} · ${env.runtime}',
                                              style: TextStyle(
                                                  fontSize: 13,
                                                  color: Theme.of(context).colorScheme.onSurfaceVariant),
                                            ),
                                          ],
                                        ),
                                      ),
                                      _probeStatusBadge(env.probeStatus),
                                      const SizedBox(width: 6),
                                      _healthBadge(vm.envHealth[env.id]),
                                    ],
                                  ),
                                  const SizedBox(height: 12),
                                  if (env.host != null)
                                    _InfoLine(label: '主机', value: '${env.host}:${env.port ?? ''}'),
                                  _InfoLine(label: '连接方式', value: env.reach),
                                  if (env.jvmVersion != null)
                                    _InfoLine(label: 'JVM', value: 'v${env.jvmVersion}'),
                                  if (env.healthCheckPort != null)
                                    _InfoLine(label: '健康检查端口', value: '${env.healthCheckPort}'),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
      ),
    );
  }

  Widget _probeStatusBadge(String status) {
    final isKnown = status == 'KNOWN';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: (isKnown ? AppTheme.accent : Colors.grey).withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.circle, size: 8, color: isKnown ? AppTheme.accent : Colors.grey),
          const SizedBox(width: 4),
          Text(isKnown ? '可达' : '未知',
              style: TextStyle(
                  fontSize: 11,
                  color: isKnown ? AppTheme.accent : Colors.grey,
                  fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }

  /// 部署健康灯：最近一次部署 SUCCESS=健康 / FAILED=部署失败 / ROLLED_BACK=已回滚 / 无=未部署
  Widget _healthBadge(dynamic last) {
    final Color color = last == null
        ? Colors.grey
        : last.result == 'SUCCESS'
            ? AppTheme.accent
            : last.result == 'FAILED'
                ? AppTheme.danger
                : Colors.purple;
    final String label = last == null
        ? '未部署'
        : last.result == 'SUCCESS'
            ? '健康'
            : last.result == 'FAILED'
                ? '部署失败'
                : '已回滚';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.circle, size: 8, color: color),
          const SizedBox(width: 4),
          Text(label, style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }

  void _showRegisterDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final hostCtrl = TextEditingController();
    final portCtrl = TextEditingController();
    final usernameCtrl = TextEditingController();
    final credentialCtrl = TextEditingController();
    final jvmVersionCtrl = TextEditingController();
    final healthCheckCtrl = TextEditingController();

    String osValue = 'linux';
    String archValue = 'amd64';
    String runtimeValue = 'JVM';
    String reachValue = 'SSH';

    final vm = context.read<TargetEnvViewModel>();

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          title: const Text('注册目标环境'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: '名称 *')),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: osValue,
                        decoration: const InputDecoration(labelText: 'OS *'),
                        items: const [
                          DropdownMenuItem(value: 'linux', child: Text('Linux')),
                          DropdownMenuItem(value: 'windows', child: Text('Windows')),
                          DropdownMenuItem(value: 'darwin', child: Text('macOS')),
                        ],
                        onChanged: (v) => setState(() => osValue = v!),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: archValue,
                        decoration: const InputDecoration(labelText: '架构 *'),
                        items: const [
                          DropdownMenuItem(value: 'amd64', child: Text('AMD64')),
                          DropdownMenuItem(value: 'arm64', child: Text('ARM64')),
                          DropdownMenuItem(value: 'armv7', child: Text('ARMv7')),
                        ],
                        onChanged: (v) => setState(() => archValue = v!),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: runtimeValue,
                        decoration: const InputDecoration(labelText: '运行时 *'),
                        items: const [
                          DropdownMenuItem(value: 'JVM', child: Text('JVM')),
                          DropdownMenuItem(value: 'DOCKER', child: Text('Docker')),
                          DropdownMenuItem(value: 'K8S', child: Text('Kubernetes')),
                          DropdownMenuItem(value: 'NATIVE', child: Text('Native')),
                        ],
                        onChanged: (v) => setState(() => runtimeValue = v!),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: reachValue,
                        decoration: const InputDecoration(labelText: '连接方式 *'),
                        items: const [
                          DropdownMenuItem(value: 'SSH', child: Text('SSH')),
                          DropdownMenuItem(value: 'WINRM', child: Text('WinRM')),
                          DropdownMenuItem(value: 'LOCAL', child: Text('本地')),
                        ],
                        onChanged: (v) => setState(() => reachValue = v!),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(child: TextField(controller: hostCtrl, decoration: const InputDecoration(labelText: '主机'))),
                    const SizedBox(width: 12),
                    SizedBox(
                      width: 120,
                      child: TextField(controller: portCtrl, decoration: const InputDecoration(labelText: '端口'), keyboardType: TextInputType.number),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(child: TextField(controller: usernameCtrl, decoration: const InputDecoration(labelText: '用户名'))),
                    const SizedBox(width: 12),
                    Expanded(child: TextField(controller: credentialCtrl, decoration: const InputDecoration(labelText: '密码/私钥'), obscureText: true)),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(child: TextField(controller: jvmVersionCtrl, decoration: const InputDecoration(labelText: 'JVM 版本'), keyboardType: TextInputType.number)),
                    const SizedBox(width: 12),
                    Expanded(child: TextField(controller: healthCheckCtrl, decoration: const InputDecoration(labelText: '健康检查端口'), keyboardType: TextInputType.number)),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
            ElevatedButton(
              onPressed: () async {
                final name = nameCtrl.text.trim();
                if (name.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('请填写必填项')),
                  );
                  return;
                }
                Navigator.pop(ctx);
                await vm.register(
                  name: name,
                  os: osValue,
                  arch: archValue,
                  runtimeType: runtimeValue,
                  reach: reachValue,
                  host: hostCtrl.text.trim().isEmpty ? null : hostCtrl.text.trim(),
                  port: int.tryParse(portCtrl.text.trim()),
                  username: usernameCtrl.text.trim().isEmpty ? null : usernameCtrl.text.trim(),
                  credential: credentialCtrl.text.trim().isEmpty ? null : credentialCtrl.text.trim(),
                  jvmVersion: int.tryParse(jvmVersionCtrl.text.trim()),
                  healthCheckPort: int.tryParse(healthCheckCtrl.text.trim()),
                );
              },
              child: const Text('注册'),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoLine extends StatelessWidget {
  final String label;
  final String value;
  const _InfoLine({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(label,
                style: TextStyle(fontSize: 13, color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ),
          Expanded(child: Text(value, style: const TextStyle(fontSize: 13))),
        ],
      ),
    );
  }
}