/// 领域模型（对齐原版 Models.kt，手写宽松解析复刻动态 JsonObject 行为）。
library;

// ---------------------------------------------------------------------------
// 用户
// ---------------------------------------------------------------------------

class User {
  const User({
    required this.id,
    required this.nickname,
    this.avatarUrl,
    this.signature,
    this.vipType = 0,
  });

  final int id;
  final String nickname;
  final String? avatarUrl;
  final String? signature;
  final int vipType;

  static User? parse(Map<String, dynamic>? json) {
    if (json == null) return null;
    final profile = json['profile'] is Map ? json['profile'] as Map : null;
    if (profile == null) return null;
    return User(
      id: (profile['userId'] as num?)?.toInt() ?? json['id'] as int? ?? 0,
      nickname: profile['nickname']?.toString() ?? '未知用户',
      avatarUrl: _nullableStr(profile['avatarUrl']),
      signature: _nullableStr(profile['signature']),
      vipType: (profile['vipType'] as num?)?.toInt() ?? 0,
    );
  }
}

// ---------------------------------------------------------------------------
// 歌手 / 专辑
// ---------------------------------------------------------------------------

class Artist {
  const Artist({required this.id, required this.name, this.imageUrl});
  final int id;
  final String name;
  final String? imageUrl;

  static Artist parse(Map json) => Artist(
        id: (json['id'] as num?)?.toInt() ?? 0,
        name: json['name']?.toString() ?? '未知歌手',
        imageUrl: _nullableStr(json['img1v1Url'] ?? json['picUrl']),
      );

  @override
  bool operator ==(Object other) => other is Artist && other.id == id;

  @override
  int get hashCode => id;
}

class Album {
  const Album({required this.id, required this.name, this.coverUrl});
  final int id;
  final String name;
  final String? coverUrl;

  static Album parse(Map json) {
    var name = json['name']?.toString() ?? '未知专辑';
    if (name.isEmpty || name == 'null') name = '未知专辑';
    return Album(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: name,
      coverUrl: _nullableStr(json['picUrl']),
    );
  }
}

// ---------------------------------------------------------------------------
// 歌曲（双形状兼容：简写 ar/al/dt 与全名 artists/album/duration）
// ---------------------------------------------------------------------------

class Song {
  const Song({
    required this.id,
    required this.name,
    required this.artists,
    this.album = const Album(id: 0, name: '未知专辑'),
    this.durationMs = 0,
    this.coverUrl,
  });

  final int id;
  final String name;
  final List<Artist> artists;
  final Album album;
  final int durationMs;
  final String? coverUrl;

  /// 副标题："歌手 - 专辑"
  String get subtitle =>
      '${artists.map((a) => a.name).join('/')} - ${album.name}';

  /// 展示封面：优先歌曲自身，回退专辑
  String? get displayCover => coverUrl ?? album.coverUrl;

  static Song? parse(Map json) {
    final id = (json['id'] as num?)?.toInt();
    if (id == null) return null;
    final name = json['name']?.toString() ?? '未知歌曲';
    if (name.isEmpty || name == 'null') return null;

    // 双形状：ar（简写）或 artists（全名）
    final artists = _parseArtists(json);
    final album = _parseAlbum(json);
    final duration = (json['dt'] as num?)?.toInt() ??
        (json['duration'] as num?)?.toInt() ??
        0;

    return Song(
      id: id,
      name: name,
      artists: artists,
      album: album,
      durationMs: duration,
      coverUrl: _nullableStr(json['al'] is Map
          ? (json['al'] as Map)['picUrl']
          : json['album'] is Map
              ? (json['album'] as Map)['picUrl']
              : json['picUrl']),
    );
  }

  static List<Artist> _parseArtists(Map json) {
    final short = json['ar'];
    if (short is List) {
      return short
          .whereType<Map>()
          .map(Artist.parse)
          .toList(growable: false);
    }
    final full = json['artists'];
    if (full is List) {
      return full
          .whereType<Map>()
          .map(Artist.parse)
          .toList(growable: false);
    }
    return const [];
  }

  static Album _parseAlbum(Map json) {
    final short = json['al'];
    if (short is Map) return Album.parse(short);
    final full = json['album'];
    if (full is Map) return Album.parse(full);
    return const Album(id: 0, name: '未知专辑');
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'artists': [
          for (final a in artists) {'id': a.id, 'name': a.name}
        ],
        'album': {'id': album.id, 'name': album.name},
        'durationMs': durationMs,
      };

  static Song fromJson(Map<String, dynamic> json) => Song(
        id: json['id'] as int,
        name: json['name'] as String,
        artists: [
          for (final a in (json['artists'] as List))
            Artist(
              id: (a as Map)['id'] as int,
              name: (a as Map)['name'] as String,
            ),
        ],
        album: Album(
          id: (json['album'] as Map)['id'] as int,
          name: (json['album'] as Map)['name'] as String,
        ),
        durationMs: json['durationMs'] as int? ?? 0,
      );

  @override
  bool operator ==(Object other) => other is Song && other.id == id;

  @override
  int get hashCode => id;
}

// ---------------------------------------------------------------------------
// 歌单
// ---------------------------------------------------------------------------

class Playlist {
  const Playlist({
    required this.id,
    required this.name,
    this.coverUrl,
    this.description,
    this.trackCount = 0,
    this.playCount = 0,
    this.specialType = 0,
    this.userId,
    this.creatorNickname,
    this.creatorAvatarUrl,
    this.tags = const [],
  });

  final int id;
  final String name;
  final String? coverUrl;
  final String? description;
  final int trackCount;
  final int playCount;
  /// specialType=5 为红心歌单
  final int specialType;
  final int? userId;
  final String? creatorNickname;
  final String? creatorAvatarUrl;
  final List<String> tags;

  bool get isFavorite => specialType == 5;

  static Playlist parse(Map json) {
    final creator = json['creator'];
    return Playlist(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '未知歌单',
      coverUrl: _nullableStr(json['coverImgUrl'] ?? json['picUrl']),
      description: _nullableStr(json['description']),
      trackCount: (json['trackCount'] as num?)?.toInt() ?? 0,
      playCount: (json['playCount'] as num?)?.toInt() ??
          (json['playcount'] as num?)?.toInt() ??
          0,
      specialType: (json['specialType'] as num?)?.toInt() ?? 0,
      userId: (creator is Map ? (creator['userId'] as num?)?.toInt() : null) ??
          (json['userId'] as num?)?.toInt(),
      creatorNickname: creator is Map
          ? creator['nickname']?.toString()
          : json['creatorNickname']?.toString(),
      creatorAvatarUrl:
          creator is Map ? _nullableStr(creator['avatarUrl']) : null,
      tags: [
        if (json['tags'] is List)
          for (final t in (json['tags'] as List)) t?.toString() ?? '',
      ].where((s) => s.isNotEmpty).toList(growable: false),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'coverUrl': coverUrl,
        'trackCount': trackCount,
        'playCount': playCount,
        'specialType': specialType,
        'tags': tags,
      };

  static Playlist fromJson(Map<String, dynamic> json) => Playlist(
        id: json['id'] as int,
        name: json['name'] as String,
        coverUrl: json['coverUrl'] as String?,
        trackCount: json['trackCount'] as int? ?? 0,
        playCount: json['playCount'] as int? ?? 0,
        specialType: json['specialType'] as int? ?? 0,
        tags: (json['tags'] as List?)?.cast<String>() ?? const [],
      );
}

// ---------------------------------------------------------------------------
// 榜单 / 播客
// ---------------------------------------------------------------------------

class Toplist {
  const Toplist({
    required this.id,
    required this.name,
    this.coverUrl,
    this.updateFrequency,
  });
  final int id;
  final String name;
  final String? coverUrl;
  final String? updateFrequency;

  static Toplist parse(Map json) => Toplist(
        id: (json['id'] as num?)?.toInt() ?? 0,
        name: json['name']?.toString() ?? '未知榜单',
        coverUrl: _nullableStr(json['coverImgUrl'] ?? json['picUrl']),
        updateFrequency: _nullableStr(json['updateFrequency']),
      );
}

class Podcast {
  const Podcast({
    required this.id,
    required this.name,
    this.coverUrl,
    this.programCount = 0,
  });
  final int id;
  final String name;
  final String? coverUrl;
  final int programCount;

  static Podcast parse(Map json) => Podcast(
        id: (json['id'] as num?)?.toInt() ?? 0,
        name: json['name']?.toString() ?? '未知播客',
        coverUrl: _nullableStr(json['coverUrl'] ?? json['picUrl']),
        programCount: (json['programCount'] as num?)?.toInt() ?? 0,
      );
}

// ---------------------------------------------------------------------------
// 评论
// ---------------------------------------------------------------------------

class Comment {
  const Comment({
    required this.commentId,
    required this.userNickname,
    this.userAvatarUrl,
    this.content,
    this.timeLabel,
    this.likedCount = 0,
    this.liked = false,
  });
  final int commentId;
  final String userNickname;
  final String? userAvatarUrl;
  final String? content;
  final String? timeLabel;
  final int likedCount;
  final bool liked;

  static Comment? parse(Map json) {
    final id = (json['commentId'] as num?)?.toInt();
    if (id == null) return null;
    return Comment(
      commentId: id,
      userNickname:
          json['user'] is Map ? (json['user'] as Map)['nickname']?.toString() ?? '未知用户' : '未知用户',
      userAvatarUrl:
          json['user'] is Map ? _nullableStr((json['user'] as Map)['avatarUrl']) : null,
      content: json['content']?.toString(),
      timeLabel: json['timeStr']?.toString(),
      likedCount: (json['likedCount'] as num?)?.toInt() ?? 0,
      liked: json['liked'] == true,
    );
  }
}

/// 评论排序
enum CommentSort { recommendation, hottest, latest }

/// 评论页（游标分页）
class CommentPage {
  const CommentPage({
    required this.comments,
    required this.hasMore,
    this.totalCount = 0,
  });
  final List<Comment> comments;
  final bool hasMore;
  final int totalCount;
}

// ---------------------------------------------------------------------------
// 听歌排行 / 用户详情
// ---------------------------------------------------------------------------

class PlayRecordEntry {
  const PlayRecordEntry({required this.song, required this.playCount});
  final Song song;
  final int playCount;

  static PlayRecordEntry? parse(Map json) {
    final song = json['song'] is Map ? Song.parse(json['song'] as Map) : null;
    if (song == null) return null;
    return PlayRecordEntry(
      song: song,
      playCount: (json['playCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class UserDetail {
  const UserDetail({
    required this.userId,
    required this.nickname,
    this.avatarUrl,
    this.level = 0,
    this.listenDays = 0,
    this.follows = 0,
    this.fans = 0,
  });
  final int userId;
  final String nickname;
  final String? avatarUrl;
  final int level;
  final int listenDays;
  final int follows;
  final int fans;

  static UserDetail? parse(Map<String, dynamic>? json) {
    if (json == null) return null;
    final profile = json['profile'] is Map ? json['profile'] as Map : null;
    if (profile == null) return null;
    return UserDetail(
      userId: (profile['userId'] as num?)?.toInt() ?? 0,
      nickname: profile['nickname']?.toString() ?? '未知用户',
      avatarUrl: _nullableStr(profile['avatarUrl']),
      level: (json['level'] as num?)?.toInt() ?? 0,
      listenDays: (json['listenSongs'] as num?)?.toInt() ?? 0,
      follows: (profile['follows'] as num?)?.toInt() ?? 0,
      fans: (profile['followeds'] as num?)?.toInt() ?? 0,
    );
  }
}

// ---------------------------------------------------------------------------
// 歌词
// ---------------------------------------------------------------------------

class LyricLine {
  const LyricLine({
    required this.timeMs,
    required this.text,
    this.translation,
  });
  final int timeMs;
  final String text;
  final String? translation;
}

// ---------------------------------------------------------------------------
// Banner
// ---------------------------------------------------------------------------

class BannerItem {
  const BannerItem({
    required this.imageUrl,
    required this.targetType,
    required this.targetId,
    this.title,
  });
  final String? imageUrl;
  /// 1=歌曲 10=专辑 1000=歌单 3000=外链
  final int targetType;
  final int? targetId;
  final String? title;

  static BannerItem? parse(Map json) {
    final img = json['imageUrl'] ?? json['pic'];
    final type = (json['targetType'] as num?)?.toInt();
    if (img == null || type == null) return null;
    return BannerItem(
      imageUrl: _nullableStr(img),
      targetType: type,
      targetId: (json['targetId'] as num?)?.toInt(),
      title: _nullableStr(json['typeTitle']),
    );
  }
}

// ---------------------------------------------------------------------------
// 搜索
// ---------------------------------------------------------------------------

class SearchResult {
  const SearchResult({
    this.songs = const [],
    this.playlists = const [],
    this.artists = const [],
    this.albums = const [],
  });
  final List<Song> songs;
  final List<Playlist> playlists;
  final List<Artist> artists;
  final List<Album> albums;

  bool get isEmpty =>
      songs.isEmpty && playlists.isEmpty && artists.isEmpty && albums.isEmpty;
}

// ---------------------------------------------------------------------------
// 会话事件
// ---------------------------------------------------------------------------

sealed class SessionEvent {
  const SessionEvent();
}

final class SessionLoggedIn extends SessionEvent {
  const SessionLoggedIn(this.user);
  final User user;
}

final class SessionLoggedOut extends SessionEvent {
  const SessionLoggedOut();
}

// ---------------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------------

String? _nullableStr(Object? value) {
  if (value == null) return null;
  final s = value.toString();
  return (s.isEmpty || s == 'null') ? null : s;
}
