import '../models/models.dart';

/// 播放器直链响应解析（对齐原版 SongJson.kt）：
/// data 数组按 id 对应；url 为 null 表示无版权/VIP。
class SongUrlInfo {
  const SongUrlInfo({required this.songId, this.url, this.size = 0});
  final int songId;
  final String? url;
  final int size;
}

/// 歌单详情（v6 响应聚合：歌单元数据 + 按序曲目 + 收藏态）
class PlaylistDetail {
  const PlaylistDetail({
    required this.playlist,
    required this.songs,
    this.subscribed = false,
  });
  final Playlist playlist;
  final List<Song> songs;
  final bool subscribed;
}

/// 歌单详情解析（v6 响应：playlist.trackIds + privileges 下的 songs）
PlaylistDetail parsePlaylistDetail(Map<String, dynamic> data) {
  final playlistJson = data['playlist'];
  if (playlistJson is! Map) {
    return const PlaylistDetail(
      playlist: Playlist(id: 0, name: '未知歌单'),
      songs: [],
    );
  }
  final playlist = Playlist.parse(playlistJson);

  // 曲目：优先 trackIds 顺序，在 songs 里找对应详情
  final trackIds = <int>[];
  if (playlistJson['trackIds'] is List) {
    for (final t in (playlistJson['trackIds'] as List)) {
      if (t is Map) {
        final id = (t['id'] as num?)?.toInt();
        if (id != null) trackIds.add(id);
      } else if (t is num) {
        trackIds.add(t.toInt());
      }
    }
  }

  final songsById = <int, Song>{};
  for (final entry in [
    if (data['songs'] is List) ...(data['songs'] as List),
    if (playlistJson['tracks'] is List) ...(playlistJson['tracks'] as List),
  ]) {
    if (entry is! Map) continue;
    final song = Song.parse(entry);
    if (song != null) songsById[song.id] = song;
  }

  final songs = <Song>[
    for (final id in trackIds)
      if (songsById[id] != null) songsById[id]!,
  ];

  final subscribed = playlistJson['subscribed'] == true;

  return PlaylistDetail(
    playlist: playlist,
    songs: songs,
    subscribed: subscribed,
  );
}

/// 直链结果：url 为 null 的条目（无版权/VIP）会被播放器自动跳过
List<SongUrlInfo> parseSongUrls(Map<String, dynamic> data) {
  final list = data['data'];
  if (list is! List) return const [];
  return [
    for (final item in list)
      if (item is Map)
        SongUrlInfo(
          songId: (item['id'] as num?)?.toInt() ?? 0,
          url: item['url'] == null ? null : item['url']?.toString(),
          size: (item['size'] as num?)?.toInt() ?? 0,
        ),
  ];
}
