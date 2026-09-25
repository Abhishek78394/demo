import React from 'react';
import {StyleSheet, Text, View} from 'react-native';

export default function PlaceholderAd({ad, categoryLabel}) {
  return (
    <View style={[styles.root, {backgroundColor: ad.background}]}>
      <View style={[styles.accentBar, {backgroundColor: ad.accent}]} />
      <Text style={styles.eyebrow}>{categoryLabel?.toUpperCase()} AD</Text>
      <Text style={styles.title}>{ad.title}</Text>
      <Text style={styles.subtitle}>{ad.subtitle}</Text>
      <View style={[styles.chip, {borderColor: ad.accent}]}>
        <Text style={[styles.chipText, {color: ad.accent}]}>Screenista Demo</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 64,
    paddingVertical: 48,
  },
  accentBar: {
    width: 72,
    height: 6,
    borderRadius: 3,
    marginBottom: 28,
  },
  eyebrow: {
    color: 'rgba(255,255,255,0.55)',
    fontSize: 18,
    letterSpacing: 4,
    fontWeight: '600',
    marginBottom: 16,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 64,
    fontWeight: '700',
    letterSpacing: -1,
    marginBottom: 16,
  },
  subtitle: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: 28,
    fontWeight: '400',
    maxWidth: 720,
    marginBottom: 40,
  },
  chip: {
    alignSelf: 'flex-start',
    borderWidth: 1,
    borderRadius: 4,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  chipText: {
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: 1,
  },
});
