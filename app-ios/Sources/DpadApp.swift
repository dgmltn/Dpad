import SwiftUI
import DpadShared

@main
struct DpadApp: App {
    init() {
        // Starts Koin with the iOS platform singletons + :data's bindings + the ViewModel
        // bindings, exactly once, before any Compose content is created. See
        // app-ios-shared/src/iosMain/kotlin/com/dgmltn/dpad/iosshared/DpadShared.kt.
        DpadSharedKt.startDpadKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
