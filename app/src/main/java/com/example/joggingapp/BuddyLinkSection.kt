package com.example.joggingapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.joggingapp.ui.theme.JogginTheme

/**
 * The Buddy Link options section (Phase 3-4). Rendered LAST among the regular
 * collapsible sections in the options pane, above the secret debug console.
 *
 * Owns the G3 first-enable flow: consent dialog -> BuddyIdentity.ensureId (inside
 * controller.enableConfirmed) -> background-location permission -> activate, or
 * revert if the user declines / denies permission. The master toggle is the sole
 * on/off control (G4 — no separate "pause").
 *
 * All data comes from [BuddyLinkController], which reads NoOpBuddyRepository until
 * Firebase is configured; until then backendAvailable is false and the section shows
 * a non-blocking "temporarily unavailable" note (Req 10).
 */
@Composable
fun BuddyLinkSection(controller: BuddyLinkController, userName: String) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current

    // Drives the consent dialog (step 1 of the G3 enable flow).
    var showConsent by remember { mutableStateOf(false) }
    // Holds a link pending an "unlink" confirmation dialog.
    var confirmRemove by remember { mutableStateOf<BuddyLink?>(null) }
    // Holds the buddy appId pending a "block" confirmation dialog.
    var confirmBlock by remember { mutableStateOf<String?>(null) }
    // Drives the "delete all my data" confirmation dialog.
    var confirmDelete by remember { mutableStateOf(false) }

    // Live background-location permission state (Req 6c). Re-checked on ON_RESUME so a
    // grant/revoke made in system settings is reflected when the user returns.
    fun bgLocationGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    var bgGranted by remember { mutableStateOf(bgLocationGranted()) }

    // Ticks periodically so the on-demand request cooldown (below) re-evaluates and the
    // button re-enables once the cooldown elapses, without needing a backend event.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(5000L); nowTick = System.currentTimeMillis() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) bgGranted = bgLocationGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Background-location permission request. On result we either finish enabling
    // (grant) or revert (deny) so the toggle never sticks ON without permission (G3).
    val bgPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            controller.enableConfirmed(userName)
        } else {
            AppLogger.log(context, LogCategory.PROFILE, "Buddy Link enable reverted — background location denied")
        }
    }

    // Step 2 of enable: provision identity happens in enableConfirmed; here we gate on
    // the background-location permission first (required for broadcast while walking).
    fun proceedAfterConsent() {
        showConsent = false
        val needsBg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsBg) {
            bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            controller.enableConfirmed(userName)
        }
    }

    CollapsibleSection(title = S.buddyLink, leadingEmoji = "🛰️") {
        // ── Master toggle (G4: sole on/off) ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(c.primary.copy(alpha = 0.15f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(S.buddyLinkEnable, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.Medium)
            val on = controller.enabled
            Box(
                modifier = Modifier.size(40.dp, 24.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.success else c.divider)
                    .clickable {
                        if (on) controller.disable() else showConsent = true
                    }
                    .semantics { contentDescription = S.buddyToggleDesc(on) },
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(modifier = Modifier.size(20.dp).padding(2.dp).clip(CircleShape).background(Color.White))
            }
        }

        if (controller.enabled) {
            // Background-location permission note (Req 6c). Shown when enabled but the
            // permission was denied or later revoked — broadcasting can't run without it.
            if (!bgGranted) {
                Spacer(Modifier.height(10.dp))
                Text(
                    S.buddyPermissionNeeded,
                    fontSize = 11.sp,
                    color = c.error.copy(alpha = 0.9f),
                    modifier = Modifier.semantics { contentDescription = S.buddyPermissionNeeded }
                )
            }

            // Backend status note (non-blocking, Req 10).
            if (!controller.backendAvailable) {
                Spacer(Modifier.height(10.dp))
                Text(S.buddyUnavailableBackend, fontSize = 11.sp, color = c.error.copy(alpha = 0.9f))
            }

            // ── Invite button ────────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(c.primary.copy(alpha = 0.15f))
                    .clickable {
                        val text = controller.buildInviteShareText(userName)
                        if (text != null) {
                            AppLogger.log(context, LogCategory.UI, "Buddy Link invite shared")
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, S.buddyInviteShare))
                        }
                    }
                    .padding(14.dp)
                    .semantics { contentDescription = S.buddyInvite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🔗", fontSize = 20.sp)
                Text(S.buddyInvite, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
            }

            // ── Pending requests ─────────────────────────────────────────────
            if (controller.pending.isNotEmpty()) {
                BuddyGroupHeader(S.buddyPending)
                controller.pending.forEach { link ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val reqName = shortId(link.a)
                        Text(reqName, fontSize = 12.sp, color = c.onSecondary, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(S.buddyAccept, fontSize = 12.sp, color = c.success,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { controller.accept(link.linkId) }
                                    .semantics { contentDescription = S.buddyActionDesc(S.buddyAccept, reqName) })
                            Text(S.buddyDecline, fontSize = 12.sp, color = c.error,
                                modifier = Modifier
                                    .clickable { controller.decline(link.linkId) }
                                    .semantics { contentDescription = S.buddyActionDesc(S.buddyDecline, reqName) })
                        }
                    }
                    Divider(color = c.onSecondary.copy(alpha = 0.12f))
                }
            }

            // ── Buddies ──────────────────────────────────────────────────────
            BuddyGroupHeader(S.buddyBuddies)
            if (controller.buddies.isEmpty()) {
                Text("—", fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.5f))
            } else {
                controller.buddies.forEach { view ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val name = view.user.displayName.ifBlank { shortId(view.user.appId) }
                            Text(name, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.Medium)
                            val status = when {
                                view.isLiveNow() -> S.buddyStatusSharing
                                view.link?.state == BuddyLinkState.PENDING -> S.buddyStatusPending
                                else -> S.buddyStatusUnavailable
                            }
                            Text(status, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.6f))
                        }
                        // Primary actions: Remove (unlink) + Block, both confirmed.
                        val buddyName = view.user.displayName.ifBlank { shortId(view.user.appId) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            view.link?.let { link ->
                                Text(S.buddyRemove, fontSize = 12.sp, color = c.error,
                                    modifier = Modifier
                                        .clickable { confirmRemove = link }
                                        .semantics { contentDescription = S.buddyActionDesc(S.buddyRemove, buddyName) })
                            }
                            Text(S.buddyBlock, fontSize = 12.sp, color = c.error, fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { confirmBlock = view.user.appId }
                                    .semantics { contentDescription = S.buddyActionDesc(S.buddyBlock, buddyName) })
                        }
                    }
                    // Secondary actions: request-now + history, only useful when live.
                    if (view.isLiveNow()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val canRequest = controller.canRequestLocation(view.user.appId, nowTick)
                            Text(
                                if (canRequest) "📍 ${S.buddyRequestLocation}" else "⏳ ${S.buddyRequestCooldown}",
                                fontSize = 11.sp,
                                color = if (canRequest) c.primary else c.onSecondary.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .then(
                                        if (canRequest) Modifier.clickable {
                                            AppLogger.log(context, LogCategory.UI, "Buddy Link: requested buddy location")
                                            controller.requestBuddyLocation(view.user.appId)
                                        } else Modifier
                                    )
                                    .semantics { contentDescription = S.buddyRequestLocation }
                            )
                            Text(
                                if (controller.showHistory) "🧭 ${S.buddyHistoryHide}" else "🧭 ${S.buddyHistoryShow}",
                                fontSize = 11.sp, color = c.primary,
                                modifier = Modifier.clickable { controller.toggleHistory() }
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                    Divider(color = c.onSecondary.copy(alpha = 0.12f))
                }
            }

            // ── Blocked ──────────────────────────────────────────────────────
            if (controller.blocked.isNotEmpty()) {
                BuddyGroupHeader(S.buddyBlocked)
                controller.blocked.forEach { appId ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val blockedName = shortId(appId)
                        Text(blockedName, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f))
                        Text(S.buddyUnblock, fontSize = 12.sp, color = c.primary,
                            modifier = Modifier
                                .clickable { controller.unblock(appId) }
                                .semantics { contentDescription = S.buddyActionDesc(S.buddyUnblock, blockedName) })
                    }
                    Divider(color = c.onSecondary.copy(alpha = 0.12f))
                }
            }

            // ── Danger zone: delete all Buddy Link data (Req 8.4) ──────────────
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(c.error.copy(alpha = 0.12f))
                    .clickable { confirmDelete = true }
                    .padding(14.dp)
                    .semantics { contentDescription = S.buddyDeleteData },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🗑", fontSize = 18.sp)
                Text(S.buddyDeleteData, fontSize = 13.sp, color = c.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ── Consent dialog (step 1 of enable, G3) ────────────────────────────────
    if (showConsent) {
        BuddyDialog(
            title = S.buddyLinkConsentTitle,
            body = S.buddyLinkConsentBody,
            confirmLabel = S.buddyLinkConsentAgree,
            dismissLabel = S.buddyLinkConsentDecline,
            onConfirm = { proceedAfterConsent() },
            onDismiss = { showConsent = false }
        )
    }

    // ── Unlink confirmation ──────────────────────────────────────────────────
    confirmRemove?.let { link ->
        BuddyDialog(
            title = S.buddyRemove,
            body = S.buddyRemoveConfirm,
            confirmLabel = S.buddyRemove,
            dismissLabel = S.buddyDecline,
            onConfirm = { controller.remove(link.linkId); confirmRemove = null },
            onDismiss = { confirmRemove = null }
        )
    }

    // ── Block confirmation ─────────────────────────────────────────────────────
    confirmBlock?.let { appId ->
        BuddyDialog(
            title = S.buddyBlock,
            body = S.buddyBlockConfirm,
            confirmLabel = S.buddyBlock,
            dismissLabel = S.buddyDecline,
            onConfirm = {
                AppLogger.log(context, LogCategory.PROFILE, "Buddy Link: blocked a buddy")
                controller.block(appId); confirmBlock = null
            },
            onDismiss = { confirmBlock = null }
        )
    }

    // ── Delete-all confirmation (Req 8.4) ──────────────────────────────────────
    if (confirmDelete) {
        BuddyDialog(
            title = S.buddyDeleteData,
            body = S.buddyDeleteConfirm,
            confirmLabel = S.buddyDeleteData,
            dismissLabel = S.buddyDecline,
            onConfirm = {
                AppLogger.log(context, LogCategory.PROFILE, "Buddy Link: delete-all-data invoked")
                controller.deleteAllData(); confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** A small section sub-header used inside the Buddy Link section. */
@Composable
private fun BuddyGroupHeader(title: String) {
    val c = JogginTheme.colors
    Spacer(Modifier.height(14.dp))
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondary.copy(alpha = 0.8f))
    Spacer(Modifier.height(4.dp))
}

/** Compact display form of an App ID (UUIDs are long) for lists without a name. */
private fun shortId(appId: String): String =
    if (appId.length <= 10) appId else appId.take(6) + "…" + appId.takeLast(4)

/**
 * A lightweight themed confirm/cancel dialog. Uses a plain overlay + card rather than
 * Material AlertDialog so it matches the app's custom theme colours.
 */
@Composable
private fun BuddyDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = JogginTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(16.dp))
                .background(c.secondary).padding(20.dp)
                // Absorb clicks so tapping the card doesn't dismiss.
                .clickable(enabled = false) {}
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onSecondary)
            Spacer(Modifier.height(12.dp))
            Text(body, fontSize = 13.sp, color = c.onSecondary.copy(alpha = 0.85f), lineHeight = 18.sp)
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(c.divider.copy(alpha = 0.3f))
                        .clickable(onClick = onDismiss).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(dismissLabel, fontSize = 13.sp, color = c.onSecondary) }
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(c.primary)
                        .clickable(onClick = onConfirm).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(confirmLabel, fontSize = 13.sp, color = c.onPrimary, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
