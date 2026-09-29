import {Linking} from 'react-native';

/**
 * Opens an advertiser landing page in the device browser.
 * Never throws — a bad URL or missing browser must not crash the ad loop.
 */
export async function openAdUrl(url) {
  const target = typeof url === 'string' ? url.trim() : '';
  if (!/^https?:\/\//i.test(target)) {
    return false;
  }
  try {
    await Linking.openURL(target);
    return true;
  } catch (e) {
    console.warn('[openAdUrl] failed', target, e?.message || e);
    return false;
  }
}
