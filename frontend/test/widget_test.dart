// 最小冒烟测试：应用根组件可构建渲染（替换 flutter create 的计数器脚手架残留）。

import 'package:flutter_test/flutter_test.dart';

import 'package:deploy_platform_web/app.dart';

void main() {
  testWidgets('根组件冒烟渲染', (WidgetTester tester) async {
    await tester.pumpWidget(const DeployPlatformApp());
    expect(find.byType(DeployPlatformApp), findsOneWidget);
  });
}
