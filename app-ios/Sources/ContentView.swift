import SwiftUI
import UIKit
import DpadShared

/// Hosts the single Kotlin/Compose entry point (`DpadSharedKt.MainViewController()`, which
/// renders `DpadTheme { AppNavHost() }`) inside SwiftUI. The remote UI is full-screen, so safe
/// areas are ignored here — Compose's own theming/insets handling owns the layout.
struct ComposeViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        DpadSharedKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No-op: Compose owns its own recomposition.
    }
}

struct ContentView: View {
    var body: some View {
        ComposeViewControllerRepresentable()
            .ignoresSafeArea(.all)
    }
}
