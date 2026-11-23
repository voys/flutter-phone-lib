import shared_secure_storage
import XCTest
@testable import flutter_phone_lib
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

final class AuthStoreTests: XCTestCase {
    private let suiteName = "AuthStoreTests"
    private let auth = Auth(username: "user", password: "secret", domain: "sip.example.com", port: 5061, secure: true)
    private var defaults: UserDefaults!
    private var passwords: FakePasswordStore!
    private var store: AuthStore!

    private var persisted: Auth? {
        authOf(defaults.string(forKey: PhoneLibPlugin.Keys.AUTH))
    }

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
        passwords = FakePasswordStore()
        store = AuthStore(defaults: defaults, passwords: passwords)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testThePasswordIsKeptOutOfTheDefaults() {
        store.save(auth)

        XCTAssertEqual(persisted?.password, "")
        XCTAssertEqual(persisted?.username, "user")
        XCTAssertEqual(passwords.password, "secret")
    }

    func testTheAccountIsLoadedWithItsPassword() {
        store.save(auth)

        XCTAssertEqual(store.load(), auth)
    }

    func testThereIsNoAccountBeforeOneIsSaved() {
        XCTAssertNil(store.load())
    }

    func testAnAccountWithoutItsPasswordCannotBeLoaded() {
        store.save(auth)
        passwords.password = nil

        XCTAssertNil(store.load())
    }

    func testAnAccountSavedByAnOlderVersionIsLoadedAndItsPasswordMoved() {
        defaults.set(auth.serialize(), forKey: PhoneLibPlugin.Keys.AUTH)

        XCTAssertEqual(store.load(), auth)
        XCTAssertEqual(persisted?.password, "")
        XCTAssertEqual(passwords.password, "secret")
    }

    func testThePasswordStaysInTheDefaultsWhenItCannotBeStoredSecurely() {
        passwords.isFailing = true

        store.save(auth)

        XCTAssertEqual(persisted, auth)
        XCTAssertEqual(store.load(), auth)
    }

    func testAPasswordThatCouldNotBeStoredSecurelyIsMovedOnTheNextLoad() {
        passwords.isFailing = true
        store.save(auth)
        passwords.isFailing = false

        _ = store.load()

        XCTAssertEqual(persisted?.password, "")
        XCTAssertEqual(passwords.password, "secret")
    }

    func testThePasswordInTheDefaultsIsNewerThanTheOneInTheKeychain() {
        passwords.password = "old"
        defaults.set(auth.serialize(), forKey: PhoneLibPlugin.Keys.AUTH)

        XCTAssertEqual(store.load(), auth)
        XCTAssertEqual(passwords.password, "secret")
    }

    func testAnUnreadableKeychainIsTheSameAsHavingNoPassword() {
        store.save(auth)
        passwords.isFailing = true

        XCTAssertNil(store.load())
    }

    func testClearingRemovesTheAccountAndItsPassword() {
        store.save(auth)

        store.clear()

        XCTAssertNil(persisted)
        XCTAssertNil(passwords.password)
    }

    func testClearingRemovesTheAccountEvenWhenThePasswordCannotBeRemoved() {
        store.save(auth)
        passwords.isFailing = true

        store.clear()

        XCTAssertNil(persisted)
    }
}

final class KeychainPasswordStoreTests: XCTestCase {
    private let store = KeychainPasswordStore()
    private var saved: String?

    override func setUp() {
        super.setUp()
        saved = try? store.read()
        try? store.delete()
    }

    override func tearDown() {
        if let saved {
            try? store.write(saved)
        } else {
            try? store.delete()
        }
        super.tearDown()
    }

    func testThePasswordSurvivesARoundTrip() throws {
        XCTAssertNil(try store.read())

        try store.write("secret")
        try store.write("changed ü")

        XCTAssertEqual(try store.read(), "changed ü")
    }

    func testDeletingRemovesThePasswordAndCanBeRepeated() throws {
        try store.write("secret")

        try store.delete()
        try store.delete()

        XCTAssertNil(try store.read())
    }

    func testThePasswordIsWhereTheSharedSecureStoragePackageKeepsIt() throws {
        try store.write("secret")

        XCTAssertEqual(try SharedSecureStorage.shared.read("flutter_phone_lib.sip_password"), "secret")

        try SharedSecureStorage.shared.write("from the package", for: "flutter_phone_lib.sip_password")

        XCTAssertEqual(try store.read(), "from the package")
    }
}

private final class FakePasswordStore: PasswordStore {
    struct Failure: Error {}

    var password: String?
    var isFailing = false

    func read() throws -> String? {
        if isFailing { throw Failure() }
        return password
    }

    func write(_ password: String) throws {
        if isFailing { throw Failure() }
        self.password = password
    }

    func delete() throws {
        if isFailing { throw Failure() }
        password = nil
    }
}
