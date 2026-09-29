import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme/melody_theme.dart';
import '../router/app_router.dart';

/// 根组件：动态取色 + 三态主题 + 路由
class MelodyApp extends ConsumerWidget {
  const MelodyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(themeSettingsProvider).mode;
    return DynamicThemeBuilder(
      builder: (context, light, dark) => MaterialApp.router(
        title: 'Melody',
        debugShowCheckedModeBanner: false,
        theme: light,
        darkTheme: dark,
        themeMode: switch (mode) {
          ThemeModeSetting.system => ThemeMode.system,
          ThemeModeSetting.light => ThemeMode.light,
          ThemeModeSetting.dark => ThemeMode.dark,
        },
        routerConfig: appRouter,
      ),
    );
  }
}
