/// 全 App 唯一错误语义（对齐原版 AppError）。
sealed class AppError {
  const AppError();

  const factory AppError.network({String? message}) = NetworkError;
  const factory AppError.api(int code, String message) = ApiError;
  const factory AppError.parse(String message) = ParseError;
  const factory AppError.unauthorized([String? message]) = UnauthorizedError;
  const factory AppError.unknown(Object? cause) = UnknownError;

  bool get retryable => switch (this) {
        NetworkError _ => true,
        ApiError(:final code) => !_nonRetryableCodes.contains(code),
        _ => false,
      };

  /// 用户可见文案
  String get displayMessage => switch (this) {
        NetworkError(:final message) =>
          message ?? '网络连接不可用，请检查网络后重试',
        ApiError(:final message) => message,
        ParseError() => '数据解析失败',
        UnauthorizedError() => '登录已过期，请重新登录',
        UnknownError() => '出错了，请稍后重试',
      };
}

const _nonRetryableCodes = {301, 400, 401, 403, 405, 406};

/// 网络结果归一化容器（对齐原版 AppResult）
sealed class AppResult<T> {
  const AppResult();
  const factory AppResult.success(T value) = Success<T>;
  const factory AppResult.failure(AppError error) = Failure<T>;

  R map<R>({
    required R Function(T value) success,
    required R Function(AppError error) failure,
  }) =>
      switch (this) {
        Success(:final value) => success(value),
        Failure(:final error) => failure(error),
      };

  T? get valueOrNull => switch (this) {
        Success(:final value) => value,
        Failure() => null,
      };
}

final class Success<T> extends AppResult<T> {
  const Success(this.value);
  final T value;
}

final class Failure<T> extends AppResult<T> {
  const Failure(this.error);
  final AppError error;
}

final class NetworkError extends AppError {
  const NetworkError({this.message});
  final String? message;
}

final class ApiError extends AppError {
  const ApiError(this.code, this.message);
  final int code;
  final String message;
}

final class ParseError extends AppError {
  const ParseError(this.message);
  final String message;
}

final class UnauthorizedError extends AppError {
  const UnauthorizedError([this.message]);
  final String? message;
}

final class UnknownError extends AppError {
  const UnknownError(this.cause);
  final Object? cause;
}
