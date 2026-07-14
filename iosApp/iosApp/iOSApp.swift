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
        // Read from Info.plist (SentryDSN = $(SENTRY_DSN)), which Config.xcconfig fills from the
        // gitignored Secrets.xcconfig. That path was already wired; a second copy of the DSN in a
        // gitignored Secrets.swift only meant a fresh clone could not build the iOS app at all —
        // it failed with "Build input file cannot be found", and CI had to generate a stub.
        let dsn = Bundle.main.object(forInfoDictionaryKey: "SentryDSN") as? String ?? ""
        guard !dsn.isEmpty, SentryBridgeKt.sentryEnabled() else { return }

        // A DSN pasted into Secrets.xcconfig without escaping its slashes arrives here
        // truncated to "https:" — xcconfig treats // as a comment. Fail loudly: silently
        // starting Sentry with a broken DSN means crash reports vanish and nobody notices.
        guard dsn.hasPrefix("https://") else {
            assertionFailure("SENTRY_DSN is malformed (\(dsn)). Escape the slashes in Secrets.xcconfig: https:/$()/…")
            return
        }

        SentrySDK.start { options in
            options.dsn = dsn
            options.debug = false
            options.enableCrashHandler = true
            options.enableAutoSessionTracking = false
            options.tracesSampleRate = 0
        }
    }
}
