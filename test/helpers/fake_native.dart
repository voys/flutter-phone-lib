import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_phone_lib/flutter_phone_lib.dart';
import 'package:flutter_test/flutter_test.dart';

/// Plays the native side of [PhoneLib.channel]: records what Dart invokes and
/// lets tests invoke Dart the way the Android and iOS bridges do.
class FakeNative {
  final List<MethodCall> calls = [];
  final Map<String, Object? Function(MethodCall call)> responses = {};

  TestDefaultBinaryMessenger get _messenger =>
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  void install() =>
      _messenger.setMockMethodCallHandler(PhoneLib.channel, (call) async {
        calls.add(call);
        return responses[call.method]?.call(call);
      });

  void uninstall() =>
      _messenger.setMockMethodCallHandler(PhoneLib.channel, null);

  List<String> get methods => calls.map((c) => c.method).toList();

  /// Returns the decoded reply, or throws the [PlatformException] that the
  /// native side would receive.
  Future<Object?> invokeDart(String method, [Object? arguments]) async {
    final reply = Completer<ByteData?>();
    await _messenger.handlePlatformMessage(
      PhoneLib.channel.name,
      PhoneLib.channel.codec.encodeMethodCall(MethodCall(method, arguments)),
      reply.complete,
    );
    final data = await reply.future;
    return data == null ? null : PhoneLib.channel.codec.decodeEnvelope(data);
  }
}

Future<PhoneLib> initializeForTest({
  void Function()? onMissedCallNotificationPressed,
}) => initializePhoneLib((builder) {
  builder
    ..preferences = Preferences.standard
    ..auth = const Auth(
      username: 'user',
      password: 'password',
      domain: 'sip.example.com',
      port: 5061,
      secure: true,
    );
  return ApplicationSetup(
    onMissedCallNotificationPressed: onMissedCallNotificationPressed,
  );
});

dynamic readContract(String name) =>
    jsonDecode(File('test/contract/$name').readAsStringSync());
