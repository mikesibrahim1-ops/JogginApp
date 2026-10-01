package com.example.joggingapp

import android.content.Context

/**
 * Single access point for the [BuddyRepository]. The rest of the app asks here for
 * the repository so there is exactly ONE place to swap the backend implementation.
 *
 * Currently returns [NoOpBuddyRepository] (inert) because Firebase is not yet
 * configured. Once `app/google-services.json` exists and the Firebase deps are added,
 * this returns `FirebaseBuddyRepository` instead — no other code changes needed.
 */
object BuddyRepositoryProvider {

    @Volatile private var instance: BuddyRepository? = null

    fun get(context: Context): BuddyRepository {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(appContext: Context): BuddyRepository {
        // When Firebase is configured (google-services.json present at build time),
        // BuildConfig.FIREBASE_ENABLED is true and FirebaseBuddyRepository is compiled
        // into the app (from the gated src/firebase source root). We load it via
        // reflection so this file never hard-references a Firebase class when the
        // feature is compiled out — keeping the app buildable without any Firebase
        // config. Otherwise we fall back to the inert NoOp stub (Req 10).
        if (BuildConfig.FIREBASE_ENABLED) {
            try {
                val cls = Class.forName("com.example.joggingapp.FirebaseBuddyRepository")
                val ctor = cls.getConstructor(Context::class.java)
                return ctor.newInstance(appContext) as BuddyRepository
            } catch (t: Throwable) {
                AppLogger.log(appContext, LogCategory.ERROR,
                    "Firebase enabled but FirebaseBuddyRepository unavailable: ${t.message}")
            }
        }
        return NoOpBuddyRepository
    }

    /** For tests / future backend swap. */
    fun override(repo: BuddyRepository) { instance = repo }
}
