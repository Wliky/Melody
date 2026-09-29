import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../data/models/models.dart';
import '../data/repos/song_repository.dart';

/// 会话快照 + 播放偏好（对齐原版 PlaybackPreferences.kt）：
/// - 5s 节流落盘；队列截断 300 首；不存直链（短时效签名）
/// - 恢复不起播：只还原 UI，点播放重新取链续播
class PlaybackPreferences {
  static const _keyQueue = 'queue_json';
  static const _keyIndex = 'index';
  static const _keyPosition = 'position_ms';
  static const _keyDuration = 'duration_ms';
  static const _keyShuffle = 'shuffle';
  static const _keyRepeat = 'repeat_mode';
  static const _keySpeed = 'playback_speed';
  static const _throttle = Duration(seconds: 5);

  Timer? _timer;
  PlayerSession? _pending;

  Future<void> saveSession(PlayerSession session) {
    _pending = session;
    _timer?.cancel();
    // 5s 节流：最后一次触发后统一落盘
    _timer = Timer(_throttle, _flush);
    return Future.value();
  }

  Future<void> _flush() async {
    final session = _pending;
    if (session == null) return;
    _pending = null;
    try {
      final sp = await SharedPreferences.getInstance();
      // 队列截断 300 首（防超大歌单 SP 撑爆）
      final queue = session.queue.length > 300
          ? session.queue.sublist(session.queue.length - 300)
          : session.queue;
      await sp.setString(_keyQueue, jsonEncode([
            for (final s in queue) s.toJson(),
          ]));
      await sp.setInt(_keyIndex, session.index);
      await sp.setInt(_keyPosition, session.positionMs);
      await sp.setInt(_keyDuration, session.durationMs);
      await sp.setBool(_keyShuffle, session.shuffle);
      await sp.setString(_keyRepeat, session.repeatMode.name);
    } catch (_) {
      // 落盘失败静默
    }
  }

  Future<PlayerSession?> restoreSession() async {
    try {
      final sp = await SharedPreferences.getInstance();
      final raw = sp.getString(_keyQueue);
      if (raw == null || raw.isEmpty) return null;
      final list = jsonDecode(raw) as List;
      final queue = [
        for (final s in list) Song.fromJson(Map<String, dynamic>.from(s as Map)),
      ];
      if (queue.isEmpty) return null;
      return PlayerSession(
        queue: queue,
        index: sp.getInt(_keyIndex) ?? 0,
        positionMs: sp.getInt(_keyPosition) ?? 0,
        durationMs: sp.getInt(_keyDuration) ?? 0,
        shuffle: sp.getBool(_keyShuffle) ?? false,
        repeatMode: _repeatFromName(sp.getString(_keyRepeat)),
      );
    } catch (_) {
      return null;
    }
  }

  static RepeatMode _repeatFromName(String? name) =>
      RepeatMode.values.where((e) => e.name == name).firstOrNull ??
      RepeatMode.all;

  Future<void> clear() async {
    _timer?.cancel();
    _pending = null;
    try {
      final sp = await SharedPreferences.getInstance();
      await sp.remove(_keyQueue);
      await sp.remove(_keyIndex);
      await sp.remove(_keyPosition);
      await sp.remove(_keyDuration);
      await sp.remove(_keyShuffle);
      await sp.remove(_keyRepeat);
    } catch (_) {}
  }

  // ---- 倍速 ----
  Future<double> loadSpeed() async {
    final sp = await SharedPreferences.getInstance();
    return sp.getDouble(_keySpeed) ?? 1.0;
  }

  Future<void> saveSpeed(double speed) async {
    final sp = await SharedPreferences.getInstance();
    await sp.setDouble(_keySpeed, speed);
  }
}

/// 播放会话快照
class PlayerSession {
  const PlayerSession({
    required this.queue,
    required this.index,
    required this.positionMs,
    required this.durationMs,
    required this.shuffle,
    required this.repeatMode,
  });
  final List<Song> queue;
  final int index;
  final int positionMs;
  final int durationMs;
  final bool shuffle;
  final RepeatMode repeatMode;
}

/// 循环模式（对齐原版 RepeatMode）
enum RepeatMode { all, one, off }

final playbackPreferencesProvider =
    Provider<PlaybackPreferences>((ref) => PlaybackPreferences());

/// 音质设置 Notifier（切换音质只影响下一首）
class AudioQualityNotifier extends Notifier<AudioQuality> {
  static const _key = 'audio_quality';

  @override
  AudioQuality build() {
    _load();
    return AudioQuality.exhigh;
  }

  Future<void> _load() async {
    final sp = await SharedPreferences.getInstance();
    final name = sp.getString(_key);
    final q = AudioQuality.fromName(name);
    if (q != state) state = q;
  }

  Future<void> set(AudioQuality q) async {
    state = q;
    final sp = await SharedPreferences.getInstance();
    await sp.setString(_key, q.value);
  }
}

final audioQualityProvider =
    NotifierProvider<AudioQualityNotifier, AudioQuality>(
  AudioQualityNotifier.new,
);
