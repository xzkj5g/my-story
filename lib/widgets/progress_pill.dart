import 'package:material_ui/material_ui.dart';

import '../theme/app_colors.dart';

/// The rounded loading bar from the Figma splash screen.
class ProgressPill extends StatelessWidget {
  const ProgressPill({super.key, required this.value, this.width = 186, this.height = 14});

  /// 0.0 - 1.0.
  final double value;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final clamped = value.clamp(0.0, 1.0);
    return Semantics(
      label: 'Progress',
      value: '${(clamped * 100).round()} percent',
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: AppColors.primaryTrack,
          borderRadius: BorderRadius.circular(48),
        ),
        alignment: Alignment.centerLeft,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: width * clamped,
          height: height,
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(48),
          ),
        ),
      ),
    );
  }
}
