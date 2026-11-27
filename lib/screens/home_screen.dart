import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/gallery_controller.dart';
import '../state/selection_controller.dart';
import '../state/shell_navigation.dart';
import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/action_card.dart';
import '../widgets/app_svg.dart';
import '../widgets/formatters.dart';
import '../widgets/media_tile.dart';
import '../widgets/section_label.dart';
import 'story_editor_screen.dart';

/// Home tab: two action buttons followed by every photo and video from the camera roll.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final ScrollController _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_maybeLoadMore);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<GalleryController>().initialize();
    });
  }

  @override
  void dispose() {
    _scroll
      ..removeListener(_maybeLoadMore)
      ..dispose();
    super.dispose();
  }

  void _maybeLoadMore() {
    if (_scroll.hasClients && _scroll.position.extentAfter < 900) {
      context.read<GalleryController>().loadMore();
    }
  }

  void _comingSoon() {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('Coming soon')));
  }

  @override
  Widget build(BuildContext context) {
    final gallery = context.watch<GalleryController>();
    final selection = context.watch<SelectionController>();
    final top = MediaQuery.paddingOf(context).top;

    return Column(
      children: [
        Expanded(
          child: RefreshIndicator(
            onRefresh: gallery.refresh,
            edgeOffset: top + 40,
            child: CustomScrollView(
              controller: _scroll,
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverToBoxAdapter(child: _Header(top: top, onComingSoon: _comingSoon)),
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 20, 16, 0),
                  sliver: SliverToBoxAdapter(
                    child: Row(
                      children: [
                        Expanded(
                          child: ActionCard(
                            label: 'create story',
                            color: AppColors.createButton,
                            badge: selection.count,
                            icon: const Icon(
                              Icons.add_photo_alternate_outlined,
                              color: Colors.white,
                              size: 28,
                            ),
                            onTap: () => openStoryEditor(context),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: ActionCard(
                            label: 'button placeholder',
                            color: AppColors.placeholderButton,
                            icon: const Icon(Icons.dashboard_outlined, color: Colors.white, size: 28),
                            onTap: _comingSoon,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SliverPadding(
                  padding: EdgeInsets.fromLTRB(16, 22, 16, 12),
                  sliver: SliverToBoxAdapter(child: SectionLabel('Your photos & videos')),
                ),
                ..._content(gallery, selection),
                const SliverToBoxAdapter(child: SizedBox(height: 24)),
              ],
            ),
          ),
        ),
      ],
    );
  }

  List<Widget> _content(GalleryController gallery, SelectionController selection) {
    if (gallery.access == null || (gallery.isLoading && gallery.items.isEmpty)) {
      return const [
        SliverFillRemaining(
          hasScrollBody: false,
          child: Center(child: CircularProgressIndicator(color: AppColors.primary)),
        ),
      ];
    }
    if (gallery.needsPermission) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: _Notice(
            icon: Icons.photo_library_outlined,
            title: 'Allow access to your photos',
            message: 'My Story builds your video from the photos and videos on this device.',
            actionLabel: 'Allow access',
            onAction: () async {
              await gallery.openSettings();
              await gallery.initialize();
            },
          ),
        ),
      ];
    }
    if (gallery.error != null && gallery.items.isEmpty) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: _Notice(
            icon: Icons.error_outline,
            title: "Couldn't load your photos",
            message: 'Check that photo access is allowed, then try again.',
            actionLabel: 'Try again',
            onAction: gallery.refresh,
          ),
        ),
      ];
    }
    if (gallery.items.isEmpty) {
      return const [
        SliverFillRemaining(
          hasScrollBody: false,
          child: _Notice(
            icon: Icons.photo_outlined,
            title: 'No photos or videos yet',
            message: 'Take a few photos or videos and they will show up here.',
          ),
        ),
      ];
    }
    return [
      if (gallery.isLimited)
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          sliver: SliverToBoxAdapter(
            child: _LimitedBanner(onManage: gallery.manageLimitedAccess),
          ),
        ),
      SliverPadding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        sliver: SliverGrid.builder(
          gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: 220,
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
            childAspectRatio: MediaTile.aspectRatio,
          ),
          itemCount: gallery.items.length,
          itemBuilder: (context, index) {
            final item = gallery.items[index];
            return MediaTile(
              key: ValueKey(item.id),
              image: item.thumbnail(),
              label: item.isVideo ? formatDuration(item.duration) : formatShortDate(item.createdAt),
              leadingLabelIcon: item.isVideo ? Icons.play_arrow_rounded : null,
              selectable: true,
              selectionNumber: selection.positionOf(item),
              onTap: () => selection.toggle(item),
            );
          },
        ),
      ),
      if (gallery.isLoading)
        const SliverToBoxAdapter(
          child: Padding(
            padding: EdgeInsets.all(20),
            child: Center(child: CircularProgressIndicator(color: AppColors.primary)),
          ),
        ),
    ];
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.top, required this.onComingSoon});

  final double top;
  final VoidCallback onComingSoon;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(16, top + 12, 16, 0),
      child: Row(
        children: [
          Semantics(
            button: true,
            label: 'Open settings',
            child: InkResponse(
              onTap: () => context.read<ShellNavigation>().select(ShellNavigation.settings),
              radius: 24,
              child: const Padding(
                padding: EdgeInsets.all(4),
                child: AppSvg('menu', width: 21.5, height: 18.5),
              ),
            ),
          ),
          const SizedBox(width: 14),
          const AppSvg('title_my_story', width: 155, height: 34),
          const Spacer(),
          Semantics(
            button: true,
            label: 'Premium',
            child: GestureDetector(
              onTap: onComingSoon,
              child: Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(24),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.primary.withValues(alpha: 0.3),
                      offset: const Offset(0, 4),
                      blurRadius: 2,
                    ),
                  ],
                ),
                alignment: Alignment.center,
                child: const AppSvg('crown', width: 18, height: 18),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 56, color: AppColors.primary),
          const SizedBox(height: 16),
          Text(title, textAlign: TextAlign.center, style: AppText.urbanist(20, weight: 700)),
          const SizedBox(height: 8),
          Text(
            message,
            textAlign: TextAlign.center,
            style: AppText.urbanist(15, weight: 500, color: AppColors.body),
          ),
          if (actionLabel != null) ...[
            const SizedBox(height: 20),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.primary,
                minimumSize: const Size(160, 48),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
              ),
              onPressed: onAction,
              child: Text(actionLabel!, style: AppText.urbanist(16, color: Colors.white)),
            ),
          ],
        ],
      ),
    );
  }
}

class _LimitedBanner extends StatelessWidget {
  const _LimitedBanner({required this.onManage});

  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
      decoration: BoxDecoration(
        color: AppColors.surfaceMuted,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              'My Story can only see some of your photos.',
              style: AppText.urbanist(13, weight: 600, color: AppColors.body),
            ),
          ),
          TextButton(
            onPressed: onManage,
            child: Text('Manage', style: AppText.urbanist(14, weight: 700, color: AppColors.primary)),
          ),
        ],
      ),
    );
  }
}
