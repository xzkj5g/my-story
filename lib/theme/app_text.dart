import 'package:material_ui/material_ui.dart';

import 'app_colors.dart';

/// Typography from the Figma template: Urbanist for UI text, Outfit for the splash tagline.
abstract final class AppText {
  static TextStyle urbanist(
    double size, {
    int weight = 600,
    Color color = AppColors.ink,
    double? letterSpacing,
    double? height,
  }) {
    return TextStyle(
      fontFamily: 'Urbanist',
      fontSize: size,
      fontWeight: _weightFor(weight),
      fontVariations: [FontVariation('wght', weight.toDouble())],
      color: color,
      letterSpacing: letterSpacing,
      height: height,
    );
  }

  static TextStyle outfit(
    double size, {
    int weight = 400,
    Color color = AppColors.body,
  }) {
    return TextStyle(
      fontFamily: 'Outfit',
      fontSize: size,
      fontWeight: _weightFor(weight),
      fontVariations: [FontVariation('wght', weight.toDouble())],
      color: color,
    );
  }

  static FontWeight _weightFor(int weight) {
    final index = ((weight / 100).round() - 1).clamp(0, FontWeight.values.length - 1);
    return FontWeight.values[index];
  }
}
