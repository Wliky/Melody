import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../data/models/models.dart';
import '../../data/repos/home_repository.dart';

/// 歌单详情状态
class PlaylistDetailState {
  const PlaylistDetailState({
    this.playlist = const Playlist(id: 0, name: ''),
    this.songs = const [],
    this.loading = false,
    this.error,
  });
  final Playlist playlist;
  final List<Song> songs;
  final bool loading;
  final AppError? error;
}

/// 歌单详情流（Family：按歌单 id 实例化）
class PlaylistDetailNotifier extends FamilyNotifier<PlaylistDetailState, int> {
  @override
  PlaylistDetailState build(int arg) =>
      const PlaylistDetailState(loading: true);

  Future<void> load() async {
    state = const PlaylistDetailState(loading: true);
    final res = await ref.read(homeRepositoryProvider).playlistDetail(arg);
    if (res case Failure(:final error)) {
      state = PlaylistDetailState(error: error);
      return;
    }
    final detail = res.valueOrNull;
    if (detail == null) {
      state = const PlaylistDetailState();
      return;
    }
    state = PlaylistDetailState(
      playlist: detail.playlist,
      songs: detail.songs,
    );
  }
}

/// 按歌单 id 供给（Riverpod 2 原生 FamilyNotifier）
final playlistDetailProvider =
    NotifierProvider.family<PlaylistDetailNotifier, PlaylistDetailState, int>(
  PlaylistDetailNotifier.new,
);
