package com.example.joggingapp

import android.net.Uri

/**
 * Buddy Link invite-link handling (Req 2.4, 3.1, 3.5, 3.5b).
 *
 * A user shares an invite link (via WhatsApp or any app) encoding their App ID and an
 * expiry timestamp. The recipient opens it to initiate a (pending) link request.
 *
 * Link format:
 *   joggin://buddy/v1/<appId>?exp=<epochMillis>
 * with an https fallback that carries the same data as query params:
 *   https://joggin-a69a7.web.app/buddy?id=<appId>&exp=<epochMillis>
 *
 * Both forms are accepted by [parse]. Invite links expire after 7 days (INVITE_EXPIRY).
 */
object BuddyInvite {

    /** Result of parsing an invite link. */
    sealed class ParseResult {
        data class Valid(val appId: String, val expiresAt: Long) : ParseResult()
        object Malformed : ParseResult()   // not a recognisable invite link / no id
        object Expired : ParseResult()      // well-formed but past its expiry
    }

    /** Builds an invite link (custom-scheme form) for the given App ID. */
    fun buildInviteUri(appId: String, nowMs: Long = System.currentTimeMillis()): String {
        val exp = nowMs + BuddyConstants.INVITE_EXPIRY_MS
        return "${BuddyConstants.INVITE_SCHEME}://${BuddyConstants.INVITE_PATH_PREFIX}/$appId?exp=$exp"
    }

    /** Builds the https fallback link (tappable even before the app is installed). */
    fun buildHttpsFallback(appId: String, nowMs: Long = System.currentTimeMillis()): String {
        val exp = nowMs + BuddyConstants.INVITE_EXPIRY_MS
        return "${BuddyConstants.INVITE_HTTPS_FALLBACK}?id=$appId&exp=$exp"
    }

    /**
     * A shareable message containing the invite link, for the share sheet.
     */
    fun buildShareText(appId: String, userName: String): String {
        val name = userName.ifBlank { "A friend" }
        val link = buildHttpsFallback(appId)
        return "$name wants to link with you on Joggin as a safety buddy.\n" +
               "Open this link in the Joggin app to connect:\n$link"
    }

    /**
     * Parses an invite link (either scheme form) and validates it.
     * Returns [ParseResult.Valid] only for a well-formed, non-expired link.
     */
    fun parse(raw: String, nowMs: Long = System.currentTimeMillis()): ParseResult {
        val uri = try { Uri.parse(raw.trim()) } catch (_: Exception) { return ParseResult.Malformed }

        val appId: String?
        when (uri.scheme?.lowercase()) {
            BuddyConstants.INVITE_SCHEME -> {
                // joggin://buddy/v1/<appId>?exp=...
                val segments = uri.pathSegments  // for joggin://buddy/v1/<id>, host=buddy
                // host is "buddy", path is "/v1/<id>"
                appId = when {
                    uri.host == "buddy" && segments.size >= 2 && segments[0] == "v1" -> segments[1]
                    else -> null
                }
            }
            "https", "http" -> {
                // https://joggin-a69a7.web.app/buddy?id=<appId>&exp=...
                appId = uri.getQueryParameter("id")
            }
            else -> return ParseResult.Malformed
        }

        if (appId.isNullOrBlank()) return ParseResult.Malformed

        val exp = uri.getQueryParameter("exp")?.toLongOrNull()
        // If no expiry present, treat as malformed (all our links carry one).
        if (exp == null) return ParseResult.Malformed
        if (nowMs > exp) return ParseResult.Expired

        return ParseResult.Valid(appId = appId, expiresAt = exp)
    }
}
