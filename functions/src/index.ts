import * as admin from "firebase-admin";
import { onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

// Handlers
import {
  handleEnsureUser,
  handleGetPolicy,
  handleRegisterDevice,
  handleStartTrial,
  handleGetLease,
  handleTransferDevice,
  handleRedeemCode
} from "./handlers/authAndDevice";

import {
  handleCreateUpiRequest,
  handleSubmitUpiPayment,
  handleCancelUpiRequest
} from "./handlers/upiPayments";

import {
  handleAdminApprovePayment,
  handleAdminRejectPayment,
  handleAdminListPayments,
  handleAdminReleaseUtr,
  handleAdminGrant,
  handleAdminRevoke,
  handleAdminBlock,
  handleAdminUnblock,
  handleAdminResetTrial
} from "./handlers/admin";

import { runHourlyMaintenance } from "./handlers/scheduled";
import { handleMigrateExistingUsers } from "./handlers/migration";

// Export Public & User Callables
export const ensureUser = onCall({ cors: true }, handleEnsureUser);
export const getPolicy = onCall({ cors: true }, handleGetPolicy);
export const registerDevice = onCall({ cors: true }, handleRegisterDevice);
export const startTrial = onCall({ cors: true }, handleStartTrial);
export const getLease = onCall({ cors: true }, handleGetLease);
export const transferDevice = onCall({ cors: true }, handleTransferDevice);
export const redeemCode = onCall({ cors: true }, handleRedeemCode);

// Export Manual UPI Payment Callables
export const createUpiRequest = onCall({ cors: true }, handleCreateUpiRequest);
export const submitUpiPayment = onCall({ cors: true }, handleSubmitUpiPayment);
export const cancelUpiRequest = onCall({ cors: true }, handleCancelUpiRequest);

// Export Admin Callables
export const adminApprovePayment = onCall({ cors: true }, handleAdminApprovePayment);
export const adminRejectPayment = onCall({ cors: true }, handleAdminRejectPayment);
export const adminListPayments = onCall({ cors: true }, handleAdminListPayments);
export const adminReleaseUtr = onCall({ cors: true }, handleAdminReleaseUtr);
export const adminGrant = onCall({ cors: true }, handleAdminGrant);
export const adminRevoke = onCall({ cors: true }, handleAdminRevoke);
export const adminBlock = onCall({ cors: true }, handleAdminBlock);
export const adminUnblock = onCall({ cors: true }, handleAdminUnblock);
export const adminResetTrial = onCall({ cors: true }, handleAdminResetTrial);

// Export Migration Callable
export const migrateExistingUsers = onCall({ cors: true }, handleMigrateExistingUsers);

// Hourly Scheduled Maintenance
export const hourlyMaintenance = onSchedule("0 * * * *", async (_event) => {
  await runHourlyMaintenance();
});
