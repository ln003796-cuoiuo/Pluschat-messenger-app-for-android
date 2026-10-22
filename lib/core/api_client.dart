import 'dart:convert';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;
import 'session_store.dart';

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode, this.code});
  final String message;
  final int? statusCode;
  final String? code;
  @override
  String toString() => message;
}

class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  static const String baseUrl = String.fromEnvironment(
    'PLUSCHAT_API_BASE_URL',
    defaultValue: 'https://xn--80avljg2a1c.xn--p1ai',
  );
  final http.Client _client;

  Uri _uri(String path, [Map<String, String>? query]) =>
      Uri.parse('$baseUrl$path').replace(queryParameters: query);

  Future<Map<String, dynamic>> get(String path,
      {Map<String, String>? query, bool authenticated = true}) async {
    final response = await _client.get(_uri(path, query),
        headers: await _headers(authenticated));
    return _decode(response);
  }

  Future<Map<String, dynamic>> delete(String path,
      {Map<String, String>? query, bool authenticated = true}) async {
    final response = await _client.delete(_uri(path, query),
        headers: await _headers(authenticated));
    return _decode(response);
  }

  Future<Map<String, dynamic>> post(String path,
      {Map<String, dynamic> body = const {}, bool authenticated = true}) async {
    final response = await _client.post(_uri(path),
        headers: await _headers(authenticated), body: jsonEncode(body));
    return _decode(response);
  }

  Future<Map<String, String>> _headers(bool authenticated) async {
    final headers = <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json; charset=utf-8',
    };
    if (authenticated) {
      final token = await SessionStore.instance.readToken();
      if (token != null && token.isNotEmpty) {
        headers['Authorization'] = 'Bearer $token';
      }
    }
    return headers;
  }

  Map<String, dynamic> _decode(http.Response response) {
    Map<String, dynamic> data;
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      data = decoded is Map<String, dynamic>
          ? decoded
          : <String, dynamic>{'data': decoded};
    } on FormatException {
      throw ApiException(
        'Сервер вернул ответ в неизвестном формате (${response.statusCode}).',
        statusCode: response.statusCode,
      );
    }
    if (response.statusCode < 200 ||
        response.statusCode >= 300 ||
        data['ok'] == false) {
      throw ApiException(
        (data['message'] ?? _humanError(data['error']) ??
                'Ошибка сервера (${response.statusCode})')
            .toString(),
        statusCode: response.statusCode,
        code: data['error']?.toString(),
      );
    }
    return data;
  }

  String? _humanError(dynamic error) {
    switch (error?.toString()) {
      case 'invalid_credentials': return 'Неверный логин или пароль.';
      case 'email_not_verified': return 'Сначала подтверди электронную почту.';
      case 'profile_completion_required': return 'Нужно завершить настройку профиля.';
      case 'endpoint_not_found': return 'Этот метод пока не доступен на сервере.';
      default: return null;
    }
  }

  Future<Map<String, dynamic>> health() =>
      get('/api/v1/health', authenticated: false);

  Future<Map<String, dynamic>> register({
    required String email, required String password,
    required String firstName, String lastName = '', String birthDate = '',
  }) => post('/api/v1/auth/register', authenticated: false, body: {
    'email': email.trim(), 'password': password,
    'first_name': firstName.trim(),
    if (lastName.trim().isNotEmpty) 'last_name': lastName.trim(),
    if (birthDate.isNotEmpty) 'birth_date': birthDate,
  });

  Future<Map<String, dynamic>> verifyEmail({
    required String email, required String code,
  }) => post('/api/v1/auth/verify', authenticated: false,
      body: {'email': email.trim(), 'code': code.trim()});

  Future<Map<String, dynamic>> resendEmailCode(String email) =>
      post('/api/v1/auth/resend-verification', authenticated: false,
          body: {'email': email.trim()});

  Future<Map<String, dynamic>> login({
    required String login, required String password,
  }) async {
    final deviceKey = await SessionStore.instance.deviceKey();
    return post('/api/v1/auth/login', authenticated: false, body: {
      'login': login.trim(),
      'password': password,
      'device_key': deviceKey,
      'device_name': 'Pluschat Android',
      'platform': 'android',
    });
  }

  Future<Map<String, dynamic>> setupTwoFactor() =>
      post('/api/v1/auth/2fa/setup');

  Future<Map<String, dynamic>> confirmTwoFactor({
    required String setupId, required String code,
  }) => post('/api/v1/auth/2fa/confirm',
      body: {'setup_id': setupId, 'code': code});

  Future<Map<String, dynamic>> verifyTwoFactor({
    required String challengeId, required String code,
  }) async {
    final result = await post('/api/v1/auth/2fa/verify', authenticated: false,
        body: {'challenge_id': challengeId, 'code': code});
    final token = result['token']?.toString();
    if (token != null && token.isNotEmpty) {
      await SessionStore.instance.saveToken(token);
    }
    return result;
  }

  Future<Map<String, dynamic>> completeProfile({
    required String username, String firstName = '', String lastName = '',
    String birthDate = '',
  }) => post('/api/v1/auth/profile', body: {
    'username': username.trim(),
    if (firstName.trim().isNotEmpty) 'first_name': firstName.trim(),
    if (lastName.trim().isNotEmpty) 'last_name': lastName.trim(),
    if (birthDate.isNotEmpty) 'birth_date': birthDate,
  });

  Future<Map<String, dynamic>> me() => get('/api/v1/auth/me');

  Future<Map<String, dynamic>> uploadAvatar(XFile file) async {
    final request = http.MultipartRequest('POST', _uri('/api/v1/me/avatar'));
    final token = await SessionStore.instance.readToken();
    if (token != null && token.isNotEmpty) request.headers['Authorization'] = 'Bearer $token';
    request.headers['Accept'] = 'application/json';
    request.files.add(await http.MultipartFile.fromPath('avatar', file.path, filename: file.name));
    final streamed = await _client.send(request);
    return _decode(await http.Response.fromStream(streamed));
  }
  Future<Map<String, dynamic>> forgotPassword(String email) =>
      post('/api/v1/auth/password/forgot',
          authenticated: false, body: {'email': email.trim()});

  Future<Map<String, dynamic>> resetPassword({
    required String token, required String password,
  }) => post('/api/v1/auth/password/reset', authenticated: false,
      body: {'token': token, 'password': password});

  Future<Map<String, dynamic>> search(String query) =>
      get('/api/v1/search', query: {'q': query.trim()});

  Future<Map<String, dynamic>> createDirectConversation(String userId) =>
      post('/api/v1/conversations', body: {'user_id': userId});

  Future<Map<String, dynamic>> blockUser(String userId) =>
      post('/api/v1/users/$userId/block');
  Future<Map<String, dynamic>> unblockUser(String userId) =>
      post('/api/v1/users/$userId/unblock');
  Future<Map<String, dynamic>> report(Map<String, dynamic> values) =>
      post('/api/v1/reports', body: values);
  Future<Map<String, dynamic>> adminReports() => get('/api/v1/admin/reports');

  Future<Map<String, dynamic>> sendMessage(String conversationId, String encryptedEnvelope) =>
      post('/api/v1/conversations/$conversationId/messages', body: {'body': encryptedEnvelope});
  Future<Map<String, dynamic>> editMessage(String messageId, String encryptedEnvelope) =>
      post('/api/v1/messages/$messageId/edit', body: {'body': encryptedEnvelope});
  Future<Map<String, dynamic>> deleteMessage(String messageId, {required bool forEveryone}) =>
      post('/api/v1/messages/$messageId/delete', body: {'for_everyone': forEveryone});
  Future<Map<String, dynamic>> messageReactions(String messageId) =>
      get('/api/v1/messages/$messageId/reactions');
  Future<Map<String, dynamic>> addReaction(String messageId, String reaction) =>
      post('/api/v1/messages/$messageId/reactions', body: {'reaction': reaction});
  Future<Map<String, dynamic>> removeReaction(String messageId, String reaction) =>
      post('/api/v1/messages/$messageId/reactions/remove', body: {'reaction': reaction});

  Future<Map<String, dynamic>> conversations() => get('/api/v1/conversations');
  Future<Map<String, dynamic>> messages(String id) =>
      get('/api/v1/conversations/$id/messages');
  Future<Map<String, dynamic>> markRead(String id) =>
      post('/api/v1/conversations/$id/read');

  // Social, groups, channels, notifications and account security APIs.
  Future<Map<String, dynamic>> friends() => get('/api/v1/friends');
  Future<Map<String, dynamic>> friendRequests({String direction = 'incoming'}) =>
      get('/api/v1/friends/requests', query: {'direction': direction});
  Future<Map<String, dynamic>> sendFriendRequest(String userId) =>
      post('/api/v1/friends/requests', body: {'user_id': userId});
  Future<Map<String, dynamic>> respondFriendRequest(String requestId, {required bool accept}) =>
      post("/api/v1/friends/requests/$requestId/${accept ? 'accept' : 'decline'}");
  Future<Map<String, dynamic>> removeFriend(String userId) =>
      delete('/api/v1/friends/$userId');

  Future<Map<String, dynamic>> notifications() => get('/api/v1/notifications');
  Future<Map<String, dynamic>> markNotificationRead(String id) =>
      post('/api/v1/notifications/$id/read');
  Future<Map<String, dynamic>> markAllNotificationsRead() =>
      post('/api/v1/notifications/read-all');

  Future<Map<String, dynamic>> devices() => get('/api/v1/devices');
  Future<Map<String, dynamic>> revokeDevice(String id) =>
      post('/api/v1/devices/$id/revoke');
  Future<Map<String, dynamic>> revokeOtherSessions() =>
      post('/api/v1/auth/sessions/revoke-others');
  Future<Map<String, dynamic>> settings() => get('/api/v1/settings');
  Future<Map<String, dynamic>> updateSettings(Map<String, dynamic> values) =>
      post('/api/v1/settings', body: values);

  Future<Map<String, dynamic>> groups() => get('/api/v1/groups');
  Future<Map<String, dynamic>> discoverGroups() => get('/api/v1/groups/discover');
  Future<Map<String, dynamic>> createGroup(Map<String, dynamic> values) =>
      post('/api/v1/groups', body: values);
  Future<Map<String, dynamic>> joinGroup(String id) => post('/api/v1/groups/$id/join');
  Future<Map<String, dynamic>> leaveGroup(String id) => post('/api/v1/groups/$id/leave');
  Future<Map<String, dynamic>> groupMessages(String id) => get('/api/v1/groups/$id/messages');
  Future<Map<String, dynamic>> sendGroupMessage(String id, Map<String, dynamic> values) =>
      post('/api/v1/groups/$id/messages', body: values);

  Future<Map<String, dynamic>> channels() => get('/api/v1/channels');
  Future<Map<String, dynamic>> discoverChannels() => get('/api/v1/channels/discover');
  Future<Map<String, dynamic>> createChannel(Map<String, dynamic> values) =>
      post('/api/v1/channels', body: values);
  Future<Map<String, dynamic>> subscribeChannel(String id) =>
      post('/api/v1/channels/$id/subscribe');
  Future<Map<String, dynamic>> channelPosts(String id) => get('/api/v1/channels/$id/posts');
  Future<Map<String, dynamic>> createChannelPost(String id, Map<String, dynamic> values) =>
      post('/api/v1/channels/$id/posts', body: values);

  Future<Map<String, dynamic>> createTopic(String parentPath, Map<String, dynamic> values) =>
      post('$parentPath/topics', body: values);
  Future<Map<String, dynamic>> topicMessages(String id) => get('/api/v1/topics/$id/messages');
  Future<Map<String, dynamic>> sendTopicMessage(String id, Map<String, dynamic> values) =>
      post('/api/v1/topics/$id/messages', body: values);

  Future<Map<String, dynamic>> createPoll(String conversationId, Map<String, dynamic> values) =>
      post('/api/v1/conversations/$conversationId/polls', body: values);
  Future<Map<String, dynamic>> poll(String id) => get('/api/v1/polls/$id');
  Future<Map<String, dynamic>> votePoll(String id, List<String> optionIds) =>
      post('/api/v1/polls/$id/vote', body: {'option_ids': optionIds});

  Future<Map<String, dynamic>> stories() => get('/api/v1/stories');
  Future<Map<String, dynamic>> createStory(Map<String, dynamic> values) =>
      post('/api/v1/stories', body: values);
  Future<Map<String, dynamic>> viewStory(String id) => post('/api/v1/stories/$id/view');
  Future<Map<String, dynamic>> deleteStory(String id) => delete('/api/v1/stories/$id');

  Future<Map<String, dynamic>> savedMessages() => get('/api/v1/saved-messages');
  Future<Map<String, dynamic>> saveMessage(String id) => post('/api/v1/messages/$id/save');
  Future<Map<String, dynamic>> unsaveMessage(String id) => delete('/api/v1/messages/$id/save');

  Future<Map<String, dynamic>> chatPreferences() => get('/api/v1/chat-preferences');
  Future<Map<String, dynamic>> updateConversationPreferences(String id, Map<String, dynamic> values) =>
      post('/api/v1/conversations/$id/preferences', body: values);
  Future<Map<String, dynamic>> chatFolders() => get('/api/v1/chat-folders');
  Future<Map<String, dynamic>> createChatFolder(String name) =>
      post('/api/v1/chat-folders', body: {'name': name});
  Future<Map<String, dynamic>> deleteChatFolder(String id) => delete('/api/v1/chat-folders/$id');
  Future<Map<String, dynamic>> addChatFolderItem(String folderId, String conversationId) =>
      post('/api/v1/chat-folders/$folderId/items', body: {'conversation_id': conversationId});
  Future<Map<String, dynamic>> removeChatFolderItem(String folderId, String conversationId) =>
      delete('/api/v1/chat-folders/$folderId/items/$conversationId');

  Future<Map<String, dynamic>> createCall(String conversationId, {required String type}) =>
      post('/api/v1/calls', body: {'conversation_id': conversationId, 'type': type});
  Future<Map<String, dynamic>> activeCall(String conversationId) =>
      get('/api/v1/calls/active', query: {'conversation_id': conversationId});
  Future<Map<String, dynamic>> call(String id) => get('/api/v1/calls/$id');
  Future<Map<String, dynamic>> joinCall(String id) => post('/api/v1/calls/$id/join');
  Future<Map<String, dynamic>> declineCall(String id) => post('/api/v1/calls/$id/decline');
  Future<Map<String, dynamic>> endCall(String id) => post('/api/v1/calls/$id/end');
  Future<Map<String, dynamic>> sendCallSignal(String id, Map<String, dynamic> values) =>
      post('/api/v1/calls/$id/signal', body: values);
  Future<Map<String, dynamic>> callSignals(String id, {int after = 0}) =>
      get('/api/v1/calls/$id/signals', query: {'after': '$after'});

  Future<void> logout() async {
    await post('/api/v1/auth/logout');
    await SessionStore.instance.clear();
  }

  void close() => _client.close();
}
