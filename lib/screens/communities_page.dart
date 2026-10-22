import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

class CommunitiesPage extends StatefulWidget {
  const CommunitiesPage({super.key, required this.api});
  final ApiClient api;
  @override
  State<CommunitiesPage> createState() => _CommunitiesPageState();
}

class _CommunitiesPageState extends State<CommunitiesPage> with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this);
  late Future<Map<String, dynamic>> _groups;
  late Future<Map<String, dynamic>> _channels;
  @override
  void initState() {
    super.initState();
    _tabs.addListener(() { if (mounted) setState(() {}); });
    _groups = widget.api.groups();
    _channels = widget.api.channels();
  }
  @override
  void dispose() { _tabs.dispose(); super.dispose(); }
  void _reload() => setState(() { _groups = widget.api.groups(); _channels = widget.api.channels(); });

  Future<void> _createCommunity() async {
    final isGroup = _tabs.index == 0;
    final titleController = TextEditingController();
    final descriptionController = TextEditingController();
    final form = await showDialog<Map<String, String>>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(isGroup ? 'Создать группу' : 'Создать канал'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: titleController, maxLength: 120, decoration: const InputDecoration(hintText: 'Название')),
          const SizedBox(height: 10),
          TextField(controller: descriptionController, maxLines: 3, maxLength: 1000, decoration: const InputDecoration(hintText: 'Описание (необязательно)')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Отмена')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, {
            'title': titleController.text.trim(),
            'description': descriptionController.text.trim(),
          }), child: const Text('Создать')),
        ],
      ),
    );
    titleController.dispose();
    descriptionController.dispose();
    if (form == null || form['title']!.isEmpty || !mounted) return;
    try {
      if (isGroup) {
        await widget.api.createGroup({...form, 'is_private': false, 'topics_enabled': true});
      } else {
        await widget.api.createChannel({...form, 'is_public': true, 'topics_enabled': true});
      }
      _reload();
      if (mounted) showPluschatNotice(context, isGroup ? 'Группа создана.' : 'Канал создан.');
    } catch (e) {
      if (mounted) showPluschatNotice(context, e.toString());
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Сообщества', style: TextStyle(fontWeight: FontWeight.w800)),
      actions: [IconButton(onPressed: _reload, icon: const Icon(Icons.refresh_rounded))],
      bottom: TabBar(controller: _tabs, tabs: const [Tab(text: 'Группы'), Tab(text: 'Каналы')], labelColor: plusBlue),
    ),
    body: TabBarView(controller: _tabs, children: [
      _communityList(_groups, 'groups', Icons.groups_rounded, join: true),
      _communityList(_channels, 'channels', Icons.campaign_rounded, join: false),
    ]),
    floatingActionButton: FloatingActionButton.extended(
      onPressed: _createCommunity,
      icon: const Icon(Icons.add_rounded),
      label: Text(_tabs.index == 0 ? 'Создать группу' : 'Создать канал'),
    ),
  );

  Widget _communityList(Future<Map<String, dynamic>> future, String key, IconData icon, {required bool join}) =>
    FutureBuilder<Map<String, dynamic>>(
      future: future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError) return Center(child: Padding(padding: const EdgeInsets.all(24), child: Text('Ошибка загрузки: ${snapshot.error}', textAlign: TextAlign.center)));
        final list = (snapshot.data?[key] as List<dynamic>?) ?? [];
        if (list.isEmpty) return Center(child: Padding(padding: const EdgeInsets.all(28), child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 48, color: plusMuted),
          const SizedBox(height: 12),
          Text(join ? 'Ты пока не состоишь в группах' : 'Ты пока не подписан на каналы', textAlign: TextAlign.center, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: plusInk)),
          const SizedBox(height: 8),
          const Text('Список загружается с основного сервера.', textAlign: TextAlign.center, style: TextStyle(color: plusMuted)),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: () async {
            try {
              final found = join ? await widget.api.discoverGroups() : await widget.api.discoverChannels();
              final items = (found[key] as List<dynamic>?) ?? [];
              if (!context.mounted) return;
              showModalBottomSheet<void>(context: context, showDragHandle: true, builder: (ctx) => ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text(join ? 'Найти группы' : 'Найти каналы', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 12),
                  if (items.isEmpty) const Padding(padding: EdgeInsets.all(20), child: Text('Ничего не найдено.')),
                  ...items.map((raw) {
                    final item = Map<String, dynamic>.from(raw as Map);
                    final id = item['id'].toString();
                    return ListTile(
                      leading: CircleAvatar(child: Icon(icon)),
                      title: Text(item['title']?.toString() ?? (join ? 'Группа' : 'Канал')),
                      subtitle: Text(item['description']?.toString() ?? ''),
                      trailing: IconButton(icon: Icon(join ? Icons.group_add_outlined : Icons.add_circle_outline), onPressed: () async {
                        try {
                          if (join) { await widget.api.joinGroup(id); } else { await widget.api.subscribeChannel(id); }
                          if (ctx.mounted) Navigator.pop(ctx);
                          _reload();
                        } catch (e) { if (ctx.mounted) showPluschatNotice(ctx, e.toString()); }
                      }),
                    );
                  }),
                ],
              ));
            } catch (e) { if (context.mounted) showPluschatNotice(context, e.toString()); }
          }, child: const Text('Найти сообщества')),
        ])));
        return RefreshIndicator(onRefresh: () async => _reload(), child: ListView(
          padding: const EdgeInsets.all(16),
          children: list.map((raw) {
            final item = Map<String, dynamic>.from(raw as Map);
            return Card(
              elevation: 0, color: Colors.white, margin: const EdgeInsets.only(bottom: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: ListTile(
                leading: CircleAvatar(backgroundColor: const Color(0xFFE0EEFF), child: Icon(icon, color: plusBlue)),
                title: Text(item['title']?.toString() ?? (join ? 'Группа' : 'Канал'), style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text(item['description']?.toString() ?? ''),
                trailing: Text((item['member_count'] ?? item['subscriber_count'] ?? '').toString(), style: const TextStyle(color: plusMuted)),
              ),
            );
          }).toList(),
        ));
      },
    );
}