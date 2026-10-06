import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../app.dart';
import '../../../core/config/app_config.dart';
import '../../../core/theme/app_theme.dart';
import '../../release/viewmodels/release_viewmodel.dart';
import '../../target_env/viewmodels/target_env_viewmodel.dart';

class ReleaseDetailPage extends StatefulWidget {
  final int releaseId;
  const ReleaseDetailPage({super.key, required this.releaseId});

  @override
  State<ReleaseDetailPage> createState() => _ReleaseDetailPageState();
}

class _ReleaseDetailPageState extends State<ReleaseDetailPage> {
  final ScrollController _logScroll = ScrollController();
  bool _logFollow = true; // 自动贴底；用户上滚后暂停，滚回底部恢复
  ReleaseViewModel? _releaseVM; // dispose 时停日志轮询用（避免 dispose 中取 context）

  @override
  void initState() {
    super.initState();
    _logScroll.addListener(_onLogScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final vm = context.read<ReleaseViewModel>();
      _releaseVM = vm;
      vm.loadDetail(widget.releaseId);
      vm.loadDeployments(widget.releaseId);
      vm.startLogPolling(widget.releaseId);
      context.read<TargetEnvViewModel>().loadEnvs();
    });
  }

  @override
  void dispose() {
    _logScroll.removeListener(_onLogScroll);
    _logScroll.dispose();
    _releaseVM?.stopLogPolling(); // ViewModel 生命周期长于页面，离开页面必须停轮询
    _releaseVM?.unwatchDetail();
    super.dispose();
  }

  /// 距底部 40px 内视为"跟随态"，离开则暂停自动滚动
  void _onLogScroll() {
    if (!_logScroll.hasClients) return;
    final pos = _logScroll.position;
    final atBottom = pos.pixels >= pos.maxScrollExtent - 40;
    if (atBottom != _logFollow) setState(() => _logFollow = atBottom);
  }

  @override
  Widget build(BuildContext context) {
    final releaseVM = context.watch<ReleaseViewModel>();
    final envVM = context.watch<TargetEnvViewModel>();
    final detail = releaseVM.detail;

    return AppScaffold(
      title: detail != null ? 'v${detail.release.version}' : '发布详情',
      body: releaseVM.loading
          ? const Center(child: CircularProgressIndicator())
          : releaseVM.error != null
              ? Center(child: Text('加载失败: ${releaseVM.error}'))
              : detail == null
                  ? const Center(child: Text('未找到发布记录'))
                  : RefreshIndicator(
                      onRefresh: () async {
                        await Future.wait([
                          releaseVM.loadDetail(widget.releaseId),
                          releaseVM.loadDeployments(widget.releaseId),
                        ]);
                      },
                      child: ListView(
                        padding: const EdgeInsets.all(16),
                        children: [
                          _buildStatusCard(detail),
                          const SizedBox(height: 16),
                          _buildActionsSection(releaseVM, envVM),
                          const SizedBox(height: 16),
                          _buildLogSection(releaseVM),
                          const SizedBox(height: 16),
                          if (detail.artifacts.isNotEmpty) _buildArtifactsSection(detail),
                          const SizedBox(height: 16),
                          if (detail.events.isNotEmpty) _buildEventsSection(detail),
                          const SizedBox(height: 16),
                          if (releaseVM.deployments.isNotEmpty) _buildDeploymentsSection(releaseVM),
                        ],
                      ),
                    ),
    );
  }

  Widget _buildStatusCard(dynamic detail) {
    final release = detail.release;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: _stateColor(release.state).withOpacity(0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(_stateIcon(release.state), color: _stateColor(release.state), size: 32),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('v${release.version}', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Chip(
                    label: Text(_stateLabel(release.state)),
                    backgroundColor: _stateColor(release.state).withOpacity(0.15),
                    labelStyle: TextStyle(color: _stateColor(release.state)),
                    side: BorderSide.none,
                  ),
                  if (release.failReason != null && release.failReason!.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(release.failReason!, style: const TextStyle(color: AppTheme.danger, fontSize: 13)),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActionsSection(ReleaseViewModel releaseVM, TargetEnvViewModel envVM) {
    final detail = releaseVM.detail;
    if (detail == null) return const SizedBox.shrink();
    final state = detail.release.state;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('操作', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                if (state == 'CREATED')
                  ElevatedButton.icon(
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('触发构建'),
                    onPressed: () => releaseVM.triggerBuild(widget.releaseId),
                  ),
                if (state == 'BUILT')
                  ElevatedButton.icon(
                    icon: const Icon(Icons.cloud_upload),
                    label: const Text('部署到目标环境'),
                    onPressed: () => _showDeployDialog(releaseVM, envVM),
                  ),
                if (state == 'DEPLOYED')
                  TextButton.icon(
                    icon: const Icon(Icons.undo),
                    label: const Text('回滚'),
                    onPressed: () => _showRollbackDialog(releaseVM, envVM),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLogSection(ReleaseViewModel vm) {
    // 日志追加触发的重建帧后贴底（仅跟随态；用户上滚时不动）
    if (_logFollow && _logScroll.hasClients) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_logFollow && _logScroll.hasClients) {
          _logScroll.jumpTo(_logScroll.position.maxScrollExtent);
        }
      });
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text('构建日志', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                ),
                if (vm.logLive)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF59E0B).withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Text('● 实时', style: TextStyle(fontSize: 11, color: Color(0xFFF59E0B))),
                  )
                else if (vm.logExists)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppTheme.accent.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Text('已完结', style: TextStyle(fontSize: 11, color: AppTheme.accent)),
                  ),
                const SizedBox(width: 8),
                Text(_formatSize(vm.logSize), style: const TextStyle(fontSize: 12, color: Colors.grey)),
                if (!_logFollow) ...[
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: () {
                      if (_logScroll.hasClients) {
                        _logScroll.jumpTo(_logScroll.position.maxScrollExtent);
                      }
                      setState(() => _logFollow = true);
                    },
                    child: const Text('↓ 最新', style: TextStyle(fontSize: 12, color: AppTheme.primary)),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 12),
            Container(
              height: 280,
              width: double.infinity,
              decoration: BoxDecoration(
                color: const Color(0xFF14171C),
                borderRadius: BorderRadius.circular(10),
              ),
              child: !vm.logExists
                  ? const Center(
                      child: Text('尚未构建——触发构建后此处实时输出日志',
                          style: TextStyle(color: Colors.grey, fontSize: 13)),
                    )
                  : Scrollbar(
                      controller: _logScroll,
                      thumbVisibility: true,
                      child: SingleChildScrollView(
                        controller: _logScroll,
                        child: Padding(
                          padding: const EdgeInsets.all(14),
                          child: SelectableText(
                            vm.logText.isEmpty ? '（等待输出）' : vm.logText,
                            style: const TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 12,
                              height: 1.4,
                              color: Color(0xFFD7DDE5),
                            ),
                          ),
                        ),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildArtifactsSection(dynamic detail) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('制品列表', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...detail.artifacts.map<Widget>((a) => ListTile(
                  leading: const Icon(Icons.insert_drive_file, color: AppTheme.primary),
                  title: Text(a.fileName),
                  subtitle: Text('${_formatSize(a.sizeBytes)} · sha256 ${a.sha256.substring(0, 16)}...'),
                  trailing: const Icon(Icons.download, color: AppTheme.primary),
                  onTap: () => _downloadArtifact(a.id as int),
                )),
          ],
        ),
      ),
    );
  }

  Widget _buildEventsSection(dynamic detail) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('事件历史', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...detail.events.map<Widget>((e) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                      Icon(Icons.circle, size: 8, color: _stateColor(e.toState)),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${e.fromState ?? '-'} → ${e.toState} ${e.message ?? ''}',
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                      Text(
                        DateFormat('MM-dd HH:mm').format(e.createdAt),
                        style: TextStyle(
                          fontSize: 12,
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }

  Widget _buildDeploymentsSection(ReleaseViewModel vm) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('部署历史', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...vm.deployments.map<Widget>((d) {
              final dur = d.finishedAt != null ? d.finishedAt!.difference(d.startedAt).inSeconds : null;
              return ListTile(
                leading: Icon(
                  d.result == 'SUCCESS' ? Icons.check_circle : d.result == 'FAILED' ? Icons.error : Icons.undo,
                  color: d.result == 'SUCCESS'
                      ? AppTheme.accent
                      : d.result == 'FAILED'
                          ? AppTheme.danger
                          : Colors.purple,
                ),
                title: Text('部署 #${d.id}'),
                subtitle: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${DateFormat('yyyy-MM-dd HH:mm').format(d.startedAt)}${dur != null ? ' · 耗时 ${dur}s' : ' · 进行中…'}',
                      style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
                    ),
                    if (d.message != null && d.message!.isNotEmpty)
                      Text(d.message!,
                          style: const TextStyle(fontSize: 11, color: Colors.grey),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis),
                  ],
                ),
                isThreeLine: true,
                trailing: Text(d.result, style: const TextStyle(fontSize: 12)),
              );
            }),
          ],
        ),
      ),
    );
  }

  void _showDeployDialog(ReleaseViewModel releaseVM, TargetEnvViewModel envVM) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('部署到目标环境'),
        content: envVM.envs.isEmpty
            ? const Text('暂无可用的目标环境，请先注册')
            : SizedBox(
                width: 400,
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: envVM.envs.length,
                  itemBuilder: (_, i) {
                    final env = envVM.envs[i];
                    return ListTile(
                      leading: const Icon(Icons.computer),
                      title: Text(env.name),
                      subtitle: Text('${env.os}/${env.arch} · ${env.runtime}'),
                      onTap: () async {
                        Navigator.pop(ctx);
                        final r = await releaseVM.deploy(widget.releaseId, env.id);
                        if (r != null) {
                          releaseVM.watchDetail(widget.releaseId); // 终态自停并刷新部署记录
                        } else if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('部署请求失败: ${releaseVM.error ?? '未知错误'}')),
                          );
                        }
                      },
                    );
                  },
                ),
              ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        ],
      ),
    );
  }

  void _showRollbackDialog(ReleaseViewModel releaseVM, TargetEnvViewModel envVM) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('回滚'),
        content: envVM.envs.isEmpty
            ? const Text('暂无可用的目标环境')
            : SizedBox(
                width: 400,
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: envVM.envs.length,
                  itemBuilder: (_, i) {
                    final env = envVM.envs[i];
                    return ListTile(
                      leading: const Icon(Icons.undo),
                      title: Text(env.name),
                      subtitle: Text('${env.os}/${env.arch}'),
                      onTap: () async {
                        Navigator.pop(ctx);
                        final r = await releaseVM.rollback(widget.releaseId, env.id);
                        if (r != null) {
                          releaseVM.watchDetail(widget.releaseId);
                        } else if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('回滚请求失败: ${releaseVM.error ?? '未知错误'}')),
                          );
                        }
                      },
                    );
                  },
                ),
              ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        ],
      ),
    );
  }

  /// 制品下载：调系统浏览器打开下载端点（同源/已配置 API_BASE_URL 时）
  void _downloadArtifact(int artifactId) async {
    final base = AppConfig.resolvedApiBaseUrl;
    if (base.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('未配置平台地址（--dart-define=API_BASE_URL=...），无法下载')),
      );
      return;
    }
    final url = Uri.parse('$base/api/artifacts/$artifactId/download');
    final ok = await launchUrl(url, mode: LaunchMode.externalApplication);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('无法打开下载: $url')));
    }
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(2)} GB';
  }

  IconData _stateIcon(String state) {
    switch (state) {
      case 'CREATED': return Icons.new_label;
      case 'BUILDING': return Icons.build;
      case 'BUILT': return Icons.archive;
      case 'DEPLOYING': return Icons.cloud_upload;
      case 'DEPLOYED': return Icons.check_circle;
      case 'FAILED': return Icons.error;
      case 'ROLLED_BACK': return Icons.undo;
      default: return Icons.help;
    }
  }

  Color _stateColor(String state) {
    switch (state) {
      case 'CREATED': return Colors.grey;
      case 'BUILDING': return AppTheme.warning;
      case 'BUILT': return AppTheme.primaryLight;
      case 'DEPLOYING': return Colors.deepOrange;
      case 'DEPLOYED': return AppTheme.accent;
      case 'FAILED': return AppTheme.danger;
      case 'ROLLED_BACK': return Colors.purple;
      default: return Colors.grey;
    }
  }

  String _stateLabel(String state) {
    switch (state) {
      case 'CREATED': return '已创建';
      case 'BUILDING': return '构建中';
      case 'BUILT': return '构建完成';
      case 'DEPLOYING': return '部署中';
      case 'DEPLOYED': return '已部署';
      case 'FAILED': return '失败';
      case 'ROLLED_BACK': return '已回滚';
      default: return state;
    }
  }
}