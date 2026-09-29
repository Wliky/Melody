import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';
import 'song_json.dart';

/// 音质档位（对齐原版）
enum AudioQuality {
  standard('standard', '标准'),
  lossless('lossless', '无损'),
  hires('hires', 'Hi-Res'),
  exhigh('exhigh', '极高');

  const AudioQuality(this.value, this.label);
  final String value;
  final String label;

  static AudioQuality fromName(String? name) =>
      values.where((e) => e.value == name).firstOrNull ?? AudioQuality.exhigh;
}

/// 歌曲操作（对齐原版 SongRepository.kt）
class SongRepository {
  final NeteaseApiClient _client;
  SongRepository(this._client);

  /// 取播放直链（level 音质，encodeType=flac）
  Future<AppResult<String?>> songUrl(int songId, String level) async {
    final res = await _client.weapi(
      '/weapi/song/enhance/player/url/v1',
      {
        'ids': '[$songId]',
        'level': level,
        'encodeType': 'flac',
      },
    );
    return res.map(
      success: (data) {
        final urls = parseSongUrls(data);
        return AppResult.success(urls.isEmpty ? null : urls.first.url);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 红心（喜欢）歌曲：
  /// 对齐原版——query 携带 alg/trackId/like，body 的 like 必须是布尔值，
  /// X-Real-IP 固定出口（未登录 301 用于 UI 回滚）
  Future<AppResult<void>> likeSong(int songId, bool like) async {
    final res = await _client.weapi(
      '/weapi/radio/like?alg=itembased&trackId=$songId&like=$like',
      {
        'alg': 'itembased',
        'trackId': songId.toString(),
        'like': like,
        'time': '3',
      },
      extraHeaders: {'X-Real-IP': '116.25.146.100'},
    );
    return res.map(
      success: (_) => const AppResult.success(null),
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 红心列表（uid）
  Future<AppResult<List<int>>> likedSongIds(int uid) async {
    final res = await _client.weapi(
      '/weapi/song/like/get',
      {'uid': uid},
    );
    return res.map(
      success: (data) {
        final ids = data['ids'];
        return AppResult.success([
          if (ids is List)
            for (final id in ids)
              if (id is num) id.toInt(),
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 歌单增删曲目（op: 'add' / 'del'；trackIds 为字符串化 JSON 数组）
  Future<AppResult<void>> manipulatePlaylistTracks({
    required String op,
    required int pid,
    required List<int> trackIds,
  }) async {
    final res = await _client.weapi(
      '/weapi/playlist/manipulate/tracks',
      {
        'op': op,
        'pid': pid,
        'trackIds': '[${trackIds.join(',')}]',
        'imme': 'true',
      },
    );
    return res.map(
      success: (_) => const AppResult.success(null),
      failure: (e) => AppResult.failure(e),
    );
  }
}

final songRepositoryProvider = Provider<SongRepository>(
  (ref) => SongRepository(ref.watch(neteaseClientProvider)),
);
