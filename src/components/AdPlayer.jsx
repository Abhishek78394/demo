import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Image, StyleSheet, View} from 'react-native';
import Video from 'react-native-video';
import {CATEGORY_LABELS, IMAGE_AD_DURATION_MS} from '../constants/categories';
import {ALL_ADS_LOOP, getAdsForCategory} from '../data/ads';
import PlaceholderAd from './PlaceholderAd';

/**
 * Playback rules:
 * - No face  → play all 4 videos in a loop (female → male → child → adult → …)
 * - Female / male / child / adult in view → play that category ad and loop it
 * - Manual category override → same as targeted (loop that ad)
 */
export default function AdPlayer({
  category,
  facePresent = false,
  muted = false,
  orientation = '0',
}) {
  const targeting = Boolean(facePresent && category);
  const ads = useMemo(() => {
    if (targeting) {
      return getAdsForCategory(category);
    }
    return ALL_ADS_LOOP;
  }, [targeting, category]);

  const [index, setIndex] = useState(0);
  const [playKey, setPlayKey] = useState(0);
  const timerRef = useRef(null);
  const modeKey = targeting ? `target:${category}` : 'idle-all';

  // Reset playlist when switching idle ↔ targeted (or target category changes).
  useEffect(() => {
    setIndex(0);
    setPlayKey(k => k + 1);
  }, [modeKey]);

  const advance = useCallback(() => {
    if (ads.length <= 1) {
      // Single targeted ad — remount so it restarts.
      setPlayKey(k => k + 1);
      return;
    }
    setIndex(prev => (prev + 1) % ads.length);
    setPlayKey(k => k + 1);
  }, [ads.length]);

  const current = ads[index] || null;
  const labelCategory = targeting
    ? category
    : current?.category || category;

  useEffect(() => {
    clearTimeout(timerRef.current);
    if (!current || current.type === 'video') {
      return undefined;
    }
    timerRef.current = setTimeout(advance, IMAGE_AD_DURATION_MS);
    return () => clearTimeout(timerRef.current);
  }, [current, advance, index, modeKey, playKey]);

  if (!current) {
    return <View style={styles.fill} />;
  }

  if (current.type === 'placeholder') {
    return (
      <PlaceholderAd
        ad={current}
        categoryLabel={CATEGORY_LABELS[labelCategory]}
      />
    );
  }

  if (current.type === 'image') {
    return (
      <View style={styles.fill} pointerEvents="none">
        <Image source={current.source} style={styles.media} resizeMode="cover" />
      </View>
    );
  }

  if (current.type === 'video') {
    const loopSingle = targeting && ads.length <= 1;
    return (
      <View style={styles.fill} pointerEvents="none" collapsable={false}>
        <Video
          key={`${modeKey}-${current.id}-${playKey}-${orientation}`}
          source={current.source}
          style={styles.media}
          resizeMode="cover"
          repeat={loopSingle}
          muted={muted}
          controls={false}
          pointerEvents="none"
          useTextureView
          onEnd={loopSingle ? undefined : advance}
          onError={advance}
        />
      </View>
    );
  }

  return <View style={styles.fill} />;
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
    backgroundColor: '#000',
  },
  media: {
    ...StyleSheet.absoluteFillObject,
    width: '100%',
    height: '100%',
  },
});
