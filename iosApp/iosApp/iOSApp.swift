import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        AppKoinKt.doInitKoin(config: nil)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    DeepLink_iosKt.handleDeepLink(uri: url.absoluteString)
                }
        }
    }
}
