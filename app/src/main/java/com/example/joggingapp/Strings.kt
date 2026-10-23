package com.example.joggingapp

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

// ── Language ──────────────────────────────────────────────────────────────────

enum class Language(val code: String, val displayName: String, val nativeName: String, val flag: String) {
    ENGLISH("en", "English", "English", "🇬🇧"),
    GREEK  ("el", "Greek",   "Ελληνικά", "🇬🇷"),
}

// ── String table ──────────────────────────────────────────────────────────────
// Every user-facing string in the app lives here. Add a field, then supply both
// the English and Greek value in the two instances below.

// NOTE: Plain class (not a `data class`) with an empty constructor and body-level
// `var` properties. A `data class` with 226 primary-constructor parameters makes the
// Kotlin compiler emit a constructor / copy$default whose single invoke passes all
// 226 arguments; on some ART verifiers that invoke exceeds the per-method argument-
// register limit and throws `java.lang.VerifyError: Rejecting invocation, expected N
// argument registers` on launch. Assigning each property one at a time (see the
// buildEnglishStrings/buildGreekStrings builders below) avoids any large invoke.
// Nothing relies on the generated equals/hashCode/copy/componentN of AppStrings.
class AppStrings {
    // Name entry
    var welcomeTitle: String = ""
    var whatsYourName: String = ""
    var enterYourName: String = ""
    var letsGo: String = ""
    // Tracker controls
    var hintAchievements: String = ""
    var hintOptions: String = ""
    var statTime: String = ""
    var statDistanceKm: String = ""
    var statKmh: String = ""
    var resume: String = ""
    var pause: String = ""
    var endRun: String = ""
    // Foreground-service notification labels
    var notifRunning: String = ""
    var notifPaused: String = ""
    var notifChannelName: String = ""
    var steps: String = ""
    // Activity modes
    var walk: String = ""
    var run: String = ""
    var cycle: String = ""
    // Past runs page
    var pastRuns: String = ""
    var newest: String = ""
    var oldest: String = ""
    var noRunsYet: String = ""
    var pastRunsCount: (Int) -> String = { _ -> "" }   // "3 past runs"
    // Welcome bubble
    var welcomeUser: (String) -> String = { _ -> "" }  // "Welcome, X 👋"
    // History card
    var noGpsData: String = ""
    var timerBasedActivity: String = ""
    var today: String = ""
    var duration: String = ""
    var km: String = ""
    var kcal: String = ""
    var na: String = ""
    var stepsCount: (Int) -> String = { _ -> "" }       // "👟 N steps"
    var share: String = ""
    var remove: String = ""
    // Editable run title
    var titlePlaceholder: String = ""          // TextField placeholder
    // Summary screen
    var complete: (String) -> String = { _ -> "" }      // "Run Complete"
    var activity: String = ""                   // summary stat row label: "Activity"
    var time: String = ""
    var distance: String = ""
    var avgSpeed: String = ""
    var maxSpeed: String = ""
    var minSpeed: String = ""
    var calories: String = ""
    var done: String = ""
    // Map markers
    var start: String = ""
    var finish: String = ""
    // Options pane — headers
    var options: String = ""
    var appTheme: String = ""
    var routeColour: String = ""
    var sharing: String = ""
    var includeMapWhenSharing: String = ""
    // Flash labels shown for ~3s when a toggle is tapped (AppToggle).
    var toggleMapShareOn: String = ""
    var toggleMapShareOff: String = ""
    var toggleBuddyLinkOn: String = ""
    var toggleBuddyLinkOff: String = ""
    var toggleShareRouteOn: String = ""
    var toggleShareRouteOff: String = ""
    var recentRuns: String = ""
    var clearAllRuns: String = ""
    var recentRunsChartDesc: String = ""
    var showAllRuns: String = ""
    var showRecentOnly: String = ""
    var shareApp: String = ""
    var shareAppWithFriends: String = ""
    var shareAppSubtitle: String = ""
    var backupRuns: String = ""
    var backupRunsSubtitle: String = ""
    var restoreRuns: String = ""
    var restoreRunsSubtitle: String = ""
    var restoredCount: (Int) -> String = { _ -> "" }    // "Restored N run(s)"
    var restoreFailed: String = ""
    // Route colour names
    var colorLightGreen: String = ""
    var colorBlue: String = ""
    var colorOrange: String = ""
    var colorRed: String = ""
    var colorPurple: String = ""
    var colorWhite: String = ""
    // Profile
    var myProfile: String = ""
    var profilePicture: String = ""
    var tapToChangePhoto: String = ""
    var rotatePhoto: String = ""
    var yourName: String = ""
    var save: String = ""
    var cancel: String = ""
    var tapToSetName: String = ""
    // Language
    var language: String = ""
    // Diagnostics
    var diagnostics: String = ""
    var all: String = ""
    var noLogEntries: String = ""
    var shareLog: String = ""
    var clearLog: String = ""
    // Achievements screen
    var myAchievements: String = ""
    var percentComplete: (Int) -> String = { _ -> "" }          // "42% complete"
    var progressCount: (Int, Int) -> String = { _, _ -> "" }       // "5 / 37"
    var completedMedals: (Int) -> String = { _ -> "" }          // "🏅 Completed Medals (N)"
    var earnedOn: (String) -> String = { _ -> "" }              // "✓ Earned MMM dd"
    var dismiss: String = ""
    var sortLabel: String = ""
    var sortDefault: String = ""
    var sortAlmostDone: String = ""
    var sortEarned: String = ""
    var sortOutstanding: String = ""
    var achEarnedHeader: String = ""
    var achOutstandingHeader: String = ""
    var achNoneInCategory: String = ""   // shown when a slicer filters to an empty list
    var trophyCaseWaiting: String = ""
    var completeFirstActivity: String = ""
    var startAnActivity: String = ""
    var unitKm: String = ""
    var unitDays: String = ""
    // Category labels
    var catAll: String = ""
    var catWalking: String = ""
    var catRunning: String = ""
    var catCycling: String = ""
    var catStreaks: String = ""
    var catSpecial: String = ""
    // Tier labels
    var tierBronze: String = ""
    var tierSilver: String = ""
    var tierGold: String = ""
    var tierSpecial: String = ""
    // Motivational quotes
    var motivations: List<String> = emptyList()
    // Toasts
    // ── Buddy Link ──────────────────────────────────────────────────────────
    var buddyLink: String = ""                 // section title
    var buddyLinkEnable: String = ""           // master toggle label
    var buddyLinkConsentTitle: String = ""
    var buddyLinkConsentBody: String = ""      // what's shared / with whom / how to stop
    var buddyLinkConsentAgree: String = ""
    var buddyLinkConsentDecline: String = ""
    // Invite share body: (name, appLink joggin://, httpsLink https://) -> message
    var buddyInviteShareBody: (String, String, String) -> String = { _, _, _ -> "" }
    var buddyInviteDialogTitle: String = ""    // Accept/Ignore dialog title
    var buddyInviteDialogBody: (String) -> String = { _ -> "" } // "{who} invited you…"
    var buddyInviteAccept: String = ""         // dialog accept action
    var buddyInviteIgnore: String = ""         // dialog ignore action
    var buddyInvites: String = ""              // "Invites" section header
    var buddyInviteRemove: String = ""         // remove/dismiss an invite
    var buddyInviteFrom: (String) -> String = { _ -> "" } // invite-row label "From {who}"
    var buddyInviteAcceptedToast: String = ""  // "Buddy added" note after accept
    var buddyInviteRequestSent: (String) -> String = { _ -> "" } // "Request sent — {who} needs to accept"
    var buddyInviteNeedsEnable: String = ""    // shown when invite arrives before enable
    var buddyBuddies: String = ""              // "Buddies"
    var buddyBlocked: String = ""              // "Blocked"
    var buddyAccept: String = ""
    var buddyDecline: String = ""
    var buddyRemove: String = ""
    var buddyBlock: String = ""
    var buddyUnblock: String = ""
    var buddyRemoveConfirm: String = ""        // confirm unlink
    var buddyBlockConfirm: String = ""         // confirm block
    var buddyDeleteData: String = ""           // "Delete my Buddy Link data"
    var buddyDeleteConfirm: String = ""        // confirm delete-all
    var buddyHistoryShow: String = ""          // "Show history" toggle label
    var buddyHistoryHide: String = ""          // "Hide history" toggle label
    var buddyStatusSharing: String = ""        // "Sharing"
    var buddyStatusUnavailable: String = ""    // "Unavailable"
    var buddyStatusPending: String = ""        // "Waiting to accept"
    var buddyRequestLocation: String = ""      // "Request location now"
    var buddyBroadcastOn: String = ""          // "Buddy Link active — broadcast on"
    var buddyEyesOnYou: String = ""            // eye-indicator label / tooltip
    var buddyUnlinkedWarning: (String) -> String = { _ -> "" } // "{name} stopped sharing"
    var buddyPermissionNeeded: String = ""     // background-location required note
    var buddyEventsChannelName: String = ""    // notification channel for social events
    var buddyNotifRequestTitle: String = ""    // "New buddy request"
    var buddyNotifRequestBody: String = ""     // "Someone wants to link with you"
    var buddyNotifRequestFrom: (String) -> String = { _ -> "" } // "{name} wants to buddy up"
    // ── V1.48 code-based invite flow ──────────────────────────────────────────
    var buddyYourCode: String = ""             // "Your Buddy code"
    var buddyCodeCopy: String = ""             // "Copy"
    var buddyCodeCopied: String = ""           // "Code copied"
    var buddyCodeShare: String = ""            // "Share"
    var buddyShareSubject: String = ""         // EXTRA_SUBJECT for share invite
    var buddyEnterCodeHint: String = ""        // input hint
    var buddyConnect: String = ""              // "Connect" button
    var buddyShowCode: String = ""             // "Show Buddy Code" reveal pill
    var buddyHideCode: String = ""             // "Hide" affordance when the code is revealed
    var buddyAddByCode: String = ""            // "Add a buddy by code" reveal control
    var buddyInviteSent: String = ""           // "Invite sent"
    var buddyCodeInvalid: String = ""          // "That code isn't valid"
    var buddyCodeUnknown: String = ""          // no one found with that code
    var buddyAlreadyConnected: String = ""     // already connected / pending
    var buddySelfInvite: String = ""           // "That's your own code"
    var buddyMemberSince: (String) -> String = { _ -> "" } // "Member since {date}"
    var buddyNotifAcceptedTitle: String = ""   // "Buddy request accepted"
    var buddyNotifAcceptedBody: (String) -> String = { _ -> "" } // "{name} is now your buddy"
    var buddyNotifUnlinkedTitle: String = ""   // "Buddy unlinked"
    var buddyNotifOnlineTitle: String = ""     // "Buddy online"
    var buddyNotifOnlineBody: (String) -> String = { _ -> "" } // "Your buddy, {name}, is online"
    var buddyToggleDesc: (Boolean) -> String = { _ -> "" }   // a11y: "Buddy Link, on/off. Double-tap to toggle."
    var buddyActionDesc: (String, String) -> String = { _, _ -> "" } // a11y: "{action} {name}"
    var achievementChannelName: String = ""    // notification channel for achievement unlocks
    var achievementNotifTitle: String = ""     // "Achievement unlocked 🏅"
    var achievementNotifBody: (String) -> String = { _ -> "" } // "You earned \"{name}\"!"
    var buddyRequestCooldown: String = ""      // "Requested — please wait" (rate-limit)
    var buddyEventsLowChannelName: String = "" // low-importance channel name
    var buddyNotifLocationSharedTitle: String = "" // "Location shared"
    var buddyNotifLocationSharedBody: String = ""  // "A buddy requested your location just now"
    var buddyLinkExpired: String = ""          // "Invite expired — ask for a new one"
    var buddyLinkInvalid: String = ""          // malformed / unknown id
    var buddyUnavailableBackend: String = ""   // backend unreachable, non-blocking
    var buddyChannelName: String = ""          // notification channel name for broadcast service
    var buddyJustNow: String = ""              // "updated just now" (fresh fix)
    var buddyMinutesAgo: (Int) -> String = { _ -> "" }  // "updated N min ago"
    // ── Buddy Link share mode (Ping / Route) ──────────────────────────────────
    var buddyShareModeLabel: String = ""       // row label for the mode switch
    var buddyShareModePing: String = ""        // sublabel/state when PING
    var buddyShareModeRoute: String = ""       // sublabel/state when ROUTE
    var buddyRouteConsentTitle: String = ""    // consent dialog title (switch to Route)
    var buddyRouteConsentBody: String = ""     // consent body
    var buddyRouteConsentAgree: String = ""    // consent confirm
    var buddyRouteConsentDecline: String = ""  // consent cancel
    var buddyRouteActiveTitle: String = ""     // re-alert title
    var buddyRouteActiveBody: String = ""      // re-alert body
    var buddyRouteActiveAck: String = ""       // re-alert dismiss ("Got it")
    var buddyShareModeDesc: (Boolean) -> String = { _ -> "" } // a11y: "Share route, on/off. Double-tap to toggle."
    // ── Exercise Targets ──────────────────────────────────────────────────────
    var exerciseTargets: String = ""           // section / screen title
    var exerciseTargetsSubtitle: String = ""   // options-pane row subtitle
    var exerciseTargetsEmpty: String = ""      // empty-state message
    var exTypeSitups: String = ""
    var exTypePushups: String = ""
    var exTypeSquats: String = ""
    var exTypeBicepCurls: String = ""
    var exTypeTricepDips: String = ""
    var exTypeLunges: String = ""
    var exTypeCrunches: String = ""
    var exTypeBurpees: String = ""
    var exTypeJumpingJacks: String = ""
    var exTypePlank: String = ""
    var exTypeWalking: String = ""
    var exTypeRunning: String = ""
    var exPeriodDaily: String = ""
    var exPeriodWeekly: String = ""
    var exPeriodMonthly: String = ""
    var exAddTarget: String = ""               // "Add target" button
    var exNewTarget: String = ""               // add-form heading
    var exExerciseLabel: String = ""           // "Exercise"
    var exPeriodLabel: String = ""             // "Period"
    var exAmountReps: String = ""              // amount field label for manual types
    var exAmountKm: String = ""                // amount field label for distance types
    var exLogReps: String = ""                 // "Log" button on a manual target
    var exDeductReps: String = ""              // "Deduct" button when entering a negative value
    var exLogRepsTitle: (String) -> String = { _ -> "" }// dialog title "Log {exercise}"
    var exProgressReps: (Int, Int) -> String = { _, _ -> "" }   // "{cur} / {goal} reps"
    var exProgressKm: (String, String) -> String = { _, _ -> "" }// "{cur} / {goal} km"
    var exDone: String = ""                    // "Goal met!" badge
    var exAutoTracked: String = ""             // note under distance targets
    var exLogAmountHint: String = ""           // placeholder in the log dialog ("Reps done")
    var exCongratsTitle: String = ""           // completion celebration title
    var exCongratsBody: (String) -> String = { _ -> "" }// "You hit your {exercise} target!"
    var exPersonalTargets: String = ""         // "Personal Targets" tab/section title
    var exPersonalTargetsEmpty: String = ""    // empty state for achieved list
    var exAchievedOn: (String) -> String = { _ -> "" }  // "Achieved {date}"
    var exTargetSummary: (String, String, String) -> String = { _, _, _ -> "" } // "{amount} {type} · {period}"
    var exAddAnother: String = ""              // batch form: stage this row + start another
    var exSaveAll: (Int) -> String = { _ -> "" }        // "Save N targets"
    var exStagedCount: (Int) -> String = { _ -> "" }    // "N target(s) to add"
    var exBadgePersonal: String = ""           // Achievements badge → personal targets view
    var exBadgeAchievements: String = ""       // badge back to achievements view
    var exGroupBy: String = ""                 // "Group by" label on the personal-targets sort control
    var exGroupDay: String = ""                // group-by option: Day
    var exGroupWeek: String = ""               // group-by option: Week
    var exGroupMonth: String = ""              // group-by option: Month
    var exWeekOf: (String) -> String = { _ -> "" }      // weekly bucket header, "Week of {date}"
    // ── App section / update check (v1.47) ────────────────────────────────────
    var appSection: String = ""                // options-pane collapsible header "App"
    var appVersionLabel: (String) -> String = { _ -> "" } // "Version 1.47"
    var checkForUpdates: String = ""           // button label
    var checkingForUpdates: String = ""        // transient while checking
    var upToDate: String = ""                  // "You're up to date"
    var updateAvailable: String = ""           // dialog title "Update available"
    var updateVersionAvailable: (String) -> String = { _ -> "" } // "Version X.Y available"
    var updateAction: String = ""              // "Update" button
    var updateLater: String = ""               // "Later" / dismiss
    var updateCheckFailed: String = ""         // "Couldn't check for updates"
}

// ── English ───────────────────────────────────────────────────────────────────

val EnglishStrings: AppStrings = buildEnglishStrings()

private fun buildEnglishStrings(): AppStrings = AppStrings().apply {
    welcomeTitle = "👋 Welcome to Joggin!"
    whatsYourName = "What's your name?"
    enterYourName = "Enter your name"
    letsGo = "Let's go!"
    hintAchievements = "Achievements"
    hintOptions = "Options"
    statTime = "Time"
    statDistanceKm = "Dist (km)"
    statKmh = "km/h"
    resume = "Resume"
    pause = "Pause"
    endRun = "End Run"
    notifRunning = "Joggin"
    notifPaused = "Paused"
    notifChannelName = "Joggin Tracking"
    steps = "steps"
    walk = "Walk"
    run = "Run"
    cycle = "Cycle"
    pastRuns = "Activity History"
    newest = "Newest"
    oldest = "Oldest"
    noRunsYet = "No runs yet.\nComplete your first activity!"
    pastRunsCount = { n -> "$n past runs" }
    welcomeUser = { name -> "Welcome, $name 👋" }
    noGpsData = "No GPS data"
    timerBasedActivity = "Timer-based activity"
    today = "Today"
    duration = "Duration"
    km = "km"
    kcal = "kcal"
    na = "N/A"
    stepsCount = { n -> "👟 $n steps" }
    share = "Share"
    remove = "Remove"

    titlePlaceholder = "Name this run"
    complete = { label -> "$label Complete" }
    activity = "Activity"
    time = "Time"
    distance = "Distance"
    avgSpeed = "Avg Speed"
    maxSpeed = "Max Speed"
    minSpeed = "Min Speed"
    calories = "Calories"
    done = "Done"
    start = "Start"
    finish = "Finish"
    options = "Options"
    appTheme = "App Theme"
    routeColour = "Route colour"
    sharing = "Sharing"
    includeMapWhenSharing = "Include map when sharing"
    toggleMapShareOn = "Map shared with friends"
    toggleMapShareOff = "Map hidden when sharing"
    toggleBuddyLinkOn = "Buddy Link on"
    toggleBuddyLinkOff = "Buddy Link off"
    toggleShareRouteOn = "Route shared"
    toggleShareRouteOff = "Location only"
    recentRuns = "Recent runs"
    clearAllRuns = "Clear all runs"
    recentRunsChartDesc = "Show recent runs summary"
    showAllRuns = "Show all"
    showRecentOnly = "Show less"
    shareApp = "Share App"
    shareAppWithFriends = "Share Joggin with friends"
    shareAppSubtitle = "Send a download link via WhatsApp, SMS, etc."
    backupRuns = "Backup Runs"
    backupRunsSubtitle = "Export all runs as a JSON file for recovery"
    restoreRuns = "Restore Runs"
    restoreRunsSubtitle = "Pick a backup file to import runs"
    restoredCount = { n -> "Restored $n run(s)" }
    restoreFailed = "Failed to restore — invalid file"
    colorLightGreen = "Light Green"
    colorBlue = "Blue"
    colorOrange = "Orange"
    colorRed = "Red"
    colorPurple = "Purple"
    colorWhite = "White"
    myProfile = "My Profile"
    profilePicture = "Profile picture"
    tapToChangePhoto = "Tap to change photo"
    rotatePhoto = "Rotate"
    yourName = "Your name"
    save = "Save"
    cancel = "Cancel"
    tapToSetName = "Tap to set your name"
    language = "Language"
    diagnostics = "Diagnostics"
    all = "All"
    noLogEntries = "No log entries yet"
    shareLog = "📤 Share Log"
    clearLog = "🗑 Clear Log"
    myAchievements = "My Achievements"
    percentComplete = { p -> "$p% complete" }
    progressCount = { e, t -> "$e / $t" }
    completedMedals = { n -> "🏅 Completed Medals ($n)" }
    earnedOn = { date -> "✓ Earned $date" }
    dismiss = "Dismiss"
    sortLabel = "Sort:"
    sortDefault = "Default"
    sortAlmostDone = "Almost Done"
    sortEarned = "Earned"
    sortOutstanding = "Outstanding"
    achEarnedHeader = "Earned"
    achOutstandingHeader = "Outstanding"
    achNoneInCategory = "Nothing here yet"
    trophyCaseWaiting = "Your trophy case is waiting!"
    completeFirstActivity = "Complete your first activity\nto start earning badges."
    startAnActivity = "Start an Activity"
    unitKm = "km"
    unitDays = "days"
    catAll = "All"
    catWalking = "Walking"
    catRunning = "Running"
    catCycling = "Cycling"
    catStreaks = "Streaks"
    catSpecial = "Special"
    tierBronze = "BRONZE"
    tierSilver = "SILVER"
    tierGold = "GOLD"
    tierSpecial = "SPECIAL"
    motivations = listOf(
        "Keep going! You're crushing it! 🔥",
        "Every step counts — you're doing great! 💪",
        "Legends are made one activity at a time! ⚡",
        "Your next badge is just around the corner! 🏅",
    )

    // ── Buddy Link ──────────────────────────────────────────────────────────
    buddyLink = "Buddy Link"
    buddyLinkEnable = "Enable Buddy Link"
    buddyLinkConsentTitle = "Share your location with a buddy"
    buddyLinkConsentBody = "When on, your location is shared with a linked buddy so they can keep you safe — only while both of you have Buddy Link on and GPS enabled. Either of you can stop or unlink at any time."
    buddyLinkConsentAgree = "I understand, enable"
    buddyLinkConsentDecline = "Not now"
    buddyInviteShareBody = { name, appLink, httpsLink ->
        "$name wants to link with you on Joggin as a safety buddy.\n\n" +
        "Already have Joggin installed? Tap to open the app and connect:\n$appLink\n\n" +
        "Otherwise tap this link (install Joggin first, then tap again):\n$httpsLink"
    }
    buddyInviteDialogTitle = "Buddy invite"
    buddyInviteDialogBody = { who -> "$who invited you to be safety buddies on Joggin. Accept to share location while you're both out." }
    buddyInviteAccept = "Accept"
    buddyInviteIgnore = "Ignore"
    buddyInvites = "Invites"
    buddyInviteRemove = "Remove"
    buddyInviteFrom = { who -> "From $who" }
    buddyInviteAcceptedToast = "Buddy added"
    buddyInviteRequestSent = { who -> "Request sent — $who needs to accept" }
    buddyInviteNeedsEnable = "Turn on Buddy Link to accept this invite."
    buddyBuddies = "Buddies"
    buddyBlocked = "Blocked"
    buddyAccept = "Accept"
    buddyDecline = "Decline"
    buddyRemove = "Remove"
    buddyBlock = "Block"
    buddyUnblock = "Unblock"
    buddyRemoveConfirm = "Unlink this buddy? Location sharing stops for both of you."
    buddyBlockConfirm = "Block this buddy? You'll be unlinked and they can't link with you again."
    buddyDeleteData = "Delete my Buddy Link data"
    buddyDeleteConfirm = "Delete all your Buddy Link data (identity, links, shared location) and turn Buddy Link off? This can't be undone."
    buddyHistoryShow = "Show history"
    buddyHistoryHide = "Hide history"
    buddyStatusSharing = "Sharing"
    buddyStatusUnavailable = "Unavailable"
    buddyStatusPending = "Waiting to accept"
    buddyRequestLocation = "Request location now"
    buddyBroadcastOn = "Buddy Link active — broadcast on"
    buddyEyesOnYou = "A buddy can see your location"
    buddyUnlinkedWarning = { name -> "$name stopped sharing their location" }
    buddyPermissionNeeded = "Buddy Link needs background location permission to work. Enable it in settings to use this feature."
    buddyEventsChannelName = "Buddy Link updates"
    buddyNotifRequestTitle = "New buddy request"
    buddyNotifRequestBody = "Someone wants to link with you on Buddy Link"
    buddyNotifRequestFrom = { name -> "$name wants to buddy up" }
    buddyYourCode = "Your Buddy code"
    buddyCodeCopy = "Copy"
    buddyCodeCopied = "Code copied"
    buddyCodeShare = "Send Buddy Invite"
    buddyShareSubject = "Add me on Joggin as a safety buddy"
    buddyEnterCodeHint = "Enter a friend's code"
    buddyConnect = "Add Buddy"
    buddyShowCode = "Show Buddy Code"
    buddyHideCode = "Hide"
    buddyAddByCode = "Add a buddy by code"
    buddyInviteSent = "Invite sent"
    buddyCodeInvalid = "That code isn't valid"
    buddyCodeUnknown = "No one found with that code"
    buddyAlreadyConnected = "You're already connected or a request is pending"
    buddySelfInvite = "That's your own code"
    buddyMemberSince = { date -> "Member since $date" }
    buddyNotifAcceptedTitle = "Buddy request accepted"
    buddyNotifAcceptedBody = { name -> "$name is now your buddy" }
    buddyNotifUnlinkedTitle = "Buddy unlinked"
    buddyNotifOnlineTitle = "Buddy online"
    buddyNotifOnlineBody = { name -> "Your buddy, $name, is online" }
    buddyToggleDesc = { on -> "Buddy Link, ${if (on) "on" else "off"}. Double-tap to toggle." }
    buddyActionDesc = { action, name -> "$action $name" }
    achievementChannelName = "Achievements"
    achievementNotifTitle = "Achievement unlocked 🏅"
    achievementNotifBody = { name -> "You earned \"$name\"!" }
    buddyRequestCooldown = "Requested — please wait"
    buddyEventsLowChannelName = "Buddy Link activity"
    buddyNotifLocationSharedTitle = "Location shared"
    buddyNotifLocationSharedBody = "A buddy requested your location just now"
    buddyLinkExpired = "This invite has expired — ask your buddy for a new one"
    buddyLinkInvalid = "This invite link is not valid"
    buddyUnavailableBackend = "Buddy Link is temporarily unavailable"
    buddyChannelName = "Buddy Link"
    buddyJustNow = "updated just now"
    buddyMinutesAgo = { min -> "updated $min min ago" }
    buddyShareModeLabel = "Share my route"
    buddyShareModePing = "Location only (ping)"
    buddyShareModeRoute = "Route (path)"
    buddyRouteConsentTitle = "Share your route?"
    buddyRouteConsentBody = "Turning this on shows your buddy the actual path you take, drawn on their map, until you turn Buddy Link off or switch back to location-only. It updates about every 5 minutes."
    buddyRouteConsentAgree = "I understand, share route"
    buddyRouteConsentDecline = "Cancel"
    buddyRouteActiveTitle = "Route sharing is on"
    buddyRouteActiveBody = "Buddy Link is on and you're sharing your route. Your buddy can see the path you take until you switch back to location-only or turn Buddy Link off."
    buddyRouteActiveAck = "Got it"
    buddyShareModeDesc = { route -> "Share route, ${if (route) "on" else "off"}. Double-tap to toggle." }
    exerciseTargets = "Exercise Targets"
    exerciseTargetsSubtitle = "Set at-home goals and track your progress"
    exerciseTargetsEmpty = "No targets yet. Add one to start tracking your at-home exercises and distance goals."
    exTypeSitups = "Sit-ups"
    exTypePushups = "Push-ups"
    exTypeSquats = "Squats"
    exTypeBicepCurls = "Bicep curls"
    exTypeTricepDips = "Tricep dips"
    exTypeLunges = "Lunges"
    exTypeCrunches = "Crunches"
    exTypeBurpees = "Burpees"
    exTypeJumpingJacks = "Jumping jacks"
    exTypePlank = "Plank (seconds)"
    exTypeWalking = "Walking"
    exTypeRunning = "Running"
    exPeriodDaily = "Daily"
    exPeriodWeekly = "Weekly"
    exPeriodMonthly = "Monthly"
    exAddTarget = "Add target"
    exNewTarget = "New target"
    exExerciseLabel = "Exercise"
    exPeriodLabel = "Period"
    exAmountReps = "Target reps"
    exAmountKm = "Target distance (km)"
    exLogReps = "Log"
    exDeductReps = "Deduct"
    exLogRepsTitle = { ex -> "Log $ex" }
    exProgressReps = { cur, goal -> "$cur / $goal reps" }
    exProgressKm = { cur, goal -> "$cur / $goal km" }
    exDone = "Goal met! 🎉"
    exAutoTracked = "Auto-tracked from your runs"
    exLogAmountHint = "Reps done (use - to deduct)"
    exCongratsTitle = "🎉 Target smashed!"
    exCongratsBody = { ex -> "You completed your $ex target. Nice work!" }
    exPersonalTargets = "Personal Targets"
    exPersonalTargetsEmpty = "No completed targets yet. Hit a target and it'll be recorded here with the date."
    exAchievedOn = { date -> "Achieved $date" }
    exTargetSummary = { amount, type, period -> "$amount $type · $period" }
    exAddAnother = "Add another"
    exSaveAll = { n -> if (n == 1) "Save 1 target" else "Save $n targets" }
    exStagedCount = { n -> if (n == 1) "1 target to add" else "$n targets to add" }
    exBadgePersonal = "🎯 Personal"
    exBadgeAchievements = "🏆 Medals"
    exGroupBy = "Group by"
    exGroupDay = "Day"
    exGroupWeek = "Week"
    exGroupMonth = "Month"
    exWeekOf = { date -> "Week of $date" }
    // ── App section / update check (v1.47) ────────────────────────────────────
    appSection = "App"
    appVersionLabel = { v -> "Version $v" }
    checkForUpdates = "Check for updates"
    checkingForUpdates = "Checking for updates…"
    upToDate = "You're up to date"
    updateAvailable = "Update available"
    updateVersionAvailable = { v -> "Version $v available" }
    updateAction = "Update"
    updateLater = "Later"
    updateCheckFailed = "Couldn't check for updates"
}

// ── Greek (formal Modern Greek, monotonic) ─────────────────────────────────────

val GreekStrings: AppStrings = buildGreekStrings()

private fun buildGreekStrings(): AppStrings = AppStrings().apply {
    welcomeTitle = "👋 Καλώς ήρθατε στο Joggin!"
    whatsYourName = "Πώς σας λένε;"
    enterYourName = "Εισαγάγετε το όνομά σας"
    letsGo = "Πάμε!"
    hintAchievements = "Επιτεύγματα"
    hintOptions = "Επιλογές"
    statTime = "Χρόνος"
    statDistanceKm = "Απόστ. (χλμ)"
    statKmh = "χλμ/ώρα"
    resume = "Συνέχεια"
    pause = "Παύση"
    endRun = "Τερματισμός"
    notifRunning = "Τζόγκιν"
    notifPaused = "Σε παύση"
    notifChannelName = "Παρακολούθηση Τζόγκιν"
    steps = "βήματα"
    walk = "Περπάτημα"
    run = "Τρέξιμο"
    cycle = "Ποδήλατο"
    pastRuns = "Προηγούμενες Δραστηριότητες"
    newest = "Νεότερες"
    oldest = "Παλαιότερες"
    noRunsYet = "Καμία δραστηριότητα ακόμη.\nΟλοκληρώστε την πρώτη σας δραστηριότητα!"
    pastRunsCount = { n -> "$n προηγούμενες δραστηριότητες" }
    welcomeUser = { name -> "Καλώς ήρθατε, $name 👋" }
    noGpsData = "Χωρίς δεδομένα GPS"
    timerBasedActivity = "Δραστηριότητα με χρονόμετρο"
    today = "Σήμερα"
    duration = "Διάρκεια"
    km = "χλμ"
    kcal = "θερμ."
    na = "Μ/Δ"
    stepsCount = { n -> "👟 $n βήματα" }
    share = "Κοινοποίηση"
    remove = "Διαγραφή"

    titlePlaceholder = "Ονομάστε αυτό το δρομολόγιο"
    complete = { label -> "$label — Ολοκληρώθηκε" }
    activity = "Δραστηριότητα"
    time = "Χρόνος"
    distance = "Απόσταση"
    avgSpeed = "Μέση Ταχύτητα"
    maxSpeed = "Μέγιστη Ταχύτητα"
    minSpeed = "Ελάχιστη Ταχύτητα"
    calories = "Θερμίδες"
    done = "Τέλος"
    start = "Αφετηρία"
    finish = "Τερματισμός"
    options = "Επιλογές"
    appTheme = "Θέμα Εφαρμογής"
    routeColour = "Χρώμα Διαδρομής"
    sharing = "Κοινοποίηση"
    includeMapWhenSharing = "Συμπερίληψη χάρτη στην κοινοποίηση"
    toggleMapShareOn = "Κοινή χρήση χάρτη με φίλους"
    toggleMapShareOff = "Απόκρυψη χάρτη κατά την κοινοποίηση"
    toggleBuddyLinkOn = "Σύνδεσμος Φίλου ενεργός"
    toggleBuddyLinkOff = "Σύνδεσμος Φίλου ανενεργός"
    toggleShareRouteOn = "Κοινή χρήση διαδρομής"
    toggleShareRouteOff = "Μόνο τοποθεσία"
    recentRuns = "Πρόσφατες Δραστηριότητες"
    clearAllRuns = "Διαγραφή όλων"
    recentRunsChartDesc = "Εμφάνιση σύνοψης πρόσφατων δραστηριοτήτων"
    showAllRuns = "Εμφάνιση όλων"
    showRecentOnly = "Εμφάνιση λιγότερων"
    shareApp = "Κοινοποίηση Εφαρμογής"
    shareAppWithFriends = "Μοιραστείτε το Joggin με φίλους"
    shareAppSubtitle = "Στείλτε σύνδεσμο λήψης μέσω WhatsApp, SMS κ.λπ."
    backupRuns = "Αντίγραφο Ασφαλείας"
    backupRunsSubtitle = "Εξαγωγή όλων των δραστηριοτήτων σε αρχείο JSON"
    restoreRuns = "Επαναφορά Δραστηριοτήτων"
    restoreRunsSubtitle = "Επιλέξτε ένα αρχείο αντιγράφου για εισαγωγή"
    restoredCount = { n -> "Επαναφέρθηκαν $n δραστηριότητες" }
    restoreFailed = "Η επαναφορά απέτυχε — μη έγκυρο αρχείο"
    colorLightGreen = "Ανοιχτό Πράσινο"
    colorBlue = "Μπλε"
    colorOrange = "Πορτοκαλί"
    colorRed = "Κόκκινο"
    colorPurple = "Μοβ"
    colorWhite = "Λευκό"
    myProfile = "Το Προφίλ μου"
    profilePicture = "Φωτογραφία προφίλ"
    tapToChangePhoto = "Πατήστε για αλλαγή φωτογραφίας"
    rotatePhoto = "Περιστροφή"
    yourName = "Το όνομά σας"
    save = "Αποθήκευση"
    cancel = "Άκυρο"
    tapToSetName = "Πατήστε για να ορίσετε το όνομά σας"
    language = "Γλώσσα"
    diagnostics = "Διαγνωστικά"
    all = "Όλα"
    noLogEntries = "Δεν υπάρχουν καταχωρίσεις ακόμη"
    shareLog = "📤 Κοινοποίηση Αρχείου"
    clearLog = "🗑 Διαγραφή Αρχείου"
    myAchievements = "Τα Επιτεύγματά μου"
    percentComplete = { p -> "$p% ολοκληρώθηκε" }
    progressCount = { e, t -> "$e / $t" }
    completedMedals = { n -> "🏅 Ολοκληρωμένα Μετάλλια ($n)" }
    earnedOn = { date -> "✓ Κερδήθηκε $date" }
    dismiss = "Κλείσιμο"
    sortLabel = "Ταξινόμηση:"
    sortDefault = "Προεπιλογή"
    sortAlmostDone = "Σχεδόν Έτοιμα"
    sortEarned = "Κερδισμένα"
    sortOutstanding = "Εκκρεμή"
    achEarnedHeader = "Κερδισμένα"
    achOutstandingHeader = "Σε εξέλιξη"
    achNoneInCategory = "Τίποτα εδώ ακόμα"
    trophyCaseWaiting = "Η προθήκη τροπαίων σας περιμένει!"
    completeFirstActivity = "Ολοκληρώστε την πρώτη σας δραστηριότητα\nγια να κερδίσετε διακριτικά."
    startAnActivity = "Ξεκινήστε Δραστηριότητα"
    unitKm = "χλμ"
    unitDays = "ημέρες"
    catAll = "Όλα"
    catWalking = "Περπάτημα"
    catRunning = "Τρέξιμο"
    catCycling = "Ποδηλασία"
    catStreaks = "Σερί"
    catSpecial = "Ειδικά"
    tierBronze = "ΧΑΛΚΙΝΟ"
    tierSilver = "ΑΣΗΜΕΝΙΟ"
    tierGold = "ΧΡΥΣΟ"
    tierSpecial = "ΕΙΔΙΚΟ"
    motivations = listOf(
        "Συνεχίστε! Τα πάτε περίφημα! 🔥",
        "Κάθε βήμα μετράει — τα καταφέρνετε υπέροχα! 💪",
        "Οι θρύλοι δημιουργούνται μία δραστηριότητα τη φορά! ⚡",
        "Το επόμενο διακριτικό σας είναι πολύ κοντά! 🏅",
    )

    // ── Buddy Link ──────────────────────────────────────────────────────────
    buddyLink = "Σύνδεσμος Φίλου"
    buddyLinkEnable = "Ενεργοποίηση Συνδέσμου Φίλου"
    buddyLinkConsentTitle = "Μοιραστείτε την τοποθεσία σας με έναν φίλο"
    buddyLinkConsentBody = "Όταν είναι ενεργό, η τοποθεσία σας μοιράζεται με έναν συνδεδεμένο φίλο για την ασφάλειά σας — μόνο όσο και οι δύο έχετε τον Σύνδεσμο Φίλου ενεργό και το GPS ανοιχτό. Ο καθένας μπορεί να το σταματήσει ή να αποσυνδεθεί ανά πάσα στιγμή."
    buddyLinkConsentAgree = "Κατάλαβα, ενεργοποίηση"
    buddyLinkConsentDecline = "Όχι τώρα"
    buddyInviteShareBody = { name, appLink, httpsLink ->
        "Ο/Η $name θέλει να συνδεθεί μαζί σας στο Joggin ως φίλος ασφαλείας.\n\n" +
        "Έχετε ήδη το Joggin; Πατήστε για να ανοίξει η εφαρμογή και να συνδεθείτε:\n$appLink\n\n" +
        "Διαφορετικά πατήστε αυτόν τον σύνδεσμο (εγκαταστήστε πρώτα το Joggin και ξαναπατήστε):\n$httpsLink"
    }
    buddyInviteDialogTitle = "Πρόσκληση φίλου"
    buddyInviteDialogBody = { who -> "Ο/Η $who σας προσκάλεσε να γίνετε φίλοι ασφαλείας στο Joggin. Αποδεχτείτε για να μοιράζεστε την τοποθεσία σας όσο είστε έξω." }
    buddyInviteAccept = "Αποδοχή"
    buddyInviteIgnore = "Αγνόηση"
    buddyInvites = "Προσκλήσεις"
    buddyInviteRemove = "Αφαίρεση"
    buddyInviteFrom = { who -> "Από $who" }
    buddyInviteAcceptedToast = "Ο φίλος προστέθηκε"
    buddyInviteRequestSent = { who -> "Το αίτημα στάλθηκε — ο/η $who πρέπει να το αποδεχτεί" }
    buddyInviteNeedsEnable = "Ενεργοποιήστε τον Σύνδεσμο Φίλου για να αποδεχτείτε αυτή την πρόσκληση."
    buddyBuddies = "Φίλοι"
    buddyBlocked = "Αποκλεισμένοι"
    buddyAccept = "Αποδοχή"
    buddyDecline = "Απόρριψη"
    buddyRemove = "Αφαίρεση"
    buddyBlock = "Αποκλεισμός"
    buddyUnblock = "Άρση αποκλεισμού"
    buddyRemoveConfirm = "Αποσύνδεση αυτού του φίλου; Η κοινή χρήση τοποθεσίας σταματά και για τους δύο."
    buddyBlockConfirm = "Αποκλεισμός αυτού του φίλου; Θα αποσυνδεθείτε και δεν θα μπορεί να συνδεθεί ξανά μαζί σας."
    buddyDeleteData = "Διαγραφή των δεδομένων μου Buddy Link"
    buddyDeleteConfirm = "Διαγραφή όλων των δεδομένων Buddy Link (ταυτότητα, συνδέσεις, κοινόχρηστη τοποθεσία) και απενεργοποίηση του Buddy Link; Δεν αναιρείται."
    buddyHistoryShow = "Εμφάνιση ιστορικού"
    buddyHistoryHide = "Απόκρυψη ιστορικού"
    buddyStatusSharing = "Κοινή χρήση"
    buddyStatusUnavailable = "Μη διαθέσιμος"
    buddyStatusPending = "Αναμονή αποδοχής"
    buddyRequestLocation = "Αίτηση τοποθεσίας τώρα"
    buddyBroadcastOn = "Σύνδεσμος Φίλου ενεργός — μετάδοση ενεργή"
    buddyEyesOnYou = "Ένας φίλος βλέπει την τοποθεσία σας"
    buddyUnlinkedWarning = { name -> "Ο/Η $name σταμάτησε την κοινή χρήση τοποθεσίας" }
    buddyPermissionNeeded = "Ο Σύνδεσμος Φίλου χρειάζεται άδεια τοποθεσίας στο παρασκήνιο. Ενεργοποιήστε την στις ρυθμίσεις για να χρησιμοποιήσετε αυτή τη λειτουργία."
    buddyEventsChannelName = "Ενημερώσεις Συνδέσμου Φίλου"
    buddyNotifRequestTitle = "Νέο αίτημα φίλου"
    buddyNotifRequestBody = "Κάποιος θέλει να συνδεθεί μαζί σας στον Σύνδεσμο Φίλου"
    buddyNotifRequestFrom = { name -> "Ο/Η $name θέλει να γίνετε φίλοι" }
    buddyYourCode = "Ο κωδικός σας Buddy"
    buddyCodeCopy = "Αντιγραφή"
    buddyCodeCopied = "Ο κωδικός αντιγράφηκε"
    buddyCodeShare = "Αποστολή πρόσκλησης φίλου"
    buddyShareSubject = "Πρόσθεσέ με στο Joggin ως φίλο ασφαλείας"
    buddyEnterCodeHint = "Εισαγάγετε τον κωδικό ενός φίλου"
    buddyConnect = "Προσθήκη φίλου"
    buddyShowCode = "Εμφάνιση κωδικού Buddy"
    buddyHideCode = "Απόκρυψη"
    buddyAddByCode = "Προσθήκη φίλου με κωδικό"
    buddyInviteSent = "Η πρόσκληση στάλθηκε"
    buddyCodeInvalid = "Μη έγκυρος κωδικός"
    buddyCodeUnknown = "Δεν βρέθηκε κανείς με αυτόν τον κωδικό"
    buddyAlreadyConnected = "Είστε ήδη συνδεδεμένοι ή εκκρεμεί αίτημα"
    buddySelfInvite = "Αυτός είναι ο δικός σας κωδικός"
    buddyMemberSince = { date -> "Μέλος από $date" }
    buddyNotifAcceptedTitle = "Το αίτημα φίλου έγινε αποδεκτό"
    buddyNotifAcceptedBody = { name -> "Ο/Η $name είναι πλέον φίλος σας" }
    buddyNotifUnlinkedTitle = "Αποσύνδεση φίλου"
    buddyNotifOnlineTitle = "Ο φίλος είναι συνδεδεμένος"
    buddyNotifOnlineBody = { name -> "Ο/Η $name, ο φίλος σου, είναι συνδεδεμένος" }
    buddyToggleDesc = { on -> "Σύνδεσμος Φίλου, ${if (on) "ενεργός" else "ανενεργός"}. Πατήστε δύο φορές για εναλλαγή." }
    buddyActionDesc = { action, name -> "$action $name" }
    achievementChannelName = "Επιτεύγματα"
    achievementNotifTitle = "Ξεκλείδωμα επιτεύγματος 🏅"
    achievementNotifBody = { name -> "Κερδίσατε «$name»!" }
    buddyRequestCooldown = "Ζητήθηκε — περιμένετε"
    buddyEventsLowChannelName = "Δραστηριότητα Συνδέσμου Φίλου"
    buddyNotifLocationSharedTitle = "Κοινοποίηση τοποθεσίας"
    buddyNotifLocationSharedBody = "Ένας φίλος ζήτησε την τοποθεσία σας μόλις τώρα"
    buddyLinkExpired = "Αυτή η πρόσκληση έληξε — ζητήστε μια νέα από τον φίλο σας"
    buddyLinkInvalid = "Αυτός ο σύνδεσμος πρόσκλησης δεν είναι έγκυρος"
    buddyUnavailableBackend = "Ο Σύνδεσμος Φίλου δεν είναι προσωρινά διαθέσιμος"
    buddyChannelName = "Σύνδεσμος Φίλου"
    buddyJustNow = "ενημερώθηκε μόλις τώρα"
    buddyMinutesAgo = { min -> "ενημερώθηκε πριν $min λεπτά" }
    buddyShareModeLabel = "Κοινή χρήση διαδρομής"
    buddyShareModePing = "Μόνο τοποθεσία (ping)"
    buddyShareModeRoute = "Διαδρομή (πορεία)"
    buddyRouteConsentTitle = "Κοινή χρήση διαδρομής;"
    buddyRouteConsentBody = "Ενεργοποιώντας το, ο φίλος σας βλέπει την πραγματική διαδρομή που ακολουθείτε, σχεδιασμένη στον χάρτη του, μέχρι να απενεργοποιήσετε τον Σύνδεσμο Φίλου ή να επιστρέψετε σε μόνο τοποθεσία. Ενημερώνεται περίπου κάθε 5 λεπτά."
    buddyRouteConsentAgree = "Κατάλαβα, κοινή χρήση"
    buddyRouteConsentDecline = "Άκυρο"
    buddyRouteActiveTitle = "Η κοινή χρήση διαδρομής είναι ενεργή"
    buddyRouteActiveBody = "Ο Σύνδεσμος Φίλου είναι ενεργός και μοιράζεστε τη διαδρομή σας. Ο φίλος σας βλέπει την πορεία σας μέχρι να επιστρέψετε σε μόνο τοποθεσία ή να τον απενεργοποιήσετε."
    buddyRouteActiveAck = "Το κατάλαβα"
    buddyShareModeDesc = { route -> "Κοινή χρήση διαδρομής, ${if (route) "ενεργή" else "ανενεργή"}. Πατήστε δύο φορές για εναλλαγή." }
    exerciseTargets = "Στόχοι Άσκησης"
    exerciseTargetsSubtitle = "Ορίστε στόχους στο σπίτι και παρακολουθήστε την πρόοδό σας"
    exerciseTargetsEmpty = "Δεν υπάρχουν στόχοι ακόμη. Προσθέστε έναν για να παρακολουθείτε τις ασκήσεις σας και τους στόχους απόστασης."
    exTypeSitups = "Κοιλιακοί"
    exTypePushups = "Κάμψεις"
    exTypeSquats = "Καθίσματα"
    exTypeBicepCurls = "Κάμψεις δικεφάλων"
    exTypeTricepDips = "Βυθίσεις τρικεφάλων"
    exTypeLunges = "Προβολές"
    exTypeCrunches = "Αναδιπλώσεις"
    exTypeBurpees = "Burpees"
    exTypeJumpingJacks = "Αναπηδήσεις"
    exTypePlank = "Σανίδα (δευτερόλεπτα)"
    exTypeWalking = "Περπάτημα"
    exTypeRunning = "Τρέξιμο"
    exPeriodDaily = "Ημερήσιο"
    exPeriodWeekly = "Εβδομαδιαίο"
    exPeriodMonthly = "Μηνιαίο"
    exAddTarget = "Προσθήκη στόχου"
    exNewTarget = "Νέος στόχος"
    exExerciseLabel = "Άσκηση"
    exPeriodLabel = "Περίοδος"
    exAmountReps = "Στόχος επαναλήψεων"
    exAmountKm = "Στόχος απόστασης (χλμ)"
    exLogReps = "Καταγραφή"
    exDeductReps = "Αφαίρεση"
    exLogRepsTitle = { ex -> "Καταγραφή: $ex" }
    exProgressReps = { cur, goal -> "$cur / $goal επαν." }
    exProgressKm = { cur, goal -> "$cur / $goal χλμ" }
    exDone = "Ο στόχος επιτεύχθηκε! 🎉"
    exAutoTracked = "Αυτόματη παρακολούθηση από τις δραστηριότητές σας"
    exLogAmountHint = "Επαναλήψεις (χρήση - για αφαίρεση)"
    exCongratsTitle = "🎉 Ο στόχος κατακτήθηκε!"
    exCongratsBody = { ex -> "Ολοκλήρωσες τον στόχο σου: $ex. Μπράβο!" }
    exPersonalTargets = "Προσωπικοί Στόχοι"
    exPersonalTargetsEmpty = "Δεν υπάρχουν ολοκληρωμένοι στόχοι ακόμη. Πέτυχε έναν στόχο και θα καταγραφεί εδώ με την ημερομηνία."
    exAchievedOn = { date -> "Επιτεύχθηκε $date" }
    exTargetSummary = { amount, type, period -> "$amount $type · $period" }
    exAddAnother = "Προσθήκη άλλου"
    exSaveAll = { n -> if (n == 1) "Αποθήκευση 1 στόχου" else "Αποθήκευση $n στόχων" }
    exStagedCount = { n -> if (n == 1) "1 στόχος προς προσθήκη" else "$n στόχοι προς προσθήκη" }
    exBadgePersonal = "🎯 Προσωπικοί"
    exBadgeAchievements = "🏆 Μετάλλια"
    exGroupBy = "Ομαδοποίηση"
    exGroupDay = "Ημέρα"
    exGroupWeek = "Εβδομάδα"
    exGroupMonth = "Μήνας"
    exWeekOf = { date -> "Εβδομάδα $date" }
    // ── App section / update check (v1.47) ────────────────────────────────────
    appSection = "Εφαρμογή"
    appVersionLabel = { v -> "Έκδοση $v" }
    checkForUpdates = "Έλεγχος για ενημερώσεις"
    checkingForUpdates = "Έλεγχος για ενημερώσεις…"
    upToDate = "Έχετε την πιο πρόσφατη έκδοση"
    updateAvailable = "Διαθέσιμη ενημέρωση"
    updateVersionAvailable = { v -> "Διαθέσιμη έκδοση $v" }
    updateAction = "Ενημέρωση"
    updateLater = "Αργότερα"
    updateCheckFailed = "Δεν ήταν δυνατός ο έλεγχος για ενημερώσεις"
}

// ── Resolver ──────────────────────────────────────────────────────────────────

fun stringsFor(lang: Language): AppStrings = when (lang) {
    Language.ENGLISH -> EnglishStrings
    Language.GREEK   -> GreekStrings
}

// ── Enum → localized label helpers ──────────────────────────────────────────────

fun categoryLabel(s: AppStrings, cat: AchievementCategory): String = when (cat) {
    AchievementCategory.WALKING -> s.catWalking
    AchievementCategory.RUNNING -> s.catRunning
    AchievementCategory.CYCLING -> s.catCycling
    AchievementCategory.STREAKS -> s.catStreaks
    AchievementCategory.SPECIAL -> s.catSpecial
}

fun tierLabel(s: AppStrings, tier: AchievementTier): String = when (tier) {
    AchievementTier.BRONZE  -> s.tierBronze
    AchievementTier.SILVER  -> s.tierSilver
    AchievementTier.GOLD    -> s.tierGold
    AchievementTier.SPECIAL -> s.tierSpecial
}

fun exerciseTypeLabel(s: AppStrings, type: ExerciseType): String = when (type) {
    ExerciseType.SITUPS        -> s.exTypeSitups
    ExerciseType.PUSHUPS       -> s.exTypePushups
    ExerciseType.SQUATS        -> s.exTypeSquats
    ExerciseType.BICEP_CURLS   -> s.exTypeBicepCurls
    ExerciseType.TRICEP_DIPS   -> s.exTypeTricepDips
    ExerciseType.LUNGES        -> s.exTypeLunges
    ExerciseType.CRUNCHES      -> s.exTypeCrunches
    ExerciseType.BURPEES       -> s.exTypeBurpees
    ExerciseType.JUMPING_JACKS -> s.exTypeJumpingJacks
    ExerciseType.PLANK_SECONDS -> s.exTypePlank
    ExerciseType.WALKING       -> s.exTypeWalking
    ExerciseType.RUNNING       -> s.exTypeRunning
}

fun periodLabel(s: AppStrings, period: TargetPeriod): String = when (period) {
    TargetPeriod.DAILY   -> s.exPeriodDaily
    TargetPeriod.WEEKLY  -> s.exPeriodWeekly
    TargetPeriod.MONTHLY -> s.exPeriodMonthly
}

// CompositionLocal so any composable can read the current language's strings
val LocalStrings = staticCompositionLocalOf { EnglishStrings }

// CompositionLocal for the current Language enum (needed for achievement data lookups)
val LocalLanguage = staticCompositionLocalOf { Language.ENGLISH }

// ── Persistence ───────────────────────────────────────────────────────────────

fun loadLanguage(context: Context): Language {
    val prefs = context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
    val code = prefs.getString("app_language", Language.ENGLISH.code) ?: Language.ENGLISH.code
    return Language.values().firstOrNull { it.code == code } ?: Language.ENGLISH
}

fun saveLanguage(context: Context, lang: Language) {
    context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
        .edit().putString("app_language", lang.code).apply()
}

// ── Localized achievement titles + descriptions ─────────────────────────────────
// Keyed by achievement id. English falls back to the values baked into Achievements.kt.

fun localizedAchievementTitle(lang: Language, id: String, fallback: String): String {
    if (lang == Language.ENGLISH) return fallback
    return GreekAchievementTitles[id] ?: fallback
}

fun localizedAchievementDescription(lang: Language, id: String, fallback: String): String {
    if (lang == Language.ENGLISH) return fallback
    return GreekAchievementDescriptions[id] ?: fallback
}

private val GreekAchievementTitles = mapOf(
    "walk_first" to "Πρώτα Βήματα",
    "walk_5total" to "Περιπατητής",
    "walk_5day" to "Καθημερινός Εξερευνητής",
    "walk_5single" to "Σταθερή Βόλτα",
    "walk_10day" to "Δέκα Χιλιόμετρα την Ημέρα",
    "walk_10single" to "Μαραθωνοδρόμος Περπατήματος",
    "walk_50total" to "Πρωτοπόρος Μονοπατιών",
    "walk_early" to "Πρωινό Πουλί",
    "walk_night" to "Νυχτοπούλι",
    "run_first" to "Έξω από τον Καναπέ",
    "run_5total" to "Προθέρμανση",
    "run_5single" to "Τερματισμός 5 χλμ",
    "run_5day" to "Καθημερινός Δρομέας",
    "run_10single" to "Δαμαστής των 10 χλμ",
    "run_10day" to "Διψήφια Νούμερα",
    "run_21single" to "Ήρωας Ημιμαραθωνίου",
    "run_100total" to "Λέσχη των Εκατό",
    "run_speed" to "Δαίμονας Ταχύτητας",
    "run_streak3" to "Σταθερός Δρομέας",
    "cycle_first" to "Πρώτη Βόλτα",
    "cycle_10total" to "Ποδηλάτης",
    "cycle_20single" to "Διαδρομή 20 χλμ",
    "cycle_20day" to "Καθημερινός Ποδηλάτης",
    "cycle_50single" to "Πολεμιστής των 50 χλμ",
    "cycle_50day" to "Σιδερένια Πόδια",
    "cycle_100total" to "Ποδηλάτης των Εκατό",
    "cycle_hill" to "Αναρριχητής Λόφων",
    "cycle_speed" to "Αγωνιστής Ταχύτητας",
    "streak_2" to "Ξεκίνημα",
    "streak_7" to "Πολεμιστής της Εβδομάδας",
    "streak_14" to "Μαχητής των Δεκαπέντε",
    "streak_30" to "Μηχανή του Μήνα",
    "streak_comeback" to "Παιδί της Επιστροφής",
    "special_social" to "Κοινωνική Πεταλούδα",
    "special_explore" to "Εξερευνητής",
    "special_allround" to "Πλήρης Αθλητής",
    "special_rain" to "Βροχή ή Λιακάδα",
    "special_sunrise" to "Από την Ανατολή στη Δύση",
)

private val GreekAchievementDescriptions = mapOf(
    "walk_first" to "Περπατήστε 1 χλμ σε μία δραστηριότητα",
    "walk_5total" to "Περπατήστε 5 χλμ συνολικά (σωρευτικά)",
    "walk_5day" to "Περπατήστε 5 χλμ σε μία ημέρα",
    "walk_5single" to "Περπατήστε 5 χλμ σε μία δραστηριότητα",
    "walk_10day" to "Περπατήστε 10 χλμ σε μία ημέρα",
    "walk_10single" to "Περπατήστε 10 χλμ σε μία δραστηριότητα",
    "walk_50total" to "Περπατήστε 50 χλμ συνολικά (σωρευτικά)",
    "walk_early" to "Ολοκληρώστε περπάτημα πριν τις 7:00 π.μ.",
    "walk_night" to "Ολοκληρώστε περπάτημα μετά τις 9:00 μ.μ.",
    "run_first" to "Τρέξτε 1 χλμ σε μία δραστηριότητα",
    "run_5total" to "Τρέξτε 5 χλμ συνολικά (σωρευτικά)",
    "run_5single" to "Τρέξτε 5 χλμ σε μία δραστηριότητα",
    "run_5day" to "Τρέξτε 5 χλμ σε μία ημέρα",
    "run_10single" to "Τρέξτε 10 χλμ σε μία δραστηριότητα",
    "run_10day" to "Τρέξτε 10 χλμ σε μία ημέρα",
    "run_21single" to "Τρέξτε 21,1 χλμ σε μία δραστηριότητα",
    "run_100total" to "Τρέξτε 100 χλμ συνολικά (σωρευτικά)",
    "run_speed" to "Τρέξτε 1 χλμ σε λιγότερο από 5 λεπτά",
    "run_streak3" to "Τρέξτε 3 ημέρες στη σειρά",
    "cycle_first" to "Κάντε ποδήλατο 2 χλμ σε μία δραστηριότητα",
    "cycle_10total" to "Κάντε ποδήλατο 10 χλμ συνολικά (σωρευτικά)",
    "cycle_20single" to "Κάντε ποδήλατο 20 χλμ σε μία δραστηριότητα",
    "cycle_20day" to "Κάντε ποδήλατο 20 χλμ σε μία ημέρα",
    "cycle_50single" to "Κάντε ποδήλατο 50 χλμ σε μία δραστηριότητα",
    "cycle_50day" to "Κάντε ποδήλατο 50 χλμ σε μία ημέρα",
    "cycle_100total" to "Κάντε ποδήλατο 100 χλμ συνολικά (σωρευτικά)",
    "cycle_hill" to "Ποδηλατήστε με υψομετρική διαφορά 500μ+ σε μία δραστηριότητα",
    "cycle_speed" to "Κάντε ποδήλατο 1 χλμ σε λιγότερο από 2 λεπτά",
    "streak_2" to "Δραστήριοι 2 ημέρες στη σειρά",
    "streak_7" to "Δραστήριοι 7 ημέρες στη σειρά",
    "streak_14" to "Δραστήριοι 14 ημέρες στη σειρά",
    "streak_30" to "Δραστήριοι 30 ημέρες στη σειρά",
    "streak_comeback" to "Επιστροφή μετά από 7+ ημέρες αδράνειας",
    "special_social" to "Μοιραστείτε μια δραστηριότητα στα μέσα κοινωνικής δικτύωσης",
    "special_explore" to "Ολοκληρώστε δραστηριότητες σε 3 διαφορετικές τοποθεσίες",
    "special_allround" to "Περπάτημα, τρέξιμο ΚΑΙ ποδήλατο σε μία εβδομάδα",
    "special_rain" to "Ολοκληρώστε μια υπαίθρια δραστηριότητα στη βροχή",
    "special_sunrise" to "Δραστηριότητα πριν τις 8 π.μ. και μετά τις 6 μ.μ. την ίδια ημέρα",
)
