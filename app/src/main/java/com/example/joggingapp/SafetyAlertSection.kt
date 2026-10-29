package com.example.joggingapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.joggingapp.ui.theme.JogginTheme
import kotlinx.coroutines.delay

/**
 * Options → Safety alert body: on/off switch (asks for SEND_SMS when switched on), the
 * chosen contact (system contact picker — no READ_CONTACTS permission needed), and a
 * "Send test text". The detection itself runs in ForegroundLocationService.
 */
@Composable
fun SafetyAlertSection() {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(PREF_SAFETY_ENABLED, false) && SafetyAlerter.hasSmsPermission(context)) }
    var number by remember { mutableStateOf(prefs.getString(PREF_SAFETY_NUMBER, null)) }
    var name by remember { mutableStateOf(prefs.getString(PREF_SAFETY_NAME, null)) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean(PREF_SAFETY_ENABLED, on).apply()
        AppLogger.log(context, LogCategory.UI, "Safety alert ${if (on) "on" else "off"}")
    }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setEnabled(true) else { setEnabled(false); msg = S.safetyPermissionNeeded }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    number = cur.getString(0); name = cur.getString(1)
                    prefs.edit().putString(PREF_SAFETY_NUMBER, number).putString(PREF_SAFETY_NAME, name).apply()
                    AppLogger.log(context, LogCategory.UI, "Safety alert: contact chosen")
                }
            }
        } catch (e: Exception) {
            AppLogger.log(context, LogCategory.ERROR, "Safety alert: contact read failed: ${e.message}")
        }
    }

    Text(S.safetyExplain, fontSize = 11.sp, color = c.onSecondary.copy(alpha = 0.8f))
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(S.safetyTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.onSecondary)
        com.example.joggingapp.ui.theme.AppToggle(
            checked = enabled,
            onToggle = {
                if (enabled) setEnabled(false)
                else if (SafetyAlerter.hasSmsPermission(context)) setEnabled(true)
                else smsPermission.launch(Manifest.permission.SEND_SMS)
            },
            onLabel = S.safetyOn,
            offLabel = S.safetyOff,
            contentDescriptionText = S.safetyTitle,
        )
    }
    Spacer(Modifier.height(10.dp))
    // Contact row: chosen contact (or none) + Choose / Change pill.
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (number.isNullOrBlank()) {
                Text(S.safetyNoContact, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.7f))
            } else {
                Text(name ?: number!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondary, maxLines = 1)
                if (name != null) Text(number!!, fontSize = 11.sp, color = c.onSecondary.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        PanePill(if (number.isNullOrBlank()) "👤 ${S.safetyPickContact}" else S.safetyChangeContact) {
            picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
    }
    if (enabled && !number.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        PanePill("✉️ ${S.safetySendTest}") {
            val userName = prefs.getString("user_name", "") ?: ""
            val ok = SafetyAlerter.sendSms(context, S.safetyTestSmsBody(userName.ifBlank { S.safetySomeone }))
            msg = if (ok) S.safetyTestSent else S.safetyTestFailed
        }
    }
    msg?.let { m ->
        Spacer(Modifier.height(8.dp))
        Text(m, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.85f))
        LaunchedEffect(m) { delay(5000L); msg = null }
    }
}

/** Pane pill: c.primary fill (== pane colour) with a visible outline, white text — matches the Buddy Link pills. */
@Composable
private fun PanePill(label: String, onClick: () -> Unit) {
    val c = JogginTheme.colors
    Box(
        modifier = Modifier.clip(CircleShape).background(c.primary)
            .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) { Text(label, fontSize = 12.sp, color = c.onPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}
