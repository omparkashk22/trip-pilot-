import * as admin from "firebase-admin";

export interface PlanItem {
  id: "daily" | "weekly" | "monthly" | "legacy";
  name: string;
  durationHours: number;
  price: number | null;
  currency: "INR";
  active: boolean;
}

export interface PlansConfig {
  plans: PlanItem[];
  trial: { durationHours: number };
  leaseMinutes: number;
  offlineGraceMinutes: number;
  maxAccountsPerDevice: number;
  maxDeviceTransfersPer30Days: number;
  requireEmailVerified: boolean;
  requirePhoneForTrial: boolean;
  requireIntegrity: boolean;
  stackPurchases: boolean;
}

export interface PaymentsConfig {
  mode: "upi_manual";
  upiId: string;
  payeeName: string;
  noteBase: string;
  currency: string;
  requestExpiryHours: number;
  maxOpenRequestsPerUser: number;
  uniqueAmountOffset: boolean;
  requireScreenshot: boolean;
}

export interface PrivateConfig {
  disposableDomains: string[];
  blockedDevices: string[];
  hmacPepper: string;
  leasePrivateKeyPem?: string;
  leasePublicKeyPem?: string;
  telegramBotToken?: string;
  telegramChatId?: string;
}

export interface UserDoc {
  email: string;
  emailNormalized: string;
  createdAt: admin.firestore.FieldValue | admin.firestore.Timestamp;
  trialUsed: boolean;
  trialStartedAt: admin.firestore.Timestamp | null;
  trialExpiresAt: admin.firestore.Timestamp | null;
  plan: "none" | "trial" | "daily" | "weekly" | "monthly" | "legacy";
  expiresAt: admin.firestore.Timestamp | null;
  status: "ACTIVE" | "EXPIRED" | "BLOCKED";
  activeDeviceHash: string;
  deviceTransfers: Array<{ fromHash: string; toHash: string; transferredAt: admin.firestore.Timestamp }>;
  flags: string[];
}

export interface DeviceIdDoc {
  trialConsumed: boolean;
  trialUid: string | null;
  trialEmailNormalized: string | null;
  firstSeenAt: admin.firestore.FieldValue | admin.firestore.Timestamp;
  lastSeenAt: admin.firestore.FieldValue | admin.firestore.Timestamp;
  uids: string[];
}

export interface PaymentRequestDoc {
  requestId: string;
  uid: string;
  email: string;
  planId: string;
  amountExpected: number;
  reference: string;
  upiNote: string;
  status: "CREATED" | "SUBMITTED" | "APPROVED" | "REJECTED" | "EXPIRED";
  expiresAt: admin.firestore.Timestamp;
  utr: string | null;
  screenshotPath: string | null;
  screenshotHash: string | null;
  deviceHash: string | null;
  createdAt: admin.firestore.FieldValue | admin.firestore.Timestamp;
  submittedAt: admin.firestore.Timestamp | null;
  reviewedBy: string | null;
  reviewedAt: admin.firestore.Timestamp | null;
  reviewNote: string | null;
  appliedAt: admin.firestore.Timestamp | null;
  flags: string[];
}

export interface UtrDoc {
  requestId: string;
  uid: string;
  at: admin.firestore.FieldValue | admin.firestore.Timestamp;
}

export interface AuditLogDoc {
  adminUid: string;
  action: string;
  target: string;
  time: admin.firestore.FieldValue | admin.firestore.Timestamp;
  details?: Record<string, any>;
}

export const DEFAULT_PLANS_CONFIG: PlansConfig = {
  plans: [
    { id: "daily", name: "Daily", durationHours: 24, price: 49, currency: "INR", active: true },
    { id: "weekly", name: "Weekly", durationHours: 168, price: 199, currency: "INR", active: true },
    { id: "monthly", name: "Monthly", durationHours: 720, price: 599, currency: "INR", active: true }
  ],
  trial: { durationHours: 6 },
  leaseMinutes: 15,
  offlineGraceMinutes: 10,
  maxAccountsPerDevice: 2,
  maxDeviceTransfersPer30Days: 2,
  requireEmailVerified: true,
  requirePhoneForTrial: false,
  requireIntegrity: false,
  stackPurchases: true
};

export const DEFAULT_PAYMENTS_CONFIG: PaymentsConfig = {
  mode: "upi_manual",
  upiId: "trippilot@upi",
  payeeName: "TripPilot Driver Assistant",
  noteBase: "TP",
  currency: "INR",
  requestExpiryHours: 24,
  maxOpenRequestsPerUser: 3,
  uniqueAmountOffset: false,
  requireScreenshot: false
};
