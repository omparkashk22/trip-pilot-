import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { UserDoc } from "../config";
import { normalizeEmail } from "../utils/email";

const db = admin.firestore();

/**
 * Migration callable for existing accounts:
 * - Sets trialUsed = true (no free trial for legacy accounts)
 * - Retains current plan & expiresAt (mapping legacy plans to 'legacy')
 * - Evaluates status according to expiresAt
 * - Leaves activeDeviceHash empty so account binds on first lease
 * - Supports dryRun mode for safety inspection
 */
export async function handleMigrateExistingUsers(request: CallableRequest<any>) {
  if (!request.auth || request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Admin privileges required.");
  }

  const { dryRun = true } = request.data || {};
  const usersSnap = await db.collection("users").get();
  const nowMs = Date.now();

  let totalProcessed = 0;
  let activeCount = 0;
  let expiredCount = 0;
  let modifiedCount = 0;

  const updates: Array<{ ref: admin.firestore.DocumentReference; data: Partial<UserDoc> }> = [];

  for (const doc of usersSnap.docs) {
    totalProcessed++;
    const raw = doc.data();

    // Map legacy plan names if any
    let plan = raw.plan || "none";
    if (plan.toUpperCase().includes("YEAR") || plan === "legacy_pro") {
      plan = "legacy";
    }

    let expiresAt: admin.firestore.Timestamp | null = null;
    if (raw.expiresAt instanceof admin.firestore.Timestamp) {
      expiresAt = raw.expiresAt;
    } else if (typeof raw.expiresAt === "number") {
      expiresAt = admin.firestore.Timestamp.fromMillis(raw.expiresAt);
    }

    const isStillActive = expiresAt ? expiresAt.toMillis() > nowMs : false;
    const status = isStillActive ? "ACTIVE" : "EXPIRED";
    if (isStillActive) activeCount++; else expiredCount++;

    const email = raw.email || "";
    const emailNormalized = raw.emailNormalized || (email ? normalizeEmail(email) : "");

    const updateData: Partial<UserDoc> = {
      trialUsed: true,
      plan,
      status,
      emailNormalized,
      activeDeviceHash: raw.activeDeviceHash || ""
    };

    if (expiresAt) {
      updateData.expiresAt = expiresAt;
    }

    updates.push({ ref: doc.ref, data: updateData });
    modifiedCount++;
  }

  if (!dryRun) {
    const batches = [];
    let currentBatch = db.batch();
    let opCount = 0;

    for (const update of updates) {
      currentBatch.update(update.ref, update.data);
      opCount++;
      if (opCount === 450) {
        batches.push(currentBatch.commit());
        currentBatch = db.batch();
        opCount = 0;
      }
    }
    if (opCount > 0) {
      batches.push(currentBatch.commit());
    }
    await Promise.all(batches);
  }

  return {
    dryRun,
    totalProcessed,
    activeCount,
    expiredCount,
    modifiedCount
  };
}
