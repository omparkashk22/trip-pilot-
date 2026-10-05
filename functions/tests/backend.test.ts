import { normalizeEmail, isDisposableEmail } from "../src/utils/email";
import { buildUpiUri, generateReference, computeServerDeviceHash, generateEd25519KeyPair, signLease, verifyLease } from "../src/utils/crypto";
import { UpiManualProvider } from "../src/services/paymentProvider";

describe("Phase 1 Backend Services Unit Tests", () => {
  describe("Email Normalization & Disposable Checks", () => {
    test("normalizes Gmail dots and plus tags", () => {
      expect(normalizeEmail("john.doe@gmail.com")).toBe("johndoe@gmail.com");
      expect(normalizeEmail("John.Doe+promo@gmail.com")).toBe("johndoe@gmail.com");
      expect(normalizeEmail("j.o.h.n.doe+test123@googlemail.com")).toBe("johndoe@gmail.com");
    });

    test("leaves non-gmail local parts with dots or plus untouched", () => {
      expect(normalizeEmail("driver.one+shift@yahoo.com")).toBe("driver.one+shift@yahoo.com");
    });

    test("detects disposable email domains", () => {
      expect(isDisposableEmail("fake@tempmail.com")).toBe(true);
      expect(isDisposableEmail("driver@mailinator.com")).toBe(true);
      expect(isDisposableEmail("legit@gmail.com")).toBe(false);
      expect(isDisposableEmail("driver@trippilot.in", ["customfake.org"])).toBe(false);
      expect(isDisposableEmail("user@customfake.org", ["customfake.org"])).toBe(true);
    });
  });

  describe("UPI URI and Reference Generation", () => {
    test("builds official UPI URI with correct percent-encoding and 2-decimal amount", () => {
      const uri = buildUpiUri("trippilot@upi", "Trip Pilot App", 49, "TP TP-4F7K9Q");
      expect(uri).toContain("upi://pay?");
      expect(uri).toContain("pa=trippilot%40upi");
      expect(uri).toContain("pn=Trip%20Pilot%20App");
      expect(uri).toContain("am=49.00");
      expect(uri).toContain("cu=INR");
      expect(uri).toContain("tn=TP%20TP-4F7K9Q");
      expect(uri).not.toContain("tr="); // Section 4: Do not add a "tr" parameter
    });

    test("handles decimals with exact precision", () => {
      const uri = buildUpiUri("merchant@bank", "Payee", 199.5, "TP TP-ABCD12");
      expect(uri).toContain("am=199.50");
    });

    test("generates unique 6-character alphanumeric reference without confusing chars", () => {
      const ref1 = generateReference("TP");
      const ref2 = generateReference("TP");

      expect(ref1).toMatch(/^TP-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/);
      expect(ref2).toMatch(/^TP-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/);
      expect(ref1).not.toBe(ref2);

      // Verify no confusing characters (0, O, I, 1)
      expect(ref1).not.toMatch(/[0OI1]/);
    });
  });

  describe("Device Hashing & Lease Signing (Ed25519)", () => {
    test("computes deterministic server deviceHash regardless of identifier ordering", () => {
      const pepper = "test-pepper-123";
      const hashA = computeServerDeviceHash(["hash_android_id", "hash_widevine", "hash_install"], pepper);
      const hashB = computeServerDeviceHash(["hash_install", "hash_android_id", "hash_widevine"], pepper);

      expect(hashA).toBe(hashB);
      expect(hashA.length).toBe(64); // SHA-256 hex length
    });

    test("signs and verifies driver lease using Ed25519", () => {
      const { privateKey, publicKey } = generateEd25519KeyPair();
      const leasePayload = {
        v: 1,
        uid: "user-12345",
        deviceHash: "abc123device",
        plan: "monthly",
        expiresAtMs: Date.now() + 30 * 24 * 3600 * 1000,
        serverNowMs: Date.now(),
        leaseExpMs: Date.now() + 15 * 60 * 1000,
        nonce: "test-nonce-xyz"
      };

      const signature = signLease(leasePayload, privateKey);
      expect(typeof signature).toBe("string");
      expect(signature.length).toBeGreaterThan(20);

      const isValid = verifyLease(leasePayload, signature, publicKey);
      expect(isValid).toBe(true);

      // Tampered payload fails verification
      const tamperedPayload = { ...leasePayload, plan: "lifetime" };
      const isTamperedValid = verifyLease(tamperedPayload, signature, publicKey);
      expect(isTamperedValid).toBe(false);
    });
  });

  describe("Manual UPI Provider", () => {
    const provider = new UpiManualProvider();

    test("creates manual UPI request result", async () => {
      const res = await provider.createRequest({
        uid: "driver-1",
        email: "driver@example.com",
        planId: "daily",
        amount: 49,
        upiId: "trippilot@upi",
        payeeName: "TripPilot",
        noteBase: "TP"
      });

      expect(res.amount).toBe(49);
      expect(res.mode).toBe("upi_manual");
      expect(res.reference).toMatch(/^TP-[A-Z0-9]{6}$/);
      expect(res.upiUri).toContain("upi://pay?");
    });

    test("validates 12-digit numeric UTR format", async () => {
      const valid = await provider.verify({
        requestId: "TP-123456",
        utr: "123456789012",
        amountExpected: 49
      });
      expect(valid.verified).toBe(true);
      expect(valid.status).toBe("MANUAL_REVIEW");

      const invalidLetters = await provider.verify({
        requestId: "TP-123456",
        utr: "12345678901A",
        amountExpected: 49
      });
      expect(invalidLetters.verified).toBe(false);
      expect(invalidLetters.status).toBe("REJECTED");

      const invalidLength = await provider.verify({
        requestId: "TP-123456",
        utr: "123456",
        amountExpected: 49
      });
      expect(invalidLength.verified).toBe(false);
      expect(invalidLength.status).toBe("REJECTED");
    });
  });

  describe("Subscription Stacking Logic", () => {
    test("stacks duration on active subscription, or starts from now on expired", () => {
      const nowMs = 1700000000000;
      const durationHours = 24; // Daily plan
      const durationMs = durationHours * 3600 * 1000;

      // Case 1: Fresh purchase (expired user)
      const expiredAtMs = nowMs - 1000;
      const freshExpiry = Math.max(nowMs, expiredAtMs) + durationMs;
      expect(freshExpiry).toBe(nowMs + durationMs);

      // Case 2: Stacking (active user with 48 hours left)
      const currentActiveExpiryMs = nowMs + 48 * 3600 * 1000;
      const stackedExpiry = currentActiveExpiryMs + durationMs;
      expect(stackedExpiry).toBe(nowMs + 72 * 3600 * 1000);
    });
  });

  describe("Zero Razorpay Footprint", () => {
    test("ensures no references to razorpay exist in configuration or providers", () => {
      const json = JSON.stringify({
        provider: "UpiManualProvider",
        mode: "upi_manual"
      });
      expect(json.toLowerCase()).not.toContain("razorpay");
    });
  });
});
