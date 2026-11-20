import Contacts
import XCTest
@testable import iOSPhoneLib

final class ContactsTests: XCTestCase {
    func testNamedContactsExist() {
        let contact = Contact("Alice")

        XCTAssertEqual(contact.name, "Alice")
        XCTAssertNil(contact.image)
        XCTAssertTrue(contact.exists)
    }

    func testNotFoundIsAnUnnamedContact() {
        let contact = Contact.notFound()

        XCTAssertEqual(contact.name, "")
        XCTAssertFalse(contact.exists)
    }

    func testAddressBookContactsAreNamedByGivenAndFamilyName() {
        let contact = CNMutableContact()
        contact.givenName = "Alice"
        contact.familyName = "Example"

        XCTAssertEqual(contact.toContact().name, "Alice Example")
    }

    func testAddressBookContactsWithOnlyAGivenNameHaveNoTrailingSpace() {
        let contact = CNMutableContact()
        contact.givenName = "Alice"

        XCTAssertEqual(contact.toContact().name, "Alice")
    }

    func testAddressBookContactsWithoutANameDoNotExist() {
        let contact = CNMutableContact()
        contact.organizationName = "Example B.V."

        let found = contact.toContact()

        XCTAssertEqual(found.name, "")
        XCTAssertFalse(found.exists)
    }

    func testNothingIsFoundWithoutContactsAccess() throws {
        try XCTSkipIf(CNContactStore.authorizationStatus(for: .contacts) == .authorized, "Contacts access was granted")

        XCTAssertNil(Contacts().find(number: "+31501234567", identifier: "call-1"))
    }

    func testClearingAnEmptyCacheIsHarmless() throws {
        try XCTSkipIf(CNContactStore.authorizationStatus(for: .contacts) == .authorized, "Contacts access was granted")
        let contacts = Contacts()

        contacts.clearCache()
        NotificationCenter.default.post(name: .CNContactStoreDidChange, object: nil)

        XCTAssertNil(contacts.find(number: "+31501234567", identifier: "call-1"))
    }
}
