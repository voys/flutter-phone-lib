import 'package:flutter/services.dart';
import 'package:flutter_phone_lib/flutter_phone_lib.dart';
import 'package:flutter_phone_lib/src/contacts/contact.dart';
import 'package:flutter_test/flutter_test.dart';

import '../helpers/fake_native.dart';

/// `<platform>_to_dart.json` holds exactly what the Android and iOS bridges
/// produce (their own tests assert this), so these tests show how Dart
/// interprets each platform's output.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  for (final platform in ['android', 'ios']) {
    group(platform, () {
      final contract = readContract('${platform}_to_dart.json') as Map;
      final expected = _expectedSessionState(platform);
      final native = FakeNative();
      late PhoneLib phoneLib;

      setUp(() async {
        native
          ..calls.clear()
          ..responses.clear()
          ..install();
        phoneLib = await initializeForTest();
      });

      tearDown(() async {
        await phoneLib.close();
        native.uninstall();
      });

      test(
        'PhoneLib.sessionState decodes to the expected Dart model',
        () async {
          native.responses['PhoneLib.sessionState'] = (_) =>
              contract['sessionState'];

          expect(await phoneLib.sessionState, expected);
        },
      );

      test('an idle session state has no calls', () async {
        native.responses['PhoneLib.sessionState'] = (_) =>
            contract['idleSessionState'];

        final state = await phoneLib.sessionState;

        expect(state.activeCall, isNull);
        expect(state.inactiveCall, isNull);
        expect(
          state.audioState,
          const AudioState(
            currentRoute: AudioRoute.phone,
            availableRoutes: [AudioRoute.speaker, AudioRoute.phone],
            isMicrophoneMuted: false,
            bluetoothRoutes: [],
          ),
        );
      });

      test('Calls.active decodes an active call without a contact', () async {
        native.responses['Calls.active'] = (_) =>
            (contract['sessionState'] as Map)['inactiveCall'];

        expect(await phoneLib.calls.active, expected.inactiveCall);
      });

      test('Calls.active decodes an active call with a contact', () async {
        native.responses['Calls.active'] = (_) =>
            (contract['sessionState'] as Map)['activeCall'];

        expect(await phoneLib.calls.active, expected.activeCall);
      });

      test('AudioManager.state decodes the audio state', () async {
        native.responses['AudioManager.state'] = (_) =>
            (contract['sessionState'] as Map)['audioState'];

        expect(await phoneLib.audio.state, expected.audioState);
      });

      final supportedTypes = <String>{
        'OutgoingCallStarted',
        'IncomingCallReceived',
        'CallEnded',
        'CallConnected',
        'CallDurationUpdated',
        'AudioStateUpdated',
        'CallStateUpdated',
        'AttendedTransferStarted',
        'AttendedTransferConnected',
        'AttendedTransferAborted',
        'AttendedTransferEnded',
      };

      for (final type
          in (contract['callSessionEventTypes'] as List).cast<String>()) {
        if (supportedTypes.contains(type)) {
          test('onEvent $type is emitted with its session state', () async {
            final events = <Event>[];
            phoneLib.events.listen(events.add);

            await native.invokeDart('onEvent', {
              'type': type,
              'state': contract['sessionState'],
            });

            expect(events, hasLength(1));
            expect(events.single.runtimeType.toString(), type);
            expect((events.single as CallSessionEvent).state, expected);
          });
        } else {
          test('onEvent $type is not supported by Dart and is reported back '
              'to native as an error', () async {
            final events = <Event>[];
            phoneLib.events.listen(events.add);

            await expectLater(
              native.invokeDart('onEvent', {
                'type': type,
                'state': contract['sessionState'],
              }),
              throwsA(
                isA<PlatformException>().having(
                  (e) => e.message,
                  'message',
                  contains('Unsupported Event'),
                ),
              ),
            );
            expect(events, isEmpty);
          });
        }
      }

      for (final type
          in (contract['callSetupFailedEventTypes'] as List).cast<String>()) {
        for (final reason
            in (contract['callSetupFailedReasons'] as List).cast<String>()) {
          test(
            'onEvent $type with $reason is emitted with its reason',
            () async {
              final events = <Event>[];
              phoneLib.events.listen(events.add);

              await native.invokeDart('onEvent', {
                'type': type,
                'reason': reason,
              });

              expect(events, hasLength(1));
              expect(events.single.runtimeType.toString(), type);
              expect(
                (events.single as CallSetupFailedEvent).reason.name,
                reason,
              );
            },
          );
        }
      }
    });
  }
}

CallSessionState _expectedSessionState(String platform) {
  final isAndroid = platform == 'android';
  String uuid(int n) {
    final value = 'c0ffee00-0000-4000-8000-00000000000$n';
    return isAndroid ? value : value.toUpperCase();
  }

  return CallSessionState(
    activeCall: Call(
      remoteNumber: '+31501234567',
      displayName: 'Alice',
      state: CallState.connected,
      direction: CallDirection.outbound,
      duration: 75,
      isOnHold: false,
      uuid: uuid(1),
      mos: 4.5,
      currentMos: 4.25,
      contact: Contact(
        name: 'Alice Example',
        imageUri: isAndroid
            ? Uri.parse('content://com.android.contacts/contacts/1/photo')
            : null,
      ),
      remotePartyHeading: 'Alice Example',
      remotePartySubheading: '+31501234567',
      prettyDuration: '01:15',
      callId: 'call-id-1',
      reason: '',
    ),
    inactiveCall: Call(
      remoteNumber: '+31507654321',
      displayName: '',
      state: CallState.heldByLocal,
      direction: CallDirection.inbound,
      duration: 3725,
      isOnHold: true,
      uuid: uuid(2),
      mos: 0,
      currentMos: 0,
      remotePartyHeading: '+31507654321',
      remotePartySubheading: '',
      prettyDuration: isAndroid ? '1:02:05' : '01:02:05',
      callId: 'call-id-2',
      reason: 'Busy Here',
    ),
    audioState: AudioState(
      currentRoute: AudioRoute.bluetooth,
      availableRoutes: const [
        AudioRoute.speaker,
        AudioRoute.phone,
        AudioRoute.bluetooth,
      ],
      bluetoothDeviceName: 'Headset',
      isMicrophoneMuted: true,
      bluetoothRoutes: isAndroid
          ? const [
              BluetoothAudioRoute(
                displayName: 'Headset',
                identifier: '00:11:22:33:44:55',
              ),
            ]
          : const [],
    ),
  );
}
