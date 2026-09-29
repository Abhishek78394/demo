import {CATEGORIES} from '../constants/categories';

/**
 * Change brand landing pages here — every creative for that advertiser
 * uses the same URL unless a creative overrides `destinationUrl`.
 */
export const ADVERTISER_URLS = {
  carrefour: 'https://www.carrefouruae.com',
  lulu: 'https://www.luluhypermarket.com',
};

function creative({id, category, advertiser, source, destinationUrl}) {
  return {
    id,
    type: 'image',
    source,
    category,
    advertiser,
    destinationUrl: destinationUrl || ADVERTISER_URLS[advertiser],
  };
}

const CARREFOUR_WOMEN = creative({
  id: 'carrefour-women',
  category: CATEGORIES.FEMALE,
  advertiser: 'carrefour',
  source: require('../assets/ads/female/carrefour-women.png'),
});

const LULU_WOMEN = creative({
  id: 'lulu-women',
  category: CATEGORIES.FEMALE,
  advertiser: 'lulu',
  source: require('../assets/ads/female/lulu-women.png'),
});

const CARREFOUR_MEN = creative({
  id: 'carrefour-men',
  category: CATEGORIES.MALE,
  advertiser: 'carrefour',
  source: require('../assets/ads/male/carrefour-men.png'),
});

const LULU_MEN = creative({
  id: 'lulu-men',
  category: CATEGORIES.MALE,
  advertiser: 'lulu',
  source: require('../assets/ads/male/lulu-men.png'),
});

const CARREFOUR_KIDS = creative({
  id: 'carrefour-kids',
  category: CATEGORIES.CHILD,
  advertiser: 'carrefour',
  source: require('../assets/ads/child/carrefour-kids.png'),
});

const LULU_KIDS = creative({
  id: 'lulu-kids',
  category: CATEGORIES.CHILD,
  advertiser: 'lulu',
  source: require('../assets/ads/child/lulu-kids.png'),
});

/**
 * Camera picks the playlist:
 *   female / girls → women creatives
 *   male / boys    → men creatives
 *   child / kids   → kids creatives
 *   adult (manual) → all creatives
 *
 * When nobody is in view, ALL_ADS_LOOP rotates every creative.
 */
export const ADS_BY_CATEGORY = {
  [CATEGORIES.FEMALE]: [CARREFOUR_WOMEN, LULU_WOMEN],
  [CATEGORIES.MALE]: [CARREFOUR_MEN, LULU_MEN],
  [CATEGORIES.CHILD]: [CARREFOUR_KIDS, LULU_KIDS],
  [CATEGORIES.ADULT]: [
    CARREFOUR_WOMEN,
    LULU_WOMEN,
    CARREFOUR_MEN,
    LULU_MEN,
    CARREFOUR_KIDS,
    LULU_KIDS,
  ],
};

/** Default playlist when no face is detected. */
export const ALL_ADS_LOOP = [
  ...ADS_BY_CATEGORY[CATEGORIES.FEMALE],
  ...ADS_BY_CATEGORY[CATEGORIES.MALE],
  ...ADS_BY_CATEGORY[CATEGORIES.CHILD],
];

export function getAdsForCategory(category) {
  return ADS_BY_CATEGORY[category] || ALL_ADS_LOOP;
}
