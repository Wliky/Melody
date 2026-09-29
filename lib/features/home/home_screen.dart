import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/radii.dart';
import '../../core/theme/spacing.dart';
import '../../data/models/models.dart';
import '../../shared/widgets/rows.dart';
import '../../shared/widgets/skeleton.dart';
import '../../shared/widgets/state_views.dart';
import '../login/session_controller.dart';
import 'home_provider.dart';

/// 首页（对齐原版 HomeScreen）：
/// Banner 轮播 + 推荐歌单网格 + 骨架 + 下拉刷新
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(homeProvider);
    final session = ref.watch(sessionProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Melody'),
        actions: [
          if (!session.loggedIn)
            TextButton(
              onPressed: () => context.go('/login'),
              child: const Text('登录'),
            ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(homeProvider.notifier).refresh(),
        child: _Body(state: state),
      ),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body({required this.state});
  final HomeState state;

  @override
  Widget build(BuildContext context) {
    if (state.snapshot == null) {
      if (state.error != null) {
        return ListView(
          children: [
            ErrorStateView(error: state.error!, onRetry: () {}),
          ],
        );
      }
      return ListView(
        padding: const EdgeInsets.all(Spacing.screen),
        children: [
          const SkeletonBox(height: 140),
          const SizedBox(height: Spacing.lg),
          const SectionHeader(title: '推荐歌单'),
          const SizedBox(height: Spacing.md),
          GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: 3,
            mainAxisSpacing: Spacing.md,
            crossAxisSpacing: Spacing.md,
            childAspectRatio: 0.72,
            children: List.generate(9, (_) => const PlaylistCardSkeleton()),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(Spacing.screen),
      children: [
        if (state.banners.isNotEmpty) _BannerCarousel(banners: state.banners),
        const SizedBox(height: Spacing.lg),
        const SectionHeader(title: '推荐歌单'),
        const SizedBox(height: Spacing.md),
        if (state.playlists.isEmpty)
          const EmptyStateView(message: '暂无推荐歌单')
        else
          GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: 3,
            mainAxisSpacing: Spacing.md,
            crossAxisSpacing: Spacing.md,
            childAspectRatio: 0.72,
            children: [
              for (final p in state.playlists)
                PlaylistCard(
                  playlist: p,
                  onTap: () => context.go('/playlist/${p.id}'),
                ),
            ],
          ),
      ],
    );
  }
}

/// Banner 轮播（5s 自动翻页）
class _BannerCarousel extends StatefulWidget {
  const _BannerCarousel({required this.banners});
  final List<BannerItem> banners;

  @override
  State<_BannerCarousel> createState() => _BannerCarouselState();
}

class _BannerCarouselState extends State<_BannerCarousel> {
  final _controller = PageController(viewportFraction: 0.92);
  int _page = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (_controller.hasClients) {
        final next = (_page + 1) % widget.banners.length;
        _controller.animateToPage(
          next,
          duration: const Duration(milliseconds: 400),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SizedBox(
          height: 140,
          child: PageView.builder(
            controller: _controller,
            itemCount: widget.banners.length,
            onPageChanged: (i) => setState(() => _page = i),
            itemBuilder: (context, i) => Padding(
              padding: const EdgeInsets.symmetric(horizontal: Spacing.xs),
              child: _BannerCard(banner: widget.banners[i]),
            ),
          ),
        ),
        const SizedBox(height: Spacing.sm),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (var i = 0; i < widget.banners.length; i++)
              AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                margin: const EdgeInsets.symmetric(horizontal: 3),
                width: i == _page ? 16 : 6,
                height: 6,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primary
                      .withValues(alpha: i == _page ? 1 : 0.3),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _BannerCard extends StatelessWidget {
  const _BannerCard({required this.banner});
  final BannerItem banner;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        // targetType 1=歌曲 10=专辑 1000=歌单 3000=外链
        if (banner.targetType == 1000 && banner.targetId != null) {
          context.go('/playlist/${banner.targetId}');
        }
      },
      child: ClipRRect(
        borderRadius: MelodyRadii.mdBorder,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (banner.imageUrl != null)
              CachedNetworkImage(
                imageUrl: banner.imageUrl!,
                fit: BoxFit.cover,
              )
            else
              Container(color: Theme.of(context).colorScheme.surfaceContainerHighest),
            if (banner.title != null)
              Positioned(
                left: Spacing.sm,
                bottom: Spacing.sm,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: Spacing.sm,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.5),
                    borderRadius: MelodyRadii.xsBorder,
                  ),
                  child: Text(
                    banner.title!,
                    style: const TextStyle(color: Colors.white, fontSize: 11),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
