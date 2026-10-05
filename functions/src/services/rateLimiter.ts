import * as admin from "firebase-admin";

/**
 * Checks and increments a rate limit bucket in Firestore.
 * Returns true if the action is allowed, or false if rate limited.
 */
export async function checkRateLimit(
  db: admin.firestore.Firestore,
  key: string,
  maxAllowed: number,
  windowSeconds: number
): Promise<{ allowed: boolean; remaining: number; retryAfterSeconds?: number }> {
  const docRef = db.collection("rateLimits").doc(key);
  const nowMs = Date.now();

  return db.runTransaction(async (t) => {
    const doc = await t.get(docRef);
    if (!doc.exists) {
      t.set(docRef, { count: 1, resetAt: nowMs + windowSeconds * 1000 });
      return { allowed: true, remaining: maxAllowed - 1 };
    }

    const data = doc.data()!;
    const resetAt = data.resetAt || nowMs;

    if (nowMs > resetAt) {
      // Window expired, reset bucket
      t.set(docRef, { count: 1, resetAt: nowMs + windowSeconds * 1000 });
      return { allowed: true, remaining: maxAllowed - 1 };
    }

    const currentCount = data.count || 0;
    if (currentCount >= maxAllowed) {
      const retryAfter = Math.ceil((resetAt - nowMs) / 1000);
      return { allowed: false, remaining: 0, retryAfterSeconds: retryAfter };
    }

    t.update(docRef, { count: currentCount + 1 });
    return { allowed: true, remaining: maxAllowed - (currentCount + 1) };
  });
}
