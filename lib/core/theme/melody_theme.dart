import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'colors.dart';

/// 主题模式：system / light / dark（对齐原版 ThemePreferences）
enum ThemeModeSetting { system, light, dark }

/// 主题设置 Notifier（shared_preferences 持久化）
class ThemeSettings {
  const ThemeSettings({this.mode = ThemeModeSetting.system});
  final ThemeModeSetting mode;

  ThemeSettings copyWith({ThemeModeSetting? mode}) =>
      ThemeSettings(mode: mode ?? this.mode);
}

class ThemeSettingsNotifier extends Notifier<ThemeSettings> {
  static const _key = 'theme_mode';

  @override
  ThemeSettings build() {
    _load();
    return const ThemeSettings();
  }

  Future<void> _load() async {
    final sp = await SharedPreferences.getInstance();
    final raw = sp.getString(_key);
    if (raw == null) return;
    final mode = ThemeModeSetting.values
        .where((e) => e.name == raw)
        .firstOrNull;
    if (mode != null && mode != state.mode) {
      state = ThemeSettings(mode: mode);
    }
  }

  Future<void> setMode(ThemeModeSetting mode) async {
    state = ThemeSettings(mode: mode);
    final sp = await SharedPreferences.getInstance();
    await sp.setString(_key, mode.name);
  }
}

final themeSettingsProvider =
    NotifierProvider<ThemeSettingsNotifier, ThemeSettings>(
  ThemeSettingsNotifier.new,
);

/// 应用主题构建（Monet 动态取色 + 静态回退）
class MelodyTheme {
  static ThemeData light(ColorScheme? dynamic) =>
      _build(dynamic ?? MelodyColors.lightScheme, Brightness.light);

  static ThemeData dark(ColorScheme? dynamic) =>
      _build(dynamic ?? MelodyColors.darkScheme, Brightness.dark);

  static ThemeData _build(ColorScheme scheme, Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor:
          isDark ? scheme.surfaceContainerLowest : scheme.surface,
      appBarTheme: AppBarTheme(
        backgroundColor: isDark
            ? scheme.surfaceContainerLowest
            : scheme.surface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
      ),
      splashFactory: InkSparkle.splashFactory,
    );
  }
}

/// 主题入口 Widget：Monet 动态色 + 模式切换 + 传给 builder
class DynamicThemeBuilder extends ConsumerWidget {
  const DynamicThemeBuilder({super.key, required this.builder});

  final Widget Function(BuildContext, ThemeData, ThemeData) builder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(themeSettingsProvider).mode;
    return DynamicColorBuilder(
      builder: (lightDynamic, darkDynamic) {
        // Android 12+ 提供 dynamic；为 null 时回退静态色板
        final light = lightDynamic ?? MelodyColors.lightScheme;
        final dark = darkDynamic ?? MelodyColors.darkScheme;
        return builder(
          context,
          MelodyTheme.light(light),
          MelodyTheme.dark(dark),
        );
      },
    );
  }
}
