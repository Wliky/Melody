import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../../core/utils/lrc_parser.dart';
import '../models/models.dart';

/// 歌词（对齐原版 LyricRepository.kt）
class LyricRepository {
  final NeteaseApiClient _client;
  LyricRepository(this._client);

  /// 获取并解析歌词（主文本 + 翻译关联；纯音乐返回空）
  Future<AppResult<List<LyricLine>>> lyric(int songId) async {
    final res = await _client.weapi(
      '/weapi/song/lyric',
      {'id': songId, 'lv': 1, 'kv': 1, 'tv': -1},
    );
    return res.map(
      success: (data) {
        final lrc = data['lrc'] is Map ? data['lrc'] as Map : null;
        final tlyric = data['tlyric'] is Map ? data['tlyric'] as Map : null;
        final main = lrc?['lyric']?.toString() ?? '';
        final translation = tlyric?['lyric']?.toString();
        return AppResult.success(LrcParser.parse(main, translation));
      },
      failure: (e) => AppResult.failure(e),
    );
  }
}

final lyricRepositoryProvider = Provider<LyricRepository>(
  (ref) => LyricRepository(ref.watch(neteaseClientProvider)),
);
