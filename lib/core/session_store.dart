import 'dart:math';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SessionStore {
  SessionStore._();
  static final SessionStore instance = SessionStore._();
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
  static const _tokenKey = 'pluschat_access_token';
  static const _deviceKey = 'pluschat_device_key';

  Future<String?> readToken() => _storage.read(key: _tokenKey);

  Future<void> saveToken(String token) async {
    if (token.trim().isEmpty) {
      throw ArgumentError.value(token, 'token', 'Token cannot be empty');
    }
    await _storage.write(key: _tokenKey, value: token);
  }

  Future<String> deviceKey() async {
    final existing = await _storage.read(key: _deviceKey);
    if (existing != null && RegExp(r'^[a-f0-9]{64}
}
).hasMatch(existing)) return existing;
    final random = Random.secure();
    final value = List.generate(32, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
    await _storage.write(key: _deviceKey, value: value);
    return value;
  }

  Future<void> clear() => _storage.delete(key: _tokenKey);
}
