import Flutter
import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

/// Parses the arguments in `dart_to_native.json`, which the Dart tests prove is what Dart sends.
///
/// `PIL` is a concrete class that needs linphone, so calls that reach it aren't replayed here.
final class DartToNativeContractTests: XCTestCase {
    private func arguments(_ method: String) -> [Any] {
        overTheWire(Contract.dartCall(method)["arguments"]) as! [Any]
    }

    func testInitializePhoneLibArgumentsParse() {
        let arguments = arguments("initializePhoneLib")

        XCTAssertEqual(
            preferencesOf(arguments[0] as! [String: Any?]),
            Preferences(useApplicationRingtone: true, includesCallsInRecents: false)
        )
        XCTAssertEqual(
            authOf(arguments[1] as! [String: Any?]),
            Auth(username: "contract-user", password: "contract-password", domain: "sip.example.com", port: 5061, secure: true)
        )
        XCTAssertEqual(arguments[2] as? String, "Contract Test Agent")
    }

    func testStartAndUpdatePreferencesArgumentsParse() {
        XCTAssertEqual(
            preferencesOf(arguments("PhoneLib.start")[0] as! [String: Any?]),
            Preferences(useApplicationRingtone: true, includesCallsInRecents: false)
        )
        XCTAssertEqual(
            preferencesOf(arguments("PhoneLib.updatePreferences")[0] as! [String: Any?]),
            Preferences(useApplicationRingtone: false, includesCallsInRecents: true)
        )
    }

    func testPreferencesFallBackToDefaultsForMissingKeys() {
        XCTAssertEqual(preferencesOf([:]), Preferences(useApplicationRingtone: false, includesCallsInRecents: true))
    }

    func testStandardAudioRoutesParse() {
        XCTAssertEqual(audioRouteOf("SPEAKER"), .speaker)
        XCTAssertEqual(audioRouteOf("PHONE"), .phone)
        XCTAssertEqual(audioRouteOf("BLUETOOTH"), .bluetooth)
        XCTAssertEqual(
            Contract.dartToNative.filter { $0["method"] as? String == "AudioManager.routeAudio" }.count,
            2,
            "One standard route and one Bluetooth device route, which iOS ignores"
        )
    }

    func testAuthSurvivesBeingPersisted() {
        let auth = Auth(username: "user", password: "pass", domain: "sip.example.com", port: 5061, secure: true)

        XCTAssertEqual(authOf(auth.serialize()), auth)
        XCTAssertNil(authOf(nil as String?))
    }

    func testPreferencesRoundTripThroughUserDefaults() {
        let preferences = Preferences(useApplicationRingtone: true, includesCallsInRecents: false)

        XCTAssertEqual(preferencesOf(preferences.serialize()), preferences)
        XCTAssertNil(preferencesOf(nil as String?))
    }

    func testUnknownMethodsAreNotImplemented() {
        let plugin = PhoneLibPlugin(RecordingMessenger().channel())

        for method in [
            "PhoneLib.unknown", "Calls.unknown", "EventsManager.unknown", "CallActions.unknown",
            "AudioManager.unknown", "Unknown.method", "unknown",
        ] {
            var reply: Any?
            plugin.handle(FlutterMethodCall(methodName: method, arguments: nil)) { reply = $0 }

            XCTAssertTrue(reply as AnyObject === FlutterMethodNotImplemented, method)
        }
    }
}
