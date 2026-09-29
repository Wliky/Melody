import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';

/// 云盘（对齐原版 CloudRepository.kt）
class CloudRepository {
  final NeteaseApiClient _client;
  CloudRepository(this._client);

  /// 云盘歌曲
  Future<AppResult<List<Song>>> cloudSongs({int limit = 200, int offset = 0}) async {
    final res = await _client.weapi(
      '/weapi/v1/cloud/get',
      {'limit': limit, 'offset': offset},
    );
    return res.map(
      success: (data) {
        final list = data['data'];
        return AppResult.success([
          for (final s in (list as List? ?? []))
            if (s is Map && s['simpleSong'] is Map)
              if (Song.parse(s['simpleSong'] as Map) != null)
                Song.parse(s['simpleSong'] as Map)!,
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }
}

/// 播客（对齐原版 PodcastRepository.kt）
class PodcastRepository {
  final NeteaseApiClient _client;
  PodcastRepository(this._client);

  /// 热门电台
  Future<AppResult<List<Podcast>>> hotRadios() async {
    final res = await _client.weapi('/weapi/djradio/recommend/v1', {});
    return res.map(
      success: (data) {
        final list = data['data'] is Map
            ? (data['data'] as Map)['djRadios']
            : data['djRadios'];
        return AppResult.success([
          for (final p in (list as List? ?? []))
            if (p is Map) Podcast.parse(p),
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 电台节目（mainSong）
  Future<AppResult<List<Song>>> programs(int radioId,
      {int limit = 100}) async {
    final res = await _client.weapi(
      '/weapi/dj/program/byradio',
      {'radioId': radioId, 'limit': limit, 'offset': 0},
    );
    return res.map(
      success: (data) {
        final list = data['programs'];
        return AppResult.success([
          for (final p in (list as List? ?? []))
            if (p is Map && p['mainSong'] is Map)
              if (Song.parse(p['mainSong'] as Map) != null)
                Song.parse(p['mainSong'] as Map)!,
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }
}

final cloudRepositoryProvider = Provider<CloudRepository>(
  (ref) => CloudRepository(ref.watch(neteaseClientProvider)),
);

final podcastRepositoryProvider = Provider<PodcastRepository>(
  (ref) => PodcastRepository(ref.watch(neteaseClientProvider)),
);
