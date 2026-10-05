import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import {
  DEFAULT_PAYMENTS_CONFIG,
  DEFAULT_PLANS_CONFIG,
  PaymentsConfig,
  PlansConfig,
  PaymentRequestDoc
} from "../config";
import { buildUpiUri, generateReference, computeServerDeviceHash } from "../utils/crypto";
import { checkRateLimit } from "../services/rateLimiter";

const db = admin.firestore();

/**
 * Creates a pending UPI manual payment request.
 */
export async function handleCreateUpiRequest(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const token = request.auth.token;
  const email = token.email || "";
  const emailVerified = token.email_verified === true;

  const plansDoc = await db.collection("config").doc("plans").get();
  const plansConfig: PlansConfig = plansDoc.exists ? (plansDoc.data() as PlansConfig) : DEFAULT_PLANS_CONFIG;

  if (plansConfig.requireEmailVerified && !emailVerified) {
    throw new HttpsError("failed-precondition", "EMAIL_NOT_VERIFIED");
  }

  const { planId, identifiers } = request.data || {};
  if (!planId) {
    throw new HttpsError("invalid-argument", "Plan ID is required.");
  }

  const plan = plansConfig.plans.find((p) => p.id === planId && p.active);
  if (!plan || plan.price === null || plan.price <= 0) {
    throw new HttpsError("not-found", "Selected plan is not currently active for purchase.");
  }

  const payDoc = await db.collection("config").doc("payments").get();
  const payConfig: PaymentsConfig = payDoc.exists ? (payDoc.data() as PaymentsConfig) : DEFAULT_PAYMENTS_CONFIG;

  // Rate limit: max 5 create requests per day per user
  const userRate = await checkRateLimit(db, `createUpi:user:${uid}`, 5, 86400);
  if (!userRate.allowed) {
    throw new HttpsError("resource-exhausted", "RATE_LIMITED: Maximum 5 payment requests allowed per day.");
  }

  let deviceHash = "";
  if (Array.isArray(identifiers) && identifiers.length > 0) {
    const privDoc = await db.collection("config").doc("private").get();
    const pepper = privDoc.exists ? privDoc.data()?.hmacPepper || "pepper" : "pepper";
    deviceHash = computeServerDeviceHash(identifiers, pepper);
    const devRate = await checkRateLimit(db, `createUpi:device:${deviceHash}`, 5, 86400);
    if (!devRate.allowed) {
      throw new HttpsError("resource-exhausted", "RATE_LIMITED: Maximum 5 payment requests allowed per device per day.");
    }
  }

  // Check open requests limit (status CREATED and not expired)
  const now = admin.firestore.Timestamp.now();
  const openRequestsSnap = await db.collection("payments")
    .where("uid", "==", uid)
    .where("status", "==", "CREATED")
    .get();

  const activeOpenRequests = openRequestsSnap.docs.filter((doc) => {
    const data = doc.data() as PaymentRequestDoc;
    return data.expiresAt && data.expiresAt.toMillis() > now.toMillis();
  });

  if (activeOpenRequests.length >= payConfig.maxOpenRequestsPerUser) {
    throw new HttpsError(
      "failed-precondition",
      `MAX_OPEN_REQUESTS_REACHED: You already have ${activeOpenRequests.length} pending open payment request(s). Please submit or cancel them first.`
    );
  }

  // Unique reference and amount offset if enabled
  const reference = generateReference(payConfig.noteBase || "TP");
  let finalAmount = plan.price;

  if (payConfig.uniqueAmountOffset) {
    // Generate an offset between 0.01 and 0.99
    const existingAmounts = new Set(activeOpenRequests.map((d) => d.data().amountExpected));
    for (let paise = 1; paise <= 99; paise++) {
      const candidate = parseFloat((plan.price + paise / 100).toFixed(2));
      if (!existingAmounts.has(candidate)) {
        finalAmount = candidate;
        break;
      }
    }
  }

  const upiNote = `${payConfig.noteBase || "TP"} ${reference}`;
  const upiUri = buildUpiUri(payConfig.upiId, payConfig.payeeName, finalAmount, upiNote);
  const expiryHours = payConfig.requestExpiryHours || 24;
  const expiresAt = new admin.firestore.Timestamp(now.seconds + expiryHours * 3600, now.nanoseconds);

  const paymentDoc: PaymentRequestDoc = {
    requestId: reference,
    uid,
    email,
    planId,
    amountExpected: finalAmount,
    reference,
    upiNote,
    status: "CREATED",
    expiresAt,
    utr: null,
    screenshotPath: null,
    screenshotHash: null,
    deviceHash: deviceHash || null,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNote: null,
    appliedAt: null,
    flags: []
  };

  await db.collection("payments").doc(reference).set(paymentDoc);

  return {
    requestId: reference,
    upiUri,
    upiId: payConfig.upiId,
    payeeName: payConfig.payeeName,
    amount: finalAmount,
    reference,
    expiresAt: expiresAt.toMillis()
  };
}

/**
 * Submits the 12-digit UTR and optional screenshot for a created request.
 * Atomically locks the UTR in `utrs/{utr}`.
 */
export async function handleSubmitUpiPayment(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const { requestId, utr, screenshotPath, screenshotHash, identifiers } = request.data || {};

  if (!requestId) {
    throw new HttpsError("invalid-argument", "requestId is required.");
  }

  const cleanUtr = (utr || "").trim();
  if (!/^[0-9]{12}$/.test(cleanUtr)) {
    throw new HttpsError("invalid-argument", "UTR must be exactly 12 numeric digits.");
  }

  const payDoc = await db.collection("config").doc("payments").get();
  const payConfig: PaymentsConfig = payDoc.exists ? (payDoc.data() as PaymentsConfig) : DEFAULT_PAYMENTS_CONFIG;

  if (payConfig.requireScreenshot && !screenshotPath) {
    throw new HttpsError("invalid-argument", "Payment screenshot is mandatory.");
  }

  // Rate limit: max 5 failed attempts per request
  const rateKey = `submitUpi:${requestId}`;
  const rate = await checkRateLimit(db, rateKey, 5, 86400);
  if (!rate.allowed) {
    throw new HttpsError("resource-exhausted", "RATE_LIMITED: Maximum submission attempts exceeded for this request.");
  }

  const paymentRef = db.collection("payments").doc(requestId);
  const utrRef = db.collection("utrs").doc(cleanUtr);

  let deviceHash: string | null = null;
  if (Array.isArray(identifiers) && identifiers.length > 0) {
    const privDoc = await db.collection("config").doc("private").get();
    const pepper = privDoc.exists ? privDoc.data()?.hmacPepper || "pepper" : "pepper";
    deviceHash = computeServerDeviceHash(identifiers, pepper);
  }

  // Check screenshot duplicate hash if provided
  const flags: string[] = [];
  if (screenshotHash) {
    const existingScreenSnap = await db.collection("payments")
      .where("screenshotHash", "==", screenshotHash)
      .limit(1)
      .get();
    if (!existingScreenSnap.empty && existingScreenSnap.docs[0].id !== requestId) {
      flags.push("duplicate_screenshot");
    }
  }

  await db.runTransaction(async (t) => {
    const paySnap = await t.get(paymentRef);
    if (!paySnap.exists) {
      throw new HttpsError("not-found", "Payment request not found.");
    }

    const payData = paySnap.data() as PaymentRequestDoc;
    if (payData.uid !== uid) {
      throw new HttpsError("permission-denied", "Unauthorized access to this payment request.");
    }

    if (payData.status !== "CREATED") {
      throw new HttpsError("failed-precondition", `Request is already in '${payData.status}' state.`);
    }

    const now = admin.firestore.Timestamp.now();
    if (payData.expiresAt && payData.expiresAt.toMillis() < now.toMillis()) {
      throw new HttpsError("failed-precondition", "This payment request has expired. Please create a new request.");
    }

    const utrSnap = await t.get(utrRef);
    if (utrSnap.exists) {
      throw new HttpsError("already-exists", "UTR_ALREADY_USED: This UTR has already been submitted for another request.");
    }

    // Atomically create UTR lock and update payment document
    t.set(utrRef, {
      requestId,
      uid,
      at: now
    });

    t.update(paymentRef, {
      status: "SUBMITTED",
      utr: cleanUtr,
      screenshotPath: screenshotPath || null,
      screenshotHash: screenshotHash || null,
      deviceHash: deviceHash || payData.deviceHash || null,
      submittedAt: now,
      flags: admin.firestore.FieldValue.arrayUnion(...flags)
    });

    // Increment adminStats pending counter
    const adminStatsRef = db.collection("config").doc("adminStats");
    t.set(
      adminStatsRef,
      { pendingPaymentsCount: admin.firestore.FieldValue.increment(1) },
      { merge: true }
    );
  });

  // Notify admins via FCM topic
  try {
    await admin.messaging().send({
      topic: "admins",
      notification: {
        title: "New Payment Submitted",
        body: `Payment ${requestId} submitted with UTR ${cleanUtr}.`
      },
      data: {
        requestId,
        utr: cleanUtr,
        type: "NEW_PAYMENT_SUBMITTED"
      }
    });
  } catch (err: any) {
    console.warn("Admin FCM notification skipped or failed:", err.message);
  }

  return {
    success: true,
    requestId,
    status: "SUBMITTED"
  };
}

/**
 * Cancels a CREATED payment request.
 */
export async function handleCancelUpiRequest(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const { requestId } = request.data || {};
  if (!requestId) {
    throw new HttpsError("invalid-argument", "requestId required.");
  }

  const paymentRef = db.collection("payments").doc(requestId);
  const snap = await paymentRef.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Payment request not found.");
  }

  const data = snap.data() as PaymentRequestDoc;
  if (data.uid !== uid) {
    throw new HttpsError("permission-denied", "Unauthorized access.");
  }

  if (data.status !== "CREATED") {
    throw new HttpsError("failed-precondition", "Only requests in CREATED status can be cancelled.");
  }

  await paymentRef.update({
    status: "EXPIRED",
    reviewNote: "Cancelled by user"
  });

  return { success: true };
}
