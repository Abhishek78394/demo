export const CATEGORIES = {
  CHILD: 'child',
  MALE: 'male',
  FEMALE: 'female',
  ADULT: 'adult',
};

export const CATEGORY_LABELS = {
  [CATEGORIES.CHILD]: 'Child',
  [CATEGORIES.MALE]: 'Male',
  [CATEGORIES.FEMALE]: 'Female',
  [CATEGORIES.ADULT]: 'Adult',
};

export const CATEGORY_ORDER = [
  CATEGORIES.CHILD,
  CATEGORIES.MALE,
  CATEGORIES.FEMALE,
  CATEGORIES.ADULT,
];

/**
 * When nobody is in view, cycle ads across these categories after each
 * creative finishes (female → male → child → …).
 */
export const IDLE_ROTATION_ORDER = [
  CATEGORIES.FEMALE,
  CATEGORIES.MALE,
  CATEGORIES.CHILD,
  CATEGORIES.ADULT,
];

/**
 * After tapping an ad, the advertiser site opens inside the app and closes
 * automatically once the user has been idle this long (ads resume).
 */
export const WEB_INACTIVITY_TIMEOUT_MS = 15000;

/** How long image ads stay on screen (digital 16:9 display) */
export const IMAGE_AD_DURATION_MS = 10000;
