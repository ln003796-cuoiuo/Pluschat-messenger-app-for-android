import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/ui_helpers.dart';
import 'chats_page.dart';
import 'friends_page.dart';
import 'notifications_page.dart';
import 'profile_page.dart';
import 'search_page.dart';
import 'communities_page.dart';

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
      FriendsPage(api: widget.api),
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