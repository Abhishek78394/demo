import {NativeModules} from 'react-native';

const {EnvConfig} = NativeModules;

export async function getPref(key, fallback = null) {
  try {
    if (!EnvConfig?.getPref) {
      return fallback;
    }
    const value = await EnvConfig.getPref(key);
    return value == null ? fallback : value;
  } catch {
    return fallback;
  }
}

export async function setPref(key, value) {
  try {
    if (!EnvConfig?.setPref) {
      return false;
    }
    await EnvConfig.setPref(key, String(value));
    return true;
  } catch {
    return false;
  }
}

/** Apply 0 / 90 / 180 / 270 to the Android activity (rotates ads + UI). */
export async function setOrientationDegrees(degrees) {
  try {
    if (!EnvConfig?.setOrientationDegrees) {
      return false;
    }
    await EnvConfig.setOrientationDegrees(Number(degrees) || 0);
    return true;
  } catch {
    return false;
  }
}

export const PREF_KEYS = {
  ORIENTATION: 'orientation',
  MUTED: 'muted',
};
