import 'dart:async';
import 'dart:math';

import 'package:audio_session/audio_session.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

import '../core/network/app_error.dart';
import '../data/models/models.dart';
import '../data/repos/song_repository.dart';
import 'playback_preferences.dart';

/// 队列状态（对齐原版 PlayerQueue.kt 的 mutableStateOf 集合）
class QueueState {
  const QueueState({
    this.queue = const [],
    this.index = -1,
    this.isPlaying = false,
    this.resolving = false,
    this.positionMs = 0,
    this.durationMs = 0,
    this.shuffle = false,
    this.repeatMode = RepeatMode.all,
    this.error,
    this.likedSongIds = const {},
    this.sessionRestored = false,
  });

  final List<Song> queue;
  final int index;
  final bool isPlaying;
  final bool resolving;
  final int positionMs;
  final int durationMs;
  final bool shuffle;
  final RepeatMode repeatMode;
  final AppErrorLike? error;
  final Set<int> likedSongIds;
  /// 快照已恢复（UI 显示占位但不起播）
  final bool sessionRestored;

  Song? get current =>
      index >= 0 && index < queue.length ? queue[index] : null;

  bool get hasQueue => queue.isNotEmpty;

  QueueState copyWith({
    List<Song>? queue,
    int? index,
    bool? isPlaying,
    bool? resolving,
    int? positionMs,
    int? durationMs,
    bool? shuffle,
    RepeatMode? repeatMode,
    Object? error = _sentinel,
    Set<int>? likedSongIds,
    bool? sessionRestored,
  }) =>
      QueueState(
        queue: queue ?? this.queue,
        index: index ?? this.index,
        isPlaying: isPlaying ?? this.isPlaying,
        resolving: resolving ?? this.resolving,
        positionMs: positionMs ?? this.positionMs,
        durationMs: durationMs ?? this.durationMs,
        shuffle: shuffle ?? this.shuffle,
        repeatMode: repeatMode ?? this.repeatMode,
        error: identical(error, _sentinel) ? this.error : error as AppErrorLike?,
        likedSongIds: likedSongIds ?? this.likedSongIds,
        sessionRestored: sessionRestored ?? this.sessionRestored,
      );

  static const _sentinel = Object();
}

/// 错误占位（避免 player 层依赖网络层错误类型的循环引用）
typedef AppErrorLike = Object;

/// 播放器队列状态机（对齐原版 PlayerQueue 467 行核心逻辑）：
///
/// 1. 现取直链 + 逐首跳过：URL null（无版权/VIP）自动跳下一首，连试 20 首停
/// 2. 客户端队列状态机：ALL/ONE/OFF + shuffle；上一首进度>3s 回本首开头；
///    ONE 模式手动切歌仍真切歌
/// 3. 会话快照懒恢复：重启只还原 UI 不发声，点播放重取链续播
/// 4. 500ms 进度采样 + delta 才写状态
/// 5. 红心乐观更新 + 失败回滚
class PlayerQueueNotifier extends Notifier<QueueState> {
  AudioPlayer? _player;
  StreamSubscription? _completedSub;
  StreamSubscription? _errorSub;
  StreamSubscription? _positionSub;
  StreamSubscription? _noisySub;
  Timer? _saveTimer;
  final _random = Random();

  /// 连试跳过上限（对齐原版 20）
  static const _maxSkipAttempts = 20;

  @override
  QueueState build() {
    ref.onDispose(() {
      _completedSub?.cancel();
      _errorSub?.cancel();
      _positionSub?.cancel();
      _noisySub?.cancel();
      _saveTimer?.cancel();
      _player?.dispose();
    });
    return const QueueState();
  }

  AudioPlayer _ensurePlayer() {
    if (_player != null) return _player!;
    final player = AudioPlayer();
    _player = player;

    // 播完事件 → 自动连播（ONE 模式 seek 回 0，ALL/OFF/shuffle 走队列推进）
    _completedSub = player.playerStateStream.listen((s) {
      if (s.processingState == ProcessingState.completed) {
        _onPlaybackEnded();
      }
    });

    // 错误兜底（原版缺失，Flutter 补齐）：失败自动跳下一首
    _errorSub = player.errorStream.listen((_) {
      if (!state.resolving) _skipOnError();
    });

    // 500ms 采样 + delta 写状态
    _positionSub = player.positionStream.listen((pos) {
      final ms = pos.inMilliseconds;
      if ((ms - state.positionMs).abs() >= 400 ||
          (ms == 0 && state.positionMs != 0)) {
        state = state.copyWith(positionMs: ms);
        _scheduleSave();
      }
    });

    // 音频焦点：拔耳机暂停（audio_session 广播，对齐原版策略）
    unawaited(() async {
      final session = await AudioSession.instance;
      _noisySub = session.becomingNoisyEventStream.listen((_) {
        if (state.isPlaying) pause();
      });
    }());

    return player;
  }

  // ---------------------------------------------------------------------
  // 队列操作
  // ---------------------------------------------------------------------

  /// 设置新队列并起播（对齐 playSongList）
  Future<void> playQueue(List<Song> songs, int startIndex) async {
    if (songs.isEmpty) return;
    state = state.copyWith(
      queue: List.of(songs),
      index: startIndex.clamp(0, songs.length - 1),
      error: null,
    );
    await _resolveAndPlay(startPositionMs: 0);
  }

  /// 单曲播放（队列 = [song]）
  Future<void> playSingle(Song song) => playQueue([song], 0);

  /// 插入下一首播放（对齐 playNext 插队语义）
  Future<void> insertNext(Song song) async {
    final queue = List.of(state.queue);
    if (queue.isEmpty) {
      await playSingle(song);
      return;
    }
    queue.insert(state.index + 1, song);
    state = state.copyWith(queue: queue);
  }

  Future<void> playAt(int index) async {
    if (index < 0 || index >= state.queue.length) return;
    state = state.copyWith(index: index, error: null);
    await _resolveAndPlay(startPositionMs: 0);
  }

  // ---------------------------------------------------------------------
  // 播放控制
  // ---------------------------------------------------------------------

  Future<void> togglePlayPause() async {
    if (state.current == null) return;
    if (state.resolving) return; // 解析中禁控制键
    if (state.isPlaying) {
      await pause();
    } else {
      await resume();
    }
  }

  Future<void> pause() async {
    await _player?.pause();
    state = state.copyWith(isPlaying: false);
  }

  Future<void> resume() async {
    if (state.current == null) return;
    final player = _ensurePlayer();
    if (player.audioSource == null) {
      // 会话恢复后的首次播放：重新取链续播
      await _resolveAndPlay(startPositionMs: state.positionMs);
      return;
    }
    await player.play();
    state = state.copyWith(isPlaying: true);
  }

  Future<void> seek(int positionMs) async {
    await _player?.seek(Duration(milliseconds: positionMs));
    state = state.copyWith(positionMs: positionMs);
  }

  Future<void> setSpeed(double speed) async {
    await _player?.setSpeed(speed);
  }

  // ---------------------------------------------------------------------
  // 切歌
  // ---------------------------------------------------------------------

  /// 手动下一首（ONE 模式仍真切歌——对齐原版）
  Future<void> next() => _skip(forward: true, auto: false);

  /// 手动上一首（进度>3s 回本首开头——对齐原版）
  Future<void> previous() async {
    if (state.positionMs > 3000) {
      await seek(0);
      return;
    }
    await _skip(forward: false, auto: false);
  }

  Future<void> _onPlaybackEnded() async {
    if (state.repeatMode == RepeatMode.one) {
      await seek(0);
      await _player?.play();
      return;
    }
    await _skip(forward: true, auto: true);
  }

  Future<void> _skip({required bool forward, required bool auto}) async {
    if (!state.hasQueue) return;
    var nextIndex = _nextIndex(forward: forward, auto: auto);
    if (nextIndex == null) {
      // 顺序越界停（OFF 模式播完）
      await pause();
      state = state.copyWith(positionMs: 0);
      return;
    }
    state = state.copyWith(index: nextIndex, error: null);
    await _resolveAndPlay(startPositionMs: 0);
  }

  /// 计算下一首下标（对齐原版状态机）
  int? _nextIndex({required bool forward, required bool auto}) {
    final queue = state.queue;
    if (queue.isEmpty) return null;

    if (state.shuffle) {
      if (queue.length == 1) return state.index;
      // 随机选非当前
      var candidate = state.index;
      while (candidate == state.index) {
        candidate = _random.nextInt(queue.length);
      }
      return candidate;
    }

    var next = state.index + (forward ? 1 : -1);
    if (next >= queue.length) {
      return state.repeatMode == RepeatMode.off ? null : 0; // 回绕
    }
    if (next < 0) {
      return queue.length - 1; // 回绕
    }
    return next;
  }

  Future<void> _skipOnError() async {
    if (!state.hasQueue) return;
    await _skip(forward: true, auto: true);
  }

  // ---------------------------------------------------------------------
  // 直链解析（现取 + 连试 20 首跳过）
  // ---------------------------------------------------------------------

  Future<void> _resolveAndPlay({required int startPositionMs}) async {
    final song = state.current;
    if (song == null) return;
    final player = _ensurePlayer();
    final quality = ref.read(audioQualityProvider);

    state = state.copyWith(resolving: true, positionMs: startPositionMs, isPlaying: false);
    try {
      var attempt = 0;
      var songId = song.id;
      while (attempt < _maxSkipAttempts) {
        final res = await ref
            .read(songRepositoryProvider)
            .songUrl(songId, quality.value);
        final url = res.valueOrNull;
        if (res case Failure(:final error)) {
          state = state.copyWith(resolving: false, error: error);
          return;
        }
        if (url != null && url.isNotEmpty) {
          await player.setUrl(url, initialPosition: Duration(milliseconds: startPositionMs));
          await player.play();
          state = state.copyWith(resolving: false, isPlaying: true, durationMs: player.duration?.inMilliseconds ?? 0);
          _scheduleSave();
          return;
        }
        // URL null：无版权/VIP → 自动跳下一首（对齐原版）
        attempt++;
        final nextIdx = _nextIndex(forward: true, auto: true);
        if (nextIdx == null || nextIdx == state.index) break;
        state = state.copyWith(index: nextIdx);
        songId = state.current!.id;
      }
      // 连试 20 首失败：停止
      state = state.copyWith(
        resolving: false,
        isPlaying: false,
        error: '连续 ${_maxSkipAttempts} 首无法播放（无版权或 VIP）',
      );
    } catch (e) {
      state = state.copyWith(resolving: false, isPlaying: false, error: e);
    }
  }

  // ---------------------------------------------------------------------
  // 模式切换
  // ---------------------------------------------------------------------

  Future<void> toggleShuffle() async {
    state = state.copyWith(shuffle: !state.shuffle);
    _scheduleSave();
  }

  /// 循环模式循环切换 ALL → ONE → OFF
  Future<void> cycleRepeatMode() async {
    final next = switch (state.repeatMode) {
      RepeatMode.all => RepeatMode.one,
      RepeatMode.one => RepeatMode.off,
      RepeatMode.off => RepeatMode.all,
    };
    state = state.copyWith(repeatMode: next);
    _scheduleSave();
  }

  // ---------------------------------------------------------------------
  // 红心（乐观更新 + 失败回滚）
  // ---------------------------------------------------------------------

  Future<void> toggleLike(int songId) async {
    final liked = Set.of(state.likedSongIds);
    final wasLiked = liked.contains(songId);
    if (wasLiked) {
      liked.remove(songId);
    } else {
      liked.add(songId);
    }
    state = state.copyWith(likedSongIds: liked); // 乐观更新

    final res =
        await ref.read(songRepositoryProvider).likeSong(songId, !wasLiked);
    if (res is Failure) {
      // 回滚
      final rollback = Set.of(state.likedSongIds);
      if (wasLiked) {
        rollback.add(songId);
      } else {
        rollback.remove(songId);
      }
      state = state.copyWith(likedSongIds: rollback, error: (res as Failure).error);
    }
  }

  void setLikedSongIds(Set<int> ids) {
    state = state.copyWith(likedSongIds: ids);
  }

  // ---------------------------------------------------------------------
  // 会话快照（5s 节流 + 截断 300）
  // ---------------------------------------------------------------------

  void _scheduleSave() {
    _saveTimer?.cancel();
    _saveTimer = Timer(const Duration(seconds: 5), () {
      final prefs = ref.read(playbackPreferencesProvider);
      prefs.saveSession(PlayerSession(
        queue: state.queue,
        index: state.index,
        positionMs: state.positionMs,
        durationMs: state.durationMs,
        shuffle: state.shuffle,
        repeatMode: state.repeatMode,
      ));
    });
  }

  /// 冷启动恢复（只还原 UI 不发声）
  Future<void> restoreSession() async {
    if (state.sessionRestored || state.hasQueue) return;
    final session =
        await ref.read(playbackPreferencesProvider).restoreSession();
    if (session == null) return;
    state = state.copyWith(
      queue: session.queue,
      index: session.index.clamp(0, session.queue.length - 1),
      positionMs: session.positionMs,
      durationMs: session.durationMs,
      shuffle: session.shuffle,
      repeatMode: session.repeatMode,
      sessionRestored: true,
    );
  }

  // ---------------------------------------------------------------------
  // 音频会话配置
  // ---------------------------------------------------------------------

  Future<void> configureAudioSession() async {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.music());
  }
}

/// 播放器队列全局供给
final playerQueueProvider =
    NotifierProvider<PlayerQueueNotifier, QueueState>(
  PlayerQueueNotifier.new,
);
