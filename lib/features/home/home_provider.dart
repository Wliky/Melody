import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../data/cache/repo_cache.dart';
import '../../data/models/models.dart';
import '../../data/repos/home_repository.dart';
import '../login/session_controller.dart';

/// 首页数据状态
class HomeState {
  const HomeState({
    this.snapshot,
    this.loading = false,
    this.refreshing = false,
    this.error,
  });
  final HomeSnapshot? snapshot;
  final bool loading;
  final bool refreshing;
  final AppError? error;

  List<BannerItem> get banners => snapshot?.banners ?? const [];
  List<Playlist> get playlists => snapshot?.playlists ?? const [];
}

/// 首页数据流（对齐原版 HomeViewModel）：
/// 先显缓存（10min TTL / SWR）→ 后台刷新 → 下拉强刷
class HomeNotifier extends Notifier<HomeState> {
  static const _cacheKey = 'home:snapshot';
  static const _ttl = Duration(minutes: 10);

  @override
  HomeState build() {
    _load();
    return const HomeState(loading: true);
  }

  Future<void> _load({bool force = false}) async {
    if (!force) {
      // 先显缓存（TTL 内直用；过期 SWR 先给旧值再刷新）
      final cached = await ref.read(repoCacheProvider).readStale<HomeSnapshot>(
            key: _cacheKey,
            ttlMs: _ttl.inMilliseconds,
            fromJson: HomeSnapshot.fromJson,
          );
      if (cached != null) {
        state = HomeState(snapshot: cached);
        if (!_isStaleFresh(cached)) return;
      }
    }

    final res = await ref.read(homeRepositoryProvider).banners();
    final bannerRes = res;
    final playlistRes =
        await ref.read(homeRepositoryProvider).personalizedPlaylists();

    if (bannerRes case Failure(:final error)) {
      state = HomeState(snapshot: state.snapshot, error: error);
      return;
    }
    if (playlistRes case Failure(:final error)) {
      state = HomeState(snapshot: state.snapshot, error: error);
      return;
    }

    final snapshot = HomeSnapshot(
      banners: bannerRes.valueOrNull ?? const [],
      playlists: playlistRes.valueOrNull ?? const [],
      user: ref.read(sessionProvider).user?.id,
    );
    // 落缓存（失败静默）
    ref.read(repoCacheProvider).write(_cacheKey, snapshot.toJson());
    state = HomeState(snapshot: snapshot);
  }

  bool _isStaleFresh(HomeSnapshot snapshot) {
    // 缓存与当前登录态一致且未过期 → 不刷新
    final uid = ref.read(sessionProvider).user?.id;
    return snapshot.user == uid;
  }

  /// 下拉强刷（绕过缓存）
  Future<void> refresh() async {
    state = HomeState(snapshot: state.snapshot, refreshing: true);
    await _load(force: true);
    state = HomeState(snapshot: state.snapshot, refreshing: false);
  }

  /// 登录态变化后强刷
  Future<void> onSessionChanged() => _load(force: true);
}

final homeProvider =
    NotifierProvider<HomeNotifier, HomeState>(HomeNotifier.new);
