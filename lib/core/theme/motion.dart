import 'package:flutter/animation.dart';

/// 动效 token（对齐原版 MelodyMotion.kt）。
abstract final class MelodyMotion {
  /// 小反馈动画（页点/按压/进度条高度等）全局统一时长
  static const Duration durationShort = Duration(milliseconds: 150);

  /// 常规转场
  static const Duration durationNormal = Duration(milliseconds: 300);

  /// 大范围/强调转场
  static const Duration durationLong = Duration(milliseconds: 450);

  /// M3 强调减速曲线（进场）
  static const Cubic easingEmphasized = Cubic(0.2, 0.0, 0.0, 1.0);

  /// M3 退出曲线（更快离场）
  static const Cubic easingExit = Cubic(0.3, 0.0, 0.8, 0.15);

  /// 列表行按压
  static const double pressedScale = 0.97;

  /// 大按钮按压
  static const double pressedScaleLarge = 0.96;
}

/// 主框架 ↔ 播放页转场时长组（毫秒，对齐原版 PlayerTransitionDurations）。
abstract final class PlayerTransitionDurations {
  static const int exitFadeMain = 180;
  static const int enterSlide = 360;
  static const int enterFade = 220;
  static const int enterFadeMain = 220;
  static const int exitSlide = 320;
  static const int exitFadePlayer = 260;
}
