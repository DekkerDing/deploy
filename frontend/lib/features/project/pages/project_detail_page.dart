import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import 'package:go_router/go_router.dart';
import '../../../app.dart';
import '../../../core/routes/app_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../models/project.dart';
import '../../project/viewmodels/project_viewmodel.dart';
import '../../release/viewmodels/release_viewmodel.dart';

class ProjectDetailPage extends StatefulWidget {
  final int projectId;
  const ProjectDetailPage({super.key, required this.projectId});

  @override
  State<ProjectDetailPage> createState() => _ProjectDetailPageState();
}

class _ProjectDetailPageState extends State<ProjectDetailPage> {
  Project? _project;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final projectVM = context.read<ProjectViewModel>();
    await projectVM.loadProjects();
    _project = projectVM.projects.where((p) => p.id == widget.projectId).firstOrNull;
    if (mounted) {
      context.read<ReleaseViewModel>().loadByProject(widget.projectId);
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    final releaseVM = context.watch<ReleaseViewModel>();

    return AppScaffold(
      title: _project?.name ?? '项目详情',
      body: (_project == null)
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadData,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _buildInfoCard(_project!),
                  const SizedBox(height: 16),
                  _buildReleasesSection(releaseVM),
                ],
              ),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateReleaseDialog(context),
        icon: const Icon(Icons.add),
        label: const Text('发布新版本'),
      ),
    );
  }

  Widget _buildInfoCard(Project p) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.primary.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.folder, color: AppTheme.primary, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(p.name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 4),
                      Text(p.buildType, style: TextStyle(color: AppTheme.primaryLight)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (p.description != null && p.description!.isNotEmpty) ...[
              Text(p.description!),
              const SizedBox(height: 12),
            ],
            _InfoRow(label: '源码路径', value: p.sourcePath),
            _InfoRow(
              label: '创建时间',
              value: DateFormat('yyyy-MM-dd HH:mm:ss').format(p.createdAt),
            ),
            _InfoRow(
              label: '更新时间',
              value: DateFormat('yyyy-MM-dd HH:mm:ss').format(p.updatedAt),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReleasesSection(ReleaseViewModel vm) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('发布历史', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            if (vm.loading)
              const Center(child: Padding(
                padding: EdgeInsets.all(24),
                child: CircularProgressIndicator(),
              ))
            else if (vm.error != null)
              Center(child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text('加载失败: ${vm.error}'),
              ))
            else if (vm.releases.isEmpty)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: Column(
                    children: [
                      Icon(Icons.inbox, size: 48, color: Colors.grey),
                      SizedBox(height: 8),
                      Text('暂无发布记录'),
                    ],
                  ),
                ),
              )
            else
              ...vm.releases.map((r) => ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: _stateColor(r.state).withOpacity(0.15),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Icon(_stateIcon(r.state), color: _stateColor(r.state), size: 20),
                    ),
                    title: Text('v${r.version}'),
                    subtitle: Text(
                      DateFormat('yyyy-MM-dd HH:mm').format(r.createdAt),
                      style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
                    ),
                    trailing: Chip(
                      label: Text(_stateLabel(r.state), style: const TextStyle(fontSize: 12)),
                      backgroundColor: _stateColor(r.state).withOpacity(0.15),
                      labelStyle: TextStyle(color: _stateColor(r.state)),
                      side: BorderSide.none,
                    ),
                    onTap: () => context.go('${"/releases"}/${r.id}'),
                  )),
          ],
        ),
      ),
    );
  }

  void _showCreateReleaseDialog(BuildContext context) {
    final ctrl = TextEditingController();
    final vm = context.read<ReleaseViewModel>();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('创建发布'),
        content: TextField(
          controller: ctrl,
          decoration: const InputDecoration(labelText: '版本号 *', hintText: '如 1.0.0'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          ElevatedButton(
            onPressed: () async {
              final v = ctrl.text.trim();
              if (v.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('请输入版本号')),
                );
                return;
              }
              Navigator.pop(ctx);
              final release = await vm.createRelease(projectId: widget.projectId, version: v);
              if (release != null && context.mounted) {
                context.go('${"/releases"}/${release.id}');
              }
            },
            child: const Text('创建'),
          ),
        ],
      ),
    );
  }

  IconData _stateIcon(String state) {
    switch (state) {
      case 'CREATED':
        return Icons.new_label;
      case 'BUILDING':
        return Icons.build;
      case 'BUILT':
        return Icons.archive;
      case 'DEPLOYING':
        return Icons.cloud_upload;
      case 'DEPLOYED':
        return Icons.check_circle;
      case 'FAILED':
        return Icons.error;
      case 'ROLLED_BACK':
        return Icons.undo;
      default:
        return Icons.help;
    }
  }

  Color _stateColor(String state) {
    switch (state) {
      case 'CREATED':
        return Colors.grey;
      case 'BUILDING':
        return AppTheme.warning;
      case 'BUILT':
        return AppTheme.primaryLight;
      case 'DEPLOYING':
        return Colors.deepOrange;
      case 'DEPLOYED':
        return AppTheme.accent;
      case 'FAILED':
        return AppTheme.danger;
      case 'ROLLED_BACK':
        return Colors.purple;
      default:
        return Colors.grey;
    }
  }

  String _stateLabel(String state) {
    switch (state) {
      case 'CREATED':
        return '已创建';
      case 'BUILDING':
        return '构建中';
      case 'BUILT':
        return '构建完成';
      case 'DEPLOYING':
        return '部署中';
      case 'DEPLOYED':
        return '已部署';
      case 'FAILED':
        return '失败';
      case 'ROLLED_BACK':
        return '已回滚';
      default:
        return state;
    }
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 80,
            child: Text(label, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}