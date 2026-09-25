import {Dimensions, StyleSheet} from 'react-native';

/** Degrees: '0' | '90' | '180' | '270' */
export function normalizeOrientation(value) {
  const n = parseInt(String(value ?? '0'), 10);
  if (Number.isNaN(n)) {
    return '0';
  }
  const deg = ((n % 360) + 360) % 360;
  if (deg === 90 || deg === 180 || deg === 270) {
    return String(deg);
  }
  return '0';
}

export function nextOrientation(current) {
  const deg = parseInt(normalizeOrientation(current), 10);
  return String((deg + 90) % 360);
}

/**
 * CSS-style rotation so digital signage can mount sideways without
 * changing the Android activity orientation.
 */
export function getRotationStyle(orientation) {
  const {width: SW, height: SH} = Dimensions.get('window');
  const deg = normalizeOrientation(orientation);

  if (deg === '90') {
    return {
      position: 'absolute',
      width: SH,
      height: SW,
      top: (SH - SW) / 2,
      left: -(SH - SW) / 2,
      transform: [{rotate: '90deg'}],
    };
  }
  if (deg === '270') {
    return {
      position: 'absolute',
      width: SH,
      height: SW,
      top: (SH - SW) / 2,
      left: -(SH - SW) / 2,
      transform: [{rotate: '270deg'}],
    };
  }
  if (deg === '180') {
    return {
      ...StyleSheet.absoluteFillObject,
      transform: [{rotate: '180deg'}],
    };
  }
  return StyleSheet.absoluteFillObject;
}
