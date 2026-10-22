import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/session_store.dart';
import '../core/ui_helpers.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key, required this.api, required this.onAuthenticated});
  final ApiClient api;
  final Future<void> Function() onAuthenticated;

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _formKey = GlobalKey<FormState>();
  final _login = TextEditingController();
  final _password = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _first = TextEditingController();
  final _last = TextEditingController();
  final _code = TextEditingController();
  bool _register = false;
  bool _busy = false;
  bool _obscure = true;
  String? _message;
  bool _error = false;
  bool _needsEmailCode = false;
  bool _needsPhoneCode = false;
  bool _needsTwoFactorCode = false;
  bool _needsTwoFactorSetup = false;
  String? _challengeId;
  String? _setupId;
  String? _setupSecret;

  @override
  void dispose() {
    for (final c in [_login, _password, _email, _phone, _first, _last, _code]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() { _busy = true; _message = null; });
    try {
      await action();
    } on ApiException catch (e) {
      if (mounted) setState(() { _message = e.message; _error = true; });
    } catch (_) {
      if (mounted) setState(() { _message = 'Не удалось подключиться. Проверь интернет и доступность сервера.'; _error = true; });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _forgotPassword() async {
    final emailController = TextEditingController(text: _login.text.contains('@') ? _login.text : '');
    final email = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Восстановление пароля'),
        content: TextField(
          controller: emailController,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(hintText: 'Электронная почта'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Отмена')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, emailController.text.trim()), child: const Text('Отправить ссылку')),
        ],
      ),
    );
    emailController.dispose();
    if (email == null || email.isEmpty || !mounted) return;
    await _run(() async {
      await widget.api.forgotPassword(email);
      if (mounted) setState(() {
        _message = 'Если аккаунт с таким адресом существует, инструкция по восстановлению отправлена на почту.';
        _error = false;
      });
    });
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    await _run(() async {
      if (_register) {
        final result = await widget.api.register(
          email: _email.text,
          phone: _phone.text,
          password: _password.text,
          firstName: _first.text,
          lastName: _last.text,
        );
        final smsSent = result['phone_verification_sent'] == true;
        if (mounted) setState(() {
          _needsPhoneCode = true;
          _message = smsSent
              ? 'Аккаунт создан. Введи код из SMS; код подтверждения почты придёт отдельно.'
              : 'Аккаунт создан, но сервер не подтвердил отправку SMS. Можно запросить код повторно через 60 секунд.';
          _error = !smsSent;
        });
        return;
      }
      final result = await widget.api.login(login: _login.text, password: _password.text);
      final token = result['token']?.toString();
      if (token != null && token.isNotEmpty) {
        await SessionStore.instance.saveToken(token);
      }
      if (result['requires_2fa'] == true) {
        _challengeId = result['challenge_id']?.toString();
        if (_challengeId == null || _challengeId!.isEmpty) {
          throw const ApiException('Сервер не вернул идентификатор проверки 2FA.');
        }
        if (mounted) setState(() {
          _needsTwoFactorCode = true;
          _message = 'Введи шестизначный код из приложения-аутентификатора.';
          _error = false;
        });
        return;
      }
      if (result['requires_2fa_setup'] == true) {
        if (token == null || token.isEmpty) {
          throw const ApiException('Сервер не выдал временный токен для настройки 2FA.');
        }
        final setup = await widget.api.setupTwoFactor();
        _setupId = setup['setup_id']?.toString();
        _setupSecret = setup['secret']?.toString();
        if (_setupId == null || _setupSecret == null) {
          throw const ApiException('Сервер не вернул данные для настройки 2FA.');
        }
        if (mounted) setState(() {
          _needsTwoFactorSetup = true;
          _message = 'Добавь этот секрет в приложение-аутентификатор (например, Aegis или Google Authenticator), затем введи текущий 6-значный код.';
          _error = false;
        });
        return;
      }
      await widget.onAuthenticated();
    });
  }

  Future<void> _verifyPhone() async {
    await _run(() async {
      await widget.api.verifyPhone(phone: _phone.text, code: _code.text);
      if (mounted) setState(() {
        _needsPhoneCode = false;
        _needsEmailCode = true;
        _message = 'Телефон подтверждён. Теперь введи шестизначный код из письма.';
        _error = false;
      });
    });
  }

  Future<void> _verifyLoginTwoFactor() async {
    await _run(() async {
      final challenge = _challengeId;
      if (challenge == null) throw const ApiException('Запрос проверки 2FA истёк. Войди заново.');
      await widget.api.verifyTwoFactor(challengeId: challenge, code: _code.text);
      await widget.onAuthenticated();
    });
  }

  Future<void> _confirmTwoFactorSetup() async {
    await _run(() async {
      final setupId = _setupId;
      if (setupId == null) throw const ApiException('Настройка 2FA истекла. Войди заново.');
      final result = await widget.api.confirmTwoFactor(setupId: setupId, code: _code.text);
      final token = result['token']?.toString();
      if (token != null && token.isNotEmpty) await SessionStore.instance.saveToken(token);
      _setupId = null;
      _setupSecret = null;
      await widget.onAuthenticated();
    });
  }

  Future<void> _verifyEmail() async {
    await _run(() async {
      await widget.api.verifyEmail(email: _email.text, code: _code.text);
      if (mounted) setState(() {
        _needsEmailCode = false;
        _register = false;
        _login.text = _email.text;
        _message = 'Почта подтверждена. Теперь можно войти и завершить настройку защиты.';
        _error = false;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final verify = _needsPhoneCode || _needsEmailCode || _needsTwoFactorCode || _needsTwoFactorSetup;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 28, 24, 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Center(
                    child: Container(
                      width: 76, height: 76,
                      decoration: BoxDecoration(color: plusBlue, borderRadius: BorderRadius.circular(26), boxShadow: [BoxShadow(color: plusBlue.withValues(alpha: .22), blurRadius: 24, offset: const Offset(0, 10))]),
                      child: const Icon(Icons.chat_bubble_rounded, color: Colors.white, size: 38),
                    ),
                  ),
                  const SizedBox(height: 22),
                  const Text('Плюс Чат', textAlign: TextAlign.center, style: TextStyle(fontSize: 30, fontWeight: FontWeight.w800, color: plusInk, letterSpacing: -.7)),
                  const SizedBox(height: 8),
                  Text(verify ? 'Подтверждение аккаунта' : 'Общение без лишних сложностей', textAlign: TextAlign.center, style: const TextStyle(color: plusMuted, fontSize: 15)),
                  const SizedBox(height: 28),
                  if (!verify) ...[
                    Container(
                      padding: const EdgeInsets.all(5),
                      decoration: BoxDecoration(color: const Color(0xFFE8EEF6), borderRadius: BorderRadius.circular(16)),
                      child: Row(children: [
                        Expanded(child: _modeButton('Войти', !_register, () => setState(() { _register = false; _message = null; }))),
                        Expanded(child: _modeButton('Создать аккаунт', _register, () => setState(() { _register = true; _message = null; }))),
                      ]),
                    ),
                    const SizedBox(height: 22),
                  ],
                  Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        if (verify) ...[
                          Text(_needsPhoneCode ? 'Код из SMS' : _needsEmailCode ? 'Код из письма' : _needsTwoFactorSetup ? 'Настройка двухэтапной защиты' : 'Код двухэтапной защиты', style: const TextStyle(fontWeight: FontWeight.w700, color: plusInk)),
                          const SizedBox(height: 8),
                          TextFormField(controller: _code, keyboardType: TextInputType.number, maxLength: 6, decoration: const InputDecoration(hintText: '123456', counterText: ''), validator: (v) => (v == null || !RegExp(r'^\d{6}$').hasMatch(v)) ? 'Введи 6 цифр' : null),
                          const SizedBox(height: 14),
                          FilledButton(onPressed: _busy ? null : (_needsPhoneCode ? _verifyPhone : _needsEmailCode ? _verifyEmail : _needsTwoFactorSetup ? _confirmTwoFactorSetup : _verifyLoginTwoFactor), child: _busy ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2)) : Text(_needsTwoFactorSetup ? 'Включить 2FA' : _needsTwoFactorCode ? 'Проверить код' : 'Подтвердить')),
                          if (_needsPhoneCode) TextButton(
                            onPressed: _busy ? null : () => _run(() async {
                              await widget.api.resendPhoneCode(_phone.text);
                              if (mounted) setState(() { _message = 'Если номер зарегистрирован, новый SMS-код отправлен.'; _error = false; });
                            }),
                            child: const Text('Запросить SMS-код повторно'),
                          ),
                          if (_needsEmailCode) TextButton(
                            onPressed: _busy ? null : () => _run(() async {
                              await widget.api.resendEmailCode(_email.text);
                              if (mounted) setState(() { _message = 'Если адрес зарегистрирован и не подтверждён, письмо отправлено.'; _error = false; });
                            }),
                            child: const Text('Отправить письмо повторно'),
                          ),
                        ] else ...[
                          if (_register) ...[
                            _field(_first, 'Имя', icon: Icons.person_outline, validator: (v) => (v == null || v.trim().isEmpty) ? 'Имя обязательно' : null),
                            const SizedBox(height: 12),
                            _field(_last, 'Фамилия (необязательно)', icon: Icons.badge_outlined),
                            const SizedBox(height: 12),
                            _field(_email, 'Электронная почта', icon: Icons.mail_outline, type: TextInputType.emailAddress, validator: (v) => (v == null || !v.contains('@')) ? 'Укажи корректный email' : null),
                            const SizedBox(height: 12),
                            _field(_phone, 'Телефон, например +79991234567', icon: Icons.phone_outlined, type: TextInputType.phone, validator: (v) => (v == null || !RegExp(r'^\+[1-9]\d{7,14}$').hasMatch(v.replaceAll(' ', ''))) ? 'Нужен международный формат с +' : null),
                          ] else
                            _field(_login, 'Email или никнейм', icon: Icons.person_outline, type: TextInputType.emailAddress, validator: (v) => (v == null || v.trim().isEmpty) ? 'Заполни поле' : null),
                          if (_register) const SizedBox(height: 12),
                          _field(_password, 'Пароль', icon: Icons.lock_outline, obscure: _obscure, validator: (v) => (v == null || v.length < 10) ? 'Минимум 10 символов' : null, suffix: IconButton(onPressed: () => setState(() => _obscure = !_obscure), icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined))),
                          const SizedBox(height: 20),
                          FilledButton(
                            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(54), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
                            onPressed: _busy ? null : _submit,
                            child: _busy ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : Text(_register ? 'Создать аккаунт' : 'Войти', style: const TextStyle(fontWeight: FontWeight.w700)),
                          ),
                          if (!_register) Align(
                            alignment: Alignment.centerRight,
                            child: TextButton(onPressed: _busy ? null : _forgotPassword, child: const Text('Забыли пароль?')),
                          ),
                        ],
                      ],
                    ),
                  ),
                  if (_message != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(color: _error ? const Color(0xFFFFEEEE) : const Color(0xFFE7F7ED), borderRadius: BorderRadius.circular(14)),
                      child: Text(_message!, style: TextStyle(color: _error ? const Color(0xFFB42318) : const Color(0xFF18794E), height: 1.4)),
                    ),
                  ],
                  const SizedBox(height: 20),
                  const Text('Защита аккаунта и приватность — в основе Pluschat.', textAlign: TextAlign.center, style: TextStyle(color: plusMuted, fontSize: 12)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _modeButton(String label, bool selected, VoidCallback onTap) => InkWell(
    borderRadius: BorderRadius.circular(12),
    onTap: onTap,
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
      decoration: BoxDecoration(color: selected ? Colors.white : Colors.transparent, borderRadius: BorderRadius.circular(12), boxShadow: selected ? [BoxShadow(color: Colors.black.withValues(alpha: .04), blurRadius: 8)] : null),
      child: Text(label, textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: selected ? plusInk : plusMuted)),
    ),
  );

  Widget _field(TextEditingController controller, String hint, {
    IconData? icon, TextInputType? type, bool obscure = false,
    String? Function(String?)? validator, Widget? suffix,
  }) => TextFormField(
    controller: controller, keyboardType: type, obscureText: obscure,
    validator: validator, autocorrect: false,
    decoration: InputDecoration(hintText: hint, prefixIcon: icon == null ? null : Icon(icon), suffixIcon: suffix),
  );
}


class ProfileCompletionScreen extends StatefulWidget {
  const ProfileCompletionScreen({super.key, required this.api, required this.onComplete});
  final ApiClient api;
  final VoidCallback onComplete;
  @override
  State<ProfileCompletionScreen> createState() => _ProfileCompletionScreenState();
}

class _ProfileCompletionScreenState extends State<ProfileCompletionScreen> {
  final _username = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() { _username.dispose(); super.dispose(); }

  Future<void> _save() async {
    final username = _username.text.trim();
    if (!RegExp(r'^[A-Za-z0-9_]{3,32}$').hasMatch(username)) {
      setState(() => _error = 'Никнейм: 3–32 латинских буквы, цифры или подчёркивания.');
      return;
    }
    setState(() { _busy = true; _error = null; });
    try {
      await widget.api.completeProfile(username: username);
      widget.onComplete();
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Заверши профиль')),
    body: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440), child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Icon(Icons.verified_user_rounded, size: 64, color: plusBlue),
          const SizedBox(height: 18),
          const Text('Выбери свой никнейм', textAlign: TextAlign.center, style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: plusInk)),
          const SizedBox(height: 10),
          const Text('Он будет виден другим пользователям и поможет найти тебя в Pluschat.', textAlign: TextAlign.center, style: TextStyle(color: plusMuted, height: 1.4)),
          const SizedBox(height: 24),
          TextField(controller: _username, autocorrect: false, decoration: const InputDecoration(prefixText: '@', hintText: 'nickname')),
          const SizedBox(height: 16),
          FilledButton(onPressed: _busy ? null : _save, child: _busy ? const CircularProgressIndicator() : const Text('Продолжить')),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!, style: const TextStyle(color: Color(0xFFB42318)))),
        ],
      )),
    )),
  );
}


