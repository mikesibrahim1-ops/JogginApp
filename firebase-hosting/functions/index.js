/**
 * Joggin Buddy Link — server-side push (task 7.3, design §12).
 *
 * Sends an FCM *data* message to the affected party when a `links/{linkId}` document
 * changes, so notifications arrive even when the recipient's app is killed (the in-app
 * BuddyNotifier only fires while the process is alive and observing).
 *
 * The Android client (FirebaseMessagingService) turns these data messages into local
 * notifications via BuddyNotifier, matching the in-app copy. Data-only messages are
 * used (no `notification` block) so the client fully controls presentation on all
 * app states.
 *
 * Data model (see .kiro/specs/buddy-link/design.md §4):
 *   links/{linkId}:  a (requester appId), b (recipient appId), state
 *                    ('pending'|'accepted'|'declined'|'removed'), reqCount
 *   users/{appId}:   fcmToken, displayName
 *
 * Event → recipient → type:
 *   created pending                → b   → "request"   (honours ≤2 alerts/24h via reqCount)
 *   pending → accepted             → a   → "accepted"
 *   accepted → removed             → other party → "unlinked"
 *   re-request (reqCount bumped)   → b   → "request"   (while reqCount ≤ 2)
 */

const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();

const REGION = "us-central1";
const REQUEST_ALERT_MAX_PER_24H = 2;

/** Fetch a user's FCM token + display name; returns {token, name} or null token. */
async function lookupUser(appId) {
  if (!appId) return { token: null, name: "" };
  const snap = await admin.firestore().collection("users").doc(appId).get();
  const data = snap.exists ? snap.data() : null;
  return { token: data && data.fcmToken ? data.fcmToken : null, name: (data && data.displayName) || "" };
}

/** Send a data-only FCM message. No-op if the target has no token. */
async function sendData(token, type, actorName) {
  if (!token) {
    functions.logger.info(`No FCM token for target — skipping ${type}`);
    return;
  }
  const message = {
    token,
    data: {
      type, // "request" | "accepted" | "unlinked"
      actorName: actorName || "",
    },
    android: { priority: "high" },
  };
  try {
    await admin.messaging().send(message);
    functions.logger.info(`Sent ${type} push`);
  } catch (e) {
    // A stale/unregistered token is expected occasionally; log and move on.
    functions.logger.warn(`FCM send failed (${type}): ${e && e.message}`);
  }
}

exports.onBuddyLinkWrite = functions
  .region(REGION)
  .firestore.document("links/{linkId}")
  .onWrite(async (change) => {
    const before = change.before.exists ? change.before.data() : null;
    const after = change.after.exists ? change.after.data() : null;
    if (!after) return null; // deleted — nothing to notify

    const a = after.a; // requester
    const b = after.b; // recipient
    const beforeState = before ? before.state : null;
    const afterState = after.state;

    // 1) New pending request → notify recipient b.
    if (!before && afterState === "pending") {
      const { name } = await lookupUser(a);
      const { token } = await lookupUser(b);
      return sendData(token, "request", name);
    }

    // 2) Re-request: still pending, reqCount bumped, within the 24h alert cap.
    if (
      before &&
      afterState === "pending" &&
      beforeState === "pending" &&
      typeof after.reqCount === "number" &&
      typeof before.reqCount === "number" &&
      after.reqCount > before.reqCount &&
      after.reqCount <= REQUEST_ALERT_MAX_PER_24H
    ) {
      const { name } = await lookupUser(a);
      const { token } = await lookupUser(b);
      return sendData(token, "request", name);
    }

    // 3) Accepted → notify the requester a.
    if (beforeState === "pending" && afterState === "accepted") {
      const { name } = await lookupUser(b);
      const { token } = await lookupUser(a);
      return sendData(token, "accepted", name);
    }

    // 4) Unlinked (was accepted, now removed) → notify the OTHER party.
    //    We can't know which side initiated from the doc alone, so notify both; the
    //    initiator's own client already reflects the change and this is a harmless
    //    "stopped sharing" note. Kept simple for v1.
    if (beforeState === "accepted" && afterState === "removed") {
      const aInfo = await lookupUser(a);
      const bInfo = await lookupUser(b);
      await sendData(aInfo.token, "unlinked", bInfo.name);
      await sendData(bInfo.token, "unlinked", aInfo.name);
      return null;
    }

    return null;
  });
