import 'package:shared_preferences/shared_preferences.dart';

import '../models/story.dart';

/// Remembers which gallery videos were created by this app.
abstract class StoryRepository {
  Future<List<Story>> load();

  Future<void> add(Story story);

  Future<void> remove(String id);
}

class SharedPreferencesStoryRepository implements StoryRepository {
  static const _key = 'stories_v1';

  Future<List<Story>> _read() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null || raw.isEmpty) return [];
    try {
      return Story.decodeList(raw);
    } on Object {
      return [];
    }
  }

  Future<void> _write(List<Story> stories) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, Story.encodeList(stories));
  }

  @override
  Future<List<Story>> load() async {
    final stories = await _read();
    stories.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return stories;
  }

  @override
  Future<void> add(Story story) async {
    final stories = await _read()..removeWhere((s) => s.id == story.id);
    await _write([...stories, story]);
  }

  @override
  Future<void> remove(String id) async {
    final stories = await _read()..removeWhere((s) => s.id == id);
    await _write(stories);
  }
}
