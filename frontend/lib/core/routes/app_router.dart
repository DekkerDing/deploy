import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../features/dashboard/pages/dashboard_page.dart';
import '../../features/project/pages/project_list_page.dart';
import '../../features/project/pages/project_detail_page.dart';
import '../../features/release/pages/release_detail_page.dart';
import '../../features/target_env/pages/target_env_list_page.dart';

final class AppRoutes {
  static const home = '/';
  static const projects = '/projects';
  static const projectDetail = '/projects/:id';
  static const releaseDetail = '/releases/:id';
  static const targetEnvs = '/target-envs';
}

final goRouter = GoRouter(
  initialLocation: AppRoutes.home,
  routes: [
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