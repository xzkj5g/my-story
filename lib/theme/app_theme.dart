import 'package:material_ui/material_ui.dart';

import 'app_colors.dart';
import 'app_text.dart';

ThemeData buildAppTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: AppColors.primary,
    primary: AppColors.primary,
    surface: AppColors.surface,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.surface,
    fontFamily: 'Urbanist',
    textTheme: TextTheme(
      titleLarge: AppText.urbanist(22, weight: 700),
      titleMedium: AppText.urbanist(18, weight: 600),
      bodyLarge: AppText.urbanist(16, weight: 500, color: AppColors.ink),
      bodyMedium: AppText.urbanist(14, weight: 500, color: AppColors.body),
      labelLarge: AppText.urbanist(16, weight: 700, color: Colors.white),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: AppColors.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      centerTitle: true,
      foregroundColor: AppColors.ink,
      titleTextStyle: AppText.urbanist(20, weight: 700),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: AppColors.ink,
      contentTextStyle: AppText.urbanist(14, weight: 600, color: Colors.white),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: AppColors.surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      titleTextStyle: AppText.urbanist(20, weight: 700),
      contentTextStyle: AppText.urbanist(15, weight: 500, color: AppColors.body),
    ),
  );
}
