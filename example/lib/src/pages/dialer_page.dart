import 'dart:io';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../phone.dart';
import '../widgets/call_button.dart';
import '../widgets/keypad.dart';

class DialerPage extends StatefulWidget {
  const DialerPage({super.key, required this.onOpenSettings});

  final VoidCallback onOpenSettings;

  @override
  State<DialerPage> createState() => _DialerPageState();
}

class _DialerPageState extends State<DialerPage> {
  final _phone = Phone.instance;

  late var _digits = _phone.lastDialedNumber;

  void _onKeyPressed(String key) {
    if (_phone.isRunning) {
      _phone.phoneLib.playToneLocally(key);
    }

    setState(() => _digits += key);
  }

  void _backspace() {
    if (_digits.isEmpty) return;

    setState(() => _digits = _digits.substring(0, _digits.length - 1));
  }

  Future<void> _call() async {
    if (_digits.isEmpty) return;

    if (!_phone.isRunning) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Configure your SIP credentials first.'),
          action: SnackBarAction(
            label: 'Settings',
            onPressed: widget.onOpenSettings,
          ),
        ),
      );
      return;
    }

    if (!await _requestCallingPermissions()) return;

    await _phone.saveLastDialedNumber(_digits);
    await _phone.phoneLib.call(_digits);
  }

  Future<bool> _requestCallingPermissions() async {
    final permissions = Platform.isAndroid
        ? [
            Permission.microphone,
            Permission.phone,
            Permission.bluetoothConnect,
            Permission.contacts,
          ]
        : [Permission.microphone];

    final statuses = await permissions.request();

    return statuses[Permission.microphone]?.isGranted ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return SafeArea(
      child: Column(
        children: [
          ListenableBuilder(
            listenable: _phone,
            builder: (context, _) => _phone.isRunning
                ? const SizedBox(height: 16)
                : Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: ActionChip(
                      avatar: const Icon(Icons.info_outline),
                      label: const Text('Not configured · Open settings'),
                      onPressed: widget.onOpenSettings,
                    ),
                  ),
          ),
          Expanded(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Text(
                    _digits,
                    maxLines: 1,
                    style: theme.textTheme.displayMedium,
                  ),
                ),
              ),
            ),
          ),
          Keypad(
            onKeyPressed: _onKeyPressed,
            onPlus: () => setState(() => _digits += '+'),
          ),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Padding(
                padding: Keypad.keyPadding,
                child: SizedBox.square(dimension: Keypad.keySize),
              ),
              Padding(
                padding: Keypad.keyPadding,
                child: CallButton.answer(onPressed: _call),
              ),
              Padding(
                padding: Keypad.keyPadding,
                child: AnimatedOpacity(
                  opacity: _digits.isEmpty ? 0 : 1,
                  duration: const Duration(milliseconds: 150),
                  child: SizedBox.square(
                    dimension: Keypad.keySize,
                    child: Tooltip(
                      message: 'Backspace',
                      child: InkWell(
                        customBorder: const CircleBorder(),
                        onTap: _digits.isEmpty ? null : _backspace,
                        onLongPress: _digits.isEmpty
                            ? null
                            : () => setState(() => _digits = ''),
                        child: Icon(
                          Icons.backspace_outlined,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }
}
