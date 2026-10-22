import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

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
              const Icon(Icons.cloud_off_rounded, size: 52, color: plusMuted),
              const SizedBox(height: 16),
              const Text('Не удалось загрузить чаты', textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18, color: plusInk)),
              const SizedBox(height: 8),
              Padding(padding: const EdgeInsets.symmetric(horizontal: 28), child: Text(snapshot.error.toString(), textAlign: TextAlign.center, style: const TextStyle(color: plusMuted))),
              const SizedBox(height: 18),
              Center(child: OutlinedButton.icon(onPressed: _reload, icon: const Icon(Icons.refresh), label: const Text('Повторить'))),
            ]);
          }
          final list = (snapshot.data?['conversations'] as List<dynamic>?) ?? const [];
          if (list.isEmpty) {
            return ListView(children: const [
              SizedBox(height: 95),
              Center(child: CircleAvatar(radius: 42, backgroundColor: Color(0xFFE0EEFF), child: Icon(Icons.forum_outlined, color: plusBlue, size: 38))),
              SizedBox(height: 18),
              Text('Пока нет чатов', textAlign: TextAlign.center, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: plusInk)),
              SizedBox(height: 8),
              Padding(padding: EdgeInsets.symmetric(horizontal: 40), child: Text('Когда начнёшь переписку, она появится здесь.', textAlign: TextAlign.center, style: TextStyle(color: plusMuted, height: 1.4))),
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
              final title = name.isEmpty ? "@${peer['username'] ?? 'user'}" : name;
              return Material(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                  leading: CircleAvatar(radius: 26, backgroundColor: const Color(0xFFDCEBFF), child: Text(title.characters.first.toUpperCase(), style: const TextStyle(color: plusBlue, fontWeight: FontWeight.w800, fontSize: 18))),
                  title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, color: plusInk)),
                  subtitle: Text('Открой переписку', style: const TextStyle(color: plusMuted)),
                  trailing: const Icon(Icons.chevron_right_rounded, color: plusMuted),
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
            if (messages.isEmpty) return const Center(child: Text('Начни переписку', style: TextStyle(color: plusMuted)));
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
                    child: const Text('Зашифрованное сообщение. Для безопасного отображения требуется расшифровка на устройстве.', style: TextStyle(color: plusMuted, height: 1.35)),
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
            IconButton(onPressed: () => showPluschatNotice(context, 'Вложения подключим через защищённый API файлов.'), icon: const Icon(Icons.attach_file_rounded, color: plusBlue)),
            Expanded(child: TextField(enabled: false, decoration: const InputDecoration(hintText: 'Сообщение', fillColor: Color(0xFFF4F7FB)))),
            const SizedBox(width: 8),
            IconButton(onPressed: () => showPluschatNotice(context, 'Отправка включится после подключения клиентского E2EE. Открытый текст на сервер не отправляем.'), icon: const Icon(Icons.send_rounded, color: plusBlue)),
          ])),
        ),
      ]),
    );
  }
}