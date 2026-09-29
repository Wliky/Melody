import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../data/repos/auth_repository.dart';
import 'session_controller.dart';

enum QrPhase { initial, waitingScan, waitingConfirm, expired, success }

class QrLoginState {
  const QrLoginState({
    this.phase = QrPhase.initial,
    this.loginUrl = '',
    this.error,
    this.onRefresh,
  });
  final QrPhase phase;
  final String loginUrl;
  final String? error;
  final VoidCallback? onRefresh;
}

typedef VoidCallback = void Function();

/// QR 登录流（对齐原版 LoginViewModel）：
/// createQrKey → 生成二维码 → 每 2s 轮询（连败 5 次中止）→ 803 授权
class QrLoginController extends Notifier<QrLoginState> {
  Timer? _timer;
  int _failStreak = 0;
  String _key = '';

  @override
  QrLoginState build() {
    ref.onDispose(() => _timer?.cancel());
    return const QrLoginState();
  }

  Future<void> start() async {
    await _createKey();
  }

  Future<void> _createKey() async {
    state = QrLoginState(onRefresh: () => start());
    final res =
        await ref.read(authRepositoryProvider).createQrKey();
    final key = res.valueOrNull;
    if (key == null || key.isEmpty) {
      state = QrLoginState(
        error: switch (res) {
          Failure(:final error) => error.displayMessage,
          _ => '生成失败',
        },
        onRefresh: () => start(),
      );
      return;
    }
    _key = key;
    state = QrLoginState(
      phase: QrPhase.waitingScan,
      loginUrl: 'https://music.163.com/login?codekey=$key',
      onRefresh: () => start(),
    );
    _startPolling();
  }

  void _startPolling() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _poll());
  }

  Future<void> _poll() async {
    if (_key.isEmpty) return;
    final res = await ref.read(authRepositoryProvider).checkQrStatus(_key);
    final phase = res.valueOrNull;
    if (res is Failure) {
      _failStreak++;
      if (_failStreak >= 5) {
        _timer?.cancel(); // 连续失败 5 次中止
      }
      return;
    }
    _failStreak = 0;
    switch (phase) {
      case QrExpired _:
        _timer?.cancel();
        state = _copyWith(phase: QrPhase.expired);
      case QrWaitingScan _:
        state = _copyWith(phase: QrPhase.waitingScan);
      case QrWaitingConfirm _:
        state = _copyWith(phase: QrPhase.waitingConfirm);
      case QrAuthorized _:
        _timer?.cancel();
        state = _copyWith(phase: QrPhase.success);
        // 捕获 MUSIC_U 后拉取用户 + 触发全局刷新
        await ref.read(sessionProvider.notifier).onLoginSuccess();
      case null:
        break;
    }
  }

  QrLoginState _copyWith({QrPhase? phase}) => QrLoginState(
        phase: phase ?? state.phase,
        loginUrl: state.loginUrl,
        onRefresh: () => start(),
      );

  void stop() {
    _timer?.cancel();
  }
}

final qrLoginProvider =
    NotifierProvider<QrLoginController, QrLoginState>(QrLoginController.new);
