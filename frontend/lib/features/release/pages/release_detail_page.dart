import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../../app.dart';
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
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<ReleaseViewModel>().loadDetail(widget.releaseId);
      context.read<ReleaseViewModel>().loadDeployments(widget.releaseId);
      context.read<TargetEnvViewModel>().loadEnvs();
    });
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
                if (state == 'BUILT' || state == 'DEPLOYED' || state == 'ROLLED_BACK')
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
                  subtitle: Text('${_formatSize(a.sizeBytes)} · ${a.sha256.substring(0, 16)}...'),
                  trailing: const Icon(Icons.download),
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
            ...vm.deployments.map<Widget>((d) => ListTile(
                  leading: Icon(
                    d.result == 'SUCCESS' ? Icons.check_circle : Icons.error,
                    color: d.result == 'SUCCESS' ? AppTheme.accent : AppTheme.danger,
                  ),
                  title: Text('部署 #${d.id}'),
                  subtitle: Text(
                    DateFormat('yyyy-MM-dd HH:mm').format(d.startedAt),
                    style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
                  ),
                  trailing: Text(d.result),
                )),
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
                        await releaseVM.deploy(widget.releaseId, env.id);
                        if (context.mounted) {
                          releaseVM.loadDetail(widget.releaseId);
                          releaseVM.loadDeployments(widget.releaseId);
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
                        await releaseVM.rollback(widget.releaseId, env.id);
                        if (context.mounted) {
                          releaseVM.loadDetail(widget.releaseId);
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