import 'package:flutter/foundation.dart';

/// Which bottom tab is showing; lets any screen jump to Library or Settings.
class ShellNavigation extends ChangeNotifier {
  static const int home = 0;
  static const int library = 1;
  static const int settings = 2;

  int _index = home;

  int get index => _index;

  void select(int index) {
    if (index == _index) return;
    _index = index;
    notifyListeners();
  }
}
