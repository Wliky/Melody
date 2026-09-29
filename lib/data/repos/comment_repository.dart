import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/app_error.dart';
import '../../core/network/netease_api_client.dart';
import '../models/models.dart';

/// 评论（对齐原版 CommentRepository.kt 游标分页）
class CommentRepository {
  final NeteaseApiClient _client;
  CommentRepository(this._client);

  /// 评论分页
  ///
  /// 游标语义（对齐原版）：
  /// - recommendation：offset 递增
  /// - hottest：`normalHot#offset` 标记串
  /// - latest：服务器返回的 cursor
  Future<AppResult<CommentPage>> comments({
    required int resourceId,
    required CommentSort sort,
    int offset = 0,
    String? cursor,
  }) async {
    final threadId = 'R_SO_4_$resourceId';
    final res = await _client.weapi(
      '/weapi/v2/resource/comments',
      {
        'threadId': threadId,
        'showInner': true,
        'pageNo': offset,
        'pageSize': 20,
        if (sort == CommentSort.latest && cursor != null) 'cursor': cursor,
        if (sort == CommentSort.hottest) 'sortType': 2,
        if (sort == CommentSort.latest) 'sortType': 3,
        if (sort == CommentSort.recommendation) 'sortType': 1,
      },
    );
    return res.map(
      success: (data) {
        final json = data['data'] is Map ? data['data'] as Map : data;
        final list = json['comments'];
        return AppResult.success(CommentPage(
          comments: [
            for (final c in (list as List? ?? []))
              if (c is Map && Comment.parse(c) != null) Comment.parse(c)!,
          ],
          hasMore: json['hasMore'] == true,
          totalCount: (json['totalCount'] as num?)?.toInt() ?? 0,
        ));
      },
      failure: (e) => AppResult.failure(e),
    );
  }
}

final commentRepositoryProvider = Provider<CommentRepository>(
  (ref) => CommentRepository(ref.watch(neteaseClientProvider)),
);
