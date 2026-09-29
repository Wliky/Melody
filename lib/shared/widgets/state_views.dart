import 'package:flutter/material.dart';

import '../../core/network/app_error.dart';
import '../../core/theme/size.dart';
import '../../core/theme/spacing.dart';

/// 空态视图（对齐原版 EmptyState）
class EmptyStateView extends StatelessWidget {
  const EmptyStateView({
    super.key,
    this.icon = Icons.inbox_outlined,
    required this.message,
    this.hint,
  });

  final IconData icon;
  final String message;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(Spacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 56, color: scheme.onSurfaceVariant),
            const SizedBox(height: Spacing.md),
            Text(
              message,
              style: Theme.of(context)
                  .textTheme
                  .bodyLarge
                  ?.copyWith(color: scheme.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            if (hint != null) ...[
              const SizedBox(height: Spacing.xs),
              Text(
                hint!,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant.withValues(alpha: 0.7),
                    ),
                textAlign: TextAlign.center,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 错误态视图（对齐原版 ErrorState：可重试才显示重试按钮）
class ErrorStateView extends StatelessWidget {
  const ErrorStateView({
    super.key,
    required this.error,
    this.onRetry,
  });

  final AppError error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(Spacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              error.retryable
                  ? Icons.wifi_off_outlined
                  : Icons.error_outline,
              size: 56,
              color: scheme.error,
            ),
            const SizedBox(height: Spacing.md),
            Text(
              error.displayMessage,
              style: Theme.of(context)
                  .textTheme
                  .bodyLarge
                  ?.copyWith(color: scheme.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            if (error.retryable && onRetry != null) ...[
              const SizedBox(height: Spacing.lg),
              FilledButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('重试'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 加载态（对齐原版 LoadingState）
class LoadingStateView extends StatelessWidget {
  const LoadingStateView({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(child: CircularProgressIndicator());
  }
}

/// AppResult 五态视图（加载/成功由 builder / 失败重试 / 空）
class ResultView<T> extends StatelessWidget {
  const ResultView({
    super.key,
    required this.result,
    required this.builder,
    this.onRetry,
    this.emptyBuilder,
    this.isEmpty,
  });

  final AppResult<T> result;
  final Widget Function(T value) builder;
  final VoidCallback? onRetry;
  final Widget Function(T value)? emptyBuilder;
  final bool Function(T value)? isEmpty;

  @override
  Widget build(BuildContext context) {
    return result.map(
      success: (value) {
        if (isEmpty?.call(value) ?? false) {
          return emptyBuilder?.call(value) ??
              const EmptyStateView(message: '这里空空如也');
        }
        return builder(value);
      },
      failure: (e) => ErrorStateView(error: e, onRetry: onRetry),
    );
  }
}
