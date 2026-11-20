import Flutter
import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

/// Asserts that the bridge produces exactly `ios_to_dart.json`, which the Dart tests decode.
final class NativeToDartContractTests: XCTestCase {
    private let contract = Contract.iosToDart

    private func callSessionEvents(_ state: CallSessionState) -> [Event] {
        [
            .outgoingCallStarted(state: state),
            .incomingCallReceived(state: state),
            .callEnded(state: state),
            .callConnected(state: state),
            .callDurationUpdated(state: state),
            .audioStateUpdated(state: state),
            .callStateUpdated(state: state),
            .attendedTransferStarted(state: state),
            .attendedTransferAborted(state: state),
            .attendedTransferConnected(state: state),
            .attendedTransferEnded(state: state),
        ]
    }

    private let reasons: [CallSetupFailedReason] = [.unknown, .inCall, .unableToRegister, .rejectedByCallKit]

    func testSessionStateMatchesTheContract() {
        XCTAssertSameValue(overTheWire(Fixtures.sessionState.toDictionary()), contract["sessionState"])
    }

    func testIdleSessionStateMatchesTheContract() {
        XCTAssertSameValue(overTheWire(Fixtures.idleSessionState.toDictionary()), contract["idleSessionState"])
    }

    func testCallSessionEventsMatchTheContract() {
        let state = Fixtures.sessionState
        var types: [String] = []

        for event in callSessionEvents(state) {
            let dictionary = event.toDictionary(state: state)
            let type = dictionary["type"] as! String
            types.append(type)

            XCTAssertSameValue(
                overTheWire(dictionary),
                ["type": type, "state": contract["sessionState"]!],
                type
            )
        }

        XCTAssertEqual(types, contract["callSessionEventTypes"] as! [String])
    }

    func testCallSetupFailedEventsMatchTheContract() {
        let events: [(CallSetupFailedReason) -> Event] = [
            { .outgoingCallSetupFailed(reason: $0) },
            { .incomingCallSetupFailed(reason: $0) },
        ]
        var types: [String] = []

        for makeEvent in events {
            for reason in reasons {
                let dictionary = makeEvent(reason).toDictionary(reason: reason)
                let type = dictionary["type"] as! String
                if !types.contains(type) { types.append(type) }

                XCTAssertSameValue(overTheWire(dictionary), ["type": type, "reason": reason.asReasonString()])
            }
        }

        XCTAssertEqual(types, contract["callSetupFailedEventTypes"] as! [String])
        XCTAssertEqual(reasons.map { $0.asReasonString() }, contract["callSetupFailedReasons"] as! [String])
    }

    func testProxyEventListenerSendsEventsToDartAsOnEvent() {
        let messenger = RecordingMessenger()
        let plugin = PhoneLibPlugin(messenger.channel())

        ProxyEventListener(plugin).onEvent(event: .callConnected(state: Fixtures.sessionState))

        XCTAssertEqual(messenger.sent.count, 1)
        XCTAssertEqual(messenger.sent.first?.method, "onEvent")
        XCTAssertSameValue(
            messenger.sent.first?.arguments,
            ["type": "CallConnected", "state": contract["sessionState"]!]
        )
    }

    func testProxyEventListenerSendsSetupFailuresWithTheirReason() {
        let messenger = RecordingMessenger()
        let plugin = PhoneLibPlugin(messenger.channel())

        ProxyEventListener(plugin).onEvent(event: .incomingCallSetupFailed(reason: .rejectedByCallKit))

        XCTAssertSameValue(
            messenger.sent.first?.arguments,
            ["type": "IncomingCallSetupFailed", "reason": "REJECTED_BY_CALL_KIT"]
        )
    }
}
