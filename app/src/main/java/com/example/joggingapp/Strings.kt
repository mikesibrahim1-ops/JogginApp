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

data class AppStrings(
    // Name entry
    val welcomeTitle: String,
    val whatsYourName: String,
    val enterYourName: String,
    val letsGo: String,
    // Tracker controls
    val hintAchievements: String,
    val hintOptions: String,
    val statTime: String,
    val statDistanceKm: String,
    val statKmh: String,
    val resume: String,
    val pause: String,
    val endRun: String,
    // Foreground-service notification labels
    val notifRunning: String,
    val notifPaused: String,
    val notifChannelName: String,
    val steps: String,
    // Activity modes
    val walk: String,
    val run: String,
    val cycle: String,
    // Past runs page
    val pastRuns: String,
    val newest: String,
    val oldest: String,
    val noRunsYet: String,
    val pastRunsCount: (Int) -> String,   // "3 past runs"
    // Welcome bubble
    val welcomeUser: (String) -> String,  // "Welcome, X 👋"
    // History card
    val noGpsData: String,
    val timerBasedActivity: String,
    val today: String,
    val duration: String,
    val km: String,
    val kcal: String,
    val na: String,
    val stepsCount: (Int) -> String,       // "👟 N steps"
    val share: String,
    val remove: String,
    // Editable run title
    val untitledRun: String,               // default title when none set
    val editTitle: String,                 // accessibility / hint for the pencil
    val titlePlaceholder: String,          // TextField placeholder
    // Summary screen
    val complete: (String) -> String,      // "Run Complete"
    val time: String,
    val distance: String,
    val avgSpeed: String,
    val maxSpeed: String,
    val minSpeed: String,
    val calories: String,
    val done: String,
    // Map markers
    val start: String,
    val finish: String,
    // Options pane — headers
    val options: String,
    val appTheme: String,
    val routeColour: String,
    val sharing: String,
    val includeMapWhenSharing: String,
    val recentRuns: String,
    val clearAllRuns: String,
    val shareApp: String,
    val shareAppWithFriends: String,
    val shareAppSubtitle: String,
    val backupRuns: String,
    val backupRunsSubtitle: String,
    val restoreRuns: String,
    val restoreRunsSubtitle: String,
    val restoredCount: (Int) -> String,    // "Restored N run(s)"
    val restoreFailed: String,
    // Route colour names
    val colorLightGreen: String,
    val colorBlue: String,
    val colorOrange: String,
    val colorRed: String,
    val colorPurple: String,
    val colorWhite: String,
    // Profile
    val myProfile: String,
    val profilePicture: String,
    val tapToChangePhoto: String,
    val rotatePhoto: String,
    val yourName: String,
    val save: String,
    val cancel: String,
    val tapToSetName: String,
    // Language
    val language: String,
    // Diagnostics
    val diagnostics: String,
    val all: String,
    val noLogEntries: String,
    val shareLog: String,
    val clearLog: String,
    // Achievements screen
    val myAchievements: String,
    val percentComplete: (Int) -> String,          // "42% complete"
    val progressCount: (Int, Int) -> String,       // "5 / 37"
    val completedMedals: (Int) -> String,          // "🏅 Completed Medals (N)"
    val earnedOn: (String) -> String,              // "✓ Earned MMM dd"
    val dismiss: String,
    val sortLabel: String,
    val sortDefault: String,
    val sortAlmostDone: String,
    val sortEarned: String,
    val sortOutstanding: String,
    val trophyCaseWaiting: String,
    val completeFirstActivity: String,
    val startAnActivity: String,
    val unitKm: String,
    val unitDays: String,
    // Category labels
    val catAll: String,
    val catWalking: String,
    val catRunning: String,
    val catCycling: String,
    val catStreaks: String,
    val catSpecial: String,
    // Tier labels
    val tierBronze: String,
    val tierSilver: String,
    val tierGold: String,
    val tierSpecial: String,
    // Motivational quotes
    val motivations: List<String>,
    // Toasts
    val noRunsToBackup: String,
    val inviteCopied: String,
    // ── Buddy Link ──────────────────────────────────────────────────────────
    val buddyLink: String,                 // section title
    val buddyLinkEnable: String,           // master toggle label
    val buddyLinkConsentTitle: String,
    val buddyLinkConsentBody: String,      // what's shared / with whom / how to stop
    val buddyLinkConsentAgree: String,
    val buddyLinkConsentDecline: String,
    val buddyInvite: String,               // "Invite Buddy"
    val buddyInviteShare: String,          // share-sheet chooser title
    val buddyBuddies: String,              // "Buddies"
    val buddyPending: String,              // "Pending requests"
    val buddyBlocked: String,              // "Blocked"
    val buddyAccept: String,
    val buddyDecline: String,
    val buddyRemove: String,
    val buddyBlock: String,
    val buddyUnblock: String,
    val buddyRemoveConfirm: String,        // confirm unlink
    val buddyBlockConfirm: String,         // confirm block
    val buddyDeleteData: String,           // "Delete my Buddy Link data"
    val buddyDeleteConfirm: String,        // confirm delete-all
    val buddyHistoryShow: String,          // "Show history" toggle label
    val buddyHistoryHide: String,          // "Hide history" toggle label
    val buddyStatusSharing: String,        // "Sharing"
    val buddyStatusUnavailable: String,    // "Unavailable"
    val buddyStatusPending: String,        // "Waiting to accept"
    val buddyUpdatedAgo: (String) -> String, // "updated {x} ago"
    val buddyRequestLocation: String,      // "Request location now"
    val buddyBroadcastOn: String,          // "Buddy Link active — broadcast on"
    val buddyEyesOnYou: String,            // eye-indicator label / tooltip
    val buddyUnlinkedWarning: (String) -> String, // "{name} stopped sharing"
    val buddyPermissionNeeded: String,     // background-location required note
    val buddyEventsChannelName: String,    // notification channel for social events
    val buddyNotifRequestTitle: String,    // "New buddy request"
    val buddyNotifRequestBody: String,     // "Someone wants to link with you"
    val buddyNotifAcceptedTitle: String,   // "Buddy request accepted"
    val buddyNotifAcceptedBody: (String) -> String, // "{name} is now your buddy"
    val buddyNotifUnlinkedTitle: String,   // "Buddy unlinked"
    val buddyToggleDesc: (Boolean) -> String,   // a11y: "Buddy Link, on/off. Double-tap to toggle."
    val buddyActionDesc: (String, String) -> String, // a11y: "{action} {name}"
    val buddyRequestCooldown: String,      // "Requested — please wait" (rate-limit)
    val buddyEventsLowChannelName: String, // low-importance channel name
    val buddyNotifLocationSharedTitle: String, // "Location shared"
    val buddyNotifLocationSharedBody: String,  // "A buddy requested your location just now"
    val buddyLinkExpired: String,          // "Invite expired — ask for a new one"
    val buddyLinkInvalid: String,          // malformed / unknown id
    val buddyUnavailableBackend: String,   // backend unreachable, non-blocking
    val buddyChannelName: String,          // notification channel name for broadcast service
    val buddyJustNow: String,              // "updated just now" (fresh fix)
    val buddyMinutesAgo: (Int) -> String,  // "updated N min ago"
    // ── Exercise Targets ──────────────────────────────────────────────────────
    val exerciseTargets: String,           // section / screen title
    val exerciseTargetsSubtitle: String,   // options-pane row subtitle
    val exerciseTargetsEmpty: String,      // empty-state message
    val exTypeSitups: String,
    val exTypePushups: String,
    val exTypeSquats: String,
    val exTypeWalking: String,
    val exTypeRunning: String,
    val exPeriodDaily: String,
    val exPeriodWeekly: String,
    val exPeriodMonthly: String,
    val exAddTarget: String,               // "Add target" button
    val exNewTarget: String,               // add-form heading
    val exExerciseLabel: String,           // "Exercise"
    val exPeriodLabel: String,             // "Period"
    val exAmountReps: String,              // amount field label for manual types
    val exAmountKm: String,                // amount field label for distance types
    val exLogReps: String,                 // "Log" button on a manual target
    val exLogRepsTitle: (String) -> String,// dialog title "Log {exercise}"
    val exProgressReps: (Int, Int) -> String,   // "{cur} / {goal} reps"
    val exProgressKm: (String, String) -> String,// "{cur} / {goal} km"
    val exDone: String,                    // "Goal met!" badge
    val exDelete: String,                  // delete a target
    val exAdd: String,                     // confirm add in the form
    val exAutoTracked: String,             // note under distance targets
    val exLogAmountHint: String,           // placeholder in the log dialog ("Reps done")
    val exCongratsTitle: String,           // completion celebration title
    val exCongratsBody: (String) -> String,// "You hit your {exercise} target!"
    val exPersonalTargets: String,         // "Personal Targets" tab/section title
    val exPersonalTargetsEmpty: String,    // empty state for achieved list
    val exAchievedOn: (String) -> String,  // "Achieved {date}"
    val exTargetSummary: (String, String, String) -> String, // "{amount} {type} · {period}"
    val exAddAnother: String,              // batch form: stage this row + start another
    val exSaveAll: (Int) -> String,        // "Save N targets"
    val exStagedCount: (Int) -> String,    // "N target(s) to add"
    val exBadgePersonal: String,           // Achievements badge → personal targets view
    val exBadgeAchievements: String,       // badge back to achievements view
    val exGroupBy: String,                 // "Group by" label on the personal-targets sort control
    val exGroupDay: String,                // group-by option: Day
    val exGroupWeek: String,               // group-by option: Week
    val exGroupMonth: String,              // group-by option: Month
    val exWeekOf: (String) -> String,      // weekly bucket header, "Week of {date}"
)

// ── English ───────────────────────────────────────────────────────────────────

val EnglishStrings = AppStrings(
    welcomeTitle = "👋 Welcome to Joggin!",
    whatsYourName = "What's your name?",
    enterYourName = "Enter your name",
    letsGo = "Let's go!",
    hintAchievements = "⟵ achievements",
    hintOptions = "options ⟶",
    statTime = "Time",
    statDistanceKm = "Dist (km)",
    statKmh = "km/h",
    resume = "Resume",
    pause = "Pause",
    endRun = "End Run",
    notifRunning = "Joggin",
    notifPaused = "Paused",
    notifChannelName = "Joggin Tracking",
    steps = "steps",
    walk = "Walk",
    run = "Run",
    cycle = "Cycle",
    pastRuns = "Past Runs",
    newest = "Newest",
    oldest = "Oldest",
    noRunsYet = "No runs yet.\nComplete your first activity!",
    pastRunsCount = { n -> "$n past runs" },
    welcomeUser = { name -> "Welcome, $name 👋" },
    noGpsData = "No GPS data",
    timerBasedActivity = "Timer-based activity",
    today = "Today",
    duration = "Duration",
    km = "km",
    kcal = "kcal",
    na = "N/A",
    stepsCount = { n -> "👟 $n steps" },
    share = "Share",
    remove = "Remove",
    untitledRun = "Untitled run",
    editTitle = "Edit title",
    titlePlaceholder = "Name this run",
    complete = { label -> "$label Complete" },
    time = "Time",
    distance = "Distance",
    avgSpeed = "Avg Speed",
    maxSpeed = "Max Speed",
    minSpeed = "Min Speed",
    calories = "Calories",
    done = "Done",
    start = "Start",
    finish = "Finish",
    options = "Options",
    appTheme = "App Theme",
    routeColour = "Route colour",
    sharing = "Sharing",
    includeMapWhenSharing = "Include map when sharing",
    recentRuns = "Recent runs",
    clearAllRuns = "Clear all runs",
    shareApp = "Share App",
    shareAppWithFriends = "Share Joggin with friends",
    shareAppSubtitle = "Send a download link via WhatsApp, SMS, etc.",
    backupRuns = "Backup Runs",
    backupRunsSubtitle = "Export all runs as a JSON file for recovery",
    restoreRuns = "Restore Runs",
    restoreRunsSubtitle = "Pick a backup file to import runs",
    restoredCount = { n -> "Restored $n run(s)" },
    restoreFailed = "Failed to restore — invalid file",
    colorLightGreen = "Light Green",
    colorBlue = "Blue",
    colorOrange = "Orange",
    colorRed = "Red",
    colorPurple = "Purple",
    colorWhite = "White",
    myProfile = "My Profile",
    profilePicture = "Profile picture",
    tapToChangePhoto = "Tap to change photo",
    rotatePhoto = "Rotate",
    yourName = "Your name",
    save = "Save",
    cancel = "Cancel",
    tapToSetName = "Tap to set your name",
    language = "Language",
    diagnostics = "Diagnostics",
    all = "All",
    noLogEntries = "No log entries yet",
    shareLog = "📤 Share Log",
    clearLog = "🗑 Clear Log",
    myAchievements = "My Achievements",
    percentComplete = { p -> "$p% complete" },
    progressCount = { e, t -> "$e / $t" },
    completedMedals = { n -> "🏅 Completed Medals ($n)" },
    earnedOn = { date -> "✓ Earned $date" },
    dismiss = "Dismiss",
    sortLabel = "Sort:",
    sortDefault = "Default",
    sortAlmostDone = "Almost Done",
    sortEarned = "Earned",
    sortOutstanding = "Outstanding",
    trophyCaseWaiting = "Your trophy case is waiting!",
    completeFirstActivity = "Complete your first activity\nto start earning badges.",
    startAnActivity = "Start an Activity",
    unitKm = "km",
    unitDays = "days",
    catAll = "All",
    catWalking = "Walking",
    catRunning = "Running",
    catCycling = "Cycling",
    catStreaks = "Streaks",
    catSpecial = "Special",
    tierBronze = "BRONZE",
    tierSilver = "SILVER",
    tierGold = "GOLD",
    tierSpecial = "SPECIAL",
    motivations = listOf(
        "Keep going! You're crushing it! 🔥",
        "Every step counts — you're doing great! 💪",
        "Legends are made one activity at a time! ⚡",
        "Your next badge is just around the corner! 🏅",
    ),
    noRunsToBackup = "No runs to backup",
    inviteCopied = "Invite message copied — paste it with the file",
    // ── Buddy Link ──────────────────────────────────────────────────────────
    buddyLink = "Buddy Link",
    buddyLinkEnable = "Enable Buddy Link",
    buddyLinkConsentTitle = "Share your location with a buddy",
    buddyLinkConsentBody = "When on, your location is shared with a linked buddy so they can keep you safe — only while both of you have Buddy Link on and GPS enabled. Either of you can stop or unlink at any time.",
    buddyLinkConsentAgree = "I understand, enable",
    buddyLinkConsentDecline = "Not now",
    buddyInvite = "Invite Buddy",
    buddyInviteShare = "Share your Buddy Link invite",
    buddyBuddies = "Buddies",
    buddyPending = "Pending requests",
    buddyBlocked = "Blocked",
    buddyAccept = "Accept",
    buddyDecline = "Decline",
    buddyRemove = "Remove",
    buddyBlock = "Block",
    buddyUnblock = "Unblock",
    buddyRemoveConfirm = "Unlink this buddy? Location sharing stops for both of you.",
    buddyBlockConfirm = "Block this buddy? You'll be unlinked and they can't link with you again.",
    buddyDeleteData = "Delete my Buddy Link data",
    buddyDeleteConfirm = "Delete all your Buddy Link data (identity, links, shared location) and turn Buddy Link off? This can't be undone.",
    buddyHistoryShow = "Show history",
    buddyHistoryHide = "Hide history",
    buddyStatusSharing = "Sharing",
    buddyStatusUnavailable = "Unavailable",
    buddyStatusPending = "Waiting to accept",
    buddyUpdatedAgo = { ago -> "updated $ago ago" },
    buddyRequestLocation = "Request location now",
    buddyBroadcastOn = "Buddy Link active — broadcast on",
    buddyEyesOnYou = "A buddy can see your location",
    buddyUnlinkedWarning = { name -> "$name stopped sharing their location" },
    buddyPermissionNeeded = "Buddy Link needs background location permission to work. Enable it in settings to use this feature.",
    buddyEventsChannelName = "Buddy Link updates",
    buddyNotifRequestTitle = "New buddy request",
    buddyNotifRequestBody = "Someone wants to link with you on Buddy Link",
    buddyNotifAcceptedTitle = "Buddy request accepted",
    buddyNotifAcceptedBody = { name -> "$name is now your buddy" },
    buddyNotifUnlinkedTitle = "Buddy unlinked",
    buddyToggleDesc = { on -> "Buddy Link, ${if (on) "on" else "off"}. Double-tap to toggle." },
    buddyActionDesc = { action, name -> "$action $name" },
    buddyRequestCooldown = "Requested — please wait",
    buddyEventsLowChannelName = "Buddy Link activity",
    buddyNotifLocationSharedTitle = "Location shared",
    buddyNotifLocationSharedBody = "A buddy requested your location just now",
    buddyLinkExpired = "This invite has expired — ask your buddy for a new one",
    buddyLinkInvalid = "This invite link is not valid",
    buddyUnavailableBackend = "Buddy Link is temporarily unavailable",
    buddyChannelName = "Buddy Link",
    buddyJustNow = "updated just now",
    buddyMinutesAgo = { min -> "updated $min min ago" },
    exerciseTargets = "Exercise Targets",
    exerciseTargetsSubtitle = "Set at-home goals and track your progress",
    exerciseTargetsEmpty = "No targets yet. Add one to start tracking your at-home exercises and distance goals.",
    exTypeSitups = "Sit-ups",
    exTypePushups = "Push-ups",
    exTypeSquats = "Squats",
    exTypeWalking = "Walking",
    exTypeRunning = "Running",
    exPeriodDaily = "Daily",
    exPeriodWeekly = "Weekly",
    exPeriodMonthly = "Monthly",
    exAddTarget = "Add target",
    exNewTarget = "New target",
    exExerciseLabel = "Exercise",
    exPeriodLabel = "Period",
    exAmountReps = "Target reps",
    exAmountKm = "Target distance (km)",
    exLogReps = "Log",
    exLogRepsTitle = { ex -> "Log $ex" },
    exProgressReps = { cur, goal -> "$cur / $goal reps" },
    exProgressKm = { cur, goal -> "$cur / $goal km" },
    exDone = "Goal met! 🎉",
    exDelete = "Delete",
    exAdd = "Add",
    exAutoTracked = "Auto-tracked from your runs",
    exLogAmountHint = "How many did you do?",
    exCongratsTitle = "🎉 Target smashed!",
    exCongratsBody = { ex -> "You completed your $ex target. Nice work!" },
    exPersonalTargets = "Personal Targets",
    exPersonalTargetsEmpty = "No completed targets yet. Hit a target and it'll be recorded here with the date.",
    exAchievedOn = { date -> "Achieved $date" },
    exTargetSummary = { amount, type, period -> "$amount $type · $period" },
    exAddAnother = "Add another",
    exSaveAll = { n -> if (n == 1) "Save 1 target" else "Save $n targets" },
    exStagedCount = { n -> if (n == 1) "1 target to add" else "$n targets to add" },
    exBadgePersonal = "🎯 Personal",
    exBadgeAchievements = "🏆 Medals",
    exGroupBy = "Group by",
    exGroupDay = "Day",
    exGroupWeek = "Week",
    exGroupMonth = "Month",
    exWeekOf = { date -> "Week of $date" },
)

// ── Greek (formal Modern Greek, monotonic) ─────────────────────────────────────

val GreekStrings = AppStrings(
    welcomeTitle = "👋 Καλώς ήρθατε στο Joggin!",
    whatsYourName = "Πώς σας λένε;",
    enterYourName = "Εισαγάγετε το όνομά σας",
    letsGo = "Πάμε!",
    hintAchievements = "⟵ επιτεύγματα",
    hintOptions = "επιλογές ⟶",
    statTime = "Χρόνος",
    statDistanceKm = "Απόστ. (χλμ)",
    statKmh = "χλμ/ώρα",
    resume = "Συνέχεια",
    pause = "Παύση",
    endRun = "Τερματισμός",
    notifRunning = "Τζόγκιν",
    notifPaused = "Σε παύση",
    notifChannelName = "Παρακολούθηση Τζόγκιν",
    steps = "βήματα",
    walk = "Περπάτημα",
    run = "Τρέξιμο",
    cycle = "Ποδήλατο",
    pastRuns = "Προηγούμενες Δραστηριότητες",
    newest = "Νεότερες",
    oldest = "Παλαιότερες",
    noRunsYet = "Καμία δραστηριότητα ακόμη.\nΟλοκληρώστε την πρώτη σας δραστηριότητα!",
    pastRunsCount = { n -> "$n προηγούμενες δραστηριότητες" },
    welcomeUser = { name -> "Καλώς ήρθατε, $name 👋" },
    noGpsData = "Χωρίς δεδομένα GPS",
    timerBasedActivity = "Δραστηριότητα με χρονόμετρο",
    today = "Σήμερα",
    duration = "Διάρκεια",
    km = "χλμ",
    kcal = "θερμ.",
    na = "Μ/Δ",
    stepsCount = { n -> "👟 $n βήματα" },
    share = "Κοινοποίηση",
    remove = "Διαγραφή",
    untitledRun = "Δρομολόγιο χωρίς τίτλο",
    editTitle = "Επεξεργασία τίτλου",
    titlePlaceholder = "Ονομάστε αυτό το δρομολόγιο",
    complete = { label -> "$label — Ολοκληρώθηκε" },
    time = "Χρόνος",
    distance = "Απόσταση",
    avgSpeed = "Μέση Ταχύτητα",
    maxSpeed = "Μέγιστη Ταχύτητα",
    minSpeed = "Ελάχιστη Ταχύτητα",
    calories = "Θερμίδες",
    done = "Τέλος",
    start = "Αφετηρία",
    finish = "Τερματισμός",
    options = "Επιλογές",
    appTheme = "Θέμα Εφαρμογής",
    routeColour = "Χρώμα Διαδρομής",
    sharing = "Κοινοποίηση",
    includeMapWhenSharing = "Συμπερίληψη χάρτη στην κοινοποίηση",
    recentRuns = "Πρόσφατες Δραστηριότητες",
    clearAllRuns = "Διαγραφή όλων",
    shareApp = "Κοινοποίηση Εφαρμογής",
    shareAppWithFriends = "Μοιραστείτε το Joggin με φίλους",
    shareAppSubtitle = "Στείλτε σύνδεσμο λήψης μέσω WhatsApp, SMS κ.λπ.",
    backupRuns = "Αντίγραφο Ασφαλείας",
    backupRunsSubtitle = "Εξαγωγή όλων των δραστηριοτήτων σε αρχείο JSON",
    restoreRuns = "Επαναφορά Δραστηριοτήτων",
    restoreRunsSubtitle = "Επιλέξτε ένα αρχείο αντιγράφου για εισαγωγή",
    restoredCount = { n -> "Επαναφέρθηκαν $n δραστηριότητες" },
    restoreFailed = "Η επαναφορά απέτυχε — μη έγκυρο αρχείο",
    colorLightGreen = "Ανοιχτό Πράσινο",
    colorBlue = "Μπλε",
    colorOrange = "Πορτοκαλί",
    colorRed = "Κόκκινο",
    colorPurple = "Μοβ",
    colorWhite = "Λευκό",
    myProfile = "Το Προφίλ μου",
    profilePicture = "Φωτογραφία προφίλ",
    tapToChangePhoto = "Πατήστε για αλλαγή φωτογραφίας",
    rotatePhoto = "Περιστροφή",
    yourName = "Το όνομά σας",
    save = "Αποθήκευση",
    cancel = "Άκυρο",
    tapToSetName = "Πατήστε για να ορίσετε το όνομά σας",
    language = "Γλώσσα",
    diagnostics = "Διαγνωστικά",
    all = "Όλα",
    noLogEntries = "Δεν υπάρχουν καταχωρίσεις ακόμη",
    shareLog = "📤 Κοινοποίηση Αρχείου",
    clearLog = "🗑 Διαγραφή Αρχείου",
    myAchievements = "Τα Επιτεύγματά μου",
    percentComplete = { p -> "$p% ολοκληρώθηκε" },
    progressCount = { e, t -> "$e / $t" },
    completedMedals = { n -> "🏅 Ολοκληρωμένα Μετάλλια ($n)" },
    earnedOn = { date -> "✓ Κερδήθηκε $date" },
    dismiss = "Κλείσιμο",
    sortLabel = "Ταξινόμηση:",
    sortDefault = "Προεπιλογή",
    sortAlmostDone = "Σχεδόν Έτοιμα",
    sortEarned = "Κερδισμένα",
    sortOutstanding = "Εκκρεμή",
    trophyCaseWaiting = "Η προθήκη τροπαίων σας περιμένει!",
    completeFirstActivity = "Ολοκληρώστε την πρώτη σας δραστηριότητα\nγια να κερδίσετε διακριτικά.",
    startAnActivity = "Ξεκινήστε Δραστηριότητα",
    unitKm = "χλμ",
    unitDays = "ημέρες",
    catAll = "Όλα",
    catWalking = "Περπάτημα",
    catRunning = "Τρέξιμο",
    catCycling = "Ποδηλασία",
    catStreaks = "Σερί",
    catSpecial = "Ειδικά",
    tierBronze = "ΧΑΛΚΙΝΟ",
    tierSilver = "ΑΣΗΜΕΝΙΟ",
    tierGold = "ΧΡΥΣΟ",
    tierSpecial = "ΕΙΔΙΚΟ",
    motivations = listOf(
        "Συνεχίστε! Τα πάτε περίφημα! 🔥",
        "Κάθε βήμα μετράει — τα καταφέρνετε υπέροχα! 💪",
        "Οι θρύλοι δημιουργούνται μία δραστηριότητα τη φορά! ⚡",
        "Το επόμενο διακριτικό σας είναι πολύ κοντά! 🏅",
    ),
    noRunsToBackup = "Δεν υπάρχουν δραστηριότητες για αντίγραφο",
    inviteCopied = "Το μήνυμα πρόσκλησης αντιγράφηκε — επικολλήστε το με το αρχείο",
    // ── Buddy Link ──────────────────────────────────────────────────────────
    buddyLink = "Σύνδεσμος Φίλου",
    buddyLinkEnable = "Ενεργοποίηση Συνδέσμου Φίλου",
    buddyLinkConsentTitle = "Μοιραστείτε την τοποθεσία σας με έναν φίλο",
    buddyLinkConsentBody = "Όταν είναι ενεργό, η τοποθεσία σας μοιράζεται με έναν συνδεδεμένο φίλο για την ασφάλειά σας — μόνο όσο και οι δύο έχετε τον Σύνδεσμο Φίλου ενεργό και το GPS ανοιχτό. Ο καθένας μπορεί να το σταματήσει ή να αποσυνδεθεί ανά πάσα στιγμή.",
    buddyLinkConsentAgree = "Κατάλαβα, ενεργοποίηση",
    buddyLinkConsentDecline = "Όχι τώρα",
    buddyInvite = "Πρόσκληση Φίλου",
    buddyInviteShare = "Μοιραστείτε την πρόσκλησή σας",
    buddyBuddies = "Φίλοι",
    buddyPending = "Εκκρεμείς αιτήσεις",
    buddyBlocked = "Αποκλεισμένοι",
    buddyAccept = "Αποδοχή",
    buddyDecline = "Απόρριψη",
    buddyRemove = "Αφαίρεση",
    buddyBlock = "Αποκλεισμός",
    buddyUnblock = "Άρση αποκλεισμού",
    buddyRemoveConfirm = "Αποσύνδεση αυτού του φίλου; Η κοινή χρήση τοποθεσίας σταματά και για τους δύο.",
    buddyBlockConfirm = "Αποκλεισμός αυτού του φίλου; Θα αποσυνδεθείτε και δεν θα μπορεί να συνδεθεί ξανά μαζί σας.",
    buddyDeleteData = "Διαγραφή των δεδομένων μου Buddy Link",
    buddyDeleteConfirm = "Διαγραφή όλων των δεδομένων Buddy Link (ταυτότητα, συνδέσεις, κοινόχρηστη τοποθεσία) και απενεργοποίηση του Buddy Link; Δεν αναιρείται.",
    buddyHistoryShow = "Εμφάνιση ιστορικού",
    buddyHistoryHide = "Απόκρυψη ιστορικού",
    buddyStatusSharing = "Κοινή χρήση",
    buddyStatusUnavailable = "Μη διαθέσιμος",
    buddyStatusPending = "Αναμονή αποδοχής",
    buddyUpdatedAgo = { ago -> "ενημερώθηκε πριν $ago" },
    buddyRequestLocation = "Αίτηση τοποθεσίας τώρα",
    buddyBroadcastOn = "Σύνδεσμος Φίλου ενεργός — μετάδοση ενεργή",
    buddyEyesOnYou = "Ένας φίλος βλέπει την τοποθεσία σας",
    buddyUnlinkedWarning = { name -> "Ο/Η $name σταμάτησε την κοινή χρήση τοποθεσίας" },
    buddyPermissionNeeded = "Ο Σύνδεσμος Φίλου χρειάζεται άδεια τοποθεσίας στο παρασκήνιο. Ενεργοποιήστε την στις ρυθμίσεις για να χρησιμοποιήσετε αυτή τη λειτουργία.",
    buddyEventsChannelName = "Ενημερώσεις Συνδέσμου Φίλου",
    buddyNotifRequestTitle = "Νέο αίτημα φίλου",
    buddyNotifRequestBody = "Κάποιος θέλει να συνδεθεί μαζί σας στον Σύνδεσμο Φίλου",
    buddyNotifAcceptedTitle = "Το αίτημα φίλου έγινε αποδεκτό",
    buddyNotifAcceptedBody = { name -> "Ο/Η $name είναι πλέον φίλος σας" },
    buddyNotifUnlinkedTitle = "Αποσύνδεση φίλου",
    buddyToggleDesc = { on -> "Σύνδεσμος Φίλου, ${if (on) "ενεργός" else "ανενεργός"}. Πατήστε δύο φορές για εναλλαγή." },
    buddyActionDesc = { action, name -> "$action $name" },
    buddyRequestCooldown = "Ζητήθηκε — περιμένετε",
    buddyEventsLowChannelName = "Δραστηριότητα Συνδέσμου Φίλου",
    buddyNotifLocationSharedTitle = "Κοινοποίηση τοποθεσίας",
    buddyNotifLocationSharedBody = "Ένας φίλος ζήτησε την τοποθεσία σας μόλις τώρα",
    buddyLinkExpired = "Αυτή η πρόσκληση έληξε — ζητήστε μια νέα από τον φίλο σας",
    buddyLinkInvalid = "Αυτός ο σύνδεσμος πρόσκλησης δεν είναι έγκυρος",
    buddyUnavailableBackend = "Ο Σύνδεσμος Φίλου δεν είναι προσωρινά διαθέσιμος",
    buddyChannelName = "Σύνδεσμος Φίλου",
    buddyJustNow = "ενημερώθηκε μόλις τώρα",
    buddyMinutesAgo = { min -> "ενημερώθηκε πριν $min λεπτά" },
    exerciseTargets = "Στόχοι Άσκησης",
    exerciseTargetsSubtitle = "Ορίστε στόχους στο σπίτι και παρακολουθήστε την πρόοδό σας",
    exerciseTargetsEmpty = "Δεν υπάρχουν στόχοι ακόμη. Προσθέστε έναν για να παρακολουθείτε τις ασκήσεις σας και τους στόχους απόστασης.",
    exTypeSitups = "Κοιλιακοί",
    exTypePushups = "Κάμψεις",
    exTypeSquats = "Καθίσματα",
    exTypeWalking = "Περπάτημα",
    exTypeRunning = "Τρέξιμο",
    exPeriodDaily = "Ημερήσιο",
    exPeriodWeekly = "Εβδομαδιαίο",
    exPeriodMonthly = "Μηνιαίο",
    exAddTarget = "Προσθήκη στόχου",
    exNewTarget = "Νέος στόχος",
    exExerciseLabel = "Άσκηση",
    exPeriodLabel = "Περίοδος",
    exAmountReps = "Στόχος επαναλήψεων",
    exAmountKm = "Στόχος απόστασης (χλμ)",
    exLogReps = "Καταγραφή",
    exLogRepsTitle = { ex -> "Καταγραφή: $ex" },
    exProgressReps = { cur, goal -> "$cur / $goal επαν." },
    exProgressKm = { cur, goal -> "$cur / $goal χλμ" },
    exDone = "Ο στόχος επιτεύχθηκε! 🎉",
    exDelete = "Διαγραφή",
    exAdd = "Προσθήκη",
    exAutoTracked = "Αυτόματη παρακολούθηση από τις δραστηριότητές σας",
    exLogAmountHint = "Πόσες έκανες;",
    exCongratsTitle = "🎉 Ο στόχος κατακτήθηκε!",
    exCongratsBody = { ex -> "Ολοκλήρωσες τον στόχο σου: $ex. Μπράβο!" },
    exPersonalTargets = "Προσωπικοί Στόχοι",
    exPersonalTargetsEmpty = "Δεν υπάρχουν ολοκληρωμένοι στόχοι ακόμη. Πέτυχε έναν στόχο και θα καταγραφεί εδώ με την ημερομηνία.",
    exAchievedOn = { date -> "Επιτεύχθηκε $date" },
    exTargetSummary = { amount, type, period -> "$amount $type · $period" },
    exAddAnother = "Προσθήκη άλλου",
    exSaveAll = { n -> if (n == 1) "Αποθήκευση 1 στόχου" else "Αποθήκευση $n στόχων" },
    exStagedCount = { n -> if (n == 1) "1 στόχος προς προσθήκη" else "$n στόχοι προς προσθήκη" },
    exBadgePersonal = "🎯 Προσωπικοί",
    exBadgeAchievements = "🏆 Μετάλλια",
    exGroupBy = "Ομαδοποίηση",
    exGroupDay = "Ημέρα",
    exGroupWeek = "Εβδομάδα",
    exGroupMonth = "Μήνας",
    exWeekOf = { date -> "Εβδομάδα $date" },
)

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
    ExerciseType.SITUPS  -> s.exTypeSitups
    ExerciseType.PUSHUPS -> s.exTypePushups
    ExerciseType.SQUATS  -> s.exTypeSquats
    ExerciseType.WALKING -> s.exTypeWalking
    ExerciseType.RUNNING -> s.exTypeRunning
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
