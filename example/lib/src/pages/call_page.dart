import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_phone_lib/flutter_phone_lib.dart';

import '../phone.dart';
import '../widgets/call_button.dart';
import '../widgets/caller_header.dart';
import '../widgets/keypad.dart';

class CallPage extends StatefulWidget {
  const CallPage({super.key});

  @override
  State<CallPage> createState() => _CallPageState();
}

class _CallPageState extends State<CallPage> {
  final _phone = Phone.instance;

  CallSessionState? _sessionState;

  StreamSubscription<Event>? _eventsSubscription;

  Call? get _activeCall => _sessionState?.activeCall;
  Call? get _inactiveCall => _sessionState?.inactiveCall;
  AudioState? get _audioState => _sessionState?.audioState;

  @override
  void initState() {
    super.initState();

    _eventsSubscription = _phone.events.listen(_onEvent);

    _phone.phoneLib.sessionState.then((state) {
      if (mounted) setState(() => _sessionState = state);
    });
  }

  @override
  void dispose() {
    _eventsSubscription?.cancel();
    super.dispose();
  }

  void _onEvent(Event event) {
    if (event is CallSessionEvent && event.state != null) {
      setState(() => _sessionState = event.state);
    }

    final message = switch (event) {
      AttendedTransferAborted() => 'Transfer cancelled',
      AttendedTransferEnded() => 'Call transferred',
      _ => null,
    };

    if (message != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    }
  }

  Future<void> _showAudioRoutes() async {
    final audio = _audioState;
    if (audio == null) return;

    final available = audio.availableRoutes;
    final bluetoothRoutes = audio.bluetoothRoutes.toList();
    final current = audio.currentRoute;

    final options = <(IconData, String, bool, Future<void> Function())>[
      if (available.contains(AudioRoute.phone))
        (
          Icons.phone_in_talk,
          'Phone',
          current == AudioRoute.phone,
          () => _phone.phoneLib.audio.routeAudio(AudioRoute.phone),
        ),
      if (available.contains(AudioRoute.speaker))
        (
          Icons.volume_up,
          'Speaker',
          current == AudioRoute.speaker,
          () => _phone.phoneLib.audio.routeAudio(AudioRoute.speaker),
        ),
      if (available.contains(AudioRoute.bluetooth))
        if (bluetoothRoutes.length > 1)
          for (final route in bluetoothRoutes)
            (
              Icons.bluetooth,
              route.displayName,
              current == AudioRoute.bluetooth &&
                  route.displayName == audio.bluetoothDeviceName,
              () => _phone.phoneLib.audio.routeAudioToBluetoothDevice(route),
            )
        else
          (
            Icons.bluetooth,
            audio.bluetoothDeviceName ?? 'Bluetooth',
            current == AudioRoute.bluetooth,
            () => _phone.phoneLib.audio.routeAudio(AudioRoute.bluetooth),
          ),
    ];

    final selected = await showModalBottomSheet<Future<void> Function()>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final (icon, label, isCurrent, action) in options)
              ListTile(
                leading: Icon(icon),
                title: Text(label),
                trailing: isCurrent ? const Icon(Icons.check) : null,
                selected: isCurrent,
                onTap: () => Navigator.pop(context, action),
              ),
          ],
        ),
      ),
    );

    await selected?.call();
  }

  Future<void> _transfer() async {
    if (_inactiveCall != null) {
      await _phone.phoneLib.actions.completeAttendedTransfer();
      return;
    }

    await _phone.phoneLib.actions.hold();

    if (!mounted) return;

    final number = await showDialog<String>(
      context: context,
      builder: (context) => const _TransferNumberDialog(),
    );

    if (number != null && number.isNotEmpty) {
      await _phone.phoneLib.actions.beginAttendedTransfer(number);
    }
  }

  void _showDtmfKeypad() {
    var sent = '';

    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: StatefulBuilder(
          builder: (context, setSheetState) => Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 48,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      sent,
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Keypad(
                onKeyPressed: (key) {
                  _phone.phoneLib.actions.sendDtmf(key);
                  setSheetState(() => sent += key);
                },
              ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }

  Widget _audioControl(AudioState? audio) {
    final current = audio?.currentRoute;

    if (!(audio?.availableRoutes.contains(AudioRoute.bluetooth) ?? false)) {
      final onSpeaker = current == AudioRoute.speaker;

      return _CallControl(
        icon: Icons.volume_up,
        label: 'Speaker',
        selected: onSpeaker,
        onPressed: audio == null
            ? null
            : () => _phone.phoneLib.audio.routeAudio(
                  onSpeaker ? AudioRoute.phone : AudioRoute.speaker,
                ),
      );
    }

    final (icon, label) = switch (current) {
      AudioRoute.speaker => (Icons.volume_up, 'Speaker'),
      AudioRoute.bluetooth => (
          Icons.bluetooth_audio,
          audio?.bluetoothDeviceName ?? 'Bluetooth',
        ),
      _ => (Icons.phone_in_talk, 'Phone'),
    };

    return _CallControl(
      icon: icon,
      label: label,
      selected: current != AudioRoute.phone,
      onPressed: _showAudioRoutes,
    );
  }

  @override
  Widget build(BuildContext context) {
    final call = _activeCall;
    final inactiveCall = _inactiveCall;
    final audioState = _audioState;
    final isMuted = audioState?.isMicrophoneMuted ?? false;

    return PopScope(
      canPop: false,
      child: Scaffold(
        body: SafeArea(
          child: call == null
              ? const Center(child: CircularProgressIndicator())
              : Column(
                  children: [
                    const SizedBox(width: double.infinity),
                    const Spacer(),
                    CallerHeader(call: call, status: _CallStatus(call)),
                    if (inactiveCall != null) ...[
                      const SizedBox(height: 24),
                      _HeldCallBanner(inactiveCall),
                    ],
                    const Spacer(flex: 2),
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 320),
                      child: Wrap(
                        alignment: WrapAlignment.center,
                        spacing: 24,
                        runSpacing: 20,
                        children: [
                          _CallControl(
                            icon: isMuted ? Icons.mic_off : Icons.mic,
                            label: 'Mute',
                            selected: isMuted,
                            onPressed: () => _phone.phoneLib.audio.toggleMute(),
                          ),
                          _CallControl(
                            icon: Icons.dialpad,
                            label: 'Keypad',
                            onPressed: _showDtmfKeypad,
                          ),
                          _audioControl(audioState),
                          _CallControl(
                            icon: Icons.pause,
                            label: 'Hold',
                            selected: call.isOnHold,
                            onPressed: () =>
                                _phone.phoneLib.actions.toggleHold(),
                          ),
                          _CallControl(
                            icon: inactiveCall != null
                                ? Icons.call_merge
                                : Icons.phone_forwarded,
                            label:
                                inactiveCall != null ? 'Complete' : 'Transfer',
                            onPressed: _transfer,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 40),
                    CallButton.hangUp(
                      width: 160,
                      height: 64,
                      onPressed: () => _phone.phoneLib.actions.end(),
                    ),
                    const SizedBox(height: 32),
                  ],
                ),
        ),
      ),
    );
  }
}

class _CallStatus extends StatelessWidget {
  const _CallStatus(this.call);

  final Call call;

  String get _text {
    final state = call.state;

    if (state == CallState.initializing) return 'Calling…';
    if (state == CallState.ringing) {
      return call.direction.isInbound ? 'Incoming call' : 'Ringing…';
    }
    if (state == CallState.heldByLocal)
      return 'On hold · ${call.prettyDuration}';
    if (state == CallState.heldByRemote) {
      return 'Held by other party · ${call.prettyDuration}';
    }
    if (state == CallState.ended) return 'Call ended';
    if (state == CallState.error) return 'Call failed';

    return call.prettyDuration;
  }

  @override
  Widget build(BuildContext context) {
    final mos = call.currentMos;
    final showQuality = call.state == CallState.connected && mos > 0;

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(_text,
            style:
                const TextStyle(fontFeatures: [FontFeature.tabularFigures()])),
        if (showQuality) ...[
          const SizedBox(width: 8),
          Tooltip(
            message: 'Call quality (MOS ${mos.toStringAsFixed(1)})',
            child: Icon(
              mos >= 4
                  ? Icons.signal_cellular_alt
                  : mos >= 3
                      ? Icons.signal_cellular_alt_2_bar
                      : Icons.signal_cellular_alt_1_bar,
              size: 18,
              color: mos >= 3 ? null : Theme.of(context).colorScheme.error,
            ),
          ),
        ],
      ],
    );
  }
}

class _HeldCallBanner extends StatelessWidget {
  const _HeldCallBanner(this.call);

  final Call call;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Material(
      color: theme.colorScheme.surfaceContainerHigh,
      shape: const StadiumBorder(),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.pause_circle_outline,
              size: 20,
              color: theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: 8),
            Flexible(
              child: Text(
                '${call.remotePartyHeading} on hold',
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CallControl extends StatelessWidget {
  const _CallControl({
    required this.icon,
    required this.label,
    this.selected = false,
    this.onPressed,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;

    return SizedBox(
      width: 80,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            onPressed: onPressed,
            iconSize: 28,
            tooltip: label,
            style: IconButton.styleFrom(
              fixedSize: const Size.square(72),
              backgroundColor:
                  selected ? colors.primary : colors.surfaceContainerHigh,
              foregroundColor: selected ? colors.onPrimary : colors.onSurface,
              disabledBackgroundColor: colors.surfaceContainerHigh,
            ),
            icon: Icon(icon),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelMedium,
          ),
        ],
      ),
    );
  }
}

class _TransferNumberDialog extends StatefulWidget {
  const _TransferNumberDialog();

  @override
  State<_TransferNumberDialog> createState() => _TransferNumberDialogState();
}

class _TransferNumberDialogState extends State<_TransferNumberDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Call Transfer'),
      content: TextField(
        controller: _controller,
        autofocus: true,
        keyboardType: TextInputType.phone,
        decoration: const InputDecoration(
          hintText: 'Enter the number to transfer to',
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, _controller.text),
          child: const Text('Transfer'),
        ),
      ],
    );
  }
}
