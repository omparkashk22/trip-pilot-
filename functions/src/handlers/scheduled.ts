import * as admin from "firebase-admin";

const db = admin.firestore();

/**
 * Hourly scheduled job:
 * 1) Marks CREATED payment requests that have passed expiresAt as EXPIRED.
 *    (SUBMITTED requests never auto-expire per Section 4).
 * 2) Marks users whose subscription has elapsed as EXPIRED.
 */
export async function runHourlyMaintenance(): Promise<{ expiredPaymentsCount: number; expiredUsersCount: number }> {
  const now = admin.firestore.Timestamp.now();

  // 1. Expire stale CREATED payment requests
  const stalePaymentsSnap = await db.collection("payments")
    .where("status", "==", "CREATED")
    .where("expiresAt", "<", now)
    .get();

  const batch = db.batch();
  let expiredPaymentsCount = 0;

  for (const doc of stalePaymentsSnap.docs) {
    batch.update(doc.ref, {
      status: "EXPIRED",
      reviewNote: "Auto-expired after 24h"
    });
    expiredPaymentsCount++;
  }

  // 2. Expire elapsed user subscriptions
  const expiredUsersSnap = await db.collection("users")
    .where("status", "==", "ACTIVE")
    .where("expiresAt", "<", now)
    .get();

  let expiredUsersCount = 0;
  for (const doc of expiredUsersSnap.docs) {
    batch.update(doc.ref, {
      status: "EXPIRED"
    });
    expiredUsersCount++;
  }

  if (expiredPaymentsCount > 0 || expiredUsersCount > 0) {
    await batch.commit();
  }

  return { expiredPaymentsCount, expiredUsersCount };
}
