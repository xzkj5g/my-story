import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../state/selection_controller.dart';
import '../state/shell_navigation.dart';
import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/app_bottom_nav.dart';
import 'home_screen.dart';
import 'library_screen.dart';
import 'settings_screen.dart';
import 'story_editor_screen.dart';

/// The three-tab shell: Home, Library, Settings.
class HomeShell extends StatelessWidget {
  const HomeShell({super.key});

  @override
  Widget build(BuildContext context) {
    final navigation = context.watch<ShellNavigation>();
    final selection = context.watch<SelectionController>();
    return Scaffold(
      body: IndexedStack(
        index: navigation.index,
        children: const [HomeScreen(), LibraryScreen(), SettingsScreen()],
      ),
      bottomNavigationBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedSize(
            duration: const Duration(milliseconds: 180),
            alignment: Alignment.bottomCenter,
            child: navigation.index == ShellNavigation.home && !selection.isEmpty
                ? _SelectionBar(count: selection.count)
                : const SizedBox(width: double.infinity),
          ),
          AppBottomNav(index: navigation.index, onSelected: navigation.select),
        ],
      ),
    );
  }
}

class _SelectionBar extends StatelessWidget {
  const _SelectionBar({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final selection = context.read<SelectionController>();
    return Material(
      color: Colors.white,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
        child: Row(
          children: [
            Expanded(
              child: Text(
                count == 1 ? '1 item selected' : '$count items selected',
                style: AppText.urbanist(15, weight: 700),
              ),
            ),
            TextButton(
              onPressed: selection.clear,
              child: Text('Clear', style: AppText.urbanist(15, color: AppColors.navInactive)),
            ),
            const SizedBox(width: 6),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.createButton,
                minimumSize: const Size(0, 44),
                padding: const EdgeInsets.symmetric(horizontal: 22),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
              onPressed: () => openStoryEditor(context),
              child: Text('create story', style: AppText.urbanist(16, color: Colors.white)),
            ),
          ],
        ),
      ),
    );
  }
}
