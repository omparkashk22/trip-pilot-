import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import {
  DEFAULT_PLANS_CONFIG,
  PlansConfig,
  PaymentRequestDoc,
  UserDoc
} from "../config";
import { normalizeEmail } from "../utils/email";

const db = admin.firestore();

function requireAdmin(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  if (request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Admin privileges required.");
  }
}

/**
 * Approves a SUBMITTED UPI payment request.
 * Applies subscription entitlement and logs audit action.
 */
export async function handleAdminApprovePayment(request: CallableRequest<any>) {
  requireAdmin(request);

  const adminUid = request.auth!.uid;
  const { requestId, note, receivedAmount } = request.data || {};

  if (!requestId) {
    throw new HttpsError("invalid-argument", "requestId required.");
  }

  const paymentRef = db.collection("payments").doc(requestId);
  const snap = await paymentRef.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Payment request not found.");
  }

  const payData = snap.data() as PaymentRequestDoc;

  // Idempotency: If already approved, return success without extending again
  if (payData.status === "APPROVED") {
    return { success: true, message: "Payment already approved." };
  }

  if (payData.status !== "SUBMITTED") {
    throw new HttpsError("failed-precondition", `Cannot approve request with status '${payData.status}'.`);
  }

  // Safety check on received amount vs expected amount
  if (typeof receivedAmount === "number" && receivedAmount < payData.amountExpected) {
    if (!note || note.trim().length === 0) {
      throw new HttpsError(
        "failed-precondition",
        `Received amount (₹${receivedAmount}) is less than expected (₹${payData.amountExpected}). Override note is required to proceed.`
      );
    }
  }

  const plansDoc = await db.collection("config").doc("plans").get();
  const plansConfig: PlansConfig = plansDoc.exists ? (plansDoc.data() as PlansConfig) : DEFAULT_PLANS_CONFIG;
  const plan = plansConfig.plans.find((p) => p.id === payData.planId) || {
    id: payData.planId,
    durationHours: payData.planId === "weekly" ? 168 : payData.planId === "monthly" ? 720 : 24
  };

  const userRef = db.collection("users").doc(payData.uid);
  const now = admin.firestore.Timestamp.now();

  await db.runTransaction(async (t) => {
    const userDoc = await t.get(userRef);
    if (!userDoc.exists) {
      throw new HttpsError("not-found", "User document not found.");
    }

    const userData = userDoc.data() as UserDoc;
    const currentExpMs = userData.expiresAt ? userData.expiresAt.toMillis() : 0;
    const isCurrentlyActive = userData.status === "ACTIVE" && currentExpMs > now.toMillis();

    const durationMs = plan.durationHours * 3600 * 1000;
    const baseMs = (plansConfig.stackPurchases && isCurrentlyActive) ? currentExpMs : now.toMillis();
    const newExpiresAt = new admin.firestore.Timestamp(Math.floor((baseMs + durationMs) / 1000), 0);

    t.update(userRef, {
      status: "ACTIVE",
      plan: payData.planId,
      expiresAt: newExpiresAt
    });

    t.update(paymentRef, {
      status: "APPROVED",
      reviewedBy: adminUid,
      reviewedAt: now,
      reviewNote: note || null,
      appliedAt: now
    });

    // Decrement pending payments counter
    const adminStatsRef = db.collection("config").doc("adminStats");
    t.set(
      adminStatsRef,
      { pendingPaymentsCount: admin.firestore.FieldValue.increment(-1) },
      { merge: true }
    );

    // Write audit log entry
    const auditRef = db.collection("auditLog").doc();
    t.set(auditRef, {
      adminUid,
      action: "APPROVE_PAYMENT",
      target: payData.uid,
      time: now,
      details: {
        requestId,
        utr: payData.utr,
        planId: payData.planId,
        amountExpected: payData.amountExpected,
        receivedAmount: receivedAmount ?? payData.amountExpected,
        newExpiresAtMs: newExpiresAt.toMillis()
      }
    });
  });

  // Send push notification to user
  try {
    await admin.messaging().send({
      token: (await getUserFcmToken(payData.uid)) || "",
      notification: {
        title: "Payment Approved!",
        body: `Your ${plan.id} subscription is now active.`
      },
      data: {
        type: "PAYMENT_APPROVED",
        requestId
      }
    });
  } catch (_e) {
    // Non-fatal if token invalid or absent
  }

  return { success: true, status: "APPROVED" };
}

/**
 * Rejects a SUBMITTED UPI payment request.
 * Keeps the UTR locked to prevent re-use.
 */
export async function handleAdminRejectPayment(request: CallableRequest<any>) {
  requireAdmin(request);

  const adminUid = request.auth!.uid;
  const { requestId, reason } = request.data || {};

  if (!requestId || !reason) {
    throw new HttpsError("invalid-argument", "requestId and rejection reason are required.");
  }

  const paymentRef = db.collection("payments").doc(requestId);
  const snap = await paymentRef.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Payment request not found.");
  }

  const payData = snap.data() as PaymentRequestDoc;
  if (payData.status !== "SUBMITTED") {
    throw new HttpsError("failed-precondition", `Cannot reject request with status '${payData.status}'.`);
  }

  const now = admin.firestore.Timestamp.now();

  await db.runTransaction(async (t) => {
    t.update(paymentRef, {
      status: "REJECTED",
      reviewedBy: adminUid,
      reviewedAt: now,
      reviewNote: reason
    });

    // Decrement pending payments counter
    const adminStatsRef = db.collection("config").doc("adminStats");
    t.set(
      adminStatsRef,
      { pendingPaymentsCount: admin.firestore.FieldValue.increment(-1) },
      { merge: true }
    );

    // Write audit log entry
    const auditRef = db.collection("auditLog").doc();
    t.set(auditRef, {
      adminUid,
      action: "REJECT_PAYMENT",
      target: payData.uid,
      time: now,
      details: {
        requestId,
        utr: payData.utr,
        reason
      }
    });
  });

  // Send push notification to user
  try {
    await admin.messaging().send({
      token: (await getUserFcmToken(payData.uid)) || "",
      notification: {
        title: "Payment Rejected",
        body: `Reason: ${reason}. Tap to view options.`
      },
      data: {
        type: "PAYMENT_REJECTED",
        requestId
      }
    });
  } catch (_e) {}

  return { success: true, status: "REJECTED" };
}

/**
 * Lists pending, approved, or rejected payments for the admin console.
 * Raw device identifiers are never returned.
 */
export async function handleAdminListPayments(request: CallableRequest<any>) {
  requireAdmin(request);

  const { status = "SUBMITTED", limit = 20, cursor } = request.data || {};

  let query: admin.firestore.Query = db.collection("payments");
  if (status !== "ALL") {
    query = query.where("status", "==", status);
  }

  // Sort oldest first for SUBMITTED, newest first for others
  const orderDirection = status === "SUBMITTED" ? "asc" : "desc";
  query = query.orderBy("createdAt", orderDirection).limit(Math.min(limit, 50));

  if (cursor) {
    const cursorDoc = await db.collection("payments").doc(cursor).get();
    if (cursorDoc.exists) {
      query = query.startAfter(cursorDoc);
    }
  }

  const snap = await query.get();
  const bucket = admin.storage().bucket();

  const items = await Promise.all(
    snap.docs.map(async (doc) => {
      const data = doc.data() as PaymentRequestDoc;
      let screenshotSignedUrl: string | null = null;

      if (data.screenshotPath) {
        try {
          const file = bucket.file(data.screenshotPath);
          const [exists] = await file.exists();
          if (exists) {
            const [url] = await file.getSignedUrl({
              action: "read",
              expires: Date.now() + 15 * 60 * 1000 // 15 minutes
            });
            screenshotSignedUrl = url;
          }
        } catch (_e) {}
      }

      return {
        requestId: data.requestId,
        uid: data.uid,
        email: data.email,
        planId: data.planId,
        amountExpected: data.amountExpected,
        reference: data.reference,
        status: data.status,
        utr: data.utr,
        submittedAt: data.submittedAt ? data.submittedAt.toMillis() : null,
        createdAt: data.createdAt instanceof admin.firestore.Timestamp ? data.createdAt.toMillis() : Date.now(),
        reviewedAt: data.reviewedAt ? data.reviewedAt.toMillis() : null,
        reviewNote: data.reviewNote,
        flags: data.flags || [],
        deviceSummary: data.deviceHash ? data.deviceHash.substring(0, 6) : "none",
        screenshotUrl: screenshotSignedUrl
      };
    })
  );

  return {
    items,
    nextCursor: snap.docs.length > 0 ? snap.docs[snap.docs.length - 1].id : null
  };
}

/**
 * Releases a UTR lock in `utrs/{utr}`.
 */
export async function handleAdminReleaseUtr(request: CallableRequest<any>) {
  requireAdmin(request);
  const adminUid = request.auth!.uid;
  const { utr } = request.data || {};

  if (!utr) {
    throw new HttpsError("invalid-argument", "utr is required.");
  }

  const utrRef = db.collection("utrs").doc(utr.trim());
  await utrRef.delete();

  await db.collection("auditLog").add({
    adminUid,
    action: "RELEASE_UTR",
    target: utr,
    time: admin.firestore.FieldValue.serverTimestamp()
  });

  return { success: true };
}

/**
 * Manually grants plan entitlement to a user.
 */
export async function handleAdminGrant(request: CallableRequest<any>) {
  requireAdmin(request);
  const adminUid = request.auth!.uid;
  const { email, planId = "monthly", hours } = request.data || {};

  if (!email) {
    throw new HttpsError("invalid-argument", "email is required.");
  }

  const normalized = normalizeEmail(email);
  const userSnap = await db.collection("users").where("emailNormalized", "==", normalized).limit(1).get();
  if (userSnap.empty) {
    throw new HttpsError("not-found", "User with this email not found.");
  }

  const userDoc = userSnap.docs[0];
  const userData = userDoc.data() as UserDoc;
  const grantHours = hours || (planId === "weekly" ? 168 : planId === "monthly" ? 720 : 24);

  const now = admin.firestore.Timestamp.now();
  const currentExpMs = userData.expiresAt ? userData.expiresAt.toMillis() : 0;
  const baseMs = (userData.status === "ACTIVE" && currentExpMs > now.toMillis()) ? currentExpMs : now.toMillis();
  const newExpiresAt = new admin.firestore.Timestamp(Math.floor((baseMs + grantHours * 3600 * 1000) / 1000), 0);

  await userDoc.ref.update({
    status: "ACTIVE",
    plan: planId,
    expiresAt: newExpiresAt
  });

  await db.collection("auditLog").add({
    adminUid,
    action: "GRANT_SUBSCRIPTION",
    target: userDoc.id,
    time: now,
    details: { email, planId, hours: grantHours, expiresAtMs: newExpiresAt.toMillis() }
  });

  return { success: true, expiresAtMs: newExpiresAt.toMillis() };
}

/**
 * Manually revokes subscription entitlement.
 */
export async function handleAdminRevoke(request: CallableRequest<any>) {
  requireAdmin(request);
  const adminUid = request.auth!.uid;
  const { email } = request.data || {};

  if (!email) {
    throw new HttpsError("invalid-argument", "email is required.");
  }

  const normalized = normalizeEmail(email);
  const userSnap = await db.collection("users").where("emailNormalized", "==", normalized).limit(1).get();
  if (userSnap.empty) {
    throw new HttpsError("not-found", "User not found.");
  }

  const userDoc = userSnap.docs[0];
  await userDoc.ref.update({
    status: "EXPIRED",
    plan: "none"
  });

  await db.collection("auditLog").add({
    adminUid,
    action: "REVOKE_SUBSCRIPTION",
    target: userDoc.id,
    time: admin.firestore.FieldValue.serverTimestamp(),
    details: { email }
  });

  return { success: true };
}

/**
 * Blocks an account or device.
 */
export async function handleAdminBlock(request: CallableRequest<any>) {
  requireAdmin(request);
  const adminUid = request.auth!.uid;
  const { email, deviceHash } = request.data || {};

  if (email) {
    const normalized = normalizeEmail(email);
    const userSnap = await db.collection("users").where("emailNormalized", "==", normalized).limit(1).get();
    if (!userSnap.empty) {
      await userSnap.docs[0].ref.update({ status: "BLOCKED" });
    }
  }

  if (deviceHash) {
    await db.collection("config").doc("private").update({
      blockedDevices: admin.firestore.FieldValue.arrayUnion(deviceHash)
    });
  }

  await db.collection("auditLog").add({
    adminUid,
    action: "BLOCK",
    target: email || deviceHash || "unknown",
    time: admin.firestore.FieldValue.serverTimestamp(),
    details: { email, deviceHash }
  });

  return { success: true };
}

/**
 * Unblocks an account or device.
 */
export async function handleAdminUnblock(request: CallableRequest<any>) {
  requireAdmin(request);
  const adminUid = request.auth!.uid;
  const { email, deviceHash } = request.data || {};

  if (email) {
    const normalized = normalizeEmail(email);
    const userSnap = await db.collection("users").where("emailNormalized", "==", normalized).limit(1).get();
    if (!userSnap.empty) {
      await userSnap.docs[0].ref.update({ status: "EXPIRED" });
    }
  }

  if (deviceHash) {
    await db.collection("config").doc("private").update({
      blockedDevices: admin.firestore.FieldValue.arrayRemove(deviceHash)
    });
  }

  await db.collection("auditLog").add({
    adminUid,
    action: "UNBLOCK",
    target: email || deviceHash || "unknown",
    time: admin.firestore.FieldValue.serverTimestamp(),
    details: { email, deviceHash }
  });

  return { success: true };
}

/**
 * Resets trial status for an account or device (audited).
 */
export async function handleAdminResetTrial(request: CallableRequest<any>) {
  requireAdmin(request);
  const adminUid = request.auth!.uid;
  const { email, identifierHash } = request.data || {};

  if (email) {
    const normalized = normalizeEmail(email);
    const userSnap = await db.collection("users").where("emailNormalized", "==", normalized).limit(1).get();
    if (!userSnap.empty) {
      await userSnap.docs[0].ref.update({ trialUsed: false, trialStartedAt: null, trialExpiresAt: null });
    }
    await db.collection("trialEmails").doc(normalized).delete();
  }

  if (identifierHash) {
    await db.collection("deviceIds").doc(identifierHash).update({
      trialConsumed: false,
      trialUid: null,
      trialEmailNormalized: null
    });
  }

  await db.collection("auditLog").add({
    adminUid,
    action: "RESET_TRIAL",
    target: email || identifierHash || "unknown",
    time: admin.firestore.FieldValue.serverTimestamp(),
    details: { email, identifierHash }
  });

  return { success: true };
}

async function getUserFcmToken(uid: string): Promise<string | null> {
  const tokenDoc = await db.collection("users").doc(uid).collection("tokens").doc("fcm").get();
  return tokenDoc.exists ? tokenDoc.data()?.token || null : null;
}
