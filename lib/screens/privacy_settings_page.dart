import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

class PrivacySettingsPage extends StatefulWidget {
  const PrivacySettingsPage({super.key, required this.api, this.notificationsOnly = false});
  final ApiClient api;
  final bool notificationsOnly;
  @override
  State<PrivacySettingsPage> createState() => _PrivacySettingsPageState();
}

class _PrivacySettingsPageState extends State<PrivacySettingsPage> {
  bool _loading = true;
  bool _saving = false;
  String _visibility = 'contacts';
  bool _showBirthday = false;
  bool _showLocal = false;
  bool _locationSharing = false;
  bool _notifications = true;
  String? _error;

  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    try {
      final result = await widget.api.settings();
      final s = Map<String, dynamic>.from((result['settings'] as Map?) ?? const {});
      if (mounted) setState(() {
        _visibility = s['profile_visibility']?.toString() ?? 'contacts';
        _showBirthday = s['show_birth_date'] == true || s['show_birth_date'] == 1 || s['show_birth_date'] == '1';
        _showLocal = s['show_local_group'] == true || s['show_local_group'] == 1 || s['show_local_group'] == '1';
        _locationSharing = s['location_sharing'] == true || s['location_sharing'] == 1 || s['location_sharing'] == '1';
        _notifications = !(s['notifications_enabled'] == false || s['notifications_enabled'] == 0 || s['notifications_enabled'] == '0');
        _loading = false;
      });
    } catch (e) {
      if (mounted) setState(() { _error = e.toString(); _loading = false; });
    }
  }

  Future<void> _save([Map<String, dynamic>? extra]) async {
    setState(() { _saving = true; _error = null; });
    try {
      await widget.api.updateSettings(extra ?? {
        'profile_visibility': _visibility,
        'show_birth_date': _showBirthday,
        'show_local_group': _showLocal,
        'location_sharing': _locationSharing,
        'notifications_enabled': _notifications,
      });
      if (mounted) showPluschatNotice(context, 'Настройки сохранены.');
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.notificationsOnly ? 'Уведомления' : 'Конфиденциальность', style: const TextStyle(fontWeight: FontWeight.w800))),
    body: _loading ? const Center(child: CircularProgressIndicator()) : ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_error!, style: const TextStyle(color: Color(0xFFB42318)))),
        if (widget.notificationsOnly) ...[
          SwitchListTile(
            tileColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: const Text('Уведомления включены', style: TextStyle(fontWeight: FontWeight.w700)),
            subtitle: const Text('Общий переключатель на сервере'),
            value: _notifications,
            onChanged: (v) { setState(() => _notifications = v); _save({'notifications_enabled': v}); },
          ),
          const SizedBox(height: 12),
          const Text('Раздельные настройки по категориям появятся после добавления соответствующих параметров в API хоста.', style: TextStyle(color: plusMuted, height: 1.4)),
        ] else ...[
          Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Кто видит профиль', style: TextStyle(fontWeight: FontWeight.w800, color: plusInk)),
            const SizedBox(height: 8),
            RadioListTile<String>(contentPadding: EdgeInsets.zero, title: const Text('Все пользователи'), value: 'everyone', groupValue: _visibility, onChanged: (v) => setState(() => _visibility = v!)),
            RadioListTile<String>(contentPadding: EdgeInsets.zero, title: const Text('Контакты'), value: 'contacts', groupValue: _visibility, onChanged: (v) => setState(() => _visibility = v!)),
            RadioListTile<String>(contentPadding: EdgeInsets.zero, title: const Text('Никто'), value: 'nobody', groupValue: _visibility, onChanged: (v) => setState(() => _visibility = v!)),
          ])),
          const SizedBox(height: 10),
          SwitchListTile(tileColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), title: const Text('Показывать день рождения'), value: _showBirthday, onChanged: (v) => setState(() => _showBirthday = v)),
          const SizedBox(height: 6),
          SwitchListTile(tileColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), title: const Text('Показывать местную группу'), value: _showLocal, onChanged: (v) => setState(() => _showLocal = v)),
          const SizedBox(height: 6),
          SwitchListTile(tileColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), title: const Text('Разрешить использование района'), subtitle: const Text('Местоположение необязательно'), value: _locationSharing, onChanged: (v) => setState(() => _locationSharing = v)),
          const SizedBox(height: 6),
          SwitchListTile(tileColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), title: const Text('Уведомления'), value: _notifications, onChanged: (v) => setState(() => _notifications = v)),
          const SizedBox(height: 16),
          FilledButton(onPressed: _saving ? null : () => _save(), child: _saving ? const CircularProgressIndicator() : const Text('Сохранить настройки')),
        ],
      ],
    ),
  );
}