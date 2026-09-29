import 'package:flutter/material.dart';

/// 设计尺寸 token（对齐原版 MelodySize）。
abstract final class MelodySize {
  // 封面档
  static const double coverXs = 40; // 评论头像
  static const double coverS = 48; // 歌曲行封面
  static const double coverM = 56; // 歌单行/播客行封面
  static const double coverL = 72; // 我的头像/歌手头像
  static const double coverXl = 96; // 个人主页大头像

  // 行高
  static const double rowSong = 64;
  static const double rowPlaylist = 72;

  // 图标档
  static const double iconS = 20;
  static const double iconM = 24;
  static const double iconL = 28;
  static const double iconPlay = 36;

  // 其他
  static const double buttonHeight = 48;
  static const double touchMin = 48;
}
