import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:my_story/services/youtube/youtube_auth.dart';
import 'package:my_story/services/youtube/youtube_service.dart';
import 'package:my_story/services/youtube/youtube_uploader.dart';

import 'fakes.dart';

const _chunk = 256 * 1024;

File _video(int bytes) {
  final dir = Directory.systemTemp.createTempSync('yt-test');
  addTearDown(() => dir.deleteSync(recursive: true));
  return File('${dir.path}/story.mp4')..writeAsBytesSync(List.filled(bytes, 7));
}

http.Response _session() =>
    http.Response('', 200, headers: {'location': 'https://upload.example/session'});

void main() {
  test('uploads small files in a single request and reports completion', () async {
    final requests = <http.Request>[];
    final client = MockClient((request) async {
      requests.add(request);
      if (request.method == 'POST') return _session();
      return http.Response(jsonEncode({'id': 'vid123'}), 200);
    });
    final progress = <double>[];
    final result = await YouTubeUploader(client: client, chunkSize: _chunk).upload(
      file: _video(1000),
      accessToken: 'tok',
      title: 'My story',
      privacy: YouTubePrivacy.unlisted,
      onProgress: progress.add,
    );

    expect(result.videoId, 'vid123');
    expect(result.watchUrl.toString(), 'https://youtu.be/vid123');
    expect(progress.last, 1);

    final init = requests.first;
    expect(init.headers['Authorization'], 'Bearer tok');
    expect(init.headers['X-Upload-Content-Length'], '1000');
    final metadata = jsonDecode(init.body) as Map<String, Object?>;
    expect((metadata['status']! as Map)['privacyStatus'], 'unlisted');
    expect((metadata['snippet']! as Map)['title'], 'My story');

    expect(requests.last.headers['Content-Range'], 'bytes 0-999/1000');
    expect(requests.last.bodyBytes, hasLength(1000));
  });

  test('sends large files in 256 KiB aligned chunks, following the Range header', () async {
    final ranges = <String>[];
    final client = MockClient((request) async {
      if (request.method == 'POST') return _session();
      final range = request.headers['Content-Range']!;
      ranges.add(range);
      final end = int.parse(RegExp(r'bytes \d+-(\d+)/').firstMatch(range)!.group(1)!);
      if (end + 1 >= _chunk * 2 + 100) return http.Response('{"id":"big"}', 201);
      return http.Response('', 308, headers: {'range': 'bytes=0-$end'});
    });
    final progress = <double>[];
    final result = await YouTubeUploader(client: client, chunkSize: _chunk).upload(
      file: _video(_chunk * 2 + 100),
      accessToken: 't',
      title: 'Big',
      onProgress: progress.add,
    );

    expect(result.videoId, 'big');
    expect(ranges, [
      'bytes 0-${_chunk - 1}/${_chunk * 2 + 100}',
      'bytes $_chunk-${_chunk * 2 - 1}/${_chunk * 2 + 100}',
      'bytes ${_chunk * 2}-${_chunk * 2 + 99}/${_chunk * 2 + 100}',
    ]);
    expect(progress, orderedEquals([...progress]..sort()));
  });

  test('resumes from the server offset after a transient server error', () async {
    var puts = 0;
    final contentRanges = <String>[];
    final client = MockClient((request) async {
      if (request.method == 'POST') return _session();
      contentRanges.add(request.headers['Content-Range']!);
      puts++;
      if (puts == 1) return http.Response('', 503);
      if (request.headers['Content-Range']!.startsWith('bytes */')) {
        return http.Response('', 308, headers: {'range': 'bytes=0-${_chunk - 1}'});
      }
      return http.Response('{"id":"resumed"}', 200);
    });
    final result = await YouTubeUploader(client: client, chunkSize: _chunk).upload(
      file: _video(_chunk + 10),
      accessToken: 't',
      title: 'Resume',
    );
    expect(result.videoId, 'resumed');
    expect(contentRanges[1], startsWith('bytes */'));
    expect(contentRanges[2], startsWith('bytes $_chunk-'));
  });

  test('gives up after repeated server errors', () async {
    final client = MockClient((request) async {
      if (request.method == 'POST') return _session();
      return http.Response('', 500);
    });
    await expectLater(
      YouTubeUploader(client: client, chunkSize: _chunk, maxRetries: 1).upload(
        file: _video(100),
        accessToken: 't',
        title: 'x',
      ),
      throwsA(isA<YouTubeUploadException>()),
    );
  });

  test('surfaces the API error message when YouTube rejects the request', () async {
    final client = MockClient((request) async => http.Response(
          jsonEncode({
            'error': {'message': 'The request cannot be completed because you have exceeded your quota.'}
          }),
          403,
        ));
    await expectLater(
      YouTubeUploader(client: client).upload(file: _video(10), accessToken: 't', title: 'x'),
      throwsA(
        isA<YouTubeUploadException>()
            .having((e) => e.message, 'message', contains('quota'))
            .having((e) => e.statusCode, 'status', 403),
      ),
    );
  });

  test('rejects empty files', () async {
    await expectLater(
      YouTubeUploader(client: MockClient((_) async => http.Response('', 500))).upload(
        file: _video(0),
        accessToken: 't',
        title: 'x',
      ),
      throwsA(isA<YouTubeUploadException>()),
    );
  });

  test('YouTubeService refreshes the token once after a 401', () async {
    final tokens = <String>[];
    final client = MockClient((request) async {
      final token = request.headers['Authorization']!;
      if (request.method == 'POST') {
        tokens.add(token);
        if (token.endsWith('token-1')) {
          return http.Response('{"error":{"message":"expired"}}', 401);
        }
        return _session();
      }
      return http.Response('{"id":"ok"}', 200);
    });
    final auth = FakeYouTubeAuth()..account = const YouTubeAccount(email: 'a@b.c');
    final service = YouTubeService(auth: auth, uploader: YouTubeUploader(client: client));
    final result = await service.upload(file: _video(10), title: 'x');
    expect(result.videoId, 'ok');
    expect(tokens, ['Bearer token-1', 'Bearer token-2']);
  });
}
