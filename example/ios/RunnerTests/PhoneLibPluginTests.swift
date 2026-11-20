import Flutter
import UIKit
import UserNotifications
import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

/// The plugin's statics, `UserDefaults` and the PIL are shared with the host app, which may have
/// started the PIL, so each test restores them.
final class PhoneLibPluginTests: XCTestCase {
    private let persistedKeys = [
        PhoneLibPlugin.Keys.PREFERENCES, PhoneLibPlugin.Keys.AUTH, PhoneLibPlugin.Keys.USER_AGENT,
    ]
    private var savedDefaults: [String: Any] = [:]
    private var savedInstance: PhoneLibPlugin?
    private var savedAppDelegate: UIApplicationDelegate?
    private var savedRegisterPlugins: ((FlutterPluginRegistry) -> Void)?
    private var savedNativeMiddleware: NativeMiddleware?
    private var savedOnCallEnded: OnCallEndedCallback?
    private var savedOnLogReceived: OnLogReceivedCallback?
    private var savedRingtonePath: String?

    override func setUp() {
        super.setUp()
        for key in persistedKeys {
            savedDefaults[key] = UserDefaults.standard.object(forKey: key)
        }
        savedInstance = PhoneLibPlugin.instance
        savedAppDelegate = PhoneLibPlugin.appDelegate
        savedRegisterPlugins = PhoneLibPlugin.registerPlugins
        savedNativeMiddleware = PhoneLibPlugin.nativeMiddleware
        savedOnCallEnded = PhoneLibPlugin.onCallEnded
        savedOnLogReceived = PhoneLibPlugin.onLogReceived
        savedRingtonePath = PhoneLibPlugin.ringtonePath
    }

    override func tearDown() {
        for key in persistedKeys {
            UserDefaults.standard.set(savedDefaults[key], forKey: key)
        }
        PhoneLibPlugin.instance = savedInstance
        PhoneLibPlugin.appDelegate = savedAppDelegate
        PhoneLibPlugin.registerPlugins = savedRegisterPlugins
        PhoneLibPlugin.nativeMiddleware = savedNativeMiddleware
        PhoneLibPlugin.onCallEnded = savedOnCallEnded
        PhoneLibPlugin.onLogReceived = savedOnLogReceived
        PhoneLibPlugin.ringtonePath = savedRingtonePath
        super.tearDown()
    }

    private func clearPluginState() {
        PhoneLibPlugin.appDelegate = nil
        PhoneLibPlugin.registerPlugins = nil
        PhoneLibPlugin.nativeMiddleware = nil
        PhoneLibPlugin.onCallEnded = nil
        PhoneLibPlugin.onLogReceived = nil
        PhoneLibPlugin.ringtonePath = nil
    }

    func testTheFirstPluginStaysTheInstance() {
        PhoneLibPlugin.instance = nil

        let first = PhoneLibPlugin(RecordingMessenger().channel())
        _ = PhoneLibPlugin(RecordingMessenger().channel())

        XCTAssertTrue(PhoneLibPlugin.instance === first)
    }

    func testLogsReachTheAppsCallback() {
        var received: [(String, LogLevel)] = []
        PhoneLibPlugin.onLogReceived = { received.append(($0, $1)) }

        PhoneLibPlugin.logDelegate.onLogReceived(message: "hello", level: .warning)

        XCTAssertEqual(received.map(\.0), ["hello"])
        XCTAssertEqual(received.map(\.1), [.warning])
    }

    func testLogsAreDroppedWithoutACallback() {
        PhoneLibPlugin.onLogReceived = nil

        PhoneLibPlugin.logDelegate.onLogReceived(message: "hello", level: .info)
    }

    func testStartPhoneLibWaitsForPersistedSettings() {
        let pil = PIL.shared
        PIL.shared = nil
        defer { PIL.shared = pil }
        clearPluginState()
        persistedKeys.forEach(UserDefaults.standard.removeObject(forKey:))
        // Everything but the user agent, as with all three the PIL would start.
        UserDefaults.standard.set(Preferences().serialize(), forKey: PhoneLibPlugin.Keys.PREFERENCES)
        UserDefaults.standard.set(Fixtures.auth.serialize(), forKey: PhoneLibPlugin.Keys.AUTH)

        TestAppDelegate().startPhoneLib(nativeMiddleware: RecordingNativeMiddleware())

        XCTAssertFalse(PIL.isInitialized)
    }

    func testStartPhoneLibKeepsWhatTheAppConfiguredFirst() {
        let pil = PIL.shared
        PIL.shared = nil
        defer { PIL.shared = pil }
        clearPluginState()
        persistedKeys.forEach(UserDefaults.standard.removeObject(forKey:))
        let firstDelegate = TestAppDelegate()
        let firstMiddleware = RecordingNativeMiddleware()
        var firstCallbacks: [String] = []

        firstDelegate.startPhoneLib(
            { _ in firstCallbacks.append("registerPlugins") },
            nativeMiddleware: firstMiddleware,
            onCallEnded: { _ in firstCallbacks.append("onCallEnded") },
            onLogReceived: { _, _ in firstCallbacks.append("onLogReceived") },
            ringtonePath: "first.caf"
        )
        TestAppDelegate().startPhoneLib(
            { _ in XCTFail("Replaced registerPlugins") },
            nativeMiddleware: RecordingNativeMiddleware(),
            onCallEnded: { _ in XCTFail("Replaced onCallEnded") },
            onLogReceived: { _, _ in XCTFail("Replaced onLogReceived") },
            ringtonePath: "second.caf"
        )

        XCTAssertTrue(PhoneLibPlugin.appDelegate === firstDelegate)
        XCTAssertTrue(PhoneLibPlugin.nativeMiddleware as AnyObject === firstMiddleware)
        XCTAssertEqual(PhoneLibPlugin.ringtonePath, "first.caf")
        PhoneLibPlugin.onCallEnded?(NativeCall(mos: "", reason: "", duration: "", direction: ""))
        PhoneLibPlugin.onLogReceived?("", .info)
        XCTAssertEqual(firstCallbacks, ["onCallEnded", "onLogReceived"])
    }

    func testMissedCallNotificationPressesCanBeForwarded() {
        let center = UNUserNotificationCenter.current()
        let previous = center.delegate
        defer { center.delegate = previous }

        TestAppDelegate().addOnMissedCallNotificationPressedDelegate()

        XCTAssertTrue(center.delegate is OnMissedCallNotificationPressedDelegate)
    }

    // Bug: the completion handler is never called, which iOS requires.
    func testMissedCallNotificationPressesAreForwardedToDart() {
        PhoneLibPlugin.instance = nil
        let messenger = RecordingMessenger()
        _ = PhoneLibPlugin(messenger.channel())
        var completed = false

        OnMissedCallNotificationPressedDelegate().userNotificationCenter(
            UNUserNotificationCenter.current(),
            didReceive: FakeNotificationResponse(identifier: NotificationRequestIdentifiers.missedCalls.rawValue),
            withCompletionHandler: { completed = true }
        )

        XCTAssertEqual(messenger.sent.map(\.method), ["onMissedCallNotificationPressed"])
        XCTAssertNil(messenger.sent.first?.arguments)
        XCTAssertFalse(completed)
    }

    func testOtherNotificationPressesAreIgnored() {
        PhoneLibPlugin.instance = nil
        let messenger = RecordingMessenger()
        _ = PhoneLibPlugin(messenger.channel())

        OnMissedCallNotificationPressedDelegate().userNotificationCenter(
            UNUserNotificationCenter.current(),
            didReceive: FakeNotificationResponse(identifier: "something-else"),
            withCompletionHandler: {}
        )

        XCTAssertTrue(messenger.sent.isEmpty)
    }

    func testInboundCallsAreSummarisedAsInbound() {
        var ended: [NativeCall] = []
        PhoneLibPlugin.onCallEnded = { ended.append($0) }
        let state = CallSessionState(
            activeCall: Fixtures.inactiveCall,
            inactiveCall: nil,
            audioState: Fixtures.idleSessionState.audioState
        )

        CallListener().onEvent(event: .callEnded(state: state))

        XCTAssertEqual(ended.first?.direction, "inbound")
        XCTAssertEqual(ended.first?.duration, "3725")
        XCTAssertEqual(ended.first?.mos, "0.0")
    }

    // Bug: Android passes the call's reason on, iOS always sends "".
    func testOnCallEndedDropsTheReason() {
        var ended: [NativeCall] = []
        PhoneLibPlugin.onCallEnded = { ended.append($0) }
        let state = CallSessionState(
            activeCall: Fixtures.inactiveCall,
            inactiveCall: nil,
            audioState: Fixtures.idleSessionState.audioState
        )
        XCTAssertEqual(Fixtures.inactiveCall.reason, "Busy Here")

        CallListener().onEvent(event: .callEnded(state: state))

        XCTAssertEqual(ended.first?.reason, "")
    }
}

private class TestAppDelegate: NSObject, UIApplicationDelegate {}
