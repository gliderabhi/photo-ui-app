package com.sevis.photos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sevis.photos.data.PhotoApi
import kotlinx.coroutines.delay

/**
 * Device-code ("sign in on your phone") Google login for the TV app: shows a QR
 * code + short code, polls Google until the user finishes on their phone, then
 * exchanges the resulting Google ID token for our own long-lived session JWT.
 */
@Composable
fun TvGoogleLoginContent(api: PhotoApi, onLoginSuccess: (String) -> Unit) {
    var userCode by remember { mutableStateOf<String?>(null) }
    var verificationUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val device = GoogleDeviceAuth.requestDeviceCode()
            userCode = device.user_code
            verificationUrl = device.verification_url
            qrBitmap = generateQrBitmap(device.verification_url, 260)

            val deadline = System.currentTimeMillis() + device.expires_in * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(device.interval * 1000L)
                val idToken = GoogleDeviceAuth.pollOnce(device.device_code)
                if (idToken != null) {
                    val auth = api.googleLogin(idToken, longLived = true)
                    onLoginSuccess(auth.token)
                    return@LaunchedEffect
                }
            }
            error = "Code expired — please try again"
        } catch (e: Exception) {
            error = e.message ?: "Google sign-in failed"
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Or sign in with Google", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))

        when {
            error != null -> Text(error!!, fontSize = 13.sp, color = Color(0xFFDC2626))
            qrBitmap == null -> CircularProgressIndicator()
            else -> {
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = "Scan to sign in",
                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                )
                Text(
                    "Scan with your phone, or visit ${verificationUrl}",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    userCode ?: "",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

private fun generateQrBitmap(text: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
