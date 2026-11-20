import 'package:flutter_phone_lib/flutter_phone_lib.dart';
import 'package:flutter_test/flutter_test.dart';

import '../helpers/fake_native.dart';

/// The Android and iOS bridge tests replay `dart_to_native.json`, so this
/// test pins down that Dart really sends those calls.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = FakeNative();
  late PhoneLib phoneLib;

  setUp(native.install);

  tearDown(() async {
    await phoneLib.close();
    native.uninstall();
  });

  test('every public API call matches dart_to_native.json', () async {
    const preferences = Preferences(
      useApplicationProvidedRingtone: true,
      showCallsInNativeRecents: false,
      enableAdvancedLogging: true,
    );
    const auth = Auth(
      username: 'contract-user',
      password: 'contract-password',
      domain: 'sip.example.com',
      port: 5061,
      secure: true,
    );
    final idle =
        (readContract('android_to_dart.json') as Map)['idleSessionState'];
    native.responses.addAll({
      'PhoneLib.linphoneVersion': (_) => '5.5.35',
      'PhoneLib.sessionState': (_) => idle,
      'AudioManager.state': (_) => (idle as Map)['audioState'],
      'AudioManager.isMicrophoneMuted': (_) => false,
    });

    phoneLib = await initializePhoneLib((builder) {
      builder
        ..preferences = preferences
        ..auth = auth;
      return const ApplicationSetup(userAgent: 'Contract Test Agent');
    });

    await phoneLib.start(preferences, auth);
    await phoneLib.unregister();
    await phoneLib.stop();
    await phoneLib.updatePreferences(Preferences.standard);
    await phoneLib.call('+31501234567');
    await phoneLib.linphoneVersion;
    await phoneLib.sessionState;
    await phoneLib.performEchoCancellationCalibration();
    await phoneLib.playToneLocally('5');
    await phoneLib.calls.active;

    final subscription = phoneLib.events.listen((_) {});
    await pumpEventQueue();
    await subscription.cancel();
    await pumpEventQueue();

    final actions = phoneLib.actions;
    await actions.hold();
    await actions.unhold();
    await actions.toggleHold();
    await actions.sendDtmf('7', playToneLocally: false);
    await actions.beginAttendedTransfer('+31507654321');
    await actions.completeAttendedTransfer();
    await actions.answer();
    await actions.decline();
    await actions.end();

    final audio = phoneLib.audio;
    await audio.isMicrophoneMuted;
    await audio.state;
    await audio.routeAudio(AudioRoute.speaker);
    await audio.routeAudioToBluetoothDevice(
      const BluetoothAudioRoute(
        displayName: 'Headset',
        identifier: '00:11:22:33:44:55',
      ),
    );
    await audio.launchAudioRoutePicker();
    await audio.mute();
    await audio.unmute();
    await audio.toggleMute();

    expect(
      native.calls
          .map((c) => {'method': c.method, 'arguments': c.arguments})
          .toList(),
      readContract('dart_to_native.json'),
    );
  });
}
