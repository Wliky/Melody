import 'package:flutter/material.dart';

/// 设计圆角 token（对齐原版 Shape.kt）。
/// xs=8(Chip/标签) sm=12(封面统一档) md=16(卡片/弹层) lg=24(大卡片/内嵌面板) xl=28(Sheet 顶部)
abstract final class MelodyRadii {
  static const xs = Radius.circular(8);
  static const sm = Radius.circular(12);
  static const md = Radius.circular(16);
  static const lg = Radius.circular(24);
  static const xl = Radius.circular(28);

  static const xsBorder = BorderRadius.all(xs);
  static const smBorder = BorderRadius.all(sm);
  static const mdBorder = BorderRadius.all(md);
  static const lgBorder = BorderRadius.all(lg);
  static const xlBorder = BorderRadius.all(xl);
}
