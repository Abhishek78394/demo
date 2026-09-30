/** How often we snap + classify (ms). */
export const DETECT_INTERVAL_MS = 1500;

/**
 * Consecutive matching reads of a *new* category before switching ads.
 * 2 = the camera must see the new person type twice in a row (avoids flicker).
 */
export const STABLE_HITS_REQUIRED = 2;

/** After manual override, auto resumes after this many ms (0 = stay manual until Auto). */
export const MANUAL_OVERRIDE_MS = 0;
