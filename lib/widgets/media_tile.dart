import 'package:material_ui/material_ui.dart';

import '../theme/app_colors.dart';
import '../theme/app_text.dart';

/// Rounded photo card matching the story cards in the Figma Home screen.
///
/// Used for camera roll items (selectable) and for finished stories in the Library.
class MediaTile extends StatelessWidget {
  const MediaTile({
    super.key,
    required this.image,
    required this.label,
    this.leadingLabelIcon,
    this.onTap,
    this.onLongPress,
    this.selectionNumber,
    this.selectable = false,
  });

  /// Figma card size: 183.5 x 216.8.
  static const double aspectRatio = 183.533 / 216.791;

  final ImageProvider image;
  final String label;
  final IconData? leadingLabelIcon;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// 1-based order in the story when selected.
  final int? selectionNumber;

  /// Whether to show the selection ring in the corner.
  final bool selectable;

  bool get _selected => selectionNumber != null;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: _selected,
      label: _selected ? '$label, selected, number $selectionNumber' : label,
      child: GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 140),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(30),
            color: AppColors.tileFallback,
            border: Border.all(
              color: _selected ? AppColors.primary : Colors.transparent,
              width: 3.5,
            ),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(26.5),
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image(
                  image: image,
                  fit: BoxFit.cover,
                  gaplessPlayback: true,
                  frameBuilder: (context, child, frame, sync) => AnimatedOpacity(
                    opacity: frame == null && !sync ? 0 : 1,
                    duration: const Duration(milliseconds: 160),
                    child: child,
                  ),
                  errorBuilder: (context, error, stack) => const ColoredBox(
                    color: AppColors.tileFallback,
                    child: Center(
                      child: Icon(Icons.broken_image_outlined, color: Colors.white70),
                    ),
                  ),
                ),
                if (_selected)
                  ColoredBox(color: AppColors.primary.withValues(alpha: 0.16)),
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  height: 62,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black.withValues(alpha: 0),
                          Colors.black.withValues(alpha: 0.5),
                        ],
                      ),
                    ),
                    child: Center(
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (leadingLabelIcon != null) ...[
                            Icon(leadingLabelIcon, size: 18, color: Colors.white),
                            const SizedBox(width: 4),
                          ],
                          Flexible(
                            child: Text(
                              label,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppText.urbanist(17.245, color: Colors.white),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                if (selectable)
                  Positioned(
                    top: 12,
                    right: 12,
                    child: _SelectionBadge(number: selectionNumber),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SelectionBadge extends StatelessWidget {
  const _SelectionBadge({this.number});

  final int? number;

  @override
  Widget build(BuildContext context) {
    final selected = number != null;
    return Container(
      width: 28,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: selected ? AppColors.primary : Colors.black.withValues(alpha: 0.18),
        border: Border.all(color: Colors.white, width: 2),
      ),
      child: selected
          ? Text('$number', style: AppText.urbanist(13, weight: 800, color: Colors.white))
          : null,
    );
  }
}
