import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_phone_lib/flutter_phone_lib.dart';
import 'package:permission_handler/permission_handler.dart';

import '../phone.dart';
import '../widgets/call_button.dart';
import '../widgets/caller_header.dart';

class IncomingCallPage extends StatefulWidget {
  const IncomingCallPage({super.key});

  @override
  State<IncomingCallPage> createState() => _IncomingCallPageState();
}

class _IncomingCallPageState extends State<IncomingCallPage> {
  final _phone = Phone.instance;

  Call? _call;

  StreamSubscription<Event>? _eventsSubscription;

  @override
  void initState() {
    super.initState();

    _eventsSubscription = _phone.events.listen(_onEvent);

    _phone.phoneLib.sessionState.then((state) {
      if (mounted) setState(() => _call = state.activeCall);
    });
  }

  @override
  void dispose() {
    _eventsSubscription?.cancel();
    super.dispose();
  }

  void _onEvent(Event event) {
    if (event is CallSessionEvent && event.state?.activeCall != null) {
      setState(() => _call = event.state!.activeCall);
    }
  }

  Future<void> _answer() async {
    if (Platform.isAndroid || Platform.isIOS) {
      final status = await Permission.microphone.request();
      if (!status.isGranted) return;
    }

    await _phone.phoneLib.actions.answer();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              const Spacer(),
              CallerHeader(call: _call, status: const Text('Incoming call')),
              const Spacer(flex: 2),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _LabeledCallButton(
                    label: 'Decline',
                    button: CallButton.hangUp(
                      tooltip: 'Decline',
                      onPressed: () => _phone.phoneLib.actions.decline(),
                    ),
                  ),
                  _LabeledCallButton(
                    label: 'Answer',
                    button: CallButton.answer(
                      tooltip: 'Answer',
                      onPressed: _answer,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 48),
            ],
          ),
        ),
      ),
    );
  }
}

class _LabeledCallButton extends StatelessWidget {
  const _LabeledCallButton({required this.label, required this.button});

  final String label;
  final Widget button;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        button,
        const SizedBox(height: 8),
        Text(label, style: Theme.of(context).textTheme.labelLarge),
      ],
    );
  }
}
