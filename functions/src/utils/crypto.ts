import * as crypto from "crypto";

/**
 * Standard SHA-256 hex hash
 */
export function hashSha256(input: string | Buffer): string {
  return crypto.createHash("sha256").update(input).digest("hex");
}

/**
 * Computes server-side deviceHash applying HMAC with a secret pepper
 * over the sorted combination of client identifier hashes.
 */
export function computeServerDeviceHash(identifiers: string[], pepper: string): string {
  const sorted = [...identifiers].filter(Boolean).sort().join("|");
  return crypto.createHmac("sha256", pepper).update(sorted).digest("hex");
}

/**
 * Generates a 6-character alphanumeric reference code
 * without confusing characters (excludes 0, O, I, 1, L).
 * Result formatted as "TP-4F7K9Q".
 */
export function generateReference(prefix: string = "TP"): string {
  const chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  let code = "";
  const randomBytes = crypto.randomBytes(6);
  for (let i = 0; i < 6; i++) {
    code += chars[randomBytes[i] % chars.length];
  }
  return `${prefix}-${code}`;
}

/**
 * Builds an official UPI URI conforming strictly to Section 4:
 * upi://pay?pa=<upiId>&pn=<payeeName>&am=<amount with exactly 2 decimals>&cu=INR&tn=<note>
 * Every query value is percent-encoded. No "tr" parameter.
 */
export function buildUpiUri(
  upiId: string,
  payeeName: string,
  amount: number,
  note: string
): string {
  const formattedAmount = amount.toFixed(2);
  const pa = encodeURIComponent(upiId);
  const pn = encodeURIComponent(payeeName);
  const am = encodeURIComponent(formattedAmount);
  const cu = encodeURIComponent("INR");
  const tn = encodeURIComponent(note);

  return `upi://pay?pa=${pa}&pn=${pn}&am=${am}&cu=${cu}&tn=${tn}`;
}

/**
 * Generates an Ed25519 key pair for signing driver leases.
 */
export function generateEd25519KeyPair(): { privateKey: string; publicKey: string } {
  const { privateKey, publicKey } = crypto.generateKeyPairSync("ed25519", {
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
    publicKeyEncoding: { type: "spki", format: "pem" }
  });
  return { privateKey, publicKey };
}

/**
 * Signs lease payload using Ed25519.
 */
export function signLease(payload: Record<string, any>, privateKeyPem: string): string {
  const data = Buffer.from(JSON.stringify(payload), "utf8");
  const signature = crypto.sign(null, data, privateKeyPem);
  return signature.toString("base64");
}

/**
 * Verifies Ed25519 signature of lease payload.
 */
export function verifyLease(payload: Record<string, any>, signatureBase64: string, publicKeyPem: string): boolean {
  try {
    const data = Buffer.from(JSON.stringify(payload), "utf8");
    const signature = Buffer.from(signatureBase64, "base64");
    return crypto.verify(null, data, publicKeyPem, signature);
  } catch (_e) {
    return false;
  }
}
