/// 图片 URL 参数化（对齐原版 CoverUrl.kt 语义）：
/// 仅 music.126.net 域追加 ?param={px}y{px}（截断已有 query）；默认 480。
String resizeCoverUrl(String? url, {int px = 480}) {
  if (url == null || url.isEmpty || url == 'null') return '';
  if (!url.contains('music.126.net')) return url;
  final base = url.split('?').first;
  return '$base?param=${px}y$px';
}

/// 毫秒 → "mm:ss"
String formatDuration(int ms) {
  final total = (ms / 1000).floor();
  final m = total ~/ 60;
  final s = total % 60;
  return '$m:${s.toString().padLeft(2, '0')}';
}

/// 播放次数 → 万/亿缩写
String formatPlayCount(int count) {
  if (count >= 100000000) {
    final v = count / 100000000;
    return '${v >= 10 ? v.toStringAsFixed(0) : v.toStringAsFixed(1)}亿';
  }
  if (count >= 10000) {
    final v = count / 10000;
    return '${v >= 10 ? v.toStringAsFixed(0) : v.toStringAsFixed(1)}万';
  }
  return count.toString();
}
