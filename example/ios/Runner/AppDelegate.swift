import UIKit
import Flutter
import flutter_phone_lib

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    startPhoneLib(
      GeneratedPluginRegistrant.register,
      nativeMiddleware: ExampleMiddleware(),
      onLogReceived: { message, level in
        print("\(level): \(message)")
      }
    )

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
  }
}
