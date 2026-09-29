import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';

/// 发现矩阵（对齐原版 DiscoverRepository.kt）
class DiscoverRepository {
  final NeteaseApiClient _client;
  DiscoverRepository(this._client);

  /// 每日推荐
  Future<AppResult<List<Song>>> dailyRecommendSongs() async {
    final res = await _client.weapi('/weapi/v3/discovery/recommend/songs', {
      'limit': 30,
      'offset': 0,
      'total': true,
    });
    return res.map(
      success: (data) {
        final json = data['data'] is Map ? data['data'] as Map : data;
        final list = json['dailySongs'];
        return AppResult.success([
          for (final s in (list as List? ?? []))
            if (s is Map && Song.parse(s) != null) Song.parse(s)!,
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 新歌速递（areaId：0 全部/7 华语/96 欧美/8 日语/16 韩语等）
  Future<AppResult<List<Song>>> newSongs({int areaId = 0}) async {
    final res = await _client.apiGet('/api/v1/discovery/new/songs', {
      'areaId': areaId,
      'limit': 100,
    });
    return res.map(
      success: (data) {
        final list = data['data'];
        return AppResult.success([
          for (final s in (list as List? ?? []))
            if (s is Map && Song.parse(s) != null) Song.parse(s)!,
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 榜单
  Future<AppResult<List<Toplist>>> toplists() async {
    final res = await _client.weapi('/weapi/toplist', {});
    return res.map(
      success: (data) => AppResult.success([
        for (final t in (data['list'] as List? ?? []))
          if (t is Map) Toplist.parse(t),
      ]),
      failure: (e) => AppResult.failure(e),
    );
  }
}

final discoverRepositoryProvider = Provider<DiscoverRepository>(
  (ref) => DiscoverRepository(ref.watch(neteaseClientProvider)),
);
