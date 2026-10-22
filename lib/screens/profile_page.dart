import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';
import 'devices_page.dart';
import 'privacy_settings_page.dart';

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Container(padding: const EdgeInsets.all(22), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22)), child: Column(children: [
        const CircleAvatar(radius: 38, backgroundColor: Color(0xFFDCEBFF), child: Icon(Icons.person_rounded, size: 42, color: plusBlue)),
        const SizedBox(height: 12),
        FutureBuilder<Map<String, dynamic>>(
          future: api.me(),
          builder: (context, snapshot) {
            final user = Map<String, dynamic>.from((snapshot.data?['user'] as Map?) ?? const {});
            final first = user['first_name']?.toString() ?? '';
            final last = user['last_name']?.toString() ?? '';
            final full = '$first $last'.trim();
            final name = full.isEmpty ? (user['username']?.toString().isNotEmpty == true ? "@${user['username']}" : 'Ваш профиль') : full;
            return Column(children: [
              Text(name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: plusInk), textAlign: TextAlign.center),
              const SizedBox(height: 4),
              Text(user['email']?.toString() ?? 'Управление аккаунтом и безопасностью', textAlign: TextAlign.center, style: const TextStyle(color: plusMuted)),
            ]);
          },
        ),
      ])),
      const SizedBox(height: 14),
      _profileItem(context, Icons.shield_outlined, 'Безопасность и устройства', 'Активные сессии и устройства', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => DevicesPage(api: api)))),
      _profileItem(context, Icons.lock_outline_rounded, 'Конфиденциальность', 'Видимость профиля и разрешения', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PrivacySettingsPage(api: api)))),
      _profileItem(context, Icons.notifications_none_rounded, 'Уведомления', 'Общие настройки уведомлений', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PrivacySettingsPage(api: api, notificationsOnly: true)))),
      _profileItem(context, Icons.palette_outlined, 'Оформление', 'Тема и внешний вид'),
      _profileItem(context, Icons.cloud_outlined, 'Состояние сервера', 'Проверить подключение', onTap: () async {
        try {
          final result = await api.health();
          if (context.mounted) showPluschatNotice(context, "API: ${result['service'] ?? 'online'}; БД: ${result['database'] ?? 'неизвестно'}; схема: ${result['schema'] ?? 'неизвестно'}");
        } catch (e) {
          if (context.mounted) showPluschatNotice(context, 'Сервер недоступен: $e');
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
      leading: Container(width: 42, height: 42, decoration: BoxDecoration(color: const Color(0xFFEAF2FF), borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: plusBlue)),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700, color: plusInk)),
      subtitle: Text(subtitle, style: const TextStyle(color: plusMuted, fontSize: 12)),
      trailing: const Icon(Icons.chevron_right_rounded, color: plusMuted),
      onTap: onTap ?? () => showPluschatNotice(context, 'Этот раздел будет подключён к серверному API в следующем этапе.'),
    ),
  );
}