import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';

/// 用户（对齐原版 UserRepository.kt）
class UserRepository {
  final NeteaseApiClient _client;
  UserRepository(this._client);

  /// 听歌排行（type 0=全部 / 1=本周，limit 100）
  Future<AppResult<List<PlayRecordEntry>>> playRecord(int uid,
      {int type = 0, int limit = 100}) async {
    final res = await _client.weapi(
      '/weapi/v1/play/record',
      {'uid': uid, 'type': type, 'limit': limit},
    );
    return res.map(
      success: (data) {
        final list = data['list'];
        return AppResult.success([
          for (final e in (list as List? ?? []))
            if (e is Map && PlayRecordEntry.parse(e) != null)
              PlayRecordEntry.parse(e)!,
        ]);
      },
      failure: (e) => AppResult.failure(e),
    );
  }

  /// 用户详情
  Future<AppResult<UserDetail?>> userDetail(int uid) async {
    final res = await _client.apiGet('/api/v1/user/detail/$uid', {});
    return res.map(
      success: (data) => AppResult.success(UserDetail.parse(data)),
      failure: (e) => AppResult.failure(e),
    );
  }
}

final userRepositoryProvider = Provider<UserRepository>(
  (ref) => UserRepository(ref.watch(neteaseClientProvider)),
);
