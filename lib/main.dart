import 'package:flutter/material.dart';
import 'core/api_client.dart';
import 'core/session_store.dart';

void main() => runApp(const PluschatApp());

const _blue = Color(0xFF2787F5);
const _ink = Color(0xFF182536);
const _muted = Color(0xFF7C8998);
const _surface = Color(0xFFF4F7FB);

class PluschatApp extends StatelessWidget {
  const PluschatApp({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(seedColor: _blue, brightness: Brightness.light);
    return MaterialApp(
      title: 'Плюс Чат',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: _surface,
        appBarTheme: const AppBarTheme(
          backgroundColor: _surface,
          foregroundColor: _ink,
          centerTitle: false,
          elevation: 0,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          hintStyle: const TextStyle(color: _muted),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
          enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: Colors.black.withValues(alpha: .04))),
          focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: _blue, width: 1.5)),
        ),
      ),
      home: const AppGate(),
    );
  }
}

class AppGate extends StatefulWidget {
  const AppGate({super.key});
  @override
  State<AppGate> createState() => _AppGateState();
}

class _AppGateState extends State<AppGate> {
  final _api = ApiClient();
  bool _checking = true;
  bool _signedIn = false;

  @override
  void initState() {
    super.initState();
    _restore();
  }

  Future<void> _restore() async {
    final token = await SessionStore.instance.readToken();
    if (token != null) {
      try {
        await _api.me();
        _signedIn = true;
      } catch (_) {
        await SessionStore.instance.clear();
      }
    }
    if (mounted) setState(() => _checking = false);
  }

  void _authenticated() {
    setState(() => _signedIn = true);
  }

  @override
  Widget build(BuildContext context) {
    if (_checking) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return _signedIn
        ? MessengerShell(api: _api, onLogout: () async {
            try { await _api.logout(); } catch (_) { await SessionStore.instance.clear(); }
            if (mounted) setState(() => _signedIn = false);
          })
        : AuthScreen(api: _api, onAuthenticated: _authenticated);
  }
}

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key, required this.api, required this.onAuthenticated});
  final ApiClient api;
  final VoidCallback onAuthenticated;

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

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    await _run(() async {
      if (_register) {
        await widget.api.register(
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
      widget.onAuthenticated();
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
      widget.onAuthenticated();
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
      widget.onAuthenticated();
    });
  }

  Future<void> _verifyEmail() async {
    await _run(() async {
      await widget.api.verifyEmail(email: _email.text, code: _code.text);
      if (mounted) setState(() {
        _needsEmailCode = false;
        _register = false;
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
                      decoration: BoxDecoration(color: _blue, borderRadius: BorderRadius.circular(26), boxShadow: [BoxShadow(color: _blue.withValues(alpha: .22), blurRadius: 24, offset: const Offset(0, 10))]),
                      child: const Icon(Icons.chat_bubble_rounded, color: Colors.white, size: 38),
                    ),
                  ),
                  const SizedBox(height: 22),
                  const Text('Плюс Чат', textAlign: TextAlign.center, style: TextStyle(fontSize: 30, fontWeight: FontWeight.w800, color: _ink, letterSpacing: -.7)),
                  const SizedBox(height: 8),
                  Text(verify ? 'Подтверждение аккаунта' : 'Общение без лишних сложностей', textAlign: TextAlign.center, style: const TextStyle(color: _muted, fontSize: 15)),
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
                          Text(_needsPhoneCode ? 'Код из SMS' : _needsEmailCode ? 'Код из письма' : _needsTwoFactorSetup ? 'Настройка двухэтапной защиты' : 'Код двухэтапной защиты', style: const TextStyle(fontWeight: FontWeight.w700, color: _ink)),
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
                  const Text('Защита аккаунта и приватность — в основе Pluschat.', textAlign: TextAlign.center, style: TextStyle(color: _muted, fontSize: 12)),
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
      child: Text(label, textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: selected ? _ink : _muted)),
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

class MessengerShell extends StatefulWidget {
  const MessengerShell({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;
  @override
  State<MessengerShell> createState() => _MessengerShellState();
}

class _MessengerShellState extends State<MessengerShell> {
  int _tab = 0;
  final _titles = const ['Чаты', 'Контакты', 'Уведомления', 'Профиль'];

  @override
  Widget build(BuildContext context) {
    final pages = [
      ChatsPage(api: widget.api),
      FeaturePage(icon: Icons.people_alt_outlined, title: 'Контакты', subtitle: 'Поиск людей и заявки в друзья будут подключены к API контактов.'),
      FeaturePage(icon: Icons.notifications_none_rounded, title: 'Уведомления', subtitle: 'Уведомления и настройки по категориям подключаются к серверным данным.'),
      ProfilePage(api: widget.api, onLogout: widget.onLogout),
    ];
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_tab], style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w800, letterSpacing: -.5)),
        actions: [
          if (_tab == 0) IconButton(onPressed: () => setState(() {}), icon: const Icon(Icons.search_rounded), tooltip: 'Поиск'),
          const SizedBox(width: 8),
        ],
      ),
      body: IndexedStack(index: _tab, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (value) => setState(() => _tab = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.chat_bubble_outline_rounded), selectedIcon: Icon(Icons.chat_bubble_rounded), label: 'Чаты'),
          NavigationDestination(icon: Icon(Icons.people_outline_rounded), selectedIcon: Icon(Icons.people_rounded), label: 'Контакты'),
          NavigationDestination(icon: Icon(Icons.notifications_none_rounded), selectedIcon: Icon(Icons.notifications_rounded), label: 'События'),
          NavigationDestination(icon: Icon(Icons.person_outline_rounded), selectedIcon: Icon(Icons.person_rounded), label: 'Профиль'),
        ],
      ),
    );
  }
}

class ChatsPage extends StatefulWidget {
  const ChatsPage({super.key, required this.api});
  final ApiClient api;
  @override
  State<ChatsPage> createState() => _ChatsPageState();
}

class _ChatsPageState extends State<ChatsPage> {
  late Future<Map<String, dynamic>> _future;
  @override
  void initState() { super.initState(); _future = widget.api.conversations(); }
  void _reload() => setState(() => _future = widget.api.conversations());

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async => _reload(),
      child: FutureBuilder<Map<String, dynamic>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return ListView(children: [
              const SizedBox(height: 110),
              const Icon(Icons.cloud_off_rounded, size: 52, color: _muted),
              const SizedBox(height: 16),
              const Text('Не удалось загрузить чаты', textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18, color: _ink)),
              const SizedBox(height: 8),
              Padding(padding: const EdgeInsets.symmetric(horizontal: 28), child: Text(snapshot.error.toString(), textAlign: TextAlign.center, style: const TextStyle(color: _muted))),
              const SizedBox(height: 18),
              Center(child: OutlinedButton.icon(onPressed: _reload, icon: const Icon(Icons.refresh), label: const Text('Повторить'))),
            ]);
          }
          final list = (snapshot.data?['conversations'] as List<dynamic>?) ?? const [];
          if (list.isEmpty) {
            return ListView(children: const [
              SizedBox(height: 95),
              Center(child: CircleAvatar(radius: 42, backgroundColor: Color(0xFFE0EEFF), child: Icon(Icons.forum_outlined, color: _blue, size: 38))),
              SizedBox(height: 18),
              Text('Пока нет чатов', textAlign: TextAlign.center, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: _ink)),
              SizedBox(height: 8),
              Padding(padding: EdgeInsets.symmetric(horizontal: 40), child: Text('Когда начнёшь переписку, она появится здесь.', textAlign: TextAlign.center, style: TextStyle(color: _muted, height: 1.4))),
            ]);
          }
          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            itemCount: list.length,
            separatorBuilder: (_, __) => const SizedBox(height: 5),
            itemBuilder: (context, index) {
              final item = Map<String, dynamic>.from(list[index] as Map);
              final peer = Map<String, dynamic>.from((item['other_user'] as Map?) ?? const {});
              final name = [peer['first_name'], peer['last_name']].where((e) => e != null && e.toString().isNotEmpty).join(' ');
              final title = name.isEmpty ? '@${peer['username'] ?? 'user'}' : name;
              return Material(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                  leading: CircleAvatar(radius: 26, backgroundColor: const Color(0xFFDCEBFF), child: Text(title.characters.first.toUpperCase(), style: const TextStyle(color: _blue, fontWeight: FontWeight.w800, fontSize: 18))),
                  title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, color: _ink)),
                  subtitle: Text('Открой переписку', style: const TextStyle(color: _muted)),
                  trailing: const Icon(Icons.chevron_right_rounded, color: _muted),
                  onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ConversationPage(api: widget.api, conversation: item, title: title))),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

class ConversationPage extends StatefulWidget {
  const ConversationPage({super.key, required this.api, required this.conversation, required this.title});
  final ApiClient api;
  final Map<String, dynamic> conversation;
  final String title;
  @override
  State<ConversationPage> createState() => _ConversationPageState();
}

class _ConversationPageState extends State<ConversationPage> {
  late Future<Map<String, dynamic>> _future;
  @override
  void initState() {
    super.initState();
    final id = widget.conversation['id'].toString();
    _future = widget.api.messages(id);
    widget.api.markRead(id).catchError((_) => <String, dynamic>{});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title, style: const TextStyle(fontWeight: FontWeight.w700)), backgroundColor: Colors.white),
      body: Column(children: [
        Expanded(child: FutureBuilder<Map<String, dynamic>>(
          future: _future,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
            if (snapshot.hasError) return Center(child: Text('Не удалось загрузить историю\n${snapshot.error}', textAlign: TextAlign.center));
            final messages = (snapshot.data?['messages'] as List<dynamic>?) ?? const [];
            if (messages.isEmpty) return const Center(child: Text('Начни переписку', style: TextStyle(color: _muted)));
            return ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: messages.length,
              itemBuilder: (context, index) {
                final message = Map<String, dynamic>.from(messages[index] as Map);
                return Align(
                  alignment: Alignment.centerLeft,
                  child: Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(14),
                    constraints: const BoxConstraints(maxWidth: 300),
                    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)),
                    child: const Text('Зашифрованное сообщение. Для безопасного отображения требуется расшифровка на устройстве.', style: TextStyle(color: _muted, height: 1.35)),
                  ),
                );
              },
            );
          },
        )),
        Container(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
          decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: Color(0xFFE8EDF3)))),
          child: SafeArea(top: false, child: Row(children: [
            IconButton(onPressed: () => _notice(context, 'Вложения подключим через защищённый API файлов.'), icon: const Icon(Icons.attach_file_rounded, color: _blue)),
            Expanded(child: TextField(enabled: false, decoration: const InputDecoration(hintText: 'Сообщение', fillColor: Color(0xFFF4F7FB)))),
            const SizedBox(width: 8),
            IconButton(onPressed: () => _notice(context, 'Отправка включится после подключения клиентского E2EE. Открытый текст на сервер не отправляем.'), icon: const Icon(Icons.send_rounded, color: _blue)),
          ])),
        ),
      ]),
    );
  }
}

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Container(padding: const EdgeInsets.all(22), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22)), child: Column(children: [
        const CircleAvatar(radius: 38, backgroundColor: Color(0xFFDCEBFF), child: Icon(Icons.person_rounded, size: 42, color: _blue)),
        const SizedBox(height: 12),
        const Text('Ваш профиль', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: _ink)),
        const SizedBox(height: 5),
        const Text('Управление аккаунтом и безопасностью', textAlign: TextAlign.center, style: TextStyle(color: _muted)),
      ])),
      const SizedBox(height: 14),
      _profileItem(context, Icons.shield_outlined, 'Безопасность и устройства', '2FA, сессии, ключ восстановления'),
      _profileItem(context, Icons.lock_outline_rounded, 'Конфиденциальность', 'Видимость профиля и блокировки'),
      _profileItem(context, Icons.notifications_none_rounded, 'Уведомления', 'Звуки и параметры чатов'),
      _profileItem(context, Icons.palette_outlined, 'Оформление', 'Тема и внешний вид'),
      _profileItem(context, Icons.cloud_outlined, 'Состояние сервера', 'Проверить подключение', onTap: () async {
        try {
          final result = await api.health();
          if (context.mounted) _notice(context, 'API: ${result['service'] ?? 'online'}; БД: ${result['database'] ?? 'неизвестно'}; схема: ${result['schema'] ?? 'неизвестно'}');
        } catch (e) {
          if (context.mounted) _notice(context, 'Сервер недоступен: $e');
        }
      }),
      const SizedBox(height: 16),
      OutlinedButton.icon(onPressed: () => onLogout(), icon: const Icon(Icons.logout_rounded), label: const Text('Выйти из аккаунта'), style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52), foregroundColor: const Color(0xFFB42318))),
    ],
  );

  Widget _profileItem(BuildContext context, IconData icon, String title, String subtitle, {VoidCallback? onTap}) => Card(
    elevation: 0, color: Colors.white, margin: const EdgeInsets.only(bottom: 7),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(17)),
    child: ListTile(
      leading: Container(width: 42, height: 42, decoration: BoxDecoration(color: const Color(0xFFEAF2FF), borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: _blue)),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700, color: _ink)),
      subtitle: Text(subtitle, style: const TextStyle(color: _muted, fontSize: 12)),
      trailing: const Icon(Icons.chevron_right_rounded, color: _muted),
      onTap: onTap ?? () => _notice(context, 'Этот раздел будет подключён к серверному API в следующем этапе.'),
    ),
  );
}

class FeaturePage extends StatelessWidget {
  const FeaturePage({super.key, required this.icon, required this.title, required this.subtitle});
  final IconData icon;
  final String title;
  final String subtitle;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        CircleAvatar(radius: 42, backgroundColor: const Color(0xFFE0EEFF), child: Icon(icon, size: 40, color: _blue)),
        const SizedBox(height: 18),
        Text(title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: _ink)),
        const SizedBox(height: 8),
        Text(subtitle, textAlign: TextAlign.center, style: const TextStyle(color: _muted, height: 1.5)),
      ]),
    ),
  );
}

void _notice(BuildContext context, String text) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text), behavior: SnackBarBehavior.floating));
}
