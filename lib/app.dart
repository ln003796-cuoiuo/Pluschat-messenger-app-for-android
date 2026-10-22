import 'package:flutter/material.dart';
import 'core/api_client.dart';
import 'core/session_store.dart';
import 'core/ui_helpers.dart';
import 'screens/auth_screen.dart';
import 'screens/messenger_screens.dart';

class PluschatApp extends StatelessWidget {
  const PluschatApp({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(seedColor: plusBlue, brightness: Brightness.light);
    return MaterialApp(
      title: 'Плюс Чат',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: plusSurface,
        appBarTheme: const AppBarTheme(
          backgroundColor: plusSurface,
          foregroundColor: plusInk,
          centerTitle: false,
          elevation: 0,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          hintStyle: const TextStyle(color: plusMuted),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
          enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide(color: Colors.black.withValues(alpha: .04))),
          focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: plusBlue, width: 1.5)),
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
  bool _needsProfileCompletion = false;

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
      } on ApiException catch (e) {
        if (e.code == 'profile_completion_required') {
          _signedIn = true;
          _needsProfileCompletion = true;
        } else {
          await SessionStore.instance.clear();
        }
      } catch (_) {
        await SessionStore.instance.clear();
      }
    }
    if (mounted) setState(() => _checking = false);
  }

  Future<void> _authenticated() async {
    var needsProfile = false;
    try {
      await _api.me();
    } on ApiException catch (e) {
      needsProfile = e.code == 'profile_completion_required';
      if (!needsProfile) rethrow;
    }
    if (mounted) setState(() {
      _signedIn = true;
      _needsProfileCompletion = needsProfile;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_checking) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_signedIn && _needsProfileCompletion) {
      return ProfileCompletionScreen(api: _api, onComplete: () {
        setState(() => _needsProfileCompletion = false);
      });
    }
    return _signedIn
        ? MessengerShell(api: _api, onLogout: () async {
            try { await _api.logout(); } catch (_) { await SessionStore.instance.clear(); }
            if (mounted) setState(() => _signedIn = false);
          })
        : AuthScreen(api: _api, onAuthenticated: _authenticated);
  }
}

