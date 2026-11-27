import 'package:material_ui/material_ui.dart';

import '../theme/app_colors.dart';
import '../theme/app_text.dart';

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key, this.trailing});

  final String text;
  final String? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(text, style: AppText.urbanist(16, color: AppColors.sectionLabel)),
        ),
        if (trailing != null)
          Text(trailing!, style: AppText.urbanist(14, weight: 600, color: AppColors.sectionLabel)),
      ],
    );
  }
}
