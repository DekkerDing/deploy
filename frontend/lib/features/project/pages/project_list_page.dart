import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../../app.dart';
import '../../../core/routes/app_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../project/viewmodels/project_viewmodel.dart';

class ProjectListPage extends StatefulWidget {
  const ProjectListPage({super.key});

  @override
  State<ProjectListPage> createState() => _ProjectListPageState();
}

class _ProjectListPageState extends State<ProjectListPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<ProjectViewModel>().loadProjects();
    });
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<ProjectViewModel>();

    return AppScaffold(
      title: '项目管理',
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateDialog(context),
        icon: const Icon(Icons.add),
        label: const Text('新建项目'),
      ),
      body: RefreshIndicator(
        onRefresh: () => vm.loadProjects(),
        child: vm.loading
            ? const Center(child: CircularProgressIndicator())
            : vm.error != null
                ? Center(child: Text('加载失败: ${vm.error}'))
                : vm.projects.isEmpty
                    ? const Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.folder_open, size: 64, color: Colors.grey),
                            SizedBox(height: 12),
                            Text('暂无项目，点击右下角按钮创建'),
                          ],
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.all(16),
                        itemCount: vm.projects.length,
                        itemBuilder: (ctx, i) {
                          final p = vm.projects[i];
                          return Card(
                            margin: const EdgeInsets.only(bottom: 12),
                            child: ListTile(
                              leading: Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: AppTheme.primary.withOpacity(0.1),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: const Icon(Icons.folder, color: AppTheme.primary),
                              ),
                              title: Text(p.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                              subtitle: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const SizedBox(height: 2),
                                  Text('构建: ${p.buildType}'),
                                  Text('路径: ${p.sourcePath}',
                                      style: TextStyle(
                                          fontSize: 12,
                                          color: Theme.of(context).colorScheme.onSurfaceVariant)),
                                ],
                              ),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () => context.go('${AppRoutes.projects}/${p.id}'),
                            ),
                          );
                        },
                      ),
      ),
    );
  }

  void _showCreateDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final pathCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    String selectedType = 'MAVEN';
    final vm = context.read<ProjectViewModel>();

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          title: const Text('新建项目'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: '项目名称*'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: selectedType,
                  decoration: const InputDecoration(labelText: '构建类型*'),
                  items: const [
                    DropdownMenuItem(value: 'MAVEN', child: Text('Maven')),
                    DropdownMenuItem(value: 'GRADLE', child: Text('Gradle')),
                    DropdownMenuItem(value: 'NPM', child: Text('NPM')),
                  ],
                  onChanged: (v) => setState(() => selectedType = v!),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: pathCtrl,
                  decoration: const InputDecoration(labelText: '源码路径*'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: descCtrl,
                  decoration: const InputDecoration(labelText: '描述 (可选)'),
                  maxLines: 2,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
            ElevatedButton(
              onPressed: () async {
                final name = nameCtrl.text.trim();
                final path = pathCtrl.text.trim();
                if (name.isEmpty || path.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('请填写必填项')),
                  );
                  return;
                }
                Navigator.pop(ctx);
                final result = await vm.createProject(
                  name: name,
                  buildType: selectedType,
                  sourcePath: path,
                  description: descCtrl.text.trim().isEmpty ? null : descCtrl.text.trim(),
                );
                if (result != null && context.mounted) {
                  context.go('${AppRoutes.projects}/${result.id}');
                }
              },
              child: const Text('创建'),
            ),
          ],
        ),
      ),
    );
  }
}