import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../../app.dart';
import '../../../core/routes/app_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../project/viewmodels/project_viewmodel.dart';
import '../../target_env/viewmodels/target_env_viewmodel.dart';

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<ProjectViewModel>().loadProjects();
      context.read<TargetEnvViewModel>().loadEnvs();
    });
  }

  @override
  Widget build(BuildContext context) {
    final projectVM = context.watch<ProjectViewModel>();
    final envVM = context.watch<TargetEnvViewModel>();

    return AppScaffold(
      title: '仪表盘',
      body: RefreshIndicator(
        onRefresh: () async {
          await Future.wait([
            projectVM.loadProjects(),
            envVM.loadEnvs(),
          ]);
        },
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            _buildHeader(),
            const SizedBox(height: 24),
            _buildStatsCards(projectVM, envVM),
            const SizedBox(height: 24),
            _buildQuickActions(context),
            const SizedBox(height: 24),
            _buildRecentProjects(projectVM),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Deploy Platform',
          style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 4),
        Text(
          'Serverless 部署平台 · 前后端一体化',
          style: TextStyle(
            fontSize: 14,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }

  Widget _buildStatsCards(ProjectViewModel projectVM, TargetEnvViewModel envVM) {
    return Wrap(
      spacing: 16,
      runSpacing: 16,
      children: [
        _StatCard(
          icon: Icons.folder,
          title: '项目',
          value: projectVM.loading ? '...' : '${projectVM.projects.length}',
          color: AppTheme.primary,
        ),
        _StatCard(
          icon: Icons.computer,
          title: '目标环境',
          value: envVM.loading ? '...' : '${envVM.envs.length}',
          color: AppTheme.accent,
        ),
        _StatCard(
          icon: Icons.cloud_upload,
          title: '构建中',
          value: '0',
          color: AppTheme.warning,
        ),
        _StatCard(
          icon: Icons.check_circle,
          title: '已部署',
          value: '0',
          color: AppTheme.primaryLight,
        ),
      ],
    );
  }

  Widget _buildQuickActions(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '快捷操作',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _ActionButton(
                    icon: Icons.add,
                    label: '新建项目',
                    onTap: () => context.go(AppRoutes.projects),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _ActionButton(
                    icon: Icons.add_location_alt,
                    label: '注册环境',
                    onTap: () => context.go(AppRoutes.targetEnvs),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRecentProjects(ProjectViewModel projectVM) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '项目列表',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                TextButton(
                  onPressed: () => context.go(AppRoutes.projects),
                  child: const Text('查看全部'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (projectVM.loading)
              const Center(child: CircularProgressIndicator())
            else if (projectVM.error != null)
              Center(child: Text('加载失败: ${projectVM.error}'))
            else if (projectVM.projects.isEmpty)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: Column(
                    children: [
                      Icon(Icons.folder_open, size: 48, color: Colors.grey),
                      SizedBox(height: 8),
                      Text('暂无项目，点击上方"新建项目"开始'),
                    ],
                  ),
                ),
              )
            else
              ...projectVM.projects.take(5).map((p) => ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTheme.primary.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.folder, color: AppTheme.primary),
                    ),
                    title: Text(p.name),
                    subtitle: Text('${p.buildType} · ${p.sourcePath}'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => context.go('${AppRoutes.projects}/${p.id}'),
                  )),
          ],
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String value;
  final Color color;

  const _StatCard({
    required this.icon,
    required this.title,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 200,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, color: color, size: 24),
              ),
              const SizedBox(height: 12),
              Text(value, style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
              const SizedBox(height: 2),
              Text(title, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            ],
          ),
        ),
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppTheme.primary.withOpacity(0.1),
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Column(
            children: [
              Icon(icon, color: AppTheme.primary, size: 28),
              const SizedBox(height: 6),
              Text(label, style: const TextStyle(fontWeight: FontWeight.w500)),
            ],
          ),
        ),
      ),
    );
  }
}