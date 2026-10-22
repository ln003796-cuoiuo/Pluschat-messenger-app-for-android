import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

class FriendsPage extends StatefulWidget {
  const FriendsPage({super.key, required this.api});
  final ApiClient api;
  @override
  State<FriendsPage> createState() => _FriendsPageState();
}

class _FriendsPageState extends State<FriendsPage> {
  late Future<List<Map<String, dynamic>>> _future;
  @override
  void initState() { super.initState(); _future = _load(); }
  Future<List<Map<String, dynamic>>> _load() async {
    final results = await Future.wait([widget.api.friends(), widget.api.friendRequests()]);
    final friends = ((results[0]['friends'] as List<dynamic>?) ?? []).map((v) => <String, dynamic>{'kind':'friend','data':v}).toList();
    final requests = ((results[1]['requests'] as List<dynamic>?) ?? []).map((v) => <String, dynamic>{'kind':'request','data':v}).toList();
    return [...requests, ...friends];
  }
  void _reload() => setState(() => _future = _load());

  @override
  Widget build(BuildContext context) => FutureBuilder<List<Map<String, dynamic>>>(
    future: _future,
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
      if (snapshot.hasError) return Center(child: Padding(padding: const EdgeInsets.all(24), child: Text('Не удалось загрузить контакты:\n${snapshot.error}', textAlign: TextAlign.center)));
      final items = snapshot.data ?? [];
      if (items.isEmpty) return RefreshIndicator(onRefresh: () async => _reload(), child: ListView(children: const [
        SizedBox(height: 100),
        Icon(Icons.people_outline_rounded, size: 54, color: plusMuted),
        SizedBox(height: 14),
        Text('Пока нет друзей', textAlign: TextAlign.center, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: plusInk)),
        SizedBox(height: 8),
        Text('Найди знакомых через поиск и отправь заявку.', textAlign: TextAlign.center, style: TextStyle(color: plusMuted)),
      ]));
      return RefreshIndicator(onRefresh: () async => _reload(), child: ListView(
        padding: const EdgeInsets.all(16),
        children: items.map((entry) {
          final isRequest = entry['kind'] == 'request';
          final raw = Map<String, dynamic>.from(entry['data'] as Map);
          final user = isRequest ? Map<String, dynamic>.from((raw['user'] as Map?) ?? const {}) : raw;
          final id = raw['id']?.toString() ?? '';
          final fullName = [user['first_name'], user['last_name']].where((v) => v != null && v.toString().isNotEmpty).join(' ');
          final name = fullName.isEmpty ? "@${user['username'] ?? 'user'}" : fullName;
          return Card(elevation: 0, color: Colors.white, margin: const EdgeInsets.only(bottom: 7), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), child: ListTile(
            leading: CircleAvatar(backgroundColor: const Color(0xFFE0EEFF), child: Text(name.characters.first.toUpperCase(), style: const TextStyle(color: plusBlue, fontWeight: FontWeight.w800))),
            title: Text(name, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text(isRequest ? 'Заявка в друзья' : "@${user['username'] ?? ''}", style: const TextStyle(color: plusMuted)),
            trailing: isRequest ? Row(mainAxisSize: MainAxisSize.min, children: [
              IconButton(tooltip: 'Принять', onPressed: () async {
                try { await widget.api.respondFriendRequest(id, accept: true); _reload(); }
                catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
              }, icon: const Icon(Icons.check_circle_outline_rounded, color: plusBlue)),
              IconButton(tooltip: 'Отклонить', onPressed: () async {
                try { await widget.api.respondFriendRequest(id, accept: false); _reload(); }
                catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
              }, icon: const Icon(Icons.close_rounded, color: plusMuted)),
            ]) : const Icon(Icons.person_rounded, color: plusMuted),
          ));
        }).toList(),
      ));
    },
  );
}