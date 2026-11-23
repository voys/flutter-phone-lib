import Foundation
import iOSPhoneLib
import Security

/// Keeps the SIP account for a native start, with the password in the Keychain instead of in
/// plain text.
internal struct AuthStore {
    var defaults: UserDefaults = .standard
    var passwords: PasswordStore = KeychainPasswordStore()

    func save(_ auth: Auth) {
        var persisted = auth

        do {
            try passwords.write(auth.password)
            persisted = auth.with(password: "")
        } catch {
            // Without a password there would be no native start anymore, so it then stays in
            // plain text like it used to. The next load moves it.
            log("Unable to store the SIP password securely: \(error)")
        }

        defaults.set(persisted.serialize(), forKey: PhoneLibPlugin.Keys.AUTH)
    }

    func load() -> Auth? {
        guard let auth = authOf(defaults.string(forKey: PhoneLibPlugin.Keys.AUTH)) else { return nil }

        // Stored by a version that kept it in plain text, or it couldn't be secured before.
        if !auth.password.isEmpty {
            save(auth)
            return auth
        }

        do {
            guard let password = try passwords.read() else { return nil }
            return auth.with(password: password)
        } catch {
            log("Unable to read the SIP password: \(error)")
            return nil
        }
    }

    func clear() {
        defaults.removeObject(forKey: PhoneLibPlugin.Keys.AUTH)

        do {
            try passwords.delete()
        } catch {
            log("Unable to remove the SIP password: \(error)")
        }
    }
}

internal protocol PasswordStore {
    func read() throws -> String?
    func write(_ password: String) throws
    func delete() throws
}

/// Reads and writes the item the way the `shared_secure_storage` package does, so the password
/// is where an app that uses that package expects it. That package can't be linked from here:
/// Flutter gives every plugin's Swift package a path that includes its version.
internal struct KeychainPasswordStore: PasswordStore {
    struct KeychainError: Error {
        let status: OSStatus
    }

    // Must stay the same as on Android, see AuthStore.kt.
    private let key = "flutter_phone_lib.sip_password"
    private let service = "shared_secure_storage"

    func read() throws -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound { return nil }

        guard status == errSecSuccess, let data = result as? Data else {
            throw KeychainError(status: status)
        }

        return String(data: data, encoding: .utf8)
    }

    func write(_ password: String) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: Data(password.utf8),
            // An incoming call must be able to read it while the device is locked.
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        var status = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)

        if status == errSecItemNotFound {
            status = SecItemAdd(baseQuery.merging(attributes) { $1 } as CFDictionary, nil)
        }

        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    func delete() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)

        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError(status: status)
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }
}

private extension Auth {
    func with(password: String) -> Auth {
        Auth(username: username, password: password, domain: domain, port: port, secure: secure)
    }
}
