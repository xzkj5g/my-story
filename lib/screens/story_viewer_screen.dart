import 'dart:io';

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:video_player/video_player.dart';

import '../services/youtube/youtube_uploader.dart';
import '../state/library_controller.dart';
import '../state/shell_navigation.dart';
import '../state/youtube_controller.dart';
import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/primary_button.dart';
import '../widgets/progress_pill.dart';
import 'library_screen.dart';

void openStoryViewer(BuildContext context, LibraryEntry entry) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(builder: (_) => StoryViewerScreen(entry: entry)),
  );
}

/// Plays a finished story; offers delete and upload to YouTube.
class StoryViewerScreen extends StatefulWidget {
  const StoryViewerScreen({super.key, required this.entry});

  final LibraryEntry entry;

  @override
  State<StoryViewerScreen> createState() => _StoryViewerScreenState();
}

class _StoryViewerScreenState extends State<StoryViewerScreen> {
  VideoPlayerController? _video;
  Object? _error;
  String? _path;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final path = await context.read<LibraryController>().pathFor(widget.entry);
      if (path == null) throw StateError('Video file not found.');
      _path = path;
      final controller = VideoPlayerController.file(File(path));
      await controller.initialize();
      await controller.setLooping(true);
      if (!mounted) {
        await controller.dispose();
        return;
      }
      setState(() => _video = controller);
      await controller.play();
    } on Object catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  void dispose() {
    _video?.dispose();
    super.dispose();
  }

  Future<void> _delete() async {
    final library = context.read<LibraryController>();
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete this story?'),
        content: const Text('The video will be removed from your device. This cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await _video?.pause();
    final outcome = await library.delete(widget.entry);
    switch (outcome) {
      case DeleteOutcome.deleted:
        if (navigator.mounted) navigator.pop();
      case DeleteOutcome.declined:
        messenger.showSnackBar(const SnackBar(content: Text('The story was not deleted.')));
        await _video?.play();
      case DeleteOutcome.failed:
        messenger.showSnackBar(const SnackBar(content: Text("Couldn't delete the story. Try again.")));
        await _video?.play();
    }
  }

  Future<void> _upload() async {
    final youtube = context.read<YouTubeController>();
    await _video?.pause();
    if (!mounted) return;
    if (!youtube.isConnected) {
      await showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        builder: (sheetContext) => _ConnectFirstSheet(
          onGoToSettings: () {
            Navigator.pop(sheetContext);
            context.read<ShellNavigation>().select(ShellNavigation.settings);
            Navigator.of(context).pop();
          },
        ),
      );
      return;
    }
    final path = _path;
    if (path == null) return;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      isDismissible: false,
      enableDrag: false,
      builder: (_) => ChangeNotifierProvider.value(
        value: youtube,
        child: _UploadSheet(title: widget.entry.story.title, file: File(path)),
      ),
    );
    youtube.resetUpload();
  }

  @override
  Widget build(BuildContext context) {
    final story = widget.entry.story;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(story.title, style: AppText.urbanist(18, weight: 700, color: Colors.white)),
        actions: [
          IconButton(
            tooltip: 'Upload to YouTube',
            onPressed: _upload,
            icon: const Icon(Icons.cloud_upload_outlined),
          ),
          IconButton(
            tooltip: 'Delete story',
            onPressed: _delete,
            icon: const Icon(Icons.delete_outline_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(child: Center(child: _player())),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 10, 20, 18),
              child: Text(
                storySubtitle(widget.entry),
                textAlign: TextAlign.center,
                style: AppText.urbanist(13, weight: 500, color: Colors.white70),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _player() {
    final video = _video;
    if (_error != null) {
      return Padding(
        padding: const EdgeInsets.all(32),
        child: Text(
          "This video can't be played.",
          style: AppText.urbanist(16, weight: 600, color: Colors.white70),
        ),
      );
    }
    if (video == null) {
      return const CircularProgressIndicator(color: Colors.white);
    }
    return AspectRatio(
      aspectRatio: video.value.aspectRatio,
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          GestureDetector(
            onTap: () => setState(() {
              video.value.isPlaying ? video.pause() : video.play();
            }),
            child: VideoPlayer(video),
          ),
          if (!video.value.isPlaying)
            IgnorePointer(
              child: Center(
                child: Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.45),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.play_arrow_rounded, size: 48, color: Colors.white),
                ),
              ),
            ),
          VideoProgressIndicator(
            video,
            allowScrubbing: true,
            colors: const VideoProgressColors(
              playedColor: AppColors.primary,
              bufferedColor: Colors.white24,
              backgroundColor: Colors.white12,
            ),
          ),
        ],
      ),
    );
  }
}

class _ConnectFirstSheet extends StatelessWidget {
  const _ConnectFirstSheet({required this.onGoToSettings});

  final VoidCallback onGoToSettings;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Connect YouTube first', style: AppText.urbanist(20, weight: 700)),
            const SizedBox(height: 8),
            Text(
              'Link your YouTube account in Settings, then come back to upload this story.',
              textAlign: TextAlign.center,
              style: AppText.urbanist(15, weight: 500, color: AppColors.body),
            ),
            const SizedBox(height: 20),
            PrimaryButton(label: 'Open Settings', onPressed: onGoToSettings),
          ],
        ),
      ),
    );
  }
}

class _UploadSheet extends StatefulWidget {
  const _UploadSheet({required this.title, required this.file});

  final String title;
  final File file;

  @override
  State<_UploadSheet> createState() => _UploadSheetState();
}

class _UploadSheetState extends State<_UploadSheet> {
  late final TextEditingController _title = TextEditingController(text: widget.title);
  YouTubePrivacy _privacy = YouTubePrivacy.private;

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final youtube = context.watch<YouTubeController>();
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(24, 0, 24, 20 + bottomInset),
        child: switch (youtube.uploadPhase) {
          UploadPhase.idle => _form(youtube),
          UploadPhase.uploading => _uploading(youtube),
          UploadPhase.done => _done(youtube),
          UploadPhase.failed => _failed(youtube),
        },
      ),
    );
  }

  Widget _form(YouTubeController youtube) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Upload to YouTube', style: AppText.urbanist(20, weight: 700)),
        const SizedBox(height: 4),
        Text(
          'Signed in as ${youtube.account?.email ?? 'your account'}',
          style: AppText.urbanist(14, weight: 500, color: AppColors.body),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _title,
          maxLength: 100,
          decoration: InputDecoration(
            labelText: 'Title',
            filled: true,
            fillColor: AppColors.surfaceMuted,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide.none,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text('Who can watch', style: AppText.urbanist(14, weight: 700, color: AppColors.sectionLabel)),
        const SizedBox(height: 8),
        SegmentedButton<YouTubePrivacy>(
          showSelectedIcon: false,
          segments: [
            for (final privacy in YouTubePrivacy.values)
              ButtonSegment(value: privacy, label: Text(privacy.label)),
          ],
          selected: {_privacy},
          onSelectionChanged: (value) => setState(() => _privacy = value.first),
        ),
        const SizedBox(height: 20),
        PrimaryButton(
          label: 'Upload',
          icon: Icons.cloud_upload_outlined,
          onPressed: () {
            final title = _title.text.trim().isEmpty ? widget.title : _title.text.trim();
            youtube.upload(file: widget.file, title: title, privacy: _privacy);
          },
        ),
        Center(
          child: TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel', style: AppText.urbanist(16, weight: 700, color: AppColors.body)),
          ),
        ),
      ],
    );
  }

  Widget _uploading(YouTubeController youtube) {
    final percent = (youtube.uploadProgress * 100).round();
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text('Uploading to YouTube', style: AppText.urbanist(20, weight: 700)),
        const SizedBox(height: 18),
        ProgressPill(value: youtube.uploadProgress, width: 260),
        const SizedBox(height: 12),
        Text('$percent%', style: AppText.urbanist(16, weight: 700)),
        const SizedBox(height: 4),
        Text(
          'Keep My Story open until the upload finishes.',
          style: AppText.urbanist(14, weight: 500, color: AppColors.body),
        ),
        const SizedBox(height: 12),
      ],
    );
  }

  Widget _done(YouTubeController youtube) {
    final url = youtube.uploadedUrl;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.check_circle_rounded, size: 52, color: AppColors.primary),
        const SizedBox(height: 12),
        Text('Uploaded to YouTube', style: AppText.urbanist(20, weight: 700)),
        if (url != null) ...[
          const SizedBox(height: 8),
          SelectableText(url.toString(), style: AppText.urbanist(15, weight: 600, color: AppColors.primary)),
          TextButton.icon(
            onPressed: () async {
              final messenger = ScaffoldMessenger.of(context);
              await Clipboard.setData(ClipboardData(text: url.toString()));
              messenger.showSnackBar(const SnackBar(content: Text('Link copied')));
            },
            icon: const Icon(Icons.copy_rounded, size: 18),
            label: const Text('Copy link'),
          ),
        ],
        const SizedBox(height: 12),
        PrimaryButton(label: 'Done', onPressed: () => Navigator.pop(context)),
      ],
    );
  }

  Widget _failed(YouTubeController youtube) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.error_outline_rounded, size: 52, color: AppColors.danger),
        const SizedBox(height: 12),
        Text("Couldn't upload", style: AppText.urbanist(20, weight: 700)),
        const SizedBox(height: 8),
        Text(
          youtube.uploadError ?? 'Something went wrong.',
          textAlign: TextAlign.center,
          style: AppText.urbanist(15, weight: 500, color: AppColors.body),
        ),
        const SizedBox(height: 20),
        PrimaryButton(label: 'Try again', onPressed: youtube.resetUpload),
        Center(
          child: TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Close', style: AppText.urbanist(16, weight: 700, color: AppColors.body)),
          ),
        ),
      ],
    );
  }
}
