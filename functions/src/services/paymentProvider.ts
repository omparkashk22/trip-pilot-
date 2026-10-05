import { buildUpiUri, generateReference } from "../utils/crypto";

export interface CreatePaymentParams {
  uid: string;
  email: string;
  planId: string;
  amount: number;
  upiId: string;
  payeeName: string;
  noteBase: string;
  offsetPaise?: number;
}

export interface PaymentRequestResult {
  requestId: string;
  upiUri: string;
  upiId: string;
  payeeName: string;
  amount: number;
  reference: string;
  upiNote: string;
  mode: string;
}

export interface VerifyPaymentParams {
  requestId: string;
  utr: string;
  amountExpected: number;
  receivedAmount?: number;
}

export interface VerifyPaymentResult {
  verified: boolean;
  status: "APPROVED" | "REJECTED" | "MANUAL_REVIEW";
  note?: string;
}

/**
 * Universal Payment Provider interface for TripPilot.
 */
export interface PaymentProvider {
  createRequest(params: CreatePaymentParams): Promise<PaymentRequestResult>;
  verify(params: VerifyPaymentParams): Promise<VerifyPaymentResult>;
}

/**
 * Implementation of manual UPI payment provider where user scans QR / sends UPI
 * with reference note and submits 12-digit UTR for admin approval.
 */
export class UpiManualProvider implements PaymentProvider {
  async createRequest(params: CreatePaymentParams): Promise<PaymentRequestResult> {
    const reference = generateReference(params.noteBase || "TP");
    const finalAmount = params.offsetPaise
      ? parseFloat((params.amount + params.offsetPaise / 100).toFixed(2))
      : params.amount;

    const upiNote = `${params.noteBase || "TP"} ${reference}`;
    const upiUri = buildUpiUri(params.upiId, params.payeeName, finalAmount, upiNote);

    return {
      requestId: reference, // or generated uuid
      upiUri,
      upiId: params.upiId,
      payeeName: params.payeeName,
      amount: finalAmount,
      reference,
      upiNote,
      mode: "upi_manual"
    };
  }

  async verify(params: VerifyPaymentParams): Promise<VerifyPaymentResult> {
    // For manual UPI, verification is completed via admin review.
    // Automated check validates UTR structure.
    const cleanUtr = (params.utr || "").trim();
    if (!/^\d{12}$/.test(cleanUtr)) {
      return {
        verified: false,
        status: "REJECTED",
        note: "Invalid UTR format. Must be exactly 12 digits."
      };
    }

    return {
      verified: true,
      status: "MANUAL_REVIEW",
      note: "UTR submitted. Awaiting admin approval."
    };
  }
}

/**
 * Empty documented adapter slot for a future Gateway-UPI provider with webhook verification.
 * (e.g. Cashfree, Setu, or Razorpay UPI Gateway).
 * Note: Do NOT implement now as manual UPI with admin approval is active per Part 16.
 */
export class GatewayUpiProvider implements PaymentProvider {
  async createRequest(_params: CreatePaymentParams): Promise<PaymentRequestResult> {
    throw new Error("GatewayUpiProvider is an adapter slot and not yet enabled in this deployment.");
  }

  async verify(_params: VerifyPaymentParams): Promise<VerifyPaymentResult> {
    throw new Error("GatewayUpiProvider is an adapter slot and not yet enabled in this deployment.");
  }
}
