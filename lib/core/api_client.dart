import 'dart:convert';
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
      case 'phone_not_verified': return 'Сначала подтверди номер телефона.';
      case 'profile_completion_required': return 'Нужно завершить настройку профиля.';
      case 'endpoint_not_found': return 'Этот метод пока не доступен на сервере.';
      default: return null;
    }
  }

  Future<Map<String, dynamic>> health() =>
      get('/api/v1/health', authenticated: false);

  Future<Map<String, dynamic>> register({
    required String email, required String phone, required String password,
    required String firstName, String lastName = '', String birthDate = '',
  }) => post('/api/v1/auth/register', authenticated: false, body: {
    'email': email.trim(), 'phone': phone.trim(), 'password': password,
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

  Future<Map<String, dynamic>> resendPhoneCode(String phone) =>
      post('/api/v1/auth/phone/resend', authenticated: false,
          body: {'phone': phone.trim()});

  Future<Map<String, dynamic>> verifyPhone({
    required String phone, required String code,
  }) => post('/api/v1/auth/phone/verify', authenticated: false,
      body: {'phone': phone.trim(), 'code': code.trim()});

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
      post('/api/v1/conversations', body: {'recipient_id': userId});

  Future<Map<String, dynamic>> conversations() => get('/api/v1/conversations');
  Future<Map<String, dynamic>> messages(String id) =>
      get('/api/v1/conversations/$id/messages');
  Future<Map<String, dynamic>> markRead(String id) =>
      post('/api/v1/conversations/$id/read');

  Future<void> logout() async {
    await post('/api/v1/auth/logout');
    await SessionStore.instance.clear();
  }

  void close() => _client.close();
}
