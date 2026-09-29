import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 会话（对齐原版 AuthRepository.kt）
class AuthRepository {
  final NeteaseApiClient _client;
  AuthRepository(this._client);

  /// 生成二维码 key
  Future<AppResult<String>> createQrKey() async {
    final res = await _client.weapi(
      '/weapi/login/qrcode/unikey',
      {'type': 1},
    );
    return res.map(
      success: (data) =>
          AppResult.success(data['unikey']?.toString() ?? ''),
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 轮询二维码状态
  Future<AppResult<QrLoginState>> checkQrStatus(String key) async {
    final res = await _client.weapi(
      '/weapi/login/qrcode/client/login',
      {'key': key, 'type': 1},
    );
    return res.map(
      success: (data) {
        final code = (data['code'] as num?)?.toInt() ?? 801;
        final state = switch (code) {
          800 => QrLoginState.expired,
          802 => QrLoginState.waitingConfirm,
          803 => QrLoginState.authorized,
          _ => QrLoginState.waitingScan,
        };
        return AppResult.success(state);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 当前登录用户
  Future<AppResult<User?>> currentUser() async {
    final res = await _client.weapi('/weapi/nuser/account/get', {});
    return res.map(
      success: (data) => AppResult.success(User.parse(data)),
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 游客注册（下发 MUSIC_A）
  Future<AppResult<void>> registerAnonymous() async {
    final res = await _client.weapi('/weapi/register/anonimous', {});
    return res.map(
      success: (_) => const AppResult.success(null),
      failure: (e) => AppResult.failure(e),
    );
  }
}

/// 二维码登录四态
sealed class QrLoginState {
  const QrLoginState();

  static const expired = QrExpired();
  static const waitingScan = QrWaitingScan();
  static const waitingConfirm = QrWaitingConfirm();
  static const authorized = QrAuthorized();
}

/// 800：已过期（需重新生成）
final class QrExpired extends QrLoginState {
  const QrExpired();
}

/// 801：待扫描
final class QrWaitingScan extends QrLoginState {
  const QrWaitingScan();
}

/// 802：待确认
final class QrWaitingConfirm extends QrLoginState {
  const QrWaitingConfirm();
}

/// 803：已授权（登录成功）
final class QrAuthorized extends QrLoginState {
  const QrAuthorized();
}

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.watch(neteaseClientProvider)),
);
