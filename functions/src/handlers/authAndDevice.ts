import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import {
  DEFAULT_PLANS_CONFIG,
  PlansConfig,
  PrivateConfig,
  UserDoc,
  DeviceIdDoc
} from "../config";
import { computeServerDeviceHash, signLease } from "../utils/crypto";
import { normalizeEmail, isDisposableEmail } from "../utils/email";
import { checkRateLimit } from "../services/rateLimiter";

const db = admin.firestore();

/**
 * Ensures a user profile exists in Firestore upon signup or signin.
 * Does not overwrite existing trial/subscription status.
 */
export async function handleEnsureUser(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const email = request.auth.token.email || "";
  if (!email) {
    throw new HttpsError("invalid-argument", "Authenticated user has no email address.");
  }

  const normalized = normalizeEmail(email);
  const userRef = db.collection("users").doc(uid);
  const userDoc = await userRef.get();

  if (!userDoc.exists) {
    const newUser: UserDoc = {
      email,
      emailNormalized: normalized,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      trialUsed: false,
      trialStartedAt: null,
      trialExpiresAt: null,
      plan: "none",
      expiresAt: null,
      status: "EXPIRED",
      activeDeviceHash: "",
      deviceTransfers: [],
      flags: []
    };
    await userRef.set(newUser);
    return { created: true, plan: "none", status: "EXPIRED" };
  } else {
    // Keep email updated if changed
    await userRef.update({
      email,
      emailNormalized: normalized
    });
    const data = userDoc.data() as UserDoc;
    return { created: false, plan: data.plan, status: data.status, trialUsed: data.trialUsed };
  }
}

/**
 * Returns public policy and plans configuration.
 */
export async function handleGetPolicy() {
  const plansDoc = await db.collection("config").doc("plans").get();
  const plansConfig: PlansConfig = plansDoc.exists
    ? (plansDoc.data() as PlansConfig)
    : DEFAULT_PLANS_CONFIG;

  const privDoc = await db.collection("config").doc("private").get();
  const privConfig: PrivateConfig = privDoc.exists ? (privDoc.data() as PrivateConfig) : { disposableDomains: [], blockedDevices: [], hmacPepper: "pepper" };

  return {
    plans: plansConfig.plans.filter((p) => p.active),
    trialDurationHours: plansConfig.trial.durationHours,
    leaseMinutes: plansConfig.leaseMinutes,
    offlineGraceMinutes: plansConfig.offlineGraceMinutes,
    requireEmailVerified: plansConfig.requireEmailVerified,
    disposableDomains: privConfig.disposableDomains || []
  };
}

/**
 * Registers device identifiers, enforces max accounts per device, and checks trial eligibility.
 */
export async function handleRegisterDevice(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const { identifiers, isEmulator, appVersion } = request.data || {};
  if (!Array.isArray(identifiers) || identifiers.length === 0) {
    throw new HttpsError("invalid-argument", "Valid device identifiers are required.");
  }

  const privDoc = await db.collection("config").doc("private").get();
  const pepper = privDoc.exists ? privDoc.data()?.hmacPepper || "trip-pilot-secret-pepper" : "trip-pilot-secret-pepper";
  const deviceHash = computeServerDeviceHash(identifiers, pepper);

  const plansDoc = await db.collection("config").doc("plans").get();
  const plansConfig: PlansConfig = plansDoc.exists ? (plansDoc.data() as PlansConfig) : DEFAULT_PLANS_CONFIG;

  const userRef = db.collection("users").doc(uid);
  const userDoc = await userRef.get();
  const userData = userDoc.exists ? (userDoc.data() as UserDoc) : null;

  // Rate limit registration calls
  const rateKey = `registerDevice:${uid}`;
  const rate = await checkRateLimit(db, rateKey, 20, 3600);
  if (!rate.allowed) {
    throw new HttpsError("resource-exhausted", "RATE_LIMITED");
  }

  let trialAvailable = true;
  let reasonIfNot: string | null = null;

  if (userData?.status === "BLOCKED") {
    trialAvailable = false;
    reasonIfNot = "ACCOUNT_BLOCKED";
  } else if (userData?.trialUsed) {
    trialAvailable = false;
    reasonIfNot = "TRIAL_USED_BY_ACCOUNT";
  }

  // Inspect each identifier
  for (const ident of identifiers) {
    const devDoc = await db.collection("deviceIds").doc(ident).get();
    if (devDoc.exists) {
      const devData = devDoc.data() as DeviceIdDoc;
      if (devData.trialConsumed) {
        trialAvailable = false;
        reasonIfNot = "TRIAL_USED_ON_DEVICE";
      }

      // Check max accounts per device
      const uids = devData.uids || [];
      if (!uids.includes(uid) && uids.length >= plansConfig.maxAccountsPerDevice) {
        trialAvailable = false;
        reasonIfNot = "TRIAL_USED_ON_DEVICE";
        // Flag user as multi_account
        await userRef.update({
          flags: admin.firestore.FieldValue.arrayUnion("multi_account")
        });
      }

      // Update last seen
      await db.collection("deviceIds").doc(ident).update({
        lastSeenAt: admin.firestore.FieldValue.serverTimestamp(),
        uids: admin.firestore.FieldValue.arrayUnion(uid)
      });
    } else {
      // First time device identifier seen
      await db.collection("deviceIds").doc(ident).set({
        trialConsumed: false,
        trialUid: null,
        trialEmailNormalized: null,
        firstSeenAt: admin.firestore.FieldValue.serverTimestamp(),
        lastSeenAt: admin.firestore.FieldValue.serverTimestamp(),
        uids: [uid]
      });
    }
  }

  const boundToThisDevice = userData?.activeDeviceHash === deviceHash;

  return {
    trialAvailable,
    reasonIfNot,
    boundToThisDevice,
    deviceHash: deviceHash.substring(0, 8)
  };
}

/**
 * Starts the 6-hour free trial once per device and once per account.
 */
export async function handleStartTrial(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const token = request.auth.token;
  const email = token.email || "";
  const emailVerified = token.email_verified === true;

  if (!email) {
    throw new HttpsError("invalid-argument", "User email missing.");
  }

  const plansDoc = await db.collection("config").doc("plans").get();
  const plansConfig: PlansConfig = plansDoc.exists ? (plansDoc.data() as PlansConfig) : DEFAULT_PLANS_CONFIG;

  if (plansConfig.requireEmailVerified && !emailVerified) {
    throw new HttpsError("failed-precondition", "EMAIL_NOT_VERIFIED");
  }

  const privDoc = await db.collection("config").doc("private").get();
  const privConfig: PrivateConfig = privDoc.exists ? (privDoc.data() as PrivateConfig) : { disposableDomains: [], blockedDevices: [], hmacPepper: "trip-pilot-secret-pepper" };

  if (isDisposableEmail(email, privConfig.disposableDomains)) {
    throw new HttpsError("invalid-argument", "DISPOSABLE_EMAIL");
  }

  const { identifiers } = request.data || {};
  if (!Array.isArray(identifiers) || identifiers.length === 0) {
    throw new HttpsError("invalid-argument", "Device identifiers required.");
  }

  const pepper = privConfig.hmacPepper || "trip-pilot-secret-pepper";
  const deviceHash = computeServerDeviceHash(identifiers, pepper);
  const normalizedEmail = normalizeEmail(email);

  // Rate limit trial attempts
  const rate = await checkRateLimit(db, `startTrial:${uid}`, 5, 86400);
  if (!rate.allowed) {
    throw new HttpsError("resource-exhausted", "RATE_LIMITED");
  }

  const userRef = db.collection("users").doc(uid);
  const trialEmailRef = db.collection("trialEmails").doc(normalizedEmail);

  return db.runTransaction(async (t) => {
    const userDoc = await t.get(userRef);
    if (!userDoc.exists) {
      throw new HttpsError("not-found", "User document not found.");
    }

    const userData = userDoc.data() as UserDoc;
    if (userData.status === "BLOCKED") {
      throw new HttpsError("permission-denied", "ACCOUNT_BLOCKED");
    }

    if (userData.trialUsed) {
      throw new HttpsError("already-exists", "TRIAL_USED_BY_ACCOUNT");
    }

    const trialEmailDoc = await t.get(trialEmailRef);
    if (trialEmailDoc.exists) {
      throw new HttpsError("already-exists", "TRIAL_USED_BY_ACCOUNT");
    }

    // Check all device identifiers
    for (const ident of identifiers) {
      const devRef = db.collection("deviceIds").doc(ident);
      const devDoc = await t.get(devRef);
      if (devDoc.exists && devDoc.data()?.trialConsumed) {
        throw new HttpsError("already-exists", "TRIAL_USED_ON_DEVICE");
      }
    }

    // Everything eligible! Start 6h trial in this single transaction
    const now = admin.firestore.Timestamp.now();
    const durationHours = plansConfig.trial.durationHours || 6;
    const expiresAt = new admin.firestore.Timestamp(now.seconds + durationHours * 3600, now.nanoseconds);

    t.update(userRef, {
      trialUsed: true,
      trialStartedAt: now,
      trialExpiresAt: expiresAt,
      plan: "trial",
      expiresAt: expiresAt,
      status: "ACTIVE",
      activeDeviceHash: deviceHash
    });

    t.set(trialEmailRef, {
      uid,
      at: now
    });

    for (const ident of identifiers) {
      const devRef = db.collection("deviceIds").doc(ident);
      t.set(
        devRef,
        {
          trialConsumed: true,
          trialUid: uid,
          trialEmailNormalized: normalizedEmail,
          lastSeenAt: now,
          uids: admin.firestore.FieldValue.arrayUnion(uid)
        },
        { merge: true }
      );
    }

    return {
      success: true,
      plan: "trial",
      expiresAtMs: expiresAt.toMillis(),
      serverNowMs: now.toMillis()
    };
  });
}

/**
 * Delivers an Ed25519-signed lease validating that the active subscription is genuine
 * and currently bound to the calling device.
 */
export async function handleGetLease(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const { identifiers } = request.data || {};
  if (!Array.isArray(identifiers) || identifiers.length === 0) {
    throw new HttpsError("invalid-argument", "Device identifiers required.");
  }

  const userRef = db.collection("users").doc(uid);
  const userDoc = await userRef.get();
  if (!userDoc.exists) {
    throw new HttpsError("not-found", "User profile not found.");
  }

  const userData = userDoc.data() as UserDoc;
  if (userData.status === "BLOCKED") {
    throw new HttpsError("permission-denied", "ACCOUNT_BLOCKED");
  }

  const nowMs = Date.now();
  const expiresAtMs = userData.expiresAt ? userData.expiresAt.toMillis() : 0;

  if (userData.status !== "ACTIVE" || expiresAtMs <= nowMs) {
    throw new HttpsError("failed-precondition", "SUBSCRIPTION_EXPIRED");
  }

  const privDoc = await db.collection("config").doc("private").get();
  const privData = privDoc.data() as PrivateConfig | undefined;
  const pepper = privData?.hmacPepper || "trip-pilot-secret-pepper";
  const deviceHash = computeServerDeviceHash(identifiers, pepper);

  // First use binding
  if (!userData.activeDeviceHash) {
    await userRef.update({ activeDeviceHash: deviceHash });
  } else if (userData.activeDeviceHash !== deviceHash) {
    throw new HttpsError("failed-precondition", "DEVICE_MISMATCH");
  }

  const plansDoc = await db.collection("config").doc("plans").get();
  const leaseMinutes = plansDoc.exists ? plansDoc.data()?.leaseMinutes || 15 : 15;
  const leaseExpMs = Math.min(expiresAtMs, nowMs + leaseMinutes * 60 * 1000);

  const nonce = Math.random().toString(36).substring(2, 15);
  const leasePayload = {
    v: 1,
    uid,
    deviceHash,
    plan: userData.plan,
    expiresAtMs,
    serverNowMs: nowMs,
    leaseExpMs,
    nonce
  };

  const privKey = privData?.leasePrivateKeyPem;
  if (!privKey) {
    // Return unsigned mock lease in initial unconfigured state or test fallback
    return {
      payload: leasePayload,
      signature: ""
    };
  }

  const signature = signLease(leasePayload, privKey);
  return {
    payload: leasePayload,
    signature
  };
}

/**
 * Transfers device binding to a new device (limited to maxDeviceTransfersPer30Days).
 */
export async function handleTransferDevice(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const authTime = request.auth.token.auth_time;
  const nowSec = Math.floor(Date.now() / 1000);

  // Require recent sign-in within 5 minutes (300 seconds)
  if (typeof authTime === "number" && nowSec - authTime > 300) {
    throw new HttpsError("failed-precondition", "RECENT_LOGIN_REQUIRED");
  }

  const { identifiers } = request.data || {};
  if (!Array.isArray(identifiers) || identifiers.length === 0) {
    throw new HttpsError("invalid-argument", "Device identifiers required.");
  }

  const privDoc = await db.collection("config").doc("private").get();
  const pepper = privDoc.exists ? privDoc.data()?.hmacPepper || "trip-pilot-secret-pepper" : "trip-pilot-secret-pepper";
  const newDeviceHash = computeServerDeviceHash(identifiers, pepper);

  const plansDoc = await db.collection("config").doc("plans").get();
  const maxTransfers = plansDoc.exists ? plansDoc.data()?.maxDeviceTransfersPer30Days || 2 : 2;

  const userRef = db.collection("users").doc(uid);
  const userDoc = await userRef.get();
  const userData = userDoc.data() as UserDoc;

  const transfers = userData.deviceTransfers || [];
  const thirtyDaysAgoMs = Date.now() - 30 * 24 * 3600 * 1000;
  const recentTransfers = transfers.filter((t) => t.transferredAt.toMillis() > thirtyDaysAgoMs);

  if (recentTransfers.length >= maxTransfers) {
    throw new HttpsError("resource-exhausted", `TRANSFER_LIMIT_REACHED: Max ${maxTransfers} transfers per 30 days.`);
  }

  const oldHash = userData.activeDeviceHash;
  await userRef.update({
    activeDeviceHash: newDeviceHash,
    deviceTransfers: admin.firestore.FieldValue.arrayUnion({
      fromHash: oldHash,
      toHash: newDeviceHash,
      transferredAt: admin.firestore.Timestamp.now()
    })
  });

  return {
    success: true,
    transfersRemaining: maxTransfers - (recentTransfers.length + 1)
  };
}

/**
 * Redeems an activation code atomically.
 */
export async function handleRedeemCode(request: CallableRequest<any>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const uid = request.auth.uid;
  const rawCode = (request.data?.code || "").trim().toUpperCase();
  if (!rawCode) {
    throw new HttpsError("invalid-argument", "Promo / redeem code is required.");
  }

  const codeRef = db.collection("redeemCodes").doc(rawCode);
  const userRef = db.collection("users").doc(uid);

  return db.runTransaction(async (t) => {
    const codeDoc = await t.get(codeRef);
    if (!codeDoc.exists) {
      throw new HttpsError("not-found", "Invalid or unrecognized code.");
    }

    const codeData = codeDoc.data()!;
    if (codeData.usedBy) {
      throw new HttpsError("already-exists", "Code has already been redeemed.");
    }

    const now = admin.firestore.Timestamp.now();
    if (codeData.expiresAt && codeData.expiresAt.toMillis() < now.toMillis()) {
      throw new HttpsError("failed-precondition", "This code has expired.");
    }

    const userDoc = await t.get(userRef);
    const userData = userDoc.data() as UserDoc;
    const hours = codeData.hours || (codeData.planId === "weekly" ? 168 : codeData.planId === "monthly" ? 720 : 24);

    const currentExpMs = userData.expiresAt ? userData.expiresAt.toMillis() : 0;
    const baseMs = (userData.status === "ACTIVE" && currentExpMs > now.toMillis()) ? currentExpMs : now.toMillis();
    const newExpiresAt = new admin.firestore.Timestamp(Math.floor((baseMs + hours * 3600 * 1000) / 1000), 0);

    t.update(codeRef, {
      usedBy: uid,
      usedAt: now
    });

    t.update(userRef, {
      status: "ACTIVE",
      plan: codeData.planId || "daily",
      expiresAt: newExpiresAt
    });

    return {
      success: true,
      plan: codeData.planId || "daily",
      expiresAtMs: newExpiresAt.toMillis()
    };
  });
}
