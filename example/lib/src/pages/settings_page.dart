import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../phone.dart';
import '../voipgrid.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final _phone = Phone.instance;

  late final _username = TextEditingController(text: _phone.username);
  late final _password = TextEditingController(text: _phone.password);
  late final _domain = TextEditingController(text: _phone.domain);
  late final _port = TextEditingController(
    text: _phone.port == 0 ? '' : '${_phone.port}',
  );
  late var _secure = _phone.secure;

  late final _voipgridUsername =
      TextEditingController(text: _phone.voipgrid.username);
  late final _voipgridPassword =
      TextEditingController(text: _phone.voipgrid.password);

  var _applying = false;
  var _loggingIn = false;
  var _registering = false;
  var _refreshingUser = false;
  var _sipRegistered = false;
  late final Timer _sipPoller;

  final _voipSection = GlobalKey();
  final _voipgridSection = GlobalKey();
  final _middlewareSection = GlobalKey();

  final _voipExpansion = ExpansibleController();
  final _voipgridExpansion = ExpansibleController();
  final _middlewareExpansion = ExpansibleController();

  void _open(GlobalKey section, ExpansibleController expansion) {
    expansion.expand();

    // Wait a frame so the section has started expanding before scrolling.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = section.currentContext;
      if (context == null) return;

      Scrollable.ensureVisible(
        context,
        duration: const Duration(milliseconds: 300),
        alignment: 0,
      );
    });
  }

  @override
  void initState() {
    super.initState();
    _phone.middleware.refresh();
    if (_phone.voipgrid.isLoggedIn) _phone.voipgrid.fetchUser();
    _pollSipRegistration();
    _sipPoller = Timer.periodic(
      const Duration(seconds: 2),
      (_) => _pollSipRegistration(),
    );
  }

  Future<void> _pollSipRegistration() async {
    final registered = _phone.isRunning && await _phone.phoneLib.isRegistered;

    if (mounted && registered != _sipRegistered) {
      setState(() => _sipRegistered = registered);
    }
  }

  @override
  void dispose() {
    _sipPoller.cancel();
    _voipExpansion.dispose();
    _voipgridExpansion.dispose();
    _middlewareExpansion.dispose();
    _username.dispose();
    _password.dispose();
    _domain.dispose();
    _port.dispose();
    _voipgridUsername.dispose();
    _voipgridPassword.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    setState(() => _loggingIn = true);

    try {
      await _phone.voipgrid.saveLogin(
        username: _voipgridUsername.text.trim(),
        password: _voipgridPassword.text,
      );
      await _phone.voipgrid.login();
      await _phone.voipgrid.fetchUser();
    } on Exception catch (e) {
      _showSnackBar('$e');
    } finally {
      if (mounted) setState(() => _loggingIn = false);
    }
  }

  Future<void> _refreshUser() async {
    setState(() => _refreshingUser = true);

    try {
      await _phone.voipgrid.fetchUser();
    } finally {
      if (mounted) setState(() => _refreshingUser = false);
    }
  }

  Future<void> _register() => _withMiddleware(
        () => _phone.middleware.register(sipUserId: _phone.username),
      );

  Future<void> _unregister() => _withMiddleware(
        () => _phone.middleware.unregister(sipUserId: _phone.username),
      );

  Future<void> _withMiddleware(Future<void> Function() action) async {
    setState(() => _registering = true);

    try {
      await action();
    } on Exception catch (e) {
      _showSnackBar('$e');
    } finally {
      if (mounted) setState(() => _registering = false);
    }
  }

  Future<void> _logout() => _phone.voipgrid.logout();

  Future<void> _stop() => _phone.stop();

  Future<void> _saveAndApply() async {
    setState(() => _applying = true);

    try {
      await _phone.saveCredentials(
        username: _username.text.trim(),
        password: _password.text,
        domain: _domain.text.trim(),
        port: int.tryParse(_port.text.trim()) ?? 0,
        secure: _secure,
      );

      final started = await _phone.start();

      if (!started) {
        _showSnackBar('Credentials are incomplete, not registering.');
      }
    } on Exception catch (e) {
      _showSnackBar('Failed to register: $e');
    } finally {
      if (mounted) setState(() => _applying = false);
    }
  }

  void _showSnackBar(String message) {
    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final voipgrid = _phone.voipgrid;
    final middleware = _phone.middleware;

    return ListenableBuilder(
      listenable: Listenable.merge([_phone, voipgrid, middleware]),
      builder: (context, _) => Column(
        children: [
          const SizedBox(height: 8),
          _StatusBar(
            items: [
              _Status(
                'SIP',
                onTap: () => _open(_voipSection, _voipExpansion),
                _sipRegistered,
                'Registered',
                _phone.isRunning ? 'Failed' : 'Not registered',
                failed: _phone.isRunning,
              ),
              _Status(
                'VoIPGRID',
                onTap: () => _open(_voipgridSection, _voipgridExpansion),
                voipgrid.isLoggedIn,
                'Logged in',
                'Not logged in',
              ),
              _Status(
                'Middleware',
                onTap: () => _open(_middlewareSection, _middlewareExpansion),
                middleware.isRegistered,
                'Registered',
                'Not registered',
              ),
            ],
          ),
          if (voipgrid.isLoggedIn && voipgrid.user != null) ...[
            const SizedBox(height: 4),
            _Account(
              voipgrid.user!,
              onTap: () => _open(_voipgridSection, _voipgridExpansion),
            ),
          ],
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _Section(
                    key: _voipSection,
                    controller: _voipExpansion,
                    icon: Icons.phone,
                    title: 'VoIP authentication',
                    subtitle: _phone.username.isEmpty
                        ? 'Not configured'
                        : '${_phone.username}@${_phone.domain}',
                    children: [
                      if (kDebugMode && _phone.username.isEmpty) ...[
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Text(
                              'Tip: debug builds can pre-fill these fields '
                              'from the .env file, see .env.example.',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                      ],
                      TextField(
                        controller: _username,
                        decoration:
                            const InputDecoration(labelText: 'Username'),
                      ),
                      _PasswordField(controller: _password),
                      TextField(
                        controller: _domain,
                        keyboardType: TextInputType.url,
                        decoration: const InputDecoration(labelText: 'Domain'),
                      ),
                      TextField(
                        controller: _port,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(labelText: 'Port'),
                      ),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Encryption (TLS)'),
                        value: _secure,
                        onChanged: (value) => setState(() => _secure = value),
                      ),
                      const SizedBox(height: 8),
                      _Actions(
                        busy: _applying,
                        primaryLabel: 'Register',
                        onPrimary: _saveAndApply,
                        secondaryLabel: 'Unregister',
                        onSecondary: _phone.isRunning ? _stop : null,
                      ),
                    ],
                  ),
                  _Section(
                    key: _voipgridSection,
                    controller: _voipgridExpansion,
                    icon: Icons.cloud_outlined,
                    title: 'VoIPGRID authentication',
                    subtitle: voipgrid.isLoggedIn
                        ? voipgrid.username
                        : 'Not logged in',
                    children: [
                      TextField(
                        controller: _voipgridUsername,
                        keyboardType: TextInputType.emailAddress,
                        decoration: const InputDecoration(labelText: 'Email'),
                      ),
                      _PasswordField(controller: _voipgridPassword),
                      const SizedBox(height: 8),
                      _Actions(
                        busy: _loggingIn,
                        primaryLabel: 'Log in',
                        onPrimary: _login,
                        secondaryLabel: 'Log out',
                        onSecondary: voipgrid.isLoggedIn ? _logout : null,
                      ),
                      if (voipgrid.isLoggedIn) ...[
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: _refreshingUser ? null : _refreshUser,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Refresh account info'),
                        ),
                      ],
                    ],
                  ),
                  _Section(
                    key: _middlewareSection,
                    controller: _middlewareExpansion,
                    icon: Icons.notifications_outlined,
                    title: 'Middleware registration',
                    subtitle: middleware.isRegistered
                        ? 'Registered'
                        : 'Not registered',
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Push token'),
                        subtitle: middleware.pushToken == null
                            ? const Text('Not received yet')
                            : SelectableText(
                                middleware.pushToken!,
                                style: const TextStyle(fontFamily: 'monospace'),
                              ),
                      ),
                      _Actions(
                        busy: _registering,
                        primaryLabel: 'Register',
                        onPrimary: voipgrid.isLoggedIn ? _register : null,
                        secondaryLabel: 'Unregister',
                        onSecondary:
                            middleware.isRegistered ? _unregister : null,
                      ),
                    ],
                  ),
                  _Section(
                    icon: Icons.tune,
                    title: 'Preferences',
                    children: [
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Use application provided ringtone'),
                        value: _phone.useApplicationProvidedRingtone,
                        onChanged: (value) =>
                            _phone.setUseApplicationProvidedRingtone(value),
                      ),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Show calls in native recents'),
                        value: _phone.showCallsInNativeRecents,
                        onChanged: (value) =>
                            _phone.setShowCallsInNativeRecents(value),
                      ),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Advanced logging'),
                        value: _phone.enableAdvancedLogging,
                        onChanged: (value) =>
                            _phone.setEnableAdvancedLogging(value),
                      ),
                    ],
                  ),
                  _Section(
                    icon: Icons.build_outlined,
                    title: 'Advanced',
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Echo cancellation calibration'),
                        enabled: _phone.isRunning,
                        onTap: () {
                          _phone.phoneLib.performEchoCancellationCalibration();
                          _showSnackBar(
                            'Performing echo cancellation calibration...',
                          );
                        },
                      ),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Linphone version'),
                        subtitle: Text(_phone.linphoneVersion ?? 'Unknown'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({
    super.key,
    this.controller,
    required this.icon,
    required this.title,
    this.subtitle,
    required this.children,
  });

  final ExpansibleController? controller;
  final IconData icon;
  final String title;
  final String? subtitle;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        controller: controller,
        leading: Icon(icon),
        title: Text(title),
        subtitle: subtitle == null ? null : Text(subtitle!),
        shape: const Border(),
        collapsedShape: const Border(),
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        children: children,
      ),
    );
  }
}

class _Account extends StatelessWidget {
  const _Account(this.user, {required this.onTap});

  final VoipgridUser user;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );

    final numbers = [
      if (user.internalNumber != null)
        (Icons.person_outline, user.internalNumber!),
      if (user.voipAccountNumber != null)
        (Icons.phone, user.voipAccountNumber!),
    ];

    return _Block(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: SizedBox(
          width: double.infinity,
          child: Column(
            children: [
              Text(
                user.name,
                style: theme.textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              if (numbers.isNotEmpty)
                Text.rich(
                  TextSpan(
                    style: theme.textTheme.bodySmall,
                    children: [
                      for (final (i, (icon, value)) in numbers.indexed) ...[
                        if (i > 0) TextSpan(text: '   |   ', style: muted),
                        WidgetSpan(
                          alignment: PlaceholderAlignment.middle,
                          child: Padding(
                            padding: const EdgeInsets.only(right: 4),
                            child: Icon(
                              icon,
                              size: 14,
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                        TextSpan(text: value),
                      ],
                    ],
                  ),
                  textAlign: TextAlign.center,
                ),
              if (user.isDestinationElsewhere)
                Text.rich(
                  TextSpan(
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.error,
                    ),
                    children: [
                      WidgetSpan(
                        alignment: PlaceholderAlignment.middle,
                        child: Padding(
                          padding: const EdgeInsets.only(right: 4),
                          child: Icon(
                            Icons.warning_amber,
                            size: 14,
                            color: theme.colorScheme.error,
                          ),
                        ),
                      ),
                      const TextSpan(
                        text: 'Destination is not set to this VoIP account',
                      ),
                    ],
                  ),
                  textAlign: TextAlign.center,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PasswordField extends StatefulWidget {
  const _PasswordField({required this.controller});

  final TextEditingController controller;

  @override
  State<_PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<_PasswordField> {
  var _obscured = true;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: widget.controller,
      obscureText: _obscured,
      decoration: InputDecoration(
        labelText: 'Password',
        suffixIcon: IconButton(
          tooltip: _obscured ? 'Show password' : 'Hide password',
          icon: Icon(_obscured ? Icons.visibility : Icons.visibility_off),
          onPressed: () => setState(() => _obscured = !_obscured),
        ),
      ),
    );
  }
}

class _Actions extends StatelessWidget {
  const _Actions({
    required this.busy,
    required this.primaryLabel,
    required this.onPrimary,
    required this.secondaryLabel,
    required this.onSecondary,
  });

  final bool busy;
  final String primaryLabel;
  final VoidCallback? onPrimary;
  final String secondaryLabel;
  final VoidCallback? onSecondary;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: FilledButton(
            onPressed: busy ? null : onPrimary,
            child: Text(primaryLabel),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton(
            onPressed: busy ? null : onSecondary,
            child: Text(secondaryLabel),
          ),
        ),
      ],
    );
  }
}

class _Status {
  const _Status(
    this.title,
    this.active,
    this.activeText,
    this.inactiveText, {
    required this.onTap,
    this.failed = false,
  });

  final String title;
  final bool active;
  final String activeText;
  final String inactiveText;
  final VoidCallback onTap;

  /// Shows the inactive state as an error rather than neutral.
  final bool failed;
}

class _StatusBar extends StatelessWidget {
  const _StatusBar({required this.items});

  final List<_Status> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return _Block(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            for (final item in items)
              Expanded(
                child: InkWell(
                  onTap: item.onTap,
                  borderRadius: BorderRadius.circular(8),
                  child: Column(
                    children: [
                      Icon(
                        item.active
                            ? Icons.check_circle
                            : item.failed
                                ? Icons.error
                                : Icons.radio_button_unchecked,
                        size: 20,
                        color: item.active
                            ? Colors.green
                            : item.failed
                                ? theme.colorScheme.error
                                : theme.colorScheme.outline,
                      ),
                      const SizedBox(height: 4),
                      Text(item.title, style: theme.textTheme.labelMedium),
                      Text(
                        item.active ? item.activeText : item.inactiveText,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Block extends StatelessWidget {
  const _Block({required this.child, this.onTap});

  final Widget child;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Material(
        color: theme.colorScheme.surfaceContainer,
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(
          side: BorderSide(color: theme.colorScheme.outlineVariant),
          borderRadius: BorderRadius.circular(8),
        ),
        child: InkWell(onTap: onTap, child: child),
      ),
    );
  }
}
