import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/models.dart';

/// 页面快照缓存（对齐原版 RepoCache.kt）：
/// 进页先显缓存 → TTL 内直用 → 过期 SWR → 下拉强刷。
/// 内存 Map + SP 存 JSON（原版 DataStore 文件）。
class RepoCache {
  final Map<String, _Entry> _memory = {};

  Future<T?> read<T>({
    required String key,
    required int ttlMs,
    required T Function(Map<String, dynamic>) fromJson,
  }) async {
    final entry = _memory[key];
    if (entry != null && !entry.expired(ttlMs)) {
      return entry.value as T?;
    }
    // 落盘恢复
    try {
      final sp = await SharedPreferences.getInstance();
      final raw = sp.getString('cache:$key');
      if (raw == null) return null;
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final cached = _Entry.fromJson(map);
      _memory[key] = cached;
      return cached.expired(ttlMs) ? null : cached.value as T?;
    } catch (_) {
      return null; // 落盘失败静默
    }
  }

  /// stale-while-revalidate：过期也先给旧值
  Future<T?> readStale<T>({
    required String key,
    required int ttlMs,
    required T Function(Map<String, dynamic>) fromJson,
  }) async {
    final fresh = await read(key: key, ttlMs: ttlMs, fromJson: fromJson);
    if (fresh != null) return fresh;
    final entry = _memory[key];
    return entry?.value as T?;
  }

  Future<void> write(String key, Object? value) async {
    final entry = _Entry(now: DateTime.now().millisecondsSinceEpoch, value: value);
    _memory[key] = entry;
    try {
      final sp = await SharedPreferences.getInstance();
      await sp.setString('cache:$key', jsonEncode(entry.toJson()));
    } catch (_) {}
  }

  /// 清空（登出时调用）
  Future<void> clear() async {
    _memory.clear();
    try {
      final sp = await SharedPreferences.getInstance();
      final keys = sp.getKeys().where((k) => k.startsWith('cache:'));
      for (final k in keys) {
        await sp.remove(k);
      }
    } catch (_) {}
  }
}

class _Entry {
  _Entry({required this.now, required this.value});
  final int now;
  final Object? value;

  bool expired(int ttlMs) =>
      DateTime.now().millisecondsSinceEpoch - now > ttlMs;

  Map<String, dynamic> toJson() => {'now': now, 'value': value};

  _Entry.fromJson(Map<String, dynamic> json)
      : now = json['now'] as int? ?? 0,
        value = json['value'];
}

/// 首页快照模型
class HomeSnapshot {
  const HomeSnapshot({
    required this.banners,
    required this.playlists,
    required this.user,
  });
  final List<BannerItem> banners;
  final List<Playlist> playlists;
  final int? user;

  Map<String, dynamic> toJson() => {
        'banners': [
          for (final b in banners)
            {
              'imageUrl': b.imageUrl,
              'targetType': b.targetType,
              'targetId': b.targetId,
              'title': b.title,
            },
        ],
        'playlists': [for (final p in playlists) p.toJson()],
        'user': user,
      };

  static HomeSnapshot fromJson(Map<String, dynamic> json) => HomeSnapshot(
        banners: [
          for (final b in (json['banners'] as List? ?? []))
            BannerItem(
              imageUrl: (b as Map)['imageUrl'] as String?,
              targetType: b['targetType'] as int,
              targetId: b['targetId'] as int?,
              title: b['title'] as String?,
            ),
        ],
        playlists: [
          for (final p in (json['playlists'] as List? ?? []))
            Playlist.fromJson(Map<String, dynamic>.from(p as Map)),
        ],
        user: json['user'] as int?,
      );
}

/// 「我的」快照模型（按 uid 校验失效）
class MineSnapshot {
  const MineSnapshot({required this.uid, required this.playlists});
  final int uid;
  final List<Playlist> playlists;

  Map<String, dynamic> toJson() =>
      {'uid': uid, 'playlists': [for (final p in playlists) p.toJson()]};

  static MineSnapshot fromJson(Map<String, dynamic> json) => MineSnapshot(
        uid: json['uid'] as int,
        playlists: [
          for (final p in (json['playlists'] as List? ?? []))
            Playlist.fromJson(Map<String, dynamic>.from(p as Map)),
        ],
      );
}

final repoCacheProvider = Provider<RepoCache>((ref) => RepoCache());
