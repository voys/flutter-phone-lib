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

    // Bug: a contact without a given or family name (e.g. only a company) becomes " ",
    // which counts as found and hides the caller's display name and number.
    func testAddressBookContactsWithoutANameAreABlankName() {
        let contact = CNMutableContact()
        contact.organizationName = "Example B.V."

        let found = contact.toContact()

        XCTAssertEqual(found.name, " ")
        XCTAssertTrue(found.exists)
        XCTAssertEqual(
            Call(
                remoteNumber: "+31501234567", displayName: "Reception", state: .ringing, direction: .inbound,
                duration: 0, isOnHold: false, uuid: UUID(), mos: 0, currentMos: 0, contact: found, reason: ""
            ).remotePartyHeading,
            " "
        )
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
