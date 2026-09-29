import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';
import 'song_json.dart';

/// 首页（对齐原版 HomeRepository.kt）
class HomeRepository {
  final NeteaseApiClient _client;
  HomeRepository(this._client);

  /// Banner 轮播（明文 api 端点，client=android）
  Future<AppResult<List<BannerItem>>> banners() async {
    final res = await _client.apiGet('/api/v2/banner/get', {'client': 'android'});
    return res.map(
      success: (data) => AppResult.success([
        for (final item in (data['banners'] as List? ?? []))
          if (item is Map && BannerItem.parse(item) != null)
            BannerItem.parse(item)!,
      ]),
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 推荐歌单
  Future<AppResult<List<Playlist>>> personalizedPlaylists() async {
    final res = await _client.weapi('/weapi/personalized/playlist', {});
    return res.map(
      success: (data) => AppResult.success([
        for (final item in (data['result'] as List? ?? []))
          if (item is Map) Playlist.parse(item),
      ]),
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 用户歌单（limit/offset 分页）
  Future<AppResult<List<Playlist>>> userPlaylists(int uid,
      {int limit = 30, int offset = 0}) async {
    final res = await _client.weapi(
      '/weapi/user/playlist',
      {'uid': uid, 'limit': limit, 'offset': offset, 'csrf_token': ''},
    );
    return res.map(
      success: (data) => AppResult.success([
        for (final item in (data['playlist'] as List? ?? []))
          if (item is Map) Playlist.parse(item),
      ]),
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 歌单详情（含曲目）
  Future<AppResult<PlaylistDetail>> playlistDetail(int id) async {
    final res = await _client.weapi(
      '/weapi/v6/playlist/detail',
      {
        'id': id,
        'n': 1000,
        's': 8,
      },
    );
    return res.map(
      success: (data) {
        final detail = parsePlaylistDetail(data);
        if (detail == null) {
          return const AppResult.failure(AppError.parse('歌单详情解析失败'));
        }
        return AppResult.success(detail);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 收藏 / 取消收藏歌单
  Future<AppResult<void>> subscribePlaylist(int id, bool subscribe) async {
    final endpoint =
        subscribe ? '/weapi/playlist/subscribe/1' : '/weapi/playlist/subscribe/2';
    final res = await _client.weapi(endpoint, {'id': id});
    return res.map(
      success: (_) => const AppResult.success(null),
      failure: (e) => AppResult.failure(e),
    );
  }
}

final homeRepositoryProvider = Provider<HomeRepository>(
  (ref) => HomeRepository(ref.watch(neteaseClientProvider)),
);
