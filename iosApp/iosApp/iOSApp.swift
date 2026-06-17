import SwiftUI
import Shared
import Sentry

@main
struct iOSApp: App {
    init() {
        AppKoinKt.doInitKoin(config: nil)
        initSentry()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    DeepLink_iosKt.handleDeepLink(uri: url.absoluteString)
                }
        }
    }

    private func initSentry() {
        let dsn = Secrets.sentryDSN
        guard !dsn.isEmpty, SentryBridgeKt.sentryEnabled() else { return }

        SentrySDK.start { options in
            options.dsn = dsn
            options.debug = false
            options.enableCrashHandler = true
            options.enableAutoSessionTracking = false
            options.tracesSampleRate = 0
        }
    }
}
