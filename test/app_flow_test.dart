import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:my_story/app.dart';
import 'package:my_story/models/media_item.dart';
import 'package:my_story/models/output_settings.dart';
import 'package:my_story/services/compiler_service.dart';
import 'package:my_story/services/gallery_service.dart';
import 'package:my_story/services/youtube/youtube_service.dart';
import 'package:my_story/services/youtube/youtube_uploader.dart';
import 'package:my_story/state/app_services.dart';

import 'fakes.dart';

class _Harness {
  _Harness({GalleryAccess access = GalleryAccess.granted, bool youtubeConfigured = true})
      : gallery = FakeGalleryService(
          access: access,
          items: [
            for (var i = 1; i <= 4; i++) fakeMedia('p$i'),
            fakeMedia('v1', kind: MediaKind.video, duration: const Duration(seconds: 5)),
          ],
        ),
        compiler = FakeCompilerService(),
        repository = MemoryStoryRepository(),
        auth = FakeYouTubeAuth(configured: youtubeConfigured);

  final FakeGalleryService gallery;
  final FakeCompilerService compiler;
  final MemoryStoryRepository repository;
  final FakeYouTubeAuth auth;

  AppServices get services => AppServices(
        gallery: gallery,
        compiler: compiler,
        repository: repository,
        youtube: YouTubeService(auth: auth, uploader: YouTubeUploader()),
      );
}

Future<void> _launch(WidgetTester tester, _Harness harness) async {
  tester.view
    ..physicalSize = const Size(1080, 2400)
    ..devicePixelRatio = 2.5;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MyStoryApp(services: harness.services, splashDuration: const Duration(milliseconds: 50)),
  );
  await tester.pump(const Duration(milliseconds: 100));
  await tester.pump(const Duration(milliseconds: 500));
}

void main() {
  testWidgets('shows the splash first and then Home with the two action buttons', (tester) async {
    final harness = _Harness();
    tester.view
      ..physicalSize = const Size(1080, 2400)
      ..devicePixelRatio = 2.5;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MyStoryApp(services: harness.services, splashDuration: const Duration(milliseconds: 400)),
    );
    expect(find.text('Craft your memories..'), findsOneWidget);
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.text('create story'), findsOneWidget);
    expect(find.text('button placeholder'), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Library'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });

  testWidgets('selecting materials, choosing settings and creating a story', (tester) async {
    final harness = _Harness();
    await _launch(tester, harness);

    expect(find.text('Your photos & videos'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('p1')));
    await tester.tap(find.byKey(const ValueKey('p2')));
    await tester.tap(find.byKey(const ValueKey('v1')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('3 items selected'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'create story'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.text('Timeline'), findsOneWidget);
    expect(find.text('3 items · 0:11'), findsOneWidget);

    await tester.tap(find.text('60 fps'));
    await tester.pump();
    await tester.tap(find.text('Portrait'));
    await tester.pump();

    await tester.tap(find.text('Start to create'));
    await tester.pump(const Duration(milliseconds: 100));
    expect(harness.compiler.startedItems!.map((i) => i.id), ['p1', 'p2', 'v1']);
    expect(harness.compiler.startedSettings!.frameRate, StoryFrameRate.fps60);
    expect(harness.compiler.startedSettings!.aspectRatio, StoryAspectRatio.portrait);

    harness.compiler.emit(const CompileUpdate(status: CompileStatus.running, progress: 55));
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text('55%'), findsOneWidget);

    harness.gallery.items.add(fakeMedia('out', kind: MediaKind.video));
    harness.compiler.emit(const CompileUpdate(status: CompileStatus.succeeded, assetId: 'out'));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('Your story is ready'), findsOneWidget);
    expect(harness.repository.stories.single.id, 'out');

    await tester.tap(find.text('View in Library'));
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('Library'), findsWidgets);
    expect(find.textContaining('Story '), findsWidgets);
  });

  testWidgets('shows a permission prompt when the library is not accessible', (tester) async {
    await _launch(tester, _Harness(access: GalleryAccess.denied));
    expect(find.text('Allow access to your photos'), findsOneWidget);
  });

  testWidgets('settings explain that YouTube sign-in is not configured in this build', (tester) async {
    await _launch(tester, _Harness(youtubeConfigured: false));
    await tester.tap(find.text('Settings'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('YouTube'), findsOneWidget);
    expect(find.textContaining('no Google client id'), findsOneWidget);
  });

  testWidgets('connecting YouTube from settings shows the account', (tester) async {
    final harness = _Harness();
    await _launch(tester, harness);
    await tester.tap(find.text('Settings'));
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(find.text('Connect with YouTube'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.textContaining('me@example.com'), findsOneWidget);
    expect(find.text('Disconnect'), findsOneWidget);
  });
}
