import 'package:flutter/material.dart';

/// 静态色板回退（Android 12 以下无 Monet 时使用）。
/// 语义色对齐原版 Color.kt 的静态方案：中性蓝紫基底 + Material 3 色彩角色。
class MelodyColors {
  // ---- Seed（蓝紫基调）----
  static const seed = Color(0xFF6750A4);

  // ---- 浅色 scheme ----
  static final lightScheme = ColorScheme.fromSeed(
    seedColor: seed,
  );

  // ---- 深色 scheme ----
  static final darkScheme = ColorScheme.fromSeed(
    seedColor: seed,
    brightness: Brightness.dark,
  );

  // ---- 固定色（深浅色一致的刻意设计）----
  /// 二维码底色（始终白底黑码，扫描兼容性）
  static const qrBackground = Color(0xFFFFFFFF);

  /// 二维码前景
  static const qrForeground = Color(0xFF000000);

  /// 播放页沉浸式黑遮罩（封面取色容器上的歌词提示 chip 底）
  static const immersiveOverlay = Color(0x9E000000);
}
