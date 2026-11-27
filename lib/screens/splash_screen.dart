import 'package:material_ui/material_ui.dart';

import '../theme/app_colors.dart';
import '../theme/app_text.dart';
import '../widgets/app_svg.dart';
import '../widgets/progress_pill.dart';

/// Splash page from the Figma template: gradient wash, tagline, loading bar, signature.
class SplashScreen extends StatefulWidget {
  const SplashScreen({
    super.key,
    required this.onFinished,
    this.duration = const Duration(milliseconds: 2400),
  });

  final VoidCallback onFinished;
  final Duration duration;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with SingleTickerProviderStateMixin {
  late final AnimationController _controller =
      AnimationController(vsync: this, duration: widget.duration);

  @override
  void initState() {
    super.initState();
    _controller.forward().whenComplete(() {
      if (mounted) widget.onFinished();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    return Scaffold(
      backgroundColor: Colors.white,
      body: Stack(
        fit: StackFit.expand,
        children: [
          Opacity(
            opacity: 0.3,
            child: RotatedBox(
              quarterTurns: 3,
              child: Image.asset('assets/images/splash_gradient.png', fit: BoxFit.cover),
            ),
          ),
          Center(
            child: Text('Craft your memories..', style: AppText.outfit(18)),
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: Padding(
              padding: EdgeInsets.only(bottom: 48 + bottomInset),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AnimatedBuilder(
                    animation: _controller,
                    builder: (context, _) =>
                        ProgressPill(value: Curves.easeInOut.transform(_controller.value)),
                  ),
                  const SizedBox(height: 30),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        'Made with',
                        style: AppText.urbanist(13, weight: 400, color: AppColors.body),
                      ),
                      const SizedBox(width: 8),
                      const AppSvg('heart', width: 16, height: 14),
                      const SizedBox(width: 8),
                      Text(
                        'from Toronto',
                        style: AppText.urbanist(13, weight: 400, color: AppColors.body),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
