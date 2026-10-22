import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';
import 'chats_page.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key, required this.api});
  final ApiClient api;
  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _query = TextEditingController();
  bool _busy = false;
  String? _error;
  List<dynamic> _users = [];
  List<dynamic> _groups = [];
  List<dynamic> _channels = [];

  @override
  void dispose() { _query.dispose(); super.dispose(); }

  Future<void> _search() async {
    final q = _query.text.trim();
    if (q.length < 2) {
      setState(() => _error = 'Введи минимум 2 символа.');
      return;
    }
    setState(() { _busy = true; _error = null; });
    try {
      final result = await widget.api.search(q);
      if (mounted) setState(() {
        _users = (result['users'] as List<dynamic>?) ?? [];
        _groups = (result['groups'] as List<dynamic>?) ?? [];
        _channels = (result['channels'] as List<dynamic>?) ?? [];
      });
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openUser(Map<String, dynamic> user) async {
    setState(() { _busy = true; _error = null; });
    try {
      final result = await widget.api.createDirectConversation(user['id'].toString());
      final id = result['conversation_id']?.toString();
      if (id == null || id.isEmpty) throw const ApiException('Сервер не вернул ID переписки.');
      final name = [user['first_name'], user['last_name']].where((v) => v != null && v.toString().isNotEmpty).join(' ');
      final title = name.isEmpty ? "@${user['username'] ?? 'user'}" : name;
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ConversationPage(
        api: widget.api,
        title: title,
        conversation: {'id': id, 'other_user': user},
      )));
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Найти людей', style: TextStyle(fontWeight: FontWeight.w800))),
    body: Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
        child: Row(children: [
          Expanded(child: TextField(
            controller: _query,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _search(),
            decoration: const InputDecoration(prefixIcon: Icon(Icons.search_rounded), hintText: 'Имя или никнейм'),
          )),
          const SizedBox(width: 8),
          IconButton.filled(onPressed: _busy ? null : _search, icon: const Icon(Icons.arrow_forward_rounded)),
        ]),
      ),
      if (_error != null) Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: Text(_error!, style: const TextStyle(color: Color(0xFFB42318)))),
      if (_busy) const LinearProgressIndicator(minHeight: 2),
      Expanded(child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
        children: [
          if (_users.isNotEmpty) const Padding(padding: EdgeInsets.only(bottom: 8), child: Text('Люди', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: plusInk))),
          ..._users.map((raw) {
            final user = Map<String, dynamic>.from(raw as Map);
            final fullName = [user['first_name'], user['last_name']].where((v) => v != null && v.toString().isNotEmpty).join(' ');
            final name = fullName.isEmpty ? "@${user['username'] ?? 'user'}" : fullName;
            return Card(
              elevation: 0, color: Colors.white, margin: const EdgeInsets.only(bottom: 6),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: ListTile(
                leading: CircleAvatar(backgroundColor: const Color(0xFFDCEBFF), child: Text(name.characters.first.toUpperCase(), style: const TextStyle(color: plusBlue, fontWeight: FontWeight.w800))),
                title: Text(name, style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text("@${user['username'] ?? ''}", style: const TextStyle(color: plusMuted)),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  IconButton(
                    tooltip: 'Добавить в друзья',
                    onPressed: _busy ? null : () async {
                      try {
                        await widget.api.sendFriendRequest(user['id'].toString());
                        if (context.mounted) showPluschatNotice(context, 'Заявка в друзья отправлена.');
                      } catch (e) {
                        if (context.mounted) showPluschatNotice(context, e.toString());
                      }
                    },
                    icon: const Icon(Icons.person_add_alt_1_rounded, color: plusBlue),
                  ),
                  const Icon(Icons.chat_bubble_outline_rounded, color: plusBlue),
                ]),
                onTap: _busy ? null : () => _openUser(user),
              ),
            );
          }),
          if (_groups.isNotEmpty) const Padding(padding: EdgeInsets.only(top: 12, bottom: 8), child: Text('Группы', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: plusInk))),
          ..._groups.map((raw) {
            final group = Map<String, dynamic>.from(raw as Map);
            return ListTile(leading: const CircleAvatar(child: Icon(Icons.groups_rounded)), title: Text(group['title']?.toString() ?? 'Группа'), subtitle: Text(group['description']?.toString() ?? ''));
          }),
          if (_channels.isNotEmpty) const Padding(padding: EdgeInsets.only(top: 12, bottom: 8), child: Text('Каналы', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: plusInk))),
          ..._channels.map((raw) {
            final channel = Map<String, dynamic>.from(raw as Map);
            return ListTile(leading: const CircleAvatar(child: Icon(Icons.campaign_rounded)), title: Text(channel['title']?.toString() ?? 'Канал'), subtitle: Text(channel['description']?.toString() ?? ''));
          }),
          if (!_busy && _error == null && _users.isEmpty && _groups.isEmpty && _channels.isEmpty)
            const Padding(padding: EdgeInsets.only(top: 65), child: Column(children: [
              Icon(Icons.person_search_rounded, size: 50, color: plusMuted),
              SizedBox(height: 12),
              Text('Найди знакомых', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: plusInk)),
              SizedBox(height: 6),
              Text('Ищи по имени или никнейму.', style: TextStyle(color: plusMuted)),
            ])),
        ],
      )),
    ]),
  );
}