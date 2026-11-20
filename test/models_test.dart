import 'package:flutter_phone_lib/flutter_phone_lib.dart';
import 'package:flutter_phone_lib/src/audio/codec.dart';
import 'package:flutter_phone_lib/src/contacts/contact.dart';
import 'package:flutter_phone_lib/src/util/cast_recursive.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('UpperCaseEnum', () {
    test('serializes to and from its upper case name', () {
      expect(CallState.heldByRemote.toJson(), 'HELD_BY_REMOTE');
      expect(CallState.fromJson('HELD_BY_REMOTE'), CallState.heldByRemote);
      expect(AudioRoute.fromJson('PHONE'), AudioRoute.phone);
      expect(CallDirection.fromJson('INBOUND'), CallDirection.inbound);
      expect(Reason.fromJson('REJECTED_BY_CALL_KIT'), Reason.rejectedByCallKit);
    });

    test('throws for values Dart does not know', () {
      expect(() => CallState.fromJson('connected'), throwsStateError);
      expect(() => AudioRoute.fromJson('WIRED_HEADSET'), throwsStateError);
    });

    test('names are unique per type', () {
      for (final values in [
        CallState.values,
        CallDirection.values,
        AudioRoute.values,
        Reason.values,
        Codec.values,
      ]) {
        expect(values.map((v) => v.name).toSet(), hasLength(values.length));
      }
    });

    test('toString includes the type and name', () {
      expect(CallState.ringing.toString(), "CallState('RINGING')");
    });

    test('CallDirection helpers', () {
      expect(CallDirection.inbound.isInbound, isTrue);
      expect(CallDirection.inbound.isOutbound, isFalse);
      expect(CallDirection.outbound.isOutbound, isTrue);
    });
  });

  group('castRecursively', () {
    test('casts nested maps', () {
      final map = <Object?, Object?>{
        'a': <Object?, Object?>{
          'b': <Object?, Object?>{'c': 1},
        },
      };

      final result = map.castRecursively();

      expect(result['a'], isA<Map<String, dynamic>>());
      expect((result['a'] as Map)['b'], isA<Map<String, dynamic>>());
    });

    test('survives a map that contains itself', () {
      final map = <Object?, Object?>{'name': 'loop'};
      map['self'] = map;

      expect(map.castRecursively, returnsNormally);
    });
  });

  group('Contact', () {
    test('parses an image URI', () {
      expect(
        Contact.fromJson({'name': 'Alice', 'imageUri': 'content://photo/1'}),
        Contact(name: 'Alice', imageUri: Uri.parse('content://photo/1')),
      );
    });

    test('treats missing, empty or non-string image URIs as null', () {
      for (final imageUri in [null, '', 42]) {
        expect(
          Contact.fromJson({'name': 'Alice', 'imageUri': imageUri}).imageUri,
          isNull,
        );
      }
    });

    // Android's bridge sends `imageUri.toString()`, so a contact without a
    // photo arrives as the string "null".
    test('keeps the literal "null" Android sends for a missing photo', () {
      expect(
        Contact.fromJson({'name': 'Alice', 'imageUri': 'null'}).imageUri,
        Uri.parse('null'),
      );
    });
  });

  group('Call', () {
    Map<String, dynamic> json() => {
      'remoteNumber': '+31501234567',
      'displayName': 'Alice',
      'state': 'RINGING',
      'direction': 'INBOUND',
      'duration': 0,
      'isOnHold': false,
      'uuid': 'uuid',
      'mos': 0,
      'currentMos': 0,
      'contact': null,
      'remotePartyHeading': 'Alice',
      'remotePartySubheading': '+31501234567',
      'prettyDuration': '00:00',
      'callId': 'id',
      'reason': '',
    };

    test('accepts integer MOS values', () {
      expect(Call.fromJson(json()).mos, 0.0);
    });

    test('round-trips through JSON', () {
      final call = Call.fromJson(json());

      expect(Call.fromJson(call.toJson()), call);
    });

    test('requires every field the bridges send', () {
      expect(
        () => Call.fromJson(json()..remove('callId')),
        throwsA(isA<TypeError>()),
      );
    });
  });

  group('configuration', () {
    test('Preferences.standard matches the PIL default', () {
      expect(Preferences.standard.toJson(), {
        'useApplicationProvidedRingtone': false,
        'showCallsInNativeRecents': true,
        'enableAdvancedLogging': false,
      });
    });

    test('enableAdvancedLogging defaults to false', () {
      expect(
        Preferences.fromJson({
          'useApplicationProvidedRingtone': true,
          'showCallsInNativeRecents': true,
        }).enableAdvancedLogging,
        isFalse,
      );
    });

    test('Auth round-trips through JSON', () {
      const auth = Auth(
        username: 'user',
        password: 'password',
        domain: 'sip.example.com',
        port: 5060,
        secure: false,
      );

      expect(Auth.fromJson(auth.toJson()), auth);
    });

    test('ApplicationSetup has a default user agent', () {
      expect(const ApplicationSetup().userAgent, 'Flutter Phone Lib');
    });

    test('ApplicationSetup rejects an empty user agent', () {
      expect(
        // ignore: prefer_const_constructors
        () => ApplicationSetup(userAgent: ''),
        throwsA(isA<AssertionError>()),
      );
    });
  });

  group('RemoteMessage', () {
    test('casts the nested data map', () {
      final message = RemoteMessage.fromMap(<Object?, Object?>{
        'data': <Object?, Object?>{'unique_key': 'abc'},
      });

      expect(message.data, {'unique_key': 'abc'});
    });

    test('has empty data when data is missing', () {
      expect(RemoteMessage.fromMap(<Object?, Object?>{}).data, isEmpty);
    });
  });

  group('Event.fromJson', () {
    test('throws for an unknown type', () {
      expect(
        () => Event.fromJson({'type': 'Unknown', 'reason': 'UNKNOWN'}),
        throwsUnsupportedError,
      );
    });

    test('throws without a type', () {
      expect(
        () => Event.fromJson({'reason': 'IN_CALL'}),
        throwsUnsupportedError,
      );
    });

    test('throws for a reason Dart does not know', () {
      expect(
        () => Event.fromJson({
          'type': 'OutgoingCallSetupFailed',
          'reason': 'NO_CONNECTIVITY',
        }),
        throwsStateError,
      );
    });
  });
}
