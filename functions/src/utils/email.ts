/**
 * Normalizes an email address according to Section 2:
 * Lowercase; for gmail.com and googlemail.com remove dots and anything after "+" in the local part.
 */
export function normalizeEmail(email: string): string {
  const trimmed = email.trim().toLowerCase();
  const atIndex = trimmed.lastIndexOf("@");
  if (atIndex <= 0) return trimmed;

  let local = trimmed.substring(0, atIndex);
  let domain = trimmed.substring(atIndex + 1);

  if (domain === "googlemail.com") {
    domain = "gmail.com";
  }

  if (domain === "gmail.com") {
    // Remove dots
    local = local.replace(/\./g, "");
    // Remove anything after "+"
    const plusIndex = local.indexOf("+");
    if (plusIndex !== -1) {
      local = local.substring(0, plusIndex);
    }
  }

  return `${local}@${domain}`;
}

/**
 * Standard list of known disposable email providers.
 */
export const DEFAULT_DISPOSABLE_DOMAINS: string[] = [
  "tempmail.com",
  "10minutemail.com",
  "guerrillamail.com",
  "mailinator.com",
  "throwawaymail.com",
  "sharklasers.com",
  "yopmail.com",
  "trashmail.com",
  "getnada.com",
  "dispostable.com"
];

/**
 * Checks if the email belongs to a disposable email domain.
 */
export function isDisposableEmail(email: string, customDisposableList: string[] = []): boolean {
  const normalized = email.trim().toLowerCase();
  const domain = normalized.substring(normalized.lastIndexOf("@") + 1);
  const fullList = new Set([...DEFAULT_DISPOSABLE_DOMAINS, ...customDisposableList.map((d) => d.toLowerCase())]);
  return fullList.has(domain);
}
