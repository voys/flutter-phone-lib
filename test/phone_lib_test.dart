import 'package:flutter/services.dart';
import 'package:flutter_phone_lib/flutter_phone_lib.dart';
import 'package:flutter_phone_lib/src/audio/audio_manager.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/fake_native.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = FakeNative();

  setUp(() {
    native
      ..calls.clear()
      ..responses.clear()
      ..install();
  });

  tearDown(native.uninstall);

  group('initializePhoneLib', () {
    test('sends preferences, auth and user agent in that order', () async {
      final phoneLib = await initializeForTest();
      addTearDown(phoneLib.close);

      final call = native.calls.single;
      expect(call.method, 'initializePhoneLib');
      expect(call.arguments, [
        Preferences.standard.toJson(),
        {
          'username': 'user',
          'password': 'password',
          'domain': 'sip.example.com',
          'port': 5061,
          'secure': true,
        },
        'Flutter Phone Lib',
      ]);
      expect(PhoneLib.instance, same(phoneLib));
    });

    test('asserts that auth is set before calling native', () async {
      await expectLater(
        initializePhoneLib((builder) {
          builder.preferences = Preferences.standard;
          return const ApplicationSetup();
        }),
        throwsA(isA<AssertionError>()),
      );
      expect(native.calls, isEmpty);
    });

    test('asserts that preferences are set before calling native', () async {
      await expectLater(
        initializePhoneLib((builder) {
          builder.auth = const Auth(
            username: 'user',
            password: 'password',
            domain: 'sip.example.com',
            port: 5061,
            secure: true,
          );
          return const ApplicationSetup();
        }),
        throwsA(isA<AssertionError>()),
      );
      expect(native.calls, isEmpty);
    });

    test('propagates native errors', () async {
      native.responses['initializePhoneLib'] = (_) =>
          throw PlatformException(code: 'NullPointerException');

      await expectLater(initializeForTest(), throwsA(isA<PlatformException>()));
    });
  });

  group('PhoneLib lifecycle', () {
    test('only one instance can exist at a time', () async {
      final phoneLib = await initializeForTest();
      addTearDown(phoneLib.close);

      expect(() => PhoneLib(null), throwsStateError);
    });

    test('close stops native and allows a new instance', () async {
      final first = await initializeForTest();
      await first.close();

      expect(native.methods.last, 'PhoneLib.stop');

      final second = await initializeForTest();
      addTearDown(second.close);
      expect(PhoneLib.instance, same(second));
    });

    test('after close, native events are no longer handled', () async {
      final phoneLib = await initializeForTest();
      await phoneLib.close();

      // No handler means the call is dropped and the reply is empty.
      expect(
        await native.invokeDart('onMissedCallNotificationPressed'),
        isNull,
      );
    });
  });

  group('events', () {
    late PhoneLib phoneLib;

    setUp(() async {
      phoneLib = await initializeForTest();
      native.calls.clear();
    });

    tearDown(() => phoneLib.close());

    test('native listening starts once, however many Dart listeners', () async {
      final first = phoneLib.events.listen((_) {});
      final second = phoneLib.events.listen((_) {});
      await pumpEventQueue();

      expect(native.methods, ['EventsManager.listen']);

      await first.cancel();
      await pumpEventQueue();
      expect(native.methods, ['EventsManager.listen']);

      await second.cancel();
      await pumpEventQueue();
      expect(native.methods, [
        'EventsManager.listen',
        'EventsManager.stopListening',
      ]);
    });

    test('native listening restarts when Dart listens again', () async {
      await phoneLib.events.listen((_) {}).cancel();
      await pumpEventQueue();
      phoneLib.events.listen((_) {});
      await pumpEventQueue();

      expect(native.methods, [
        'EventsManager.listen',
        'EventsManager.stopListening',
        'EventsManager.listen',
      ]);
    });

    test('every listener receives native events', () async {
      final first = <Event>[];
      final second = <Event>[];
      phoneLib.events.listen(first.add);
      phoneLib.events.listen(second.add);

      await native.invokeDart('onEvent', {
        'type': 'OutgoingCallSetupFailed',
        'reason': 'UNABLE_TO_REGISTER',
      });

      expect(first, hasLength(1));
      expect(second, first);
    });

    test('unknown native methods are ignored', () async {
      expect(await native.invokeDart('somethingNew', {'a': 1}), isNull);
    });
  });

  group('missed call notification', () {
    test('invokes the callback from ApplicationSetup', () async {
      var pressed = 0;
      final phoneLib = await initializeForTest(
        onMissedCallNotificationPressed: () => pressed++,
      );
      addTearDown(phoneLib.close);

      await native.invokeDart('onMissedCallNotificationPressed');

      expect(pressed, 1);
    });

    test('is a no-op without a callback', () async {
      final phoneLib = await initializeForTest();
      addTearDown(phoneLib.close);

      expect(
        await native.invokeDart('onMissedCallNotificationPressed'),
        isNull,
      );
    });
  });

  group('PhoneLib methods', () {
    late PhoneLib phoneLib;

    setUp(() async {
      phoneLib = await initializeForTest();
      native.calls.clear();
    });

    tearDown(() => phoneLib.close());

    test('playToneLocally rejects anything but a single character', () {
      expect(() => phoneLib.playToneLocally(''), throwsArgumentError);
      expect(() => phoneLib.playToneLocally('12'), throwsArgumentError);
      expect(native.calls, isEmpty);
    });

    test('linphoneVersion falls back to an empty string', () async {
      expect(await phoneLib.linphoneVersion, '');

      native.responses['PhoneLib.linphoneVersion'] = (_) => '5.5.35';
      expect(await phoneLib.linphoneVersion, '5.5.35');
    });

    test('calls.active is null when native has no active call', () async {
      expect(await phoneLib.calls.active, isNull);
    });

    test('audio.isMicrophoneMuted returns the native value', () async {
      native.responses['AudioManager.isMicrophoneMuted'] = (_) => true;

      expect(await phoneLib.audio.isMicrophoneMuted, isTrue);
    });

    test(
      'a method native does not implement throws MissingPluginException',
      () async {
        native.responses['AudioManager.launchAudioRoutePicker'] = (_) =>
            throw MissingPluginException();

        await expectLater(
          phoneLib.audio.launchAudioRoutePicker(),
          throwsA(isA<MissingPluginException>()),
        );
      },
    );

    test('sendDtmf plays the tone locally by default', () async {
      await phoneLib.actions.sendDtmf('#');

      expect(native.calls.single.arguments, {
        'dtmf': '#',
        'playToneLocally': true,
      });
    });

    test('codecs are not implemented', () {
      expect(() => AudioManager().availableCodecs, throwsUnimplementedError);
    });
  });
}
