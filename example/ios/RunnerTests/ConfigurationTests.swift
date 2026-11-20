import XCTest
@testable import iOSPhoneLib

final class ConfigurationTests: XCTestCase {
    private let auth = Fixtures.auth

    func testCompleteAuthIsValid() {
        XCTAssertTrue(auth.isValid)
        XCTAssertTrue(Auth(username: "u", password: "p", domain: "d", port: 5060, secure: false).isValid)
    }

    func testAuthWithoutAnyRequiredFieldIsInvalid() {
        XCTAssertFalse(Auth(username: "", password: "pass", domain: "sip.example.com", port: 5061, secure: true).isValid)
        XCTAssertFalse(Auth(username: "user", password: "", domain: "sip.example.com", port: 5061, secure: true).isValid)
        XCTAssertFalse(Auth(username: "user", password: "pass", domain: "", port: 5061, secure: true).isValid)
        XCTAssertFalse(Auth(username: "user", password: "pass", domain: "sip.example.com", port: 0, secure: true).isValid)
    }

    func testAuthEqualityComparesEveryField() {
        XCTAssertEqual(auth, Auth(username: "user", password: "pass", domain: "sip.example.com", port: 5061, secure: true))
        XCTAssertNotEqual(auth, Auth(username: "other", password: "pass", domain: "sip.example.com", port: 5061, secure: true))
        XCTAssertNotEqual(auth, Auth(username: "user", password: "other", domain: "sip.example.com", port: 5061, secure: true))
        XCTAssertNotEqual(auth, Auth(username: "user", password: "pass", domain: "other", port: 5061, secure: true))
        XCTAssertNotEqual(auth, Auth(username: "user", password: "pass", domain: "sip.example.com", port: 5060, secure: true))
        XCTAssertNotEqual(auth, Auth(username: "user", password: "pass", domain: "sip.example.com", port: 5061, secure: false))
    }

    func testPreferencesDefaults() {
        let preferences = Preferences()

        XCTAssertTrue(preferences.useApplicationRingtone)
        XCTAssertFalse(preferences.includesCallsInRecents)
    }

    func testPreferencesEqualityComparesEveryField() {
        XCTAssertEqual(Preferences(), Preferences(useApplicationRingtone: true, includesCallsInRecents: false))
        XCTAssertNotEqual(Preferences(), Preferences(useApplicationRingtone: false, includesCallsInRecents: false))
        XCTAssertNotEqual(Preferences(), Preferences(useApplicationRingtone: true, includesCallsInRecents: true))
    }

    func testApplicationSetupDefaults() {
        var requestedCallUi = 0
        let setup = ApplicationSetup(requestCallUi: { requestedCallUi += 1 })

        XCTAssertNil(setup.middleware)
        XCTAssertNil(setup.logDelegate)
        XCTAssertEqual(setup.userAgent, "iOS PIL")
        XCTAssertTrue(setup.notifyOnMissedCall)
        XCTAssertEqual(setup.pushKitPhoneNumberKey, "phonenumber")
        XCTAssertEqual(setup.pushKitCallerNameKey, "caller_id")
        XCTAssertEqual(setup.ringtonePath, "")

        setup.requestCallUi()
        XCTAssertEqual(requestedCallUi, 1)
    }
}
