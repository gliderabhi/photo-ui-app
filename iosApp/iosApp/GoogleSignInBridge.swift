import ComposeApp
import GoogleSignIn
import UIKit

/// Implements the Kotlin-declared GoogleNativeSignIn interface (see GoogleNativeSignIn.kt)
/// using Google's own GoogleSignIn-iOS SDK. Google's current SDK (7.x+) no longer supports
/// switching to the Google/Gmail app for sign-in — Google removed that, and Apple no longer
/// allows third-party app-to-app OAuth hand-off anyway — so under the hood this presents the
/// same kind of system browser sheet our old hand-rolled ASWebAuthenticationSession/PKCE
/// flow (GoogleAuth.kt) did. What this SDK *does* give us: a Keychain-backed session,
/// restored at launch (see iOSApp.swift's restorePreviousSignIn call) — so signIn() below
/// tries to reuse that silently first, and only presents the sheet if there's truly nothing
/// to reuse (first-ever sign-in, or a restored session that's stopped being valid, e.g.
/// revoked from the user's Google account settings). Registered once, at launch, in
/// iOSApp.swift.
final class GoogleSignInBridge: NSObject, GoogleNativeSignIn {
    func signIn(onResult: @escaping (String?, String?) -> Void) {
        if let restoredUser = GIDSignIn.sharedInstance.currentUser {
            restoredUser.refreshTokensIfNeeded { user, error in
                if let idToken = user?.idToken?.tokenString {
                    onResult(idToken, nil)
                } else {
                    // Restored session no longer refreshes (revoked/expired) — fall through
                    // to a real, interactive sign-in below instead of failing outright.
                    self.presentInteractiveSignIn(onResult: onResult)
                }
            }
            return
        }
        presentInteractiveSignIn(onResult: onResult)
    }

    private func presentInteractiveSignIn(onResult: @escaping (String?, String?) -> Void) {
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
