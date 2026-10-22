import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

class DevicesPage extends StatefulWidget {
  const DevicesPage({super.key, required this.api});
  final ApiClient api;
  @override
  State<DevicesPage> createState() => _DevicesPageState();
}

class _DevicesPageState extends State<DevicesPage> {
  late Future<Map<String, dynamic>> _future;
  @override
  void initState() { super.initState(); _future = widget.api.devices(); }
  void _reload() => setState(() => _future = widget.api.devices());

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Устройства и сессии', style: TextStyle(fontWeight: FontWeight.w800))),
    body: FutureBuilder<Map<String, dynamic>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError) return Center(child: Padding(padding: const EdgeInsets.all(24), child: Text('Не удалось загрузить устройства:\n${snapshot.error}', textAlign: TextAlign.center)));
        final devices = (snapshot.data?['devices'] as List<dynamic>?) ?? [];
        return ListView(padding: const EdgeInsets.all(16), children: [
          Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)), child: const Text('Двухэтапная защита обязательна. Не отключай её и отзывай незнакомые сессии.', style: TextStyle(color: plusInk, height: 1.4))),
          const SizedBox(height: 12),
          FilledButton.icon(onPressed: () async {
            try { await widget.api.revokeOtherSessions(); _reload(); if (context.mounted) showPluschatNotice(context, 'Другие сессии отозваны.'); }
            catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
          }, icon: const Icon(Icons.phonelink_erase_rounded), label: const Text('Завершить другие сессии')),
          const SizedBox(height: 16),
          if (devices.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Text('Нет данных об устройствах.', textAlign: TextAlign.center, style: TextStyle(color: plusMuted))),
          ...devices.map((raw) {
            final d = Map<String, dynamic>.from(raw as Map);
            final id = d['id']?.toString() ?? '';
            final name = d['device_name']?.toString() ?? d['name']?.toString() ?? 'Устройство';
            final platform = d['platform']?.toString() ?? 'unknown';
            return Card(elevation: 0, color: Colors.white, child: ListTile(
              leading: const CircleAvatar(backgroundColor: Color(0xFFE0EEFF), child: Icon(Icons.devices_rounded, color: plusBlue)),
              title: Text(name, style: const TextStyle(fontWeight: FontWeight.w700)),
              subtitle: Text("$platform · ${d['last_seen_at'] ?? 'дата неизвестна'}", style: const TextStyle(color: plusMuted)),
              trailing: id.isEmpty ? null : IconButton(icon: const Icon(Icons.block_rounded, color: Color(0xFFB42318)), onPressed: () async {
                try { await widget.api.revokeDevice(id); _reload(); }
                catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
              }),
            ));
          }),
        ]);
      },
    ),
  );
}