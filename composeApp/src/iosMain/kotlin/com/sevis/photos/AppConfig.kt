package com.sevis.photos

// Mirrors BuildConfig.API_BASE_URL (see composeApp/build.gradle.kts) — Android
// sources this from a Gradle buildConfigField; iOS has no BuildConfig equivalent,
// so it's just a plain constant here. Same gateway, same value on both platforms.
const val API_BASE_URL = "https://photos.sevis.store"

/**
 * Google "iOS" OAuth client ID (Google Cloud Console → APIs & Services →
 * Credentials → Create Credentials → OAuth client ID → iOS, bundle ID
 * com.sevis.photos). Distinct from Android's Web/TV client IDs in
 * local.properties — Google requires an iOS-type client (no client secret,
 * PKCE + custom URL scheme redirect) for native iOS sign-in. Wire the real
 * value in before shipping; sign-in fails fast with a clear error until then.
 */
const val GOOGLE_IOS_CLIENT_ID = "1059813087193-afms6rj5b3vvjm8njbm8scd2efjg778d.apps.googleusercontent.com"

/** The reversed-client-id URL scheme Info.plist registers for the OAuth
 *  redirect (see project.yml) — must match GOOGLE_IOS_CLIENT_ID's reversed form,
 *  e.g. client id "123-abc.apps.googleusercontent.com" → scheme
 *  "com.googleusercontent.apps.123-abc". */
const val GOOGLE_IOS_URL_SCHEME = "com.googleusercontent.apps.1059813087193-afms6rj5b3vvjm8njbm8scd2efjg778d"
