import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'screens/home_shell.dart';
import 'screens/splash_screen.dart';
import 'state/app_services.dart';
import 'state/gallery_controller.dart';
import 'state/library_controller.dart';
import 'state/selection_controller.dart';
import 'state/shell_navigation.dart';
import 'state/story_creation_controller.dart';
import 'state/youtube_controller.dart';
import 'theme/app_theme.dart';

class MyStoryApp extends StatelessWidget {
  const MyStoryApp({
    super.key,
    required this.services,
    this.splashDuration = const Duration(milliseconds: 2400),
  });

  final AppServices services;
  final Duration splashDuration;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ShellNavigation()),
        ChangeNotifierProvider(create: (_) => SelectionController()),
        ChangeNotifierProvider(create: (_) => GalleryController(services.gallery)),
        ChangeNotifierProvider(
          create: (_) => LibraryController(
            repository: services.repository,
            gallery: services.gallery,
          ),
        ),
        ChangeNotifierProvider(
          create: (context) => StoryCreationController(
            compiler: services.compiler,
            gallery: services.gallery,
            repository: services.repository,
            onStoryCreated: () => context.read<LibraryController>().refresh(),
          ),
        ),
        ChangeNotifierProvider(
          create: (_) => YouTubeController(services.youtube)..restore(),
        ),
      ],
      child: MaterialApp(
        title: 'My Story',
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(),
        home: _Root(splashDuration: splashDuration),
      ),
    );
  }
}

class _Root extends StatefulWidget {
  const _Root({required this.splashDuration});

  final Duration splashDuration;

  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  bool _splashDone = false;

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 350),
      child: _splashDone
          ? const HomeShell(key: ValueKey('home'))
          : SplashScreen(
              key: const ValueKey('splash'),
              duration: widget.splashDuration,
              onFinished: () => setState(() => _splashDone = true),
            ),
    );
  }
}
