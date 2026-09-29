import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../../core/theme/radii.dart';
import '../../../core/theme/spacing.dart';
import 'session_controller.dart';
import 'qr_login_provider.dart';

/// 二维码登录页（对齐原版 LoginScreen）
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  @override
  void initState() {
    super.initState();
    // 进页探测：cookie 有效直接成功态
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(sessionProvider.notifier).initialize();
      ref.read(qrLoginProvider.notifier).start();
    });
  }

  @override
  void dispose() {
    ref.read(qrLoginProvider.notifier).stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(qrLoginProvider);
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('登录')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(Spacing.xl),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                '扫码登录',
                style: Theme.of(context)
                    .textTheme
                    .headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: Spacing.sm),
              Text(
                '使用网易云 App 扫描二维码',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
              ),
              const SizedBox(height: Spacing.xl),
              _QrPanel(state: state),
              const SizedBox(height: Spacing.lg),
              Text(
                switch (state.phase) {
                  QrPhase.waitingScan => '等待扫描…',
                  QrPhase.waitingConfirm => '请在手机上确认登录',
                  QrPhase.expired => '二维码已过期，点击刷新',
                  QrPhase.success => '登录成功',
                  QrPhase.initial => '正在生成二维码…',
                },
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: state.phase == QrPhase.expired
                          ? scheme.error
                          : scheme.onSurfaceVariant,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _QrPanel extends StatelessWidget {
  const _QrPanel({required this.state});
  final QrLoginState state;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final expired = state.phase == QrPhase.expired;

    Widget panel = Container(
      width: 220,
      height: 220,
      padding: const EdgeInsets.all(Spacing.sm),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: MelodyRadii.lgBorder,
      ),
      child: state.loginUrl.isEmpty
          ? const Center(
              child: SizedBox(
                width: 32,
                height: 32,
                child: CircularProgressIndicator(strokeWidth: 2.5),
              ),
            )
          : QrImageView(
              data: state.loginUrl,
              version: QrVersions.auto,
              backgroundColor: Colors.white,
            ),
    );

    if (expired) {
      panel = Stack(
        alignment: Alignment.center,
        children: [
          Opacity(opacity: 0.25, child: panel),
          FilledButton.icon(
            onPressed: state.onRefresh,
            icon: const Icon(Icons.refresh),
            label: const Text('刷新'),
          ),
        ],
      );
    }

    return GestureDetector(
      onTap: expired ? state.onRefresh : null,
      child: panel,
    );
  }
}
