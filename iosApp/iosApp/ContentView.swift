import ComposeApp
import SwiftUI

// The entire UI (all screens, navigation, state) lives in shared Kotlin — this is just
// the thinnest possible SwiftUI shell hosting the Compose Multiplatform view controller.
// See MainViewController.kt for everything platform-specific this wires up.
struct ContentView: View {
    var body: some View {
        ComposeViewControllerRepresentable()
    }
}

struct ComposeViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
