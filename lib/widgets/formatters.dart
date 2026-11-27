import 'package:intl/intl.dart';

String formatDuration(Duration duration) {
  final totalSeconds = duration.inSeconds;
  final hours = totalSeconds ~/ 3600;
  final minutes = (totalSeconds % 3600) ~/ 60;
  final seconds = totalSeconds % 60;
  String two(int n) => n.toString().padLeft(2, '0');
  return hours > 0 ? '$hours:${two(minutes)}:${two(seconds)}' : '$minutes:${two(seconds)}';
}

String formatShortDate(DateTime date) => DateFormat.MMMd().format(date);

String formatStoryDate(DateTime date) => DateFormat.yMMMd().add_jm().format(date);

String defaultStoryTitle(DateTime now) => 'Story ${DateFormat.MMMd().format(now)}';
