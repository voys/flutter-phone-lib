import LinphoneWrapper
import linphonesw
import XCTest
@testable import iOSPhoneLib

/// Only what works without a linphone core: starting one would need a real SIP setup.
final class VoIPLibTests: XCTestCase {
    func testCallStatesMirrorLinphone() {
        let states: [(LinphoneCall.State, VoipLibCallState)] = [
            (.Idle, .idle),
            (.IncomingReceived, .incomingReceived),
            (.PushIncomingReceived, .incomingReceivedFromPush),
            (.OutgoingInit, .outgoingDidInitialize),
            (.OutgoingProgress, .outgoingProgress),
            (.OutgoingRinging, .outgoingRinging),
            (.OutgoingEarlyMedia, .outgoingEarlyMedia),
            (.Connected, .connected),
            (.StreamsRunning, .streamsRunning),
            (.Pausing, .pausing),
            (.Paused, .paused),
            (.Resuming, .resuming),
            (.Referred, .referred),
            (.Error, .error),
            (.End, .ended),
            (.PausedByRemote, .pausedByRemote),
            (.UpdatedByRemote, .updatedByRemote),
            (.IncomingEarlyMedia, .incomingEarlyMedia),
            (.Updating, .updating),
            (.Released, .released),
            (.EarlyUpdatedByRemote, .earlyUpdatedByRemote),
            (.EarlyUpdating, .earlyUpdating),
        ]

        for (linphoneState, state) in states {
            XCTAssertEqual(VoipLibCallState(rawValue: linphoneState.rawValue), state, "\(linphoneState)")
        }
        // A new linphone state would otherwise silently become `.idle`.
        XCTAssertNil(LinphoneCall.State(rawValue: states.count))
        XCTAssertNil(VoipLibCallState(rawValue: states.count))
    }

    func testAudioRoutesMatchTheirLinphoneDevices() {
        XCTAssertEqual(AudioRoute.speaker.asLinphoneRoutes, [.Speaker])
        XCTAssertEqual(AudioRoute.phone.asLinphoneRoutes, [.Microphone])
        XCTAssertEqual(AudioRoute.bluetooth.asLinphoneRoutes, [.Bluetooth, .BluetoothA2DP])
    }

    func testRegistrationCallbacksAreAllInvokedInOrder() {
        var received: [String] = []
        let callbacks: [RegistrationCallback] = [
            { received.append("first \($0)") },
            { received.append("second \($0)") },
        ]

        callbacks.invoke(state: .registered)

        XCTAssertEqual(received, ["first registered", "second registered"])
    }

    func testRegistrationInvokeSkipsAnythingButCallbacks() {
        var received: [iOSPhoneLib.RegistrationState] = []
        let callback: RegistrationCallback = { received.append($0) }
        let mixed: [Any] = ["not a callback", callback, 42]

        mixed.invoke(state: .failed)

        XCTAssertEqual(received, [.failed])
    }

    func testAFreshVoIPLibIsNotInitializedOrRegistered() {
        let voipLib = VoIPLib()

        XCTAssertFalse(voipLib.isInitialized)
        XCTAssertFalse(voipLib.isRegistered)
        XCTAssertNil(voipLib.config)
    }

    func testTheLinphoneVersionIsReported() {
        XCTAssertNotNil(VoIPLib().version.range(of: #"^\d+\.\d+\.\d+"#, options: .regularExpression))
    }

    func testUnregisteringWithoutACoreCompletesImmediately() {
        var completed = 0

        VoIPLib().unregisterAndWait { completed += 1 }
        LinphoneManager().unregisterAndWait { completed += 1 }

        XCTAssertEqual(completed, 2)
    }
}
