import PushKit
import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

final class NativeMiddlewareBridgerTests: XCTestCase {
    private let native = RecordingNativeMiddleware()
    private lazy var bridger = NativeMiddlewareBridger(nativeMiddleware: native)
    private let payload = FakePushPayload(["phonenumber": "+31501234567"])

    func testAvailabilityIsPassedOnWithTheSamePayload() {
        bridger.respond(payload: payload, available: true, reason: nil)

        XCTAssertEqual(native.responses.count, 1)
        XCTAssertTrue(native.responses[0].payload === payload)
        XCTAssertTrue(native.responses[0].available)
        XCTAssertNil(native.responses[0].reason)
    }

    func testUnavailableReasonsMapToTheirNativeCounterparts() {
        bridger.respond(payload: payload, available: false, reason: .inCall)
        bridger.respond(payload: payload, available: false, reason: .unableToRegister)

        XCTAssertEqual(native.responses.map(\.reason), [.inCall, .unableToRegister])
        XCTAssertEqual(native.responses.map(\.available), [false, false])
    }

    func testTheDefaultReasonIsNone() {
        let middleware: Middleware = bridger

        middleware.respond(payload: payload, available: false)

        XCTAssertEqual(native.responses.count, 1)
        XCTAssertNil(native.responses[0].reason)
    }

    func testInspectedPushesReachTheNativeMiddleware() {
        bridger.inspect(payload: payload, type: .voIP)

        XCTAssertEqual(native.inspected.count, 1)
        XCTAssertTrue(native.inspected[0].payload === payload)
        XCTAssertEqual(native.inspected[0].type, .voIP)
    }

    func testPushPayloadsAreSentUnderData() {
        XCTAssertSameValue(
            overTheWire(payload.toDictionary()),
            ["data": ["phonenumber": "+31501234567"]]
        )
    }
}
