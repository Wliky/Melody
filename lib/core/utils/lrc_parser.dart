import '../../data/models/models.dart';

/// LRC 歌词解析（对齐原版 LyricRepository.kt 内解析器）：
/// - 多时间戳行全展开（[mm:ss.xx][mm:ss.xx]text）
/// - 空行/元数据行跳过；按 timeMs 排序；毫秒 1/2/3 位自适应
class LrcParser {
  static final _lineRegex = RegExp(
    r'((?:\[\d+:\d+(?:[.:]\d{1,3})?\])+)(.*)$',
  );
  static final _timeRegex = RegExp(r'\[(\d+):(\d+)(?:[.:](\d{1,3}))?\]');

  /// 主文本 + 翻译（相同 timeMs 关联）
  static List<LyricLine> parse(String main, String? translation) {
    final mainLines = _parseSingle(main);
    if (mainLines.isEmpty) return const [];

    final translationByTime = <int, String>{};
    if (translation != null && translation.isNotEmpty) {
      for (final line in _parseSingle(translation)) {
        translationByTime.putIfAbsent(line.timeMs, () => line.text);
      }
    }

    return [
      for (final line in mainLines)
        LyricLine(
          timeMs: line.timeMs,
          text: line.text,
          translation: translationByTime[line.timeMs],
        ),
    ];
  }

  static List<LyricLine> _parseSingle(String raw) {
    final result = <LyricLine>[];
    for (final rawLine in raw.split('\n')) {
      final line = rawLine.trim();
      if (line.isEmpty) continue;
      final match = _lineRegex.firstMatch(line);
      if (match == null) continue; // 元数据行（ti/ar/al 等）跳过

      final text = match.group(2)!.trim();
      for (final tm in _timeRegex.allMatches(match.group(1)!)) {
        final minutes = int.parse(tm.group(1)!);
        final seconds = int.parse(tm.group(2)!);
        final fracRaw = tm.group(3);
        // 1/2/3 位小数自适应到毫秒
        var fracMs = 0;
        if (fracRaw != null) {
          final padded = fracRaw.padRight(3, '0');
          fracMs = int.parse(padded.length > 3 ? padded.substring(0, 3) : padded);
        }
        if (text.isEmpty) continue;
        result.add(LyricLine(
          timeMs: minutes * 60 * 1000 + seconds * 1000 + fracMs,
          text: text,
        ));
      }
    }
    result.sort((a, b) => a.timeMs.compareTo(b.timeMs));
    return result;
  }
}
