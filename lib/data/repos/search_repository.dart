import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';

/// 搜索（对齐原版 SearchRepository.kt）
class SearchRepository {
  final NeteaseApiClient _client;
  SearchRepository(this._client);

  /// 热搜（type=1111）
  Future<AppResult<List<String>>> hotSearch() async {
    final res = await _client.weapi('/weapi/search/hot', {'type': 1111});
    return res.map(
      success: (data) {
        final list = data['result'] is Map
            ? (data['result'] as Map)['hots']
            : data['hots'];
        return AppResult.success([
          for (final h in (list as List? ?? []))
            if (h is Map) h['first']?.toString() ?? '',
        ].where((s) => s.isNotEmpty).toList(growable: false));
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 联想
  Future<AppResult<List<String>>> suggest(String keyword) async {
    final res = await _client.weapi(
      '/weapi/search/suggest/keyword',
      {'s': keyword},
    );
    return res.map(
      success: (data) {
        final result = data['result'] is Map ? data['result'] as Map : null;
        final list = result?['allMatch'];
        return AppResult.success([
          for (final m in (list as List? ?? []))
            if (m is Map) m['keyword']?.toString() ?? '',
        ].where((s) => s.isNotEmpty).toList(growable: false));
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 综合搜索（单曲失败整体失败；歌单失败降级空）
  Future<AppResult<SearchResult>> search(String keyword) async {
    final res = await _client.weapi(
      '/weapi/cloudsearch/get/web',
      {'s': keyword, 'type': 1, 'offset': 0, 'total': true, 'limit': 30},
    );
    return res.map(
      success: (data) {
        final result = data['result'] is Map ? data['result'] as Map : null;
        final songs = [
          for (final s in ((result?['songs'] as List?) ?? []))
            if (s is Map && Song.parse(s) != null) Song.parse(s)!,
        ];
        // 歌单失败降级空（原版语义）
        List<Playlist> playlists = [];
        try {
          playlists = [
            for (final p in ((result?['playLists'] as List?) ??
                (result?['playlists'] as List?) ??
                []))
              if (p is Map) Playlist.parse(p),
          ];
        } catch (_) {}
        final artists = [
          for (final a in ((result?['artists'] as List?) ?? []))
            if (a is Map) Artist.parse(a),
        ];
        final albums = [
          for (final al in ((result?['albums'] as List?) ?? []))
            if (al is Map) Album.parse(al),
        ];
        return AppResult.success(SearchResult(
          songs: songs,
          playlists: playlists,
          artists: artists,
          albums: albums,
        ));
      },
      failure: (e) => AppResult.failure(e),
    );
  }
}

final searchRepositoryProvider = Provider<SearchRepository>(
  (ref) => SearchRepository(ref.watch(neteaseClientProvider)),
);
