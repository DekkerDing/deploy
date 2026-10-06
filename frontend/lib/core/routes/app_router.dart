import 'package:go_router/go_router.dart';
import '../../services/auth_session.dart';
import '../../features/auth/pages/login_page.dart';
import '../../features/dashboard/pages/dashboard_page.dart';
import '../../features/project/pages/project_list_page.dart';
import '../../features/project/pages/project_detail_page.dart';
import '../../features/release/pages/release_detail_page.dart';
import '../../features/target_env/pages/target_env_list_page.dart';

final class AppRoutes {
  static const home = '/';
  static const login = '/login';
  static const projects = '/projects';
  static const projectDetail = '/projects/:id';
  static const releaseDetail = '/releases/:id';
  static const targetEnvs = '/target-envs';
}

/// 路由守卫（specs/console-auth）：
/// - 收到 401（启用认证且会话缺失/失效）→ 重定向 /login
/// - 兼容模式（认证关闭）下 AuthSession.needsLogin 恒 false，从不重定向
/// - 已登录访问 /login → 回首页
final goRouter = GoRouter(
  initialLocation: AppRoutes.home,
  refreshListenable: AuthSession.instance,
  redirect: (context, state) {
    final auth = AuthSession.instance;
    final loggingIn = state.matchedLocation == AppRoutes.login;
    if (auth.needsLogin && !loggingIn) {
      return AppRoutes.login;
    }
    if (loggingIn && !auth.needsLogin) {
      return AppRoutes.home;
    }
    return null;
  },
  routes: [
    GoRoute(
      path: AppRoutes.login,
      builder: (_, __) => const LoginPage(),
    ),
    GoRoute(
      path: AppRoutes.home,
      builder: (_, __) => const DashboardPage(),
    ),
    GoRoute(
      path: AppRoutes.projects,
      builder: (_, __) => const ProjectListPage(),
    ),
    GoRoute(
      path: AppRoutes.projectDetail,
      builder: (_, state) {
        final id = int.parse(state.pathParameters['id']!);
        return ProjectDetailPage(projectId: id);
      },
    ),
    GoRoute(
      path: AppRoutes.releaseDetail,
      builder: (_, state) {
        final id = int.parse(state.pathParameters['id']!);
        return ReleaseDetailPage(releaseId: id);
      },
    ),
    GoRoute(
      path: AppRoutes.targetEnvs,
      builder: (_, __) => const TargetEnvListPage(),
    ),
  ],
);
