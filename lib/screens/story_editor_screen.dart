import 'dart:io';

import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../models/media_item.dart';
import '../models/output_settings.dart';
import '../state/selection_controller.dart';
import '../state/shell_navigation.dart';
import '../state/story_creation_controller.dart';
import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/formatters.dart';
import '../widgets/primary_button.dart';
import '../widgets/progress_pill.dart';
import '../widgets/section_label.dart';

/// Opens the story editor, or explains that something has to be picked first.
void openStoryEditor(BuildContext context) {
  final selection = context.read<SelectionController>();
  if (selection.isEmpty) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('Pick at least one photo or video first.')));
    return;
  }
  Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const StoryEditorScreen()));
}

/// Review the picked items as a thumbnail timeline, choose fps and aspect ratio, then create.
class StoryEditorScreen extends StatefulWidget {
  const StoryEditorScreen({super.key});

  @override
  State<StoryEditorScreen> createState() => _StoryEditorScreenState();
}

class _StoryEditorScreenState extends State<StoryEditorScreen> {
  late final TextEditingController _title =
      TextEditingController(text: defaultStoryTitle(DateTime.now()));
  OutputSettings _settings = const OutputSettings();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<StoryCreationController>().reset();
    });
  }

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  Future<void> _confirmLeave() async {
    final creation = context.read<StoryCreationController>();
    final navigator = Navigator.of(context);
    final cancel = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Stop creating your story?'),
        content: const Text('Your video is still being made. If you stop now, it will be discarded.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep going'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Stop', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (cancel == true) {
      await creation.cancel();
      if (navigator.mounted && !creation.isRunning) navigator.pop();
    }
  }

  void _start() {
    final selection = context.read<SelectionController>();
    final title = _title.text.trim().isEmpty ? defaultStoryTitle(DateTime.now()) : _title.text.trim();
    FocusScope.of(context).unfocus();
    context.read<StoryCreationController>().start(
          title: title,
          items: selection.selected,
          settings: _settings,
        );
  }

  void _finish({required bool openLibrary}) {
    context.read<SelectionController>().clear();
    if (openLibrary) context.read<ShellNavigation>().select(ShellNavigation.library);
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final creation = context.watch<StoryCreationController>();
    final selection = context.watch<SelectionController>();
    final showingProgress = creation.phase == CreationPhase.running ||
        creation.phase == CreationPhase.succeeded ||
        creation.phase == CreationPhase.failed;

    return PopScope(
      canPop: !creation.isRunning,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && creation.isRunning) _confirmLeave();
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Your story'),
          automaticallyImplyLeading: !showingProgress || creation.phase == CreationPhase.failed,
        ),
        body: SafeArea(
          child: showingProgress
              ? _CreationView(
                  creation: creation,
                  onCancel: _confirmLeave,
                  onRetry: creation.reset,
                  onOpenLibrary: () => _finish(openLibrary: true),
                  onCreateAnother: () => _finish(openLibrary: false),
                )
              : _EditorForm(
                  selection: selection,
                  title: _title,
                  settings: _settings,
                  onSettingsChanged: (value) => setState(() => _settings = value),
                  onStart: selection.isEmpty ? null : _start,
                ),
        ),
      ),
    );
  }
}

class _EditorForm extends StatelessWidget {
  const _EditorForm({
    required this.selection,
    required this.title,
    required this.settings,
    required this.onSettingsChanged,
    required this.onStart,
  });

  final SelectionController selection;
  final TextEditingController title;
  final OutputSettings settings;
  final ValueChanged<OutputSettings> onSettingsChanged;
  final VoidCallback? onStart;

  @override
  Widget build(BuildContext context) {
    final items = selection.selected;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            children: [
              TextField(
                controller: title,
                textCapitalization: TextCapitalization.sentences,
                maxLength: 80,
                style: AppText.urbanist(17, weight: 600),
                decoration: InputDecoration(
                  labelText: 'Story title',
                  counterText: '',
                  filled: true,
                  fillColor: AppColors.surfaceMuted,
                  labelStyle: AppText.urbanist(15, weight: 500, color: AppColors.sectionLabel),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(18),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
              const SizedBox(height: 22),
              SectionLabel(
                'Timeline',
                trailing: '${items.length} ${items.length == 1 ? 'item' : 'items'}'
                    ' · ${formatDuration(selection.totalDuration)}',
              ),
              const SizedBox(height: 10),
              if (items.isEmpty)
                const _EmptyTimeline()
              else ...[
                SizedBox(
                  height: 146,
                  child: ReorderableListView.builder(
                    scrollDirection: Axis.horizontal,
                    buildDefaultDragHandles: true,
                    itemCount: items.length,
                    onReorderItem: selection.reorder,
                    proxyDecorator: (child, index, animation) => Material(
                      color: Colors.transparent,
                      elevation: 6,
                      shadowColor: AppColors.primary.withValues(alpha: 0.4),
                      borderRadius: BorderRadius.circular(20),
                      child: child,
                    ),
                    itemBuilder: (context, index) => _TimelineCard(
                      key: ValueKey(items[index].id),
                      item: items[index],
                      position: index + 1,
                      onRemove: () => selection.remove(items[index]),
                    ),
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  items.length > 1
                      ? 'Press and hold a thumbnail, then drag to change the order.'
                      : 'Photos stay on screen for ${MediaItem.photoDisplayDuration.inSeconds} seconds.',
                  style: AppText.urbanist(13, weight: 500, color: AppColors.sectionLabel),
                ),
              ],
              const SizedBox(height: 26),
              const SectionLabel('Frame rate'),
              const SizedBox(height: 10),
              Row(
                children: [
                  for (final rate in StoryFrameRate.values) ...[
                    Expanded(
                      child: _ChoicePill(
                        label: rate.label,
                        selected: settings.frameRate == rate,
                        onTap: () => onSettingsChanged(settings.copyWith(frameRate: rate)),
                      ),
                    ),
                    if (rate != StoryFrameRate.values.last) const SizedBox(width: 10),
                  ],
                ],
              ),
              const SizedBox(height: 26),
              const SectionLabel('Aspect ratio'),
              const SizedBox(height: 10),
              Row(
                children: [
                  for (final ratio in StoryAspectRatio.values) ...[
                    Expanded(
                      child: _RatioCard(
                        ratio: ratio,
                        selected: settings.aspectRatio == ratio,
                        onTap: () => onSettingsChanged(settings.copyWith(aspectRatio: ratio)),
                      ),
                    ),
                    if (ratio != StoryAspectRatio.values.last) const SizedBox(width: 10),
                  ],
                ],
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: PrimaryButton(label: 'Start to create', onPressed: onStart),
        ),
      ],
    );
  }
}

class _TimelineCard extends StatelessWidget {
  const _TimelineCard({
    super.key,
    required this.item,
    required this.position,
    required this.onRemove,
  });

  final MediaItem item;
  final int position;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final label = item.isVideo
        ? formatDuration(item.duration)
        : '${MediaItem.photoDisplayDuration.inSeconds}s';
    return Padding(
      padding: const EdgeInsets.only(right: 10),
      child: SizedBox(
        width: 104,
        child: Semantics(
          label: 'Item $position, ${item.isVideo ? 'video' : 'photo'}, $label',
          child: ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: Stack(
              fit: StackFit.expand,
              children: [
                ColoredBox(
                  color: AppColors.tileFallback,
                  child: Image(image: item.thumbnail(300), fit: BoxFit.cover, gaplessPlayback: true),
                ),
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  height: 40,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Colors.transparent, Colors.black.withValues(alpha: 0.55)],
                      ),
                    ),
                  ),
                ),
                Positioned(
                  left: 8,
                  top: 8,
                  child: Container(
                    width: 24,
                    height: 24,
                    alignment: Alignment.center,
                    decoration: const BoxDecoration(
                      color: AppColors.primary,
                      shape: BoxShape.circle,
                    ),
                    child: Text(
                      '$position',
                      style: AppText.urbanist(12, weight: 800, color: Colors.white),
                    ),
                  ),
                ),
                Positioned(
                  right: 4,
                  top: 4,
                  child: Semantics(
                    button: true,
                    label: 'Remove item $position',
                    child: InkResponse(
                      onTap: onRemove,
                      radius: 18,
                      child: Container(
                        width: 26,
                        height: 26,
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.45),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.close_rounded, size: 16, color: Colors.white),
                      ),
                    ),
                  ),
                ),
                Positioned(
                  left: 8,
                  bottom: 6,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        item.isVideo ? Icons.play_arrow_rounded : Icons.image_outlined,
                        size: 15,
                        color: Colors.white,
                      ),
                      const SizedBox(width: 2),
                      Text(label, style: AppText.urbanist(12, weight: 700, color: Colors.white)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _EmptyTimeline extends StatelessWidget {
  const _EmptyTimeline();

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 110,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: AppColors.surfaceMuted,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        'Nothing left in your story. Go back and pick more.',
        style: AppText.urbanist(14, weight: 600, color: AppColors.body),
      ),
    );
  }
}

class _ChoicePill extends StatelessWidget {
  const _ChoicePill({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      label: label,
      excludeSemantics: true,
      child: Material(
        color: selected ? AppColors.primary : AppColors.surfaceMuted,
        borderRadius: BorderRadius.circular(18),
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: onTap,
          child: SizedBox(
            height: 52,
            child: Center(
              child: Text(
                label,
                style: AppText.urbanist(
                  16,
                  weight: 700,
                  color: selected ? Colors.white : AppColors.primary,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _RatioCard extends StatelessWidget {
  const _RatioCard({required this.ratio, required this.selected, required this.onTap});

  final StoryAspectRatio ratio;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const box = 34.0;
    final width = ratio.ratio >= 1 ? box : box * ratio.ratio;
    final height = ratio.ratio >= 1 ? box / ratio.ratio : box;
    final color = selected ? Colors.white : AppColors.primary;
    return Semantics(
      button: true,
      selected: selected,
      label: '${ratio.label} ${ratio.wire}',
      excludeSemantics: true,
      child: Material(
        color: selected ? AppColors.primary : AppColors.surfaceMuted,
        borderRadius: BorderRadius.circular(18),
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: onTap,
          child: SizedBox(
            height: 92,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: box,
                  height: box,
                  child: Center(
                    child: Container(
                      width: width,
                      height: height,
                      decoration: BoxDecoration(
                        border: Border.all(color: color, width: 2.4),
                        borderRadius: BorderRadius.circular(5),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Text(ratio.wire, style: AppText.urbanist(15, weight: 700, color: color)),
                Text(
                  ratio.label,
                  style: AppText.urbanist(11, weight: 600, color: color.withValues(alpha: 0.8)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CreationView extends StatelessWidget {
  const _CreationView({
    required this.creation,
    required this.onCancel,
    required this.onRetry,
    required this.onOpenLibrary,
    required this.onCreateAnother,
  });

  final StoryCreationController creation;
  final VoidCallback onCancel;
  final VoidCallback onRetry;
  final VoidCallback onOpenLibrary;
  final VoidCallback onCreateAnother;

  @override
  Widget build(BuildContext context) {
    return switch (creation.phase) {
      CreationPhase.succeeded => _ResultView(
          icon: Icons.check_rounded,
          iconColor: AppColors.primary,
          title: 'Your story is ready',
          message: Platform.isIOS
              ? 'It has been saved to your Photos and added to your Library.'
              : 'It has been saved to Movies/MyStory and added to your Library.',
          primaryLabel: 'View in Library',
          onPrimary: onOpenLibrary,
          secondaryLabel: 'Create another',
          onSecondary: onCreateAnother,
        ),
      CreationPhase.failed => _ResultView(
          icon: Icons.error_outline_rounded,
          iconColor: AppColors.danger,
          title: "We couldn't create your story",
          message: creation.errorMessage ?? 'Something went wrong.',
          primaryLabel: 'Try again',
          onPrimary: onRetry,
        ),
      _ => _ProgressView(creation: creation, onCancel: onCancel),
    };
  }
}

class _ProgressView extends StatelessWidget {
  const _ProgressView({required this.creation, required this.onCancel});

  final StoryCreationController creation;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text('${creation.progress}%', style: AppText.outfit(56, weight: 500, color: AppColors.ink)),
          const SizedBox(height: 18),
          ProgressPill(value: creation.progress / 100, width: 260),
          const SizedBox(height: 22),
          Text(
            'Creating your story..',
            style: AppText.urbanist(18, weight: 700),
          ),
          const SizedBox(height: 6),
          Text(
            Platform.isAndroid
                ? 'You can leave the app. We will notify you when it is done.'
                : 'Keep My Story open until your video is ready.',
            textAlign: TextAlign.center,
            style: AppText.urbanist(14, weight: 500, color: AppColors.body),
          ),
          if (creation.warning != null) ...[
            const SizedBox(height: 14),
            Text(
              creation.warning!,
              textAlign: TextAlign.center,
              style: AppText.urbanist(13, weight: 600, color: AppColors.danger),
            ),
          ],
          const SizedBox(height: 28),
          TextButton(
            onPressed: onCancel,
            child: Text('Cancel', style: AppText.urbanist(16, weight: 700, color: AppColors.danger)),
          ),
        ],
      ),
    );
  }
}

class _ResultView extends StatelessWidget {
  const _ResultView({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.message,
    required this.primaryLabel,
    required this.onPrimary,
    this.secondaryLabel,
    this.onSecondary,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String message;
  final String primaryLabel;
  final VoidCallback onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 84,
            height: 84,
            decoration: BoxDecoration(
              color: iconColor.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 44, color: iconColor),
          ),
          const SizedBox(height: 22),
          Text(title, textAlign: TextAlign.center, style: AppText.urbanist(24, weight: 700)),
          const SizedBox(height: 8),
          Text(
            message,
            textAlign: TextAlign.center,
            style: AppText.urbanist(15, weight: 500, color: AppColors.body),
          ),
          const SizedBox(height: 28),
          PrimaryButton(label: primaryLabel, onPressed: onPrimary),
          if (secondaryLabel != null) ...[
            const SizedBox(height: 8),
            TextButton(
              onPressed: onSecondary,
              child: Text(
                secondaryLabel!,
                style: AppText.urbanist(16, weight: 700, color: AppColors.primary),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
