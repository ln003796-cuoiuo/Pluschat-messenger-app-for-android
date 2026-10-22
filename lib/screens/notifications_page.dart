import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

class NotificationsPage extends StatefulWidget {
  const NotificationsPage({super.key, required this.api});
  final ApiClient api;
  @override
  State<NotificationsPage> createState() => _NotificationsPageState();
}

class _NotificationsPageState extends State<NotificationsPage> {
  late Future<Map<String, dynamic>> _future;
  @override
  void initState() { super.initState(); _future = widget.api.notifications(); }
  void _reload() => setState(() => _future = widget.api.notifications());

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
    future: _future,
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
      if (snapshot.hasError) return Center(child: Padding(padding: const EdgeInsets.all(24), child: Text('Не удалось загрузить уведомления.\n${snapshot.error}', textAlign: TextAlign.center)));
      final list = (snapshot.data?['notifications'] as List<dynamic>?) ?? [];
      if (list.isEmpty) return const Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
        Icon(Icons.notifications_none_rounded, size: 54, color: plusMuted),
        SizedBox(height: 12),
        Text('Пока тихо', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: plusInk)),
        SizedBox(height: 6),
        Text('Новые события появятся здесь.', style: TextStyle(color: plusMuted)),
      ]));
      return RefreshIndicator(onRefresh: () async => _reload(), child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Align(alignment: Alignment.centerRight, child: TextButton.icon(
            onPressed: () async {
              try { await widget.api.markAllNotificationsRead(); _reload(); }
              catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
            },
            icon: const Icon(Icons.done_all_rounded), label: const Text('Прочитать все'),
          )),
          ...list.map((raw) {
            final item = Map<String, dynamic>.from(raw as Map);
            final id = item['id']?.toString() ?? '';
            final isRead = item['read_at'] != null;
            final title = item['title']?.toString() ?? item['type']?.toString() ?? 'Уведомление';
            final body = item['body']?.toString() ?? item['message']?.toString() ?? '';
            return Card(
              elevation: 0, color: Colors.white, margin: const EdgeInsets.only(bottom: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: ListTile(
                leading: CircleAvatar(backgroundColor: isRead ? const Color(0xFFF0F3F7) : const Color(0xFFE0EEFF), child: Icon(isRead ? Icons.notifications_none : Icons.notifications_active_outlined, color: isRead ? plusMuted : plusBlue)),
                title: Text(title, style: TextStyle(fontWeight: isRead ? FontWeight.w500 : FontWeight.w800, color: plusInk)),
                subtitle: body.isEmpty ? null : Text(body, style: const TextStyle(color: plusMuted)),
                onTap: isRead || id.isEmpty ? null : () async {
                  try { await widget.api.markNotificationRead(id); _reload(); }
                  catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
                },
              ),
            );
          }),
        ],
      ));
    },
  );
}