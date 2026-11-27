import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/library_controller.dart';
import '../state/shell_navigation.dart';
import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/formatters.dart';
import '../widgets/media_tile.dart';
import 'story_viewer_screen.dart';

/// Library tab: every story created in the app. Tap one to watch it.
class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<LibraryController>().refresh();
    });
  }

  @override
  Widget build(BuildContext context) {
    final library = context.watch<LibraryController>();
    final navigation = context.watch<ShellNavigation>();
    final top = MediaQuery.paddingOf(context).top;

    // The shell keeps every tab alive, so reload whenever this tab becomes visible.
    if (navigation.index == ShellNavigation.library && !library.isLoading && !library.hasLoaded) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) library.refresh();
      });
    }

    return RefreshIndicator(
      onRefresh: library.refresh,
      edgeOffset: top + 40,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: EdgeInsets.fromLTRB(16, top + 16, 16, 18),
            sliver: SliverToBoxAdapter(
              child: Text('Library', style: AppText.outfit(34, weight: 500, color: Colors.black)),
            ),
          ),
          if (!library.hasLoaded)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: Center(child: CircularProgressIndicator(color: AppColors.primary)),
            )
          else if (library.entries.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: _EmptyLibrary(
                onCreate: () => context.read<ShellNavigation>().select(ShellNavigation.home),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
              sliver: SliverGrid.builder(
                gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: 220,
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                  childAspectRatio: MediaTile.aspectRatio,
                ),
                itemCount: library.entries.length,
                itemBuilder: (context, index) {
                  final entry = library.entries[index];
                  return MediaTile(
                    key: ValueKey(entry.story.id),
                    image: entry.media.thumbnail(),
                    label: entry.story.title,
                    onTap: () => openStoryViewer(context, entry),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _EmptyLibrary extends StatelessWidget {
  const _EmptyLibrary({required this.onCreate});

  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.video_library_outlined, size: 56, color: AppColors.primary),
          const SizedBox(height: 16),
          Text('No stories yet', style: AppText.urbanist(20, weight: 700)),
          const SizedBox(height: 8),
          Text(
            'Stories you create will show up here.',
            textAlign: TextAlign.center,
            style: AppText.urbanist(15, weight: 500, color: AppColors.body),
          ),
          const SizedBox(height: 20),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.createButton,
              minimumSize: const Size(180, 48),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
            ),
            onPressed: onCreate,
            child: Text('create story', style: AppText.urbanist(16, color: Colors.white)),
          ),
        ],
      ),
    );
  }
}

/// Shared by the Library tile and the viewer header.
String storySubtitle(LibraryEntry entry) {
  final story = entry.story;
  return '${formatStoryDate(story.createdAt)} · ${formatDuration(story.duration)} · '
      '${story.settings.aspectRatio.wire} · ${story.settings.frameRate.label}';
}
