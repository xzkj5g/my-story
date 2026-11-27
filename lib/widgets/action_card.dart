import 'package:material_ui/material_ui.dart';

import '../theme/app_text.dart';

/// The big rounded buttons at the top of the Home screen ("create story" and the placeholder).
class ActionCard extends StatelessWidget {
  const ActionCard({
    super.key,
    required this.label,
    required this.color,
    required this.icon,
    required this.onTap,
    this.badge,
  });

  final String label;
  final Color color;
  final Widget icon;
  final VoidCallback onTap;

  /// Optional counter shown in the top-right corner (selected item count).
  final int? badge;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      child: Material(
        color: color,
        borderRadius: BorderRadius.circular(22),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: SizedBox(
            height: 102,
            child: Stack(
              children: [
                const Positioned(right: -14, top: -22, child: _Ring(size: 60, stroke: 7)),
                const Positioned(right: 10, top: 28, child: _Ring(size: 31, stroke: 3.6)),
                Positioned(
                  left: 16,
                  top: 15,
                  right: 16,
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: Text(
                      label,
                      maxLines: 1,
                      style: AppText.urbanist(18, color: Colors.white),
                    ),
                  ),
                ),
                Positioned(right: 15, bottom: 15, child: icon),
                if (badge != null && badge! > 0)
                  Positioned(
                    left: 16,
                    bottom: 14,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.9),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        '$badge selected',
                        style: AppText.urbanist(12, weight: 700, color: color),
                      ),
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

class _Ring extends StatelessWidget {
  const _Ring({required this.size, required this.stroke});

  final double size;
  final double stroke;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white.withValues(alpha: 0.28), width: stroke),
        ),
      ),
    );
  }
}
