import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/cookie_store.dart';
import '../../core/network/netease_api_client.dart';
import '../../core/network/weapi_crypto.dart';
import '../../data/cache/repo_cache.dart';
import '../../data/models/models.dart';
import '../../data/repos/auth_repository.dart';
import '../../data/repos/song_repository.dart';
import '../../player/player_queue_notifier.dart';

/// 全局会话状态（对齐原版 SessionViewModel + 全局失效引导增强）
class SessionState {
  const SessionState({
    this.user,
    this.isGuest = false,
    this.ready = false,
  });
  final User? user;
  final bool isGuest;
  /// 启动初始化完成（游客注册 / cookie 恢复）
  final bool ready;

  bool get loggedIn => user != null;
}

class SessionController extends Notifier<SessionState> {
  StreamSubscription? _qrSub;

  @override
  SessionState build() {
    ref.onDispose(() => _qrSub?.cancel());
    return const SessionState();
  }

  /// 启动初始化（main 调用一次）：
  /// cookie 恢复 → 种子 → 有 MUSIC_U 则探用户；无登录且无游客则注册 MUSIC_A
  Future<void> initialize() async {
    if (state.ready) return;
    final cookies = ref.read(cookieStoreProvider);
    await cookies.ensureRestored(WeapiCrypto.randomNmtid());

    final auth = ref.read(authRepositoryProvider);
    if (cookies.hasLogin) {
      final res = await auth.currentUser();
      final user = res.valueOrNull;
      if (user != null) {
        state = SessionState(user: user, ready: true);
        _loadLikedIds(user.id);
        return;
      }
    }
    if (!cookies.hasGuest) {
      await auth.registerAnonymous();
    }
    state = const SessionState(isGuest: true, ready: true);
  }

  Future<void> _loadLikedIds(int uid) async {
    final res = await ref.read(songRepositoryProvider).likedSongIds(uid);
    final ids = res.valueOrNull;
    if (ids != null) {
      ref.read(playerQueueProvider.notifier).setLikedSongIds(ids.toSet());
    }
  }

  /// 登录成功（QR 授权后调用）
  Future<void> onLoginSuccess() async {
    final auth = ref.read(authRepositoryProvider);
    final res = await auth.currentUser();
    final user = res.valueOrNull;
    if (user == null) return;
    state = SessionState(user: user, ready: true);
    _loadLikedIds(user.id);
  }

  /// 退出登录（原版逻辑有但未接线，Flutter 补齐）：
  /// 停播 → 清 cookie → 清快照 → LoggedOut
  Future<void> logout() async {
    await ref.read(playerQueueProvider.notifier).pause();
    await ref.read(cookieStoreProvider).clear();
    await ref.read(repoCacheProvider).clear();
    state = const SessionState(ready: true);
    // 重新注册游客态
    await ref.read(authRepositoryProvider).registerAnonymous();
    state = const SessionState(isGuest: true, ready: true);
  }
}

final sessionProvider =
    NotifierProvider<SessionController, SessionState>(SessionController.new);

