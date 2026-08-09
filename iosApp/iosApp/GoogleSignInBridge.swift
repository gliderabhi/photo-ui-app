import ComposeApp
import GoogleSignIn
import UIKit

/// Implements the Kotlin-declared GoogleNativeSignIn interface (see GoogleNativeSignIn.kt)
/// using Google's own GoogleSignIn-iOS SDK — this is the Swift side of the bridge that
/// lets the shared Compose "Sign in with Google" button hand off to the Google/Gmail app
/// instead of always falling back to the plain ASWebAuthenticationSession sheet
/// (GoogleAuth.kt). Registered once, at launch, in iOSApp.swift.
final class GoogleSignInBridge: NSObject, GoogleNativeSignIn {
    func signIn(onResult: @escaping (String?, String?) -> Void) {
        guard let rootViewController = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
            .first?.rootViewController
        else {
            onResult(nil, "Google sign-in: no window to present from")
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { result, error in
            if let error = error as NSError?,
               error.domain == kGIDSignInErrorDomain,
               error.code == GIDSignInError.canceled.rawValue {
                onResult(nil, nil) // user cancelled — not a real error, see GoogleSignIn.kt
                return
            }
            if let error = error {
                onResult(nil, error.localizedDescription)
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                onResult(nil, "Google sign-in didn't return an ID token")
                return
            }
            onResult(idToken, nil)
        }
    }
}
