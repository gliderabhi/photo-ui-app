import ComposeApp
import GoogleSignIn
import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Wires the shared Compose "Sign in with Google" button (GoogleSignIn.kt) to
        // Google's native SDK instead of only the plain browser-sheet fallback — see
        // GoogleSignInBridge.swift/GoogleNativeSignIn.kt for why this has to be set from
        // Swift rather than called from Kotlin directly. Left unset (nil) if the client ID
        // is missing, same "fail into the fallback, not a crash" posture as AppConfig.kt's
        // GOOGLE_IOS_CLIENT_ID check elsewhere.
        if !AppConfigKt.GOOGLE_IOS_CLIENT_ID.isEmpty {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: AppConfigKt.GOOGLE_IOS_CLIENT_ID)
            GoogleNativeSignInKt.googleNativeSignIn = GoogleSignInBridge()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all) // Compose draws its own status/safe-area insets
                .onOpenURL { url in
                    // Completes GIDSignIn's callback when it hands off to the Google/Gmail
                    // app or a browser sheet and control returns via this app's URL scheme
                    // (see project.yml's CFBundleURLTypes).
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
