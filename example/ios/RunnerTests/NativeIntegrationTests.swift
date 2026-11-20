import Flutter
import PushKit
import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

/// The native-only API that apps use from their `AppDelegate`.
final class NativeIntegrationTests: XCTestCase {
    override func tearDown() {
        PhoneLibPlugin.onCallEnded = nil
        super.tearDown()
    }

    func testCallEndedIsSummarisedForOnCallEnded() {
        var ended: [NativeCall] = []
        PhoneLibPlugin.onCallEnded = { ended.append($0) }

        CallListener().onEvent(event: .callEnded(state: Fixtures.sessionState))

        XCTAssertEqual(ended.count, 1)
        XCTAssertEqual(ended.first?.mos, "4.5")
        XCTAssertEqual(ended.first?.reason, "")
        XCTAssertEqual(ended.first?.duration, "75")
        XCTAssertEqual(ended.first?.direction, "outbound")
    }

    func testOnCallEndedIsSkippedWithoutAnActiveCall() {
        var ended: [NativeCall] = []
        PhoneLibPlugin.onCallEnded = { ended.append($0) }

        CallListener().onEvent(event: .callEnded(state: Fixtures.idleSessionState))
        CallListener().onEvent(event: .callConnected(state: Fixtures.sessionState))

        XCTAssertTrue(ended.isEmpty)
    }

    func testTokensReachTheNativeMiddleware() {
        let native = RecordingMiddleware()

        NativeMiddlewareBridger(nativeMiddleware: native).tokenReceived(token: "token")

        XCTAssertEqual(native.tokens, ["token"])
    }

    func testDescribeAsUpperSnakeCase() {
        XCTAssertEqual(describeAsUpperSnakeCase(CallState.heldByRemote), "HELD_BY_REMOTE")
        XCTAssertEqual(describeAsUpperSnakeCase(CallDirection.inbound), "INBOUND")
        XCTAssertEqual(describeAsUpperSnakeCase(AudioRoute.speaker), "SPEAKER")
    }

    func testContactsAreSentWithoutAnImage() {
        XCTAssertSameValue(overTheWire(Contact("Bob").toDictionary()), ["name": "Bob", "imageUri": NSNull()])
    }
}

private class RecordingMiddleware: NativeMiddleware {
    var tokens: [String] = []

    func respond(payload: PKPushPayload, available: Bool, reason: NativeMiddlewareUnavailableReason?) {}

    func tokenReceived(token: String) {
        tokens.append(token)
    }

    func inspect(payload: PKPushPayload, type: PKPushType) {}
}
