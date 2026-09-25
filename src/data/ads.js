import {CATEGORIES} from '../constants/categories';

/**
 * Demo ad catalog — camera picks the playlist:
 *   child  → child/ad1.mp4
 *   male   → male/ad1.mp4
 *   female → female/ad1.mp4
 *   adult  → adult/ad1.mp4
 *
 * When nobody is in view, ALL_ADS_LOOP plays every video in sequence.
 */
export const ADS_BY_CATEGORY = {
  [CATEGORIES.CHILD]: [
    {
      id: 'child-1',
      type: 'video',
      source: require('../assets/ads/child/ad1.mp4'),
      category: CATEGORIES.CHILD,
    },
  ],
  [CATEGORIES.MALE]: [
    {
      id: 'male-1',
      type: 'video',
      source: require('../assets/ads/male/ad1.mp4'),
      category: CATEGORIES.MALE,
    },
  ],
  [CATEGORIES.FEMALE]: [
    {
      id: 'female-1',
      type: 'video',
      source: require('../assets/ads/female/ad1.mp4'),
      category: CATEGORIES.FEMALE,
    },
  ],
  [CATEGORIES.ADULT]: [
    {
      id: 'adult-1',
      type: 'video',
      source: require('../assets/ads/adult/ad1.mp4'),
      category: CATEGORIES.ADULT,
    },
  ],
};

/** Default playlist when no face is detected — all 4 videos, in order. */
export const ALL_ADS_LOOP = [
  ...ADS_BY_CATEGORY[CATEGORIES.FEMALE],
  ...ADS_BY_CATEGORY[CATEGORIES.MALE],
  ...ADS_BY_CATEGORY[CATEGORIES.CHILD],
  ...ADS_BY_CATEGORY[CATEGORIES.ADULT],
];

export function getAdsForCategory(category) {
  return ADS_BY_CATEGORY[category] || ADS_BY_CATEGORY[CATEGORIES.ADULT];
}
