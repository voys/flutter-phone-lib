import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

final class BridgeUtilitiesTests: XCTestCase {
    func testJsonSurvivesARoundTrip() {
        let json = serializeToJSON(["text": "a \"quoted\" ü", "number": 5061, "flag": true, "missing": nil])

        let decoded = deserializeFromJSON(json)

        XCTAssertEqual(decoded["text"] as? String, "a \"quoted\" ü")
        XCTAssertEqual(decoded["number"] as? Int, 5061)
        XCTAssertEqual(decoded["flag"] as? Bool, true)
        XCTAssertTrue(decoded["missing"]! is NSNull)
    }

    func testEveryCaseIsDescribedInUpperSnakeCase() {
        XCTAssertEqual(
            [CallState.initializing, .ringing, .connected, .heldByLocal, .heldByRemote, .ended, .error]
                .map(describeAsUpperSnakeCase),
            ["INITIALIZING", "RINGING", "CONNECTED", "HELD_BY_LOCAL", "HELD_BY_REMOTE", "ENDED", "ERROR"]
        )
        XCTAssertEqual(describeAsUpperSnakeCase(CallDirection.outbound), "OUTBOUND")
        XCTAssertEqual(describeAsUpperSnakeCase("outgoingCallStarted"), "OUTGOING_CALL_STARTED")
        XCTAssertEqual(describeAsUpperSnakeCase("already_snake"), "ALREADY_SNAKE")
    }

    func testAudioRoutesSurviveARoundTrip() {
        for route in [AudioRoute.speaker, .phone, .bluetooth] {
            XCTAssertEqual(audioRouteOf(describeAsUpperSnakeCase(route)), route)
        }
    }

    func testAuthIsPersistedWithEveryField() {
        let auth = Auth(username: "user", password: "pass", domain: "sip.example.com", port: 5061, secure: false)

        XCTAssertSameValue(
            deserializeFromJSON(auth.serialize()) as NSDictionary,
            ["username": "user", "password": "pass", "domain": "sip.example.com", "port": 5061, "secure": false]
        )
    }

    func testPreferencesArePersistedUnderTheKeysDartSends() {
        let preferences = Preferences(useApplicationRingtone: true, includesCallsInRecents: false)

        XCTAssertSameValue(
            deserializeFromJSON(preferences.serialize()) as NSDictionary,
            ["useApplicationProvidedRingtone": true, "showCallsInNativeRecents": false]
        )
    }

    func testCallsAreSentWithTheirCallId() {
        XCTAssertEqual(Fixtures.activeCall.toDictionary()["callId"] as? String, "call-id-1")
    }

    func testAudioStateIsSentWithoutBluetoothRoutes() {
        XCTAssertSameValue(Fixtures.sessionState.audioState.toDictionary()["bluetoothRoutes"] ?? nil, [])
    }
}
