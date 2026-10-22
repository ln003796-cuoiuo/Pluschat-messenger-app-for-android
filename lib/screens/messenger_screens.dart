import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';

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
      SearchPage(api: widget.api),
      NotificationsPage(api: widget.api),
      ProfilePage(api: widget.api, onLogout: widget.onLogout),
    ];
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_tab], style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w800, letterSpacing: -.5)),
        actions: [
          if (_tab == 0 || _tab == 1) IconButton(onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => SearchPage(api: widget.api))), icon: const Icon(Icons.search_rounded), tooltip: 'Поиск людей'),
          if (_tab == 0) IconButton(onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => CommunitiesPage(api: widget.api))), icon: const Icon(Icons.groups_2_outlined), tooltip: 'Группы и каналы'),
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
              final title = name.isEmpty ? '@${peer['username'] ?? 'user'}' : name;
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
        const Text('Ваш профиль', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: plusInk)),
        const SizedBox(height: 5),
        const Text('Управление аккаунтом и безопасностью', textAlign: TextAlign.center, style: TextStyle(color: plusMuted)),
      ])),
      const SizedBox(height: 14),
      _profileItem(context, Icons.shield_outlined, 'Безопасность и устройства', 'Активные сессии и устройства', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => DevicesPage(api: api)))),
      _profileItem(context, Icons.lock_outline_rounded, 'Конфиденциальность', 'Видимость профиля и разрешения', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PrivacySettingsPage(api: api)))),
      _profileItem(context, Icons.notifications_none_rounded, 'Уведомления', 'Общие настройки уведомлений', onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PrivacySettingsPage(api: api, notificationsOnly: true)))),
      _profileItem(context, Icons.palette_outlined, 'Оформление', 'Тема и внешний вид'),
      _profileItem(context, Icons.cloud_outlined, 'Состояние сервера', 'Проверить подключение', onTap: () async {
        try {
          final result = await api.health();
          if (context.mounted) showPluschatNotice(context, 'API: ${result['service'] ?? 'online'}; БД: ${result['database'] ?? 'неизвестно'}; схема: ${result['schema'] ?? 'неизвестно'}');
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
        CircleAvatar(radius: 42, backgroundColor: const Color(0xFFE0EEFF), child: Icon(icon, size: 40, color: plusBlue)),
        const SizedBox(height: 18),
        Text(title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: plusInk)),
        const SizedBox(height: 8),
        Text(subtitle, textAlign: TextAlign.center, style: const TextStyle(color: plusMuted, height: 1.5)),
      ]),
    ),
  );
}





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
      final title = name.isEmpty ? '@${user['username'] ?? 'user'}' : name;
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
            final name = fullName.isEmpty ? '@${user['username'] ?? 'user'}' : fullName;
            return Card(
              elevation: 0, color: Colors.white, margin: const EdgeInsets.only(bottom: 6),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: ListTile(
                leading: CircleAvatar(backgroundColor: const Color(0xFFDCEBFF), child: Text(name.characters.first.toUpperCase(), style: const TextStyle(color: plusBlue, fontWeight: FontWeight.w800))),
                title: Text(name, style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('@${user['username'] ?? ''}', style: const TextStyle(color: plusMuted)),
                trailing: const Icon(Icons.chat_bubble_outline_rounded, color: plusBlue),
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
              subtitle: Text('$platform · ${d['last_seen_at'] ?? 'дата неизвестна'}', style: const TextStyle(color: plusMuted)),
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
