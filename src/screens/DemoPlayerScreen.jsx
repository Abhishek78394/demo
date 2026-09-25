import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';
import {Gesture, GestureDetector} from 'react-native-gesture-handler';
import ImmersiveMode from 'react-native-immersive-mode';
import AdPlayer from '../components/AdPlayer';
import AudienceCamera from '../components/AudienceCamera';
import AudiencePicker from '../components/AudiencePicker';
import {CATEGORY_LABELS} from '../constants/categories';
import useAudienceDetection from '../utils/hooks/useAudienceDetection';
import {
  getPref,
  setPref,
  setOrientationDegrees,
  PREF_KEYS,
} from '../utils/nativeModules/demoPrefs';
import {
  nextOrientation,
  normalizeOrientation,
} from '../utils/orientation';

/**
 * Full-screen ads with camera audience targeting.
 * No SET button — press & hold anywhere to open / close settings.
 */
export default function DemoPlayerScreen() {
  const cameraRef = useRef(null);
  const [controlsVisible, setControlsVisible] = useState(false);
  const [manualCategory, setManualCategory] = useState(null);
  const [cameraInfo, setCameraInfo] = useState(null);
  const [orientation, setOrientation] = useState('0');
  const [muted, setMuted] = useState(false);
  const [prefsReady, setPrefsReady] = useState(false);

  const {category, lastDetection, status, facePresent} = useAudienceDetection({
    cameraRef,
    enabled: true,
    manualCategory,
  });

  const displayCategoryLabel = facePresent
    ? CATEGORY_LABELS[category]
    : 'All ads (loop)';

  useEffect(() => {
    try {
      ImmersiveMode.fullLayout(true);
      ImmersiveMode.setBarMode('FullSticky');
    } catch {
      // best-effort
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [savedOrientation, savedMuted] = await Promise.all([
        getPref(PREF_KEYS.ORIENTATION, '0'),
        getPref(PREF_KEYS.MUTED, '0'),
      ]);
      if (cancelled) {
        return;
      }
      const deg = normalizeOrientation(savedOrientation);
      setOrientation(deg);
      setMuted(savedMuted === '1' || savedMuted === 'true');
      await setOrientationDegrees(deg);
      setPrefsReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const toggleControls = useCallback(() => {
    setControlsVisible(v => !v);
  }, []);

  const cycleOrientation = useCallback(() => {
    setOrientation(prev => {
      const next = nextOrientation(prev);
      setPref(PREF_KEYS.ORIENTATION, next);
      setOrientationDegrees(next);
      return next;
    });
  }, []);

  const toggleMute = useCallback(() => {
    setMuted(prev => {
      const next = !prev;
      setPref(PREF_KEYS.MUTED, next ? '1' : '0');
      return next;
    });
  }, []);

  // Gesture-handler long-press (works over TextureView video).
  const longPressGesture = useMemo(
    () =>
      Gesture.LongPress()
        .minDuration(500)
        .maxDistance(80)
        .runOnJS(true)
        .onEnd((_e, success) => {
          if (success) {
            toggleControls();
          }
        }),
    [toggleControls],
  );

  const detectionLine = (() => {
    if (manualCategory) {
      return 'Manual override';
    }
    if (status === 'error') {
      return lastDetection?.error || 'Detection error';
    }
    if (!cameraInfo?.hasPermission) {
      return 'Camera permission needed';
    }
    if (!cameraInfo?.hasDevice) {
      return 'No camera found';
    }
    if (status === 'detecting') {
      return 'Scanning…';
    }
    if (lastDetection?.faceFound && lastDetection?.category) {
      return `Detected ~${lastDetection.ageYears}yrs · ${lastDetection.gender}`;
    }
    if (status === 'no_face' || !facePresent) {
      return 'No face — looping all 4 ads';
    }
    return 'Camera auto · offline';
  })();

  return (
    <View style={styles.outer}>
      {/* Ads never steal touches */}
      <View style={styles.mediaLayer} pointerEvents="none">
        <AdPlayer
          category={category}
          facePresent={facePresent || Boolean(manualCategory)}
          muted={muted}
          orientation={orientation}
        />
      </View>

      <AudienceCamera
        ref={cameraRef}
        preview={controlsVisible}
        onStatusChange={setCameraInfo}
      />

      {/*
        Full-screen hold layer above video.
        Uses both RNGH LongPress + RN Pressable for device compatibility.
      */}
      <GestureDetector gesture={longPressGesture}>
        <Pressable
          style={styles.touchLayer}
          onLongPress={toggleControls}
          delayLongPress={500}
          // Let settings panel (higher zIndex) receive taps when open.
          pointerEvents={controlsVisible ? 'box-none' : 'auto'}
        />
      </GestureDetector>

      {controlsVisible ? (
        <View style={styles.badge} pointerEvents="none">
          <Text style={styles.badgeLabel}>NOW TARGETING</Text>
          <Text style={styles.badgeValue}>{displayCategoryLabel}</Text>
          <Text style={styles.badgeMeta}>{detectionLine}</Text>
          {prefsReady ? (
            <Text style={styles.badgeMeta}>
              Orientation {orientation}° · {muted ? 'Muted' : 'Sound on'}
            </Text>
          ) : null}
          <Text style={styles.badgeMeta}>Press & hold to close</Text>
        </View>
      ) : null}

      <AudiencePicker
        visible={controlsVisible}
        activeCategory={category}
        autoMode={!manualCategory}
        onAuto={() => setManualCategory(null)}
        onSelect={setManualCategory}
        orientation={orientation}
        onCycleOrientation={cycleOrientation}
        muted={muted}
        onToggleMute={toggleMute}
        onClose={() => setControlsVisible(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  outer: {
    flex: 1,
    backgroundColor: '#000',
  },
  mediaLayer: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 1,
  },
  touchLayer: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 10,
    backgroundColor: 'transparent',
  },
  badge: {
    position: 'absolute',
    top: 28,
    left: 28,
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 10,
    maxWidth: 360,
    zIndex: 25,
  },
  badgeLabel: {
    color: 'rgba(255,255,255,0.55)',
    fontSize: 11,
    letterSpacing: 2,
    fontWeight: '600',
    marginBottom: 2,
  },
  badgeValue: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '700',
  },
  badgeMeta: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 13,
    marginTop: 4,
  },
});
