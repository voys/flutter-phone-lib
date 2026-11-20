import PushKit
import XCTest
@testable import iOSPhoneLib

final class PILTests: XCTestCase {
    func testNumbersAreStrippedOfDashesAndParentheses() {
        XCTAssertEqual("(050) 123-45-67".normalizedForCalling, "050 1234567")
        XCTAssertEqual("+31-(0)50-1234567".normalizedForCalling, "+310501234567")
        XCTAssertEqual("+31501234567".normalizedForCalling, "+31501234567")
        XCTAssertEqual("".normalizedForCalling, "")
    }

    func testOtherSeparatorsAreKept() {
        XCTAssertEqual("+31 50 123.45.67".normalizedForCalling, "+31 50 123.45.67")
        XCTAssertEqual("*31#".normalizedForCalling, "*31#")
    }

    func testIsInitializedFollowsTheSharedInstance() {
        XCTAssertEqual(PIL.isInitialized, PIL.shared != nil)
    }

    func testApnsTokensAreLowercaseHex() {
        XCTAssertEqual(String(apnsToken: Data([0x00, 0x0f, 0xab, 0xff])), "000fabff")
        XCTAssertEqual(String(apnsToken: Data()), "")
    }

    func testMiddlewareRespondsWithoutAReasonByDefault() {
        let middleware = RecordingMiddleware()
        let payload = FakePushPayload()

        middleware.respond(payload: payload, available: true)
        middleware.respond(payload: payload, available: false, reason: .inCall)

        XCTAssertEqual(middleware.responses.map(\.available), [true, false])
        XCTAssertEqual(middleware.responses.map(\.reason), [nil, .inCall])
    }

    func testMissedCallNotificationsCanBeFoundByClients() {
        XCTAssertEqual(NotificationRequestIdentifiers.missedCalls.rawValue, "pil-missed_calls_notification")
        XCTAssertEqual(NotificationDataKeys.missedCallsCount.rawValue, "missedCallsCount")
    }

    func testMissedCallNotificationsTrackTheirCount() {
        let key = NotificationDataKeys.missedCallsCount.rawValue

        XCTAssertEqual(FakeNotification(identifier: "n", userInfo: [key: 3]).missedCallsCount, 3)
        XCTAssertEqual(FakeNotification(identifier: "n").missedCallsCount, 0)
        XCTAssertEqual(FakeNotification(identifier: "n", userInfo: [key: "3"]).missedCallsCount, 0)
    }
}

private final class RecordingMiddleware: Middleware {
    var responses: [(available: Bool, reason: UnavailableReason?)] = []

    func respond(payload: PKPushPayload, available: Bool, reason: UnavailableReason?) {
        responses.append((available, reason))
    }

    func tokenReceived(token: String) {}

    func inspect(payload: PKPushPayload, type: PKPushType) {}
}
