package com.sevis.photos

/**
 * Implemented in Swift (see iosApp/GoogleSignInBridge.swift) using Google's own
 * GoogleSignIn-iOS SDK, which can hand off to the Google/Gmail app for sign-in instead of
 * always showing a browser sheet — the native experience Android's Credential Manager
 * flow already has (MobileGoogleLoginContent.kt).
 *
 * Kotlin can't call GoogleSignIn-iOS directly: it's a third-party Swift Package Manager
 * dependency that only exists inside the Xcode app target, which Xcode compiles *after*
 * `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` has already produced
 * ComposeApp.framework (see project.yml's preBuildScripts) — there's no build-graph point
 * at which Kotlin/Native's cinterop could bind against it. This interface is the supported
 * way around that: it's Swift that imports GoogleSignIn (ordinary SPM usage) and calls back
 * into Kotlin through this framework's exported Objective-C surface — the same direction
 * MainViewController() already uses to let Swift host the shared Compose UI.
 */
interface GoogleNativeSignIn {
    /** [onResult] is called with (idToken, null) on success, (null, null) if the user
     *  cancelled, or (null, message) on a real failure — mirroring signInWithGoogle()'s
     *  nullable-return-or-throw shape closely enough that GoogleSignInButton can treat
     *  both the same way. */
    fun signIn(onResult: (idToken: String?, errorMessage: String?) -> Unit)
}

/**
 * Set once, at app launch, by iOSApp.swift. Left null until then — and permanently null if
 * Swift-side wiring is ever missing or GoogleSignIn-iOS fails to initialize — so
 * GoogleSignInButton always has the old ASWebAuthenticationSession/PKCE flow
 * (signInWithGoogle() in GoogleAuth.kt) to fall back to rather than a hard failure.
 */
var googleNativeSignIn: GoogleNativeSignIn? = null
