import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import 'core/routes/app_router.dart';
import 'core/theme/app_theme.dart';
import 'services/auth_session.dart';
import 'features/project/viewmodels/project_viewmodel.dart';
import 'features/release/viewmodels/release_viewmodel.dart';
import 'features/target_env/viewmodels/target_env_viewmodel.dart';

class DeployPlatformApp extends StatelessWidget {
  const DeployPlatformApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ProjectViewModel()),
        ChangeNotifierProvider(create: (_) => ReleaseViewModel()),
        ChangeNotifierProvider(create: (_) => TargetEnvViewModel()),
      ],
      child: MaterialApp.router(
        title: 'Deploy Platform - Serverless',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: ThemeMode.system,
        routerConfig: goRouter,
      ),
    );
  }
}

class AppScaffold extends StatelessWidget {
  final Widget body;
  final String title;
  final List<Widget>? actions;
  final Widget? floatingActionButton;

  const AppScaffold({
    super.key,
    required this.body,
    required this.title,
    this.actions,
    this.floatingActionButton,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        leading: context.canPop()
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => context.pop(),
              )
            : null,
        actions: actions,
      ),
      drawer: _buildDrawer(context),
      body: body,
      floatingActionButton: floatingActionButton,
    );
  }

  Widget _buildDrawer(BuildContext context) {
    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          DrawerHeader(
            decoration: const BoxDecoration(color: AppTheme.primary),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.end,
              children: const [
                Icon(Icons.cloud_sync, size: 48, color: Colors.white),
                SizedBox(height: 8),
                Text(
                  'Deploy Platform',
                  style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                ),
                Text(
                  'Serverless 部署平台',
                  style: TextStyle(color: Colors.white70, fontSize: 12),
                ),
              ],
            ),
          ),
          ListTile(
            leading: const Icon(Icons.dashboard),
            title: const Text('仪表盘'),
            onTap: () {
              context.go(AppRoutes.home);
              Navigator.pop(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.folder),
            title: const Text('项目管理'),
            onTap: () {
              context.go(AppRoutes.projects);
              Navigator.pop(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.computer),
            title: const Text('目标环境'),
            onTap: () {
              context.go(AppRoutes.targetEnvs);
              Navigator.pop(context);
            },
          ),
          // 已登录（启用认证）时显示退出；兼容模式无会话不显示
          ListenableBuilder(
            listenable: AuthSession.instance,
            builder: (context, _) {
              if (AuthSession.instance.sessionId == null) {
                return const SizedBox.shrink();
              }
              return ListTile(
                leading: const Icon(Icons.logout),
                title: const Text('退出登录'),
                onTap: () {
                  AuthSession.instance.logout();
                  Navigator.pop(context);
                },
              );
            },
          ),
        ],
      ),
    );
  }
}