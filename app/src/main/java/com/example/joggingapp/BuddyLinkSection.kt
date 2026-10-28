package com.example.joggingapp

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
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
    // Drives the consent dialog when switching the share mode PING -> ROUTE (req 5a).
    var showRouteConsent by remember { mutableStateOf(false) }

    // Live background-location permission state (Req 6c). Re-checked on ON_RESUME so a
    // grant/revoke made in system settings is reflected when the user returns.
    fun bgLocationGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    var bgGranted by remember { mutableStateOf(bgLocationGranted()) }

    // Foreground (fine/coarse) location state — the first step of the Q+ enable chain.
    fun fgLocationGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // Visible, non-silent feedback when a background-location grant is needed but was
    // denied during an enable attempt (replaces the old silent log-only revert). While
    // true the section shows the "Allow all the time" message + an Open Settings action;
    // the toggle stays honestly OFF.
    var permissionDenied by remember { mutableStateOf(false) }

    // On API 30+ a runtime background-location request is auto-denied, so instead of a
    // dead one-shot request we show a brief rationale and then drive the user to the
    // app's location-settings page (the only place "Allow all the time" can be granted).
    // This flag controls that rationale dialog.
    var showBgRationale by remember { mutableStateOf(false) }

    // Ticks periodically so the on-demand request cooldown (below) re-evaluates and the
    // button re-enables once the cooldown elapses, without needing a backend event.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(5000L); nowTick = System.currentTimeMillis() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bgGranted = bgLocationGranted()
                // Returning from Settings with background now granted: clear the stale
                // "needs settings" message. DECISION (see plan): we do NOT auto-complete
                // enable here — provisioning + starting the foreground service is an
                // explicit, consent-gated (G3) action, so the user re-taps the toggle.
                // Because fg+bg are now held, that re-tap runs enableConfirmed with no
                // further prompts. Do not "fix" this into an auto-enable.
                if (bgGranted) permissionDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Forward declaration so the launchers (defined before it) can drive the sequence.
    // Assigned below once the launchers exist; invoked after consent and after each
    // permission result to advance foreground -> background -> enable.
    var advanceEnable by remember { mutableStateOf<() -> Unit>({}) }

    // Background-location request (step 2 of the Q+ chain). Grant -> finish enabling;
    // deny -> surface the visible "Allow all the time" / Open Settings message (no
    // silent failure) and leave the toggle OFF.
    val bgPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        bgGranted = bgLocationGranted()
        if (granted) {
            permissionDenied = false
            controller.enableConfirmed(userName)
        } else {
            permissionDenied = true
            AppLogger.log(context, LogCategory.PROFILE, "Buddy Link enable: background location denied — routing to settings")
        }
    }

    // Foreground-location request (step 1 of the Q+ chain, mirrors MainActivity). On
    // grant we re-evaluate and continue to background (or enable); on deny we surface
    // the same visible feedback and leave the toggle OFF.
    val fgPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            permissionDenied = false
            advanceEnable()
        } else {
            permissionDenied = true
            AppLogger.log(context, LogCategory.PROFILE, "Buddy Link enable: foreground location denied")
        }
    }

    // Drives the permission sequence using the pure decision logic. Foreground first,
    // then background, then enable — never a one-shot background request that Android
    // 10+ auto-denies and strands the toggle OFF.
    advanceEnable = {
        when (BuddyPermissionFlow.nextStep(Build.VERSION.SDK_INT, fgLocationGranted(), bgLocationGranted())) {
            BuddyPermissionFlow.EnableStep.ENABLE_NOW -> {
                permissionDenied = false
                controller.enableConfirmed(userName)
            }
            BuddyPermissionFlow.EnableStep.REQUEST_FOREGROUND -> fgPermLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            BuddyPermissionFlow.EnableStep.REQUEST_BACKGROUND ->
                when (BuddyPermissionFlow.backgroundRequestMode(Build.VERSION.SDK_INT)) {
                    // API 29: the OS can still show the in-app "Allow all the time" dialog.
                    BuddyPermissionFlow.BackgroundRequestMode.IN_APP_REQUEST ->
                        bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    // API 30+: a runtime request is auto-denied, so proactively show a brief
                    // rationale that then opens the app's location settings (no dead no-op).
                    BuddyPermissionFlow.BackgroundRequestMode.GO_TO_SETTINGS ->
                        showBgRationale = true
                }
        }
    }

    // Step 2 of enable: consent (G3) has been accepted; kick off the chained permission
    // sequence. Enable only completes once foreground + background are actually held.
    fun proceedAfterConsent() {
        showConsent = false
        advanceEnable()
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
            // The REAL "on" confirmation is the pill's green state, which is bound to
            // `controller.enabled` and only turns green once enableConfirmed runs. A tap
            // that turns the toggle ON merely opens the consent dialog and may still fail
            // (permission denied), so we must NOT flash a misleading "Buddy Link on"
            // label. AppToggle always flashes its `onLabel` on an off->on tap, so for the
            // enable path we pass a neutral "Checking permissions…" label instead of the
            // "on" string. (AppToggle itself is the shared standard component and is left
            // unchanged per apptoggle-standard-switch.)
            com.example.joggingapp.ui.theme.AppToggle(
                checked = on,
                onToggle = { if (on) controller.disable() else showConsent = true },
                onLabel = if (on) S.toggleBuddyLinkOn else S.buddyEnablePending,
                offLabel = S.toggleBuddyLinkOff,
                // onExplanation (S.buddyLinkExplain) is intentionally NOT passed here: the raw
                // ON tap only opens the consent dialog and may still fail, so flashing a
                // success-implying explanation would promise enablement prematurely. The neutral
                // pending label above is kept for honesty.
                contentDescriptionText = S.buddyToggleDesc(on),
            )
        }

        // ── Enable-failed feedback (non-silent, Req: no silent failure) ───────
        // Shown when a background-location grant was needed to enable but was denied.
        // Gives a clear message + an Open Settings deep link so a willing user can grant
        // "Allow all the time" and then re-tap. The toggle stays honestly OFF meanwhile.
        if (permissionDenied && !controller.enabled) {
            Spacer(Modifier.height(10.dp))
            Text(
                S.buddyPermissionDeniedAllowAllTime,
                fontSize = 11.sp,
                color = c.error.copy(alpha = 0.9f),
                modifier = Modifier.semantics { contentDescription = S.buddyPermissionDeniedAllowAllTime }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                S.buddyOpenSettings,
                fontSize = 12.sp,
                color = c.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        context.startActivity(BuddyPermissionFlow.appSettingsIntent(context.packageName))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = S.buddyOpenSettings }
            )
        }

        if (controller.enabled) {
            // ── Share-mode switch (Ping / Route) — design §11 ─────────────────
            // Mirrors the master toggle's chrome. Turning ON (PING -> ROUTE) opens a
            // consent dialog first; turning OFF (ROUTE -> PING) is immediate.
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(c.primary.copy(alpha = 0.15f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(S.buddyShareModeLabel, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.Medium)
                    Text(
                        if (controller.shareMode == BuddyShareMode.ROUTE) S.buddyShareModeRoute else S.buddyShareModePing,
                        fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.7f)
                    )
                }
                val isRoute = controller.shareMode == BuddyShareMode.ROUTE
                com.example.joggingapp.ui.theme.AppToggle(
                    checked = isRoute,
                    onToggle = { if (isRoute) controller.disableRouteMode() else showRouteConsent = true },
                    onLabel = S.toggleShareRouteOn,
                    offLabel = S.toggleShareRouteOff,
                    onExplanation = S.shareRouteExplain,
                    contentDescriptionText = S.buddyShareModeDesc(isRoute),
                )
            }

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

            // ── My Buddy code card (Copy / Share) + member-since ──────────────
            val myId = controller.appId
            if (myId != null) {
                Spacer(Modifier.height(12.dp))
                // Transient status line under the card (copied / connect result).
                var codeMsg by remember { mutableStateOf<String?>(null) }
                // Buddy code is HIDDEN by default; revealed only on double-tap of the
                // "Show Buddy Code" control (saves space / keeps the code private).
                var codeRevealed by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(c.primary.copy(alpha = 0.10f)).padding(14.dp)
                ) {
                    // Tapping the title toggles the code: reveals when collapsed,
                    // collapses when already revealed (vertical padding enlarges the
                    // touch target beyond the small text bounds).
                    Text(S.buddyYourCode, fontSize = 11.sp, color = c.onSecondary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { codeRevealed = !codeRevealed }
                            .padding(vertical = 6.dp)
                            .semantics { contentDescription = S.buddyYourCode })
                    Spacer(Modifier.height(6.dp))
                    if (!codeRevealed) {
                        // Default: a tappable PILL (obvious it's clickable) that reveals the
                        // code. Reveal-only — tapping the pill shows the code and swaps the pill
                        // out, so there's no accidental hide-again. Same pill chrome as Copy/Share.
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.primary)
                                .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
                                .clickable { codeRevealed = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { contentDescription = S.buddyShowCode }
                        ) {
                            Text(
                                "👁 ${S.buddyShowCode}", fontSize = 12.sp, color = c.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                    // Revealed code. Tap the title above to collapse again.
                    Text(
                        formatBuddyCode(myId), fontSize = 15.sp, color = c.onSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { contentDescription = S.buddyYourCode }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Copy: filled PILL button (c.primary bg / c.onPrimary text) with a
                        // contrasting c.onSurface border so the pill outline is always visible
                        // against the card background in every theme (does not blend in).
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.primary)
                                .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Buddy code", myId))
                                    AppLogger.log(context, LogCategory.UI, "Buddy Link code copied")
                                    codeMsg = S.buddyCodeCopied
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { contentDescription = S.buddyCodeCopy }
                        ) {
                            Text("📋 ${S.buddyCodeCopy}", fontSize = 12.sp, color = c.onPrimary,
                                fontWeight = FontWeight.SemiBold)
                        }
                        // Share: same pill chrome + contrasting border, identical behaviour as before.
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.primary)
                                .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
                                .clickable {
                                    AppLogger.log(context, LogCategory.UI, "Buddy Link code shared")
                                    val shareText = controller.buildInviteShareText(userName) { name, appLink, httpsLink ->
                                        S.buddyInviteShareBody(name, appLink, httpsLink)
                                    }
                                    val body = if (shareText != null) shareText else myId
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, S.buddyShareSubject)
                                        putExtra(Intent.EXTRA_TEXT, body)
                                    }
                                    context.startActivity(Intent.createChooser(send, S.buddyCodeShare))
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { contentDescription = S.buddyCodeShare }
                        ) {
                            Text("↗ ${S.buddyCodeShare}", fontSize = 12.sp, color = c.onPrimary,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    } // end codeRevealed else-branch
                }

                // ── Share live location (S2 public link for non-app viewers) ──────
                // Sits directly under the buddy-code card (user request). The card owns
                // its own leading Spacer(12.dp), which is the only gap to the code card.
                ShareLiveLocationCard(controller)

                // ── Add a buddy: enter a friend's code ─────────────────────────
                // Hidden by default behind a reveal control to save space.
                Spacer(Modifier.height(12.dp))
                var addByCodeRevealed by remember { mutableStateOf(false) }
                var codeInput by remember { mutableStateOf("") }
                if (!addByCodeRevealed) {
                    Text(
                        S.buddyAddByCode, fontSize = 13.sp, color = c.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { addByCodeRevealed = true }
                            .semantics { contentDescription = S.buddyAddByCode }
                    )
                } else {
                androidx.compose.material.TextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    singleLine = true,
                    placeholder = { Text(S.buddyEnterCodeHint, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(c.primary.copy(alpha = 0.15f))
                        .clickable {
                            val normalized = normalizeBuddyCode(codeInput)
                            if (normalized == myId) {
                                codeMsg = S.buddySelfInvite
                            } else if (!isValidBuddyCode(normalized)) {
                                codeMsg = S.buddyCodeInvalid
                            } else {
                                codeMsg = S.buddyInviteSent  // optimistic; corrected on result
                                controller.requestLink(normalized, fromName = userName) { result ->
                                    codeMsg = when (result) {
                                        is BuddyResult.Success -> {
                                            codeInput = ""
                                            S.buddyInviteSent
                                        }
                                        is BuddyResult.Failure -> when (result.reason) {
                                            BuddyResult.Reason.NOT_FOUND -> S.buddyCodeUnknown
                                            BuddyResult.Reason.DUPLICATE -> S.buddyAlreadyConnected
                                            else -> S.buddyUnavailableBackend
                                        }
                                    }
                                }
                            }
                        }
                        .padding(14.dp)
                        .semantics { contentDescription = S.buddyConnect },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔗", fontSize = 20.sp)
                    Text(S.buddyConnect, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
                }
                } // end addByCodeRevealed else-branch
                codeMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 11.sp, color = c.onSecondary.copy(alpha = 0.85f))
                }
            }

            // ── Invites (incoming/pending; dismissable at any time, Req #4) ───
            if (controller.pending.isNotEmpty()) {
                BuddyGroupHeader(S.buddyInvites)
                controller.pending.forEach { link ->
                    val senderLabel = link.fromName.ifBlank { shortId(link.a) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.buddyInviteFrom(senderLabel), fontSize = 12.sp, color = c.onSecondary,
                            modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(S.buddyAccept, fontSize = 12.sp, color = c.success,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { controller.accept(link.linkId) }
                                    .semantics { contentDescription = S.buddyActionDesc(S.buddyAccept, senderLabel) })
                            Text(S.buddyInviteRemove, fontSize = 12.sp, color = c.error,
                                modifier = Modifier
                                    .clickable { controller.remove(link.linkId) }
                                    .semantics { contentDescription = S.buddyActionDesc(S.buddyInviteRemove, senderLabel) })
                        }
                    }
                    Divider(color = c.onSecondary.copy(alpha = 0.12f))
                }
            }

            // ── Buddies ──────────────────────────────────────────────────────
            BuddyGroupHeader(S.buddyBuddies)
            if (controller.activeBuddies.isEmpty()) {
                Text("—", fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.5f))
            } else {
                // Transient reason line for the tap-to-locate action, mirroring the
                // existing `codeMsg` pattern. Declared ONCE here (outside the forEach):
                // correct only under the verified v1 1:1 invariant (controller.activeBuddies has
                // at most one accepted buddy). If v1 ever becomes multi-buddy, move this
                // into the row keyed by view.user.appId.
                var locateMsg by remember { mutableStateOf<String?>(null) }
                // Transient line for the Live Cheer (S1) send result, same single-line
                // treatment as `locateMsg` (valid under the v1 1:1 invariant noted above).
                var cheerMsg by remember { mutableStateOf<String?>(null) }
                // Incoming cheer, shown in-app while foregrounded and auto-cleared. The
                // controller holds only the sender NAME; the string is formatted here so
                // all localized text stays in the UI layer (like `locateMsg`).
                controller.incomingCheerFromName?.let { fromName ->
                    Text(
                        S.buddyCheerReceived(fromName),
                        fontSize = 12.sp, color = c.primary, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                LaunchedEffect(controller.incomingCheerFromName) {
                    if (controller.incomingCheerFromName != null) {
                        kotlinx.coroutines.delay(4000)
                        controller.consumeCheer()
                    }
                }
                controller.activeBuddies.forEach { view ->
                    // Reads the controller's 3s livenessTick (inside isBuddyLive) so this
                    // row re-evaluates time-based liveness reactively — the list now
                    // refreshes within ~3s of a buddy going live, no swipe/restart.
                    val isLive = controller.isBuddyLive(view)
                    val name = view.user.displayName.ifBlank { shortId(view.user.appId) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Tap-to-locate: tapping the buddy's name/status column either
                        // centres the map on them (LOCATE, via the existing BuddyFocusBus
                        // focus path) or surfaces a specific reason. Reads `view` from
                        // controller.buddies (NOT primaryBuddyView) so a non-live buddy is
                        // still classified and explained. .padding(vertical = 8.dp) keeps
                        // the touch target ≥ 48dp.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    when (BuddyLocate.decide(view)) {
                                        BuddyLocate.Outcome.LOCATE -> {
                                            AppLogger.log(context, LogCategory.UI, "Buddy Link: locate buddy tapped (live)")
                                            BuddyFocusBus.pending = view.user.appId
                                            locateMsg = null
                                        }
                                        BuddyLocate.Outcome.REASON_BUDDY_LINK_OFF -> locateMsg = S.buddyLocateLinkOff(name)
                                        BuddyLocate.Outcome.REASON_SHARING_OFF -> locateMsg = S.buddyLocateSharingOff(name)
                                        BuddyLocate.Outcome.REASON_STALE_OR_NO_FIX -> locateMsg = S.buddyLocateUnavailable(name)
                                    }
                                }
                                .semantics { contentDescription = S.buddyLocateDesc(name) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(name, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.Medium)
                            if (view.user.createdAt > 0L) {
                                Text(S.buddyMemberSince(formatMemberDate(view.user.createdAt)),
                                    fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.6f))
                            }
                            val status = when {
                                isLive -> S.buddyStatusSharing
                                view.link?.state == BuddyLinkState.PENDING -> S.buddyStatusPending
                                else -> S.buddyStatusUnavailable
                            }
                            Text(status, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.6f))
                        }
                        // Live Cheer (S1): one-tap 💪 encouragement. A separate ≥48dp
                        // target with its own contentDescription, kept distinct from the
                        // name column (tap-to-locate) so neither action steals the other's
                        // tap. Theme-token colours only (c.primary) — no hardcoded colours.
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.primary.copy(alpha = 0.15f))
                                .clickable {
                                    val decision = controller.sendCheer(view.user.appId, name)
                                    cheerMsg = when (decision.reason) {
                                        CheerRateLimit.Reason.OK -> S.buddyCheerSent(name)
                                        CheerRateLimit.Reason.COOLDOWN -> S.buddyCheerCooldown
                                        CheerRateLimit.Reason.DAILY_CAP -> S.buddyCheerDailyCap
                                    }
                                }
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .padding(horizontal = 12.dp)
                                .semantics { contentDescription = S.buddyCheerDesc(name) },
                            contentAlignment = Alignment.Center
                        ) { Text("💪", fontSize = 20.sp) }
                        // Primary actions: Remove (unlink) + Block, both confirmed.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            view.link?.let { link ->
                                Text(S.buddyRemove, fontSize = 12.sp, color = c.error,
                                    modifier = Modifier
                                        .clickable { confirmRemove = link }
                                        .semantics { contentDescription = S.buddyActionDesc(S.buddyRemove, name) })
                            }
                            Text(S.buddyBlock, fontSize = 12.sp, color = c.error, fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { confirmBlock = view.user.appId }
                                    .semantics { contentDescription = S.buddyActionDesc(S.buddyBlock, name) })
                        }
                    }
                    // Secondary actions: request-now + history, only useful when live.
                    if (isLive) {
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
                // Tap-to-locate reason line, rendered ONCE below the list (same small
                // treatment as `codeMsg`). Valid as a single line only under the v1 1:1
                // invariant noted where `locateMsg` is declared above.
                locateMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.85f))
                }
                // Live Cheer (S1) send-result line, same small treatment as `locateMsg`.
                cheerMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.85f))
                }
            }

            // ── Blocked ──────────────────────────────────────────────────────
            // Block is reversible: the accepted link is preserved, so a blocked buddy
            // still arrives as a named BuddyView (controller.blockedBuddies) and is shown
            // with its real display name. Legacy blocks whose link was torn down under the
            // old build survive only as ids (controller.blockedOnlyIds) and render short.
            // A single tap unblocks immediately (no confirm dialog) and the buddy returns
            // to the active list via the reactive observeBuddies flow.
            if (controller.blockedBuddies.isNotEmpty() || controller.blockedOnlyIds.isNotEmpty()) {
                BuddyGroupHeader(S.buddyBlocked)
                controller.blockedBuddies.forEach { view ->
                    val blockedName = view.user.displayName.ifBlank { shortId(view.user.appId) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // One-tap unblock — tapping reinstates the buddy immediately.
                        // .padding(vertical = 8.dp) keeps the touch target ≥ 48dp.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    AppLogger.log(context, LogCategory.PROFILE, "Buddy Link: unblocked a buddy")
                                    controller.unblock(view.user.appId)
                                }
                                .semantics { contentDescription = S.buddyActionDesc(S.buddyUnblock, blockedName) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(blockedName, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.7f))
                            Text(S.buddyBlocked, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.6f))
                        }
                    }
                    Divider(color = c.onSecondary.copy(alpha = 0.12f))
                }
                controller.blockedOnlyIds.forEach { appId ->
                    val blockedName = shortId(appId)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    AppLogger.log(context, LogCategory.PROFILE, "Buddy Link: unblocked a buddy")
                                    controller.unblock(appId)
                                }
                                .semantics { contentDescription = S.buddyActionDesc(S.buddyUnblock, blockedName) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(blockedName, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.7f))
                            Text(S.buddyBlocked, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.6f))
                        }
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

    // ── Background-location rationale (API 30+ REQUEST_BACKGROUND step) ───────
    // On R+ a runtime background request is auto-denied, so we explain why "Allow all
    // the time" is needed and send the user to the app's location settings. Dismiss
    // leaves the toggle honestly OFF (no silent no-op — the body states what happens).
    if (showBgRationale) {
        BuddyDialog(
            title = S.buddyBgRationaleTitle,
            body = S.buddyBgRationaleBody,
            confirmLabel = S.buddyOpenSettings,
            dismissLabel = S.buddyLinkConsentDecline,
            onConfirm = {
                showBgRationale = false
                context.startActivity(BuddyPermissionFlow.appSettingsIntent(context.packageName))
            },
            onDismiss = { showBgRationale = false }
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

    // ── Route-share consent (switch PING -> ROUTE, req 5a) ───────────────────
    if (showRouteConsent) {
        BuddyDialog(
            title = S.buddyRouteConsentTitle,
            body = S.buddyRouteConsentBody,
            confirmLabel = S.buddyRouteConsentAgree,
            dismissLabel = S.buddyRouteConsentDecline,
            onConfirm = { controller.enableRouteMode(); showRouteConsent = false },
            onDismiss = { showRouteConsent = false }
        )
    }

    // ── Route-active re-alert (fires on each enable while ROUTE, req 5b) ──────
    // A notice, not a choice: both actions just dismiss (consume the one-shot flag).
    if (controller.routeActiveAlertPending) {
        BuddyDialog(
            title = S.buddyRouteActiveTitle,
            body = S.buddyRouteActiveBody,
            confirmLabel = S.buddyRouteActiveAck,
            dismissLabel = S.buddyRouteActiveAck,
            onConfirm = { controller.consumeRouteActiveAlert() },
            onDismiss = { controller.consumeRouteActiveAlert() }
        )
    }
}

/**
 * The "Share live location" card (S2). Rendered below the buddy-code card, gated by
 * `controller.enabled` like the rest of the section. Two states:
 *  - no active share: a create pill + an optional opt-in label field (reveal pattern);
 *  - active share: the public URL, a "Live for …" remaining-time line, the optional
 *    label, Copy + Share pills (reusing the Clipboard + ACTION_SEND pattern), and a
 *    destructive Stop control. A failed revoke keeps the active state (the controller
 *    clears activeShare only on success). All colours come from JogginTheme.colors.
 */
@Composable
private fun ShareLiveLocationCard(controller: BuddyLinkController) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current

    // Transient status line mirroring codeMsg/cheerMsg.
    var shareMsg by remember { mutableStateOf<String?>(null) }
    // Opt-in optional label field (hidden until the user reveals it), like the
    // add-by-code reveal pattern.
    var labelRevealed by remember { mutableStateOf(false) }
    var labelInput by remember { mutableStateOf("") }
    // Ticks so the "Live for …" remaining-time line counts down without a backend event.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(controller.activeShare) {
        while (true) { nowTick = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000L) }
    }

    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.primary.copy(alpha = 0.10f)).padding(14.dp)
    ) {
        Text(S.shareLiveTitle, fontSize = 11.sp, color = c.onSecondary.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        val share = controller.activeShare
        if (share == null) {
            // No active share: a create pill + an optional label field.
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(c.primary)
                    .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
                    .clickable {
                        controller.createShareLink(labelInput.ifBlank { null }) { result ->
                            shareMsg = when (result) {
                                is BuddyResult.Success -> {
                                    labelInput = ""; labelRevealed = false
                                    S.shareLinkCreated
                                }
                                is BuddyResult.Failure -> S.shareLinkCreateFailed
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = S.shareLiveCreate }
            ) {
                Text("🔗 ${S.shareLiveCreate}", fontSize = 12.sp, color = c.onPrimary,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            if (!labelRevealed) {
                Text(
                    S.shareLiveLabelHint, fontSize = 12.sp, color = c.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { labelRevealed = true }
                        .semantics { contentDescription = S.shareLiveLabelHint }
                )
            } else {
                androidx.compose.material.TextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    singleLine = true,
                    placeholder = { Text(S.shareLiveLabelHint, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Active share: URL + remaining time + label + Copy/Share + Stop.
            val url = controller.shareUrl(share.token)
            Text(url, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { contentDescription = url })
            Spacer(Modifier.height(4.dp))
            val remaining = formatShareRemaining(ShareLifetime.remainingMs(share.expiresAtMs, nowTick))
            Text(S.shareLiveRemaining(remaining), fontSize = 10.sp,
                color = c.onSecondary.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Copy pill.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.primary)
                        .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Share link", url))
                            AppLogger.log(context, LogCategory.UI, "Buddy Link: share link copied")
                            shareMsg = S.shareLinkCopied
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { contentDescription = S.shareLiveCopy }
                ) {
                    Text("📋 ${S.shareLiveCopy}", fontSize = 12.sp, color = c.onPrimary,
                        fontWeight = FontWeight.SemiBold)
                }
                // Share pill (ACTION_SEND chooser).
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.primary)
                        .border(BorderStroke(1.dp, c.onSurface.copy(alpha = 0.5f)), CircleShape)
                        .clickable {
                            AppLogger.log(context, LogCategory.UI, "Buddy Link: share link shared")
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, S.shareLiveShareSubject)
                                putExtra(Intent.EXTRA_TEXT, S.shareLiveShareBody(url))
                            }
                            context.startActivity(Intent.createChooser(send, S.shareLiveShareSubject))
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { contentDescription = S.shareLiveShareSubject }
                ) {
                    Text("↗ ${S.shareLiveShareSubject}", fontSize = 12.sp, color = c.onPrimary,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Destructive stop control (c.error), mirroring the danger-zone styling.
            Text(
                "🛑 ${S.shareLiveStop}", fontSize = 12.sp, color = c.error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        controller.revokeShareLink { result ->
                            shareMsg = when (result) {
                                is BuddyResult.Success -> S.shareLinkStopped
                                is BuddyResult.Failure -> S.shareLinkStopFailed
                            }
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = S.shareLiveStop }
            )
        }

        // Backend status note (non-blocking, Req 10) — same as the code card.
        if (!controller.backendAvailable) {
            Spacer(Modifier.height(10.dp))
            Text(S.buddyUnavailableBackend, fontSize = 11.sp, color = c.error.copy(alpha = 0.9f))
        }

        shareMsg?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, fontSize = 11.sp, color = c.onSecondary.copy(alpha = 0.85f))
        }
    }
}

/** Format a remaining-millis duration as a compact "Hh Mm" (e.g. "3h 40m"), minute "0m". */
internal fun formatShareRemaining(remainingMs: Long): String {
    val totalMinutes = remainingMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
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
internal fun buddyShortId(appId: String): String =
    if (appId.length <= 10) appId else appId.take(6) + "…" + appId.takeLast(4)

/**
 * Pure helpers for the V1.48 code-based invite flow. The "code" IS the raw appId (a
 * UUID with dashes removed → 32 lowercase hex chars); these only format/parse it for
 * display and entry, and are unit-tested in BuddyCodeTest.
 */

/** Group a raw appId into readable 4-char chunks for display (never used as the key). */
internal fun formatBuddyCode(rawId: String): String =
    rawId.chunked(4).joinToString(" ")

/** Normalize a pasted/typed code to the appId form: strip spaces + dashes, lowercase. */
internal fun normalizeBuddyCode(input: String): String =
    input.filterNot { it == ' ' || it == '-' }.lowercase()

/** A valid appId code is exactly 32 hex characters (a dash-stripped UUID). */
internal fun isValidBuddyCode(normalized: String): Boolean =
    normalized.length == 32 && normalized.all { it in '0'..'9' || it in 'a'..'f' }

/** Format a "member since" epoch-ms time the same way the Achievements screen does. */
internal fun formatMemberDate(epochMs: Long): String =
    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(epochMs))

// Local alias so existing call sites in this file stay unchanged.
private fun shortId(appId: String): String = buddyShortId(appId)

/**
 * The Accept/Ignore invite dialog shown to the RECIPIENT when they open an invite link
 * (required behavior #2). [senderLabel] identifies the sender (display name if known,
 * else a short id). Accept drives the recipient-side pairing; Ignore discards cleanly.
 */
@Composable
fun BuddyInviteDialog(senderLabel: String, onAccept: () -> Unit, onIgnore: () -> Unit) {
    val S = LocalStrings.current
    BuddyDialog(
        title = S.buddyInviteDialogTitle,
        body = S.buddyInviteDialogBody(senderLabel),
        confirmLabel = S.buddyInviteAccept,
        dismissLabel = S.buddyInviteIgnore,
        onConfirm = onAccept,
        onDismiss = onIgnore
    )
}

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
