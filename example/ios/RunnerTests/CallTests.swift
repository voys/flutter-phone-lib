import XCTest
@testable import iOSPhoneLib

final class CallTests: XCTestCase {
    private func call(displayName: String = "", contact: Contact? = nil, duration: Int = 0) -> Call {
        Call(
            remoteNumber: "+31501234567",
            displayName: displayName,
            state: .connected,
            direction: .outbound,
            duration: duration,
            isOnHold: false,
            uuid: UUID(),
            mos: 0,
            currentMos: 0,
            contact: contact,
            reason: "",
            callId: ""
        )
    }

    func testTheContactNameIsPreferred() {
        let call = call(displayName: "Alice", contact: Contact("Alice Example"))

        XCTAssertEqual(call.remotePartyHeading, "Alice Example")
        XCTAssertEqual(call.remotePartySubheading, "+31501234567")
        XCTAssertEqual(call.prettyRemotePartyHeading, "Alice Example (+31501234567)")
    }

    func testTheDisplayNameIsUsedWithoutAContact() {
        let call = call(displayName: "Alice")

        XCTAssertEqual(call.remotePartyHeading, "Alice")
        XCTAssertEqual(call.remotePartySubheading, "+31501234567")
        XCTAssertEqual(call.prettyRemotePartyHeading, "Alice (+31501234567)")
    }

    func testTheNumberIsUsedWithoutANameOrContact() {
        let call = call()

        XCTAssertEqual(call.remotePartyHeading, "+31501234567")
        XCTAssertEqual(call.remotePartySubheading, "")
        XCTAssertEqual(call.prettyRemotePartyHeading, "+31501234567")
    }

    func testDurationsUnderAnHourShowMinutesAndSeconds() {
        XCTAssertEqual(call(duration: 0).prettyDuration, "00:00")
        XCTAssertEqual(call(duration: 59).prettyDuration, "00:59")
        XCTAssertEqual(call(duration: 75).prettyDuration, "01:15")
        XCTAssertEqual(call(duration: 3599).prettyDuration, "59:59")
    }

    func testLongerDurationsAlsoShowHours() {
        XCTAssertEqual(call(duration: 3600).prettyDuration, "01:00:00")
        XCTAssertEqual(call(duration: 3725).prettyDuration, "01:02:05")
        XCTAssertEqual(call(duration: 36000).prettyDuration, "10:00:00")
    }
}
