import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_error.dart';
import 'cookie_store.dart';
import 'weapi_crypto.dart';

/// 网易云 API 客户端（对齐原版 NeteaseClient.kt 语义）：
/// - weapi 端点：Form 表单（params + encSecKey）
/// - api 端点：明文 JSON body（经 weapi 加密通道外的简化路径，仅个别端点）
/// - Cookie 注入 / Set-Cookie 捕获 / 错误归一化（全部在拦截器完成）
/// - UA 伪装桌面 Chrome；红心接口带 X-Real-IP
class NeteaseApiClient {
  static const baseUrl = 'https://music.163.com';
  static const _realIp = '116.25.146.100';
  static const _userAgent =
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';

  final Dio dio;
  final CookieStore cookieStore;

  NeteaseApiClient(this.dio, this.cookieStore) {
    dio.options
      ..baseUrl = baseUrl
      ..connectTimeout = const Duration(seconds: 10)
      ..receiveTimeout = const Duration(seconds: 15)
      ..headers['User-Agent'] = _userAgent;

    dio.interceptors.addAll([
      InterceptorsWrapper(
        onRequest: (options, handler) {
          // Cookie 全量注入
          final header = cookieStore.toHeader();
          if (header.isNotEmpty) {
            options.headers['Cookie'] = header;
          }
          // 写接口 csrf 一致性（防 524）
          if (options.data is Map && cookieStore.csrf != null) {
            (options.data as Map)['csrf_token'] = cookieStore.csrf;
          }
          // 红心接口固定出口 IP
          if (options.path.contains('/weapi/radio/like')) {
            options.headers['X-Real-IP'] = _realIp;
          }
          handler.next(options);
        },
        onResponse: (response, handler) {
          final setCookies = response.headers
              .map[HttpHeadersReplacements.setCookieKey];
          if (setCookies != null) {
            for (final sc in setCookies) {
              cookieStore.captureSetCookie(sc);
            }
          }
          handler.next(response);
        },
        onError: (e, handler) {
          handler.next(e); // 归一化在 _unwrap 统一处理
        },
      ),
    ]);
  }

  /// weapi 加密调用（POST Form）
  Future<AppResult<Map<String, dynamic>>> weapi(
    String endpoint,
    Map<String, Object?> payload, {
    Map<String, String>? extraHeaders,
  }) async {
    final body = WeapiCrypto.encrypt(payload);
    try {
      final response = await dio.post<Map>(
        endpoint,
        data: body,
        options: Options(
          contentType: Headers.formUrlEncodedContentType,
          headers: extraHeaders,
        ),
      );
      return _unwrap(response);
    } on DioException catch (e) {
      return AppResult.failure(_mapDioError(e));
    } catch (e) {
      return AppResult.failure(AppError.unknown(e));
    }
  }

  /// api 明文端点（GET）
  Future<AppResult<Map<String, dynamic>>> apiGet(
    String endpoint,
    Map<String, Object?> query,
  ) async {
    try {
      final response = await dio.get<Map>(
        endpoint,
        queryParameters: query,
      );
      return _unwrap(response);
    } on DioException catch (e) {
      return AppResult.failure(_mapDioError(e));
    } catch (e) {
      return AppResult.failure(AppError.unknown(e));
    }
  }

  AppResult<Map<String, dynamic>> _unwrap(Response<Map> response) {
    final data = response.data;
    if (data == null) {
      return AppResult.failure(const AppError.parse('响应为空'));
    }
    final map = Map<String, dynamic>.from(data);
    final code = map['code'];
    if (code is int && code != 200) {
      if (code == 301) {
        return AppResult.failure(const AppError.unauthorized());
      }
      return AppResult.failure(
        AppError.api(code, map['message']?.toString() ?? '服务异常（$code）'),
      );
    }
    return AppResult.success(map);
  }

  AppError _mapDioError(DioException e) {
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.connectionError) {
      return const AppError.network();
    }
    final status = e.response?.statusCode;
    if (status != null) {
      final body = e.response?.data;
      final msg = body is Map
          ? (body['message']?.toString() ?? body['msg']?.toString())
          : null;
      if (status == 401 || _bodyCode(body) == 301) {
        return const AppError.unauthorized();
      }
      return AppError.api(status, msg ?? '服务异常（$status）');
    }
    return AppError.network(message: e.message);
  }

  int? _bodyCode(Object? body) {
    if (body is Map) {
      final c = body['code'];
      if (c is int) return c;
    }
    return null;
  }
}

/// dio 复用 HttpHeaders 常量替代（header name 小写不敏感，拦截器直接使用）
abstract final class HttpHeadersReplacements {
  static const setCookieKey = 'set-cookie';
}

/// Riverpod 供给（全局唯一实例）
final cookieStoreProvider = Provider<CookieStore>((ref) => CookieStore());

final neteaseClientProvider = Provider<NeteaseApiClient>((ref) {
  final dio = Dio();
  final store = ref.watch(cookieStoreProvider);
  return NeteaseApiClient(dio, store);
});
