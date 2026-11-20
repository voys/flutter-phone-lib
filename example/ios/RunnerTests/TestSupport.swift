import Flutter
import Foundation
import PushKit
import UserNotifications
import XCTest
@testable import flutter_phone_lib
@testable import iOSPhoneLib

/// The JSON contract in `test/contract`, shared with the Dart and Android tests.
enum Contract {
    private static let dir = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // RunnerTests
        .deletingLastPathComponent() // ios
        .deletingLastPathComponent() // example
        .deletingLastPathComponent() // repository root
        .appendingPathComponent("test/contract")

    static var iosToDart: [String: Any] { read("ios_to_dart.json") as! [String: Any] }

    static var dartToNative: [[String: Any]] { read("dart_to_native.json") as! [[String: Any]] }

    static func dartCall(_ method: String) -> [String: Any] {
        dartToNative.first { $0["method"] as? String == method }!
    }

    private static func read(_ name: String) -> Any {
        let data = try! Data(contentsOf: dir.appendingPathComponent(name))
        return try! JSONSerialization.jsonObject(with: data)
    }
}

/// The native session state that `ios_to_dart.json` describes.
enum Fixtures {
    static let auth = Auth(username: "user", password: "pass", domain: "sip.example.com", port: 5061, secure: true)

    static let activeCall = Call(
        remoteNumber: "+31501234567",
        displayName: "Alice",
        state: .connected,
        direction: .outbound,
        duration: 75,
        isOnHold: false,
        uuid: UUID(uuidString: "c0ffee00-0000-4000-8000-000000000001")!,
        mos: 4.5,
        currentMos: 4.25,
        contact: Contact("Alice Example"),
        reason: ""
    )

    static let inactiveCall = Call(
        remoteNumber: "+31507654321",
        displayName: "",
        state: .heldByLocal,
        direction: .inbound,
        duration: 3725,
        isOnHold: true,
        uuid: UUID(uuidString: "c0ffee00-0000-4000-8000-000000000002")!,
        mos: 0,
        currentMos: 0,
        contact: nil,
        reason: "Busy Here"
    )

    static var sessionState: CallSessionState {
        CallSessionState(
            activeCall: activeCall,
            inactiveCall: inactiveCall,
            audioState: AudioState(
                currentRoute: .bluetooth,
                availableRoutes: [.speaker, .phone, .bluetooth],
                bluetoothDeviceName: "Headset",
                isMicrophoneMuted: true
            )
        )
    }

    static var idleSessionState: CallSessionState {
        CallSessionState(
            activeCall: nil,
            inactiveCall: nil,
            audioState: AudioState(
                currentRoute: .phone,
                availableRoutes: [.speaker, .phone],
                bluetoothDeviceName: nil,
                isMicrophoneMuted: false
            )
        )
    }
}

/// What the other side receives after `value` crosses the method channel.
func overTheWire(_ value: Any?) -> Any? {
    let codec = FlutterStandardMessageCodec.sharedInstance()
    return codec.decode(codec.encode(value))
}

/// Compares like Foundation does, so 4.5 as a Float equals 4.5 from JSON.
func XCTAssertSameValue(
    _ actual: Any?,
    _ expected: Any?,
    _ message: String = "",
    file: StaticString = #filePath,
    line: UInt = #line
) {
    let actualObject = (actual ?? NSNull()) as AnyObject
    let expectedObject = (expected ?? NSNull()) as AnyObject
    XCTAssertTrue(
        actualObject.isEqual(expectedObject),
        "\(message)\nactual:   \(actualObject)\nexpected: \(expectedObject)",
        file: file,
        line: line
    )
}

/// Records what native sends to Dart.
class RecordingMessenger: NSObject, FlutterBinaryMessenger {
    private(set) var sent: [FlutterMethodCall] = []

    func channel() -> FlutterMethodChannel {
        FlutterMethodChannel(name: "org.openvoipalliance.flutterphonelib/foreground", binaryMessenger: self)
    }

    func send(onChannel channel: String, message: Data?) {
        send(onChannel: channel, message: message, binaryReply: nil)
    }

    func send(onChannel channel: String, message: Data?, binaryReply callback: FlutterBinaryReply? = nil) {
        sent.append(FlutterStandardMethodCodec.sharedInstance().decodeMethodCall(message!))
    }

    func setMessageHandlerOnChannel(
        _ channel: String,
        binaryMessageHandler handler: FlutterBinaryMessageHandler? = nil
    ) -> FlutterBinaryMessengerConnection {
        0
    }

    func cleanUpConnection(_ connection: FlutterBinaryMessengerConnection) {}
}

/// `PKPushPayload` can't be created with content, but its payload can be overridden.
final class FakePushPayload: PKPushPayload {
    private let payload: [AnyHashable: Any]

    init(_ payload: [AnyHashable: Any] = [:]) {
        self.payload = payload
        super.init()
    }

    override var dictionaryPayload: [AnyHashable: Any] { payload }
}

/// An empty archive, as the notification classes can only be created by decoding.
private func emptyCoder() -> NSCoder {
    let archiver = NSKeyedArchiver(requiringSecureCoding: false)
    archiver.finishEncoding()
    let coder = try! NSKeyedUnarchiver(forReadingFrom: archiver.encodedData)
    coder.requiresSecureCoding = false
    return coder
}

final class FakeNotification: UNNotification {
    private let fakeRequest: UNNotificationRequest

    init(identifier: String, userInfo: [AnyHashable: Any] = [:]) {
        let content = UNMutableNotificationContent()
        content.userInfo = userInfo
        fakeRequest = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        super.init(coder: emptyCoder())!
    }

    required init?(coder: NSCoder) {
        fatalError("Not decodable")
    }

    override var request: UNNotificationRequest { fakeRequest }
}

final class FakeNotificationResponse: UNNotificationResponse {
    private let fakeNotification: UNNotification

    init(identifier: String) {
        fakeNotification = FakeNotification(identifier: identifier)
        super.init(coder: emptyCoder())!
    }

    required init?(coder: NSCoder) {
        fatalError("Not decodable")
    }

    override var notification: UNNotification { fakeNotification }
}

/// Records what the bridge hands to the app's native middleware.
final class RecordingNativeMiddleware: NativeMiddleware {
    struct Response {
        let payload: PKPushPayload
        let available: Bool
        let reason: NativeMiddlewareUnavailableReason?
    }

    var responses: [Response] = []
    var tokens: [String] = []
    var inspected: [(payload: PKPushPayload, type: PKPushType)] = []

    func respond(payload: PKPushPayload, available: Bool, reason: NativeMiddlewareUnavailableReason?) {
        responses.append(Response(payload: payload, available: available, reason: reason))
    }

    func tokenReceived(token: String) {
        tokens.append(token)
    }

    func inspect(payload: PKPushPayload, type: PKPushType) {
        inspected.append((payload, type))
    }
}
