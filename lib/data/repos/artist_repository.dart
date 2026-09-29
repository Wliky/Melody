import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';

/// 歌手（对齐原版 ArtistRepository.kt：热门歌曲走 cloudsearch type=100）
class ArtistRepository {
  final NeteaseApiClient _client;
  ArtistRepository(this._client);

  /// 歌手热门歌曲
  Future<AppResult<List<Song>>> artistHotSongs(int artistId) async {
    final res = await _client.weapi(
      '/weapi/cloudsearch/get/web',
      {'s': '', 'type': 100, 'id': artistId, 'offset': 0, 'limit': 50},
    );
    return res.map(
      success: (data) {
        final result = data['result'] is Map ? data['result'] as Map : null;
        final songs = [
          for (final s in ((result?['songs'] as List?) ?? []))
            if (s is Map && Song.parse(s) != null) Song.parse(s)!,
        ];
        return AppResult.success(songs);
      },
      failure: (e) => AppResult.failure(e),
    );
  }
}

final artistRepositoryProvider = Provider<ArtistRepository>(
  (ref) => ArtistRepository(ref.watch(neteaseClientProvider)),
);
