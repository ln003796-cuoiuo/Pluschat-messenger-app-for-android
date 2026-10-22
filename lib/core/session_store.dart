import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SessionStore {
  SessionStore._();
  static final SessionStore instance = SessionStore._();
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
  static const _tokenKey = 'pluschat_access_token';

  Future<String?> readToken() => _storage.read(key: _tokenKey);

  Future<void> saveToken(String token) async {
    if (token.trim().isEmpty) {
      throw ArgumentError.value(token, 'token', 'Token cannot be empty');
    }
    await _storage.write(key: _tokenKey, value: token);
  }

  Future<void> clear() => _storage.delete(key: _tokenKey);
}
