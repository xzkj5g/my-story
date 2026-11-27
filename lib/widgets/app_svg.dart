import 'package:flutter/widgets.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// Loads one of the icons exported from the Figma template (`assets/icons`).
class AppSvg extends StatelessWidget {
  const AppSvg(this.name, {super.key, this.width, this.height, this.color});

  final String name;
  final double? width;
  final double? height;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return SvgPicture.asset(
      'assets/icons/$name.svg',
      width: width,
      height: height,
      colorFilter: color == null ? null : ColorFilter.mode(color!, BlendMode.srcIn),
    );
  }
}
