import XCTest
@testable import iOSPhoneLib

/// Tracked calls are `VoIPLibCall`s, which wrap a linphone call, so only the idle state is covered here.
final class CallsTests: XCTestCase {
    private let factory = PILCallFactory(contacts: Contacts())

    func testCallsStartIdle() {
        let calls = Calls(factory: factory)

        XCTAssertFalse(calls.isInCall)
        XCTAssertFalse(calls.isInTransfer)
        XCTAssertNil(calls.activeVoipLibCall)
        XCTAssertNil(calls.inactiveVoipLibCall)
        XCTAssertNil(calls.activeCall)
        XCTAssertNil(calls.inactiveCall)
        XCTAssertNil(calls.transferSession)
    }

    func testAtMostTwoCallsAreTracked() {
        let calls = Calls(factory: factory)

        XCTAssertEqual(calls.list.maxCalls, 2)
        XCTAssertTrue(calls.list.callArray.isEmpty)
    }

    func testNoCallIsMadeFromNothing() {
        XCTAssertNil(factory.make(voipLibCall: nil))
    }

    func testServicesThatDontNeedThePILAreShared() {
        XCTAssertTrue(di.resolve(Calls.self)! === di.resolve(Calls.self)!)
        XCTAssertTrue(di.resolve(Contacts.self)! === di.resolve(Contacts.self)!)
        XCTAssertTrue(di.resolve(PILCallFactory.self)! === di.resolve(PILCallFactory.self)!)
        XCTAssertTrue(di.resolve(SystemTones.self)! === di.resolve(SystemTones.self)!)
        XCTAssertTrue(di.resolve(MissedCallNotification.self)! === di.resolve(MissedCallNotification.self)!)
        XCTAssertTrue(di.resolve(VoIPLib.self)! === VoIPLib.shared)
    }
}
