import React from 'react';
import {Linking, Pressable, StyleSheet, Text, View} from 'react-native';
import {
  CATEGORY_LABELS,
  CATEGORY_ORDER,
} from '../constants/categories';

function Chip({label, selected, onPress}) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.btn, selected && styles.btnActive]}>
      <Text style={[styles.btnText, selected && styles.btnTextActive]}>
        {label}
      </Text>
    </Pressable>
  );
}

/**
 * Long-press settings: audience override + orientation + system shortcuts.
 */
export default function AudiencePicker({
  activeCategory,
  onSelect,
  onAuto,
  autoMode,
  visible,
  orientation = '0',
  onCycleOrientation,
  muted = false,
  onToggleMute,
  onClose,
}) {
  if (!visible) {
    return null;
  }

  const openWifi = () => {
    Linking.sendIntent('android.settings.WIFI_SETTINGS').catch(() => {
      Linking.openSettings().catch(() => {});
    });
  };

  const openAppSettings = () => {
    Linking.openSettings().catch(() => {});
  };

  const openSystemSettings = () => {
    Linking.sendIntent('android.settings.SETTINGS').catch(() => {
      Linking.openSettings().catch(() => {});
    });
  };

  return (
    <View style={styles.wrap} pointerEvents="box-none">
      <View style={styles.panel}>
        <View style={styles.headerRow}>
          <Text style={styles.heading}>Settings</Text>
          {onClose ? (
            <Pressable onPress={onClose} style={styles.closeBtn} hitSlop={12}>
              <Text style={styles.closeText}>Close</Text>
            </Pressable>
          ) : null}
        </View>
        <Text style={styles.hint}>
          Press & hold the screen to hide. Auto uses camera face / age / gender.
        </Text>

        <Text style={styles.section}>Audience</Text>
        <View style={styles.row}>
          <Chip label="Auto" selected={autoMode} onPress={onAuto} />
          {CATEGORY_ORDER.map(key => (
            <Chip
              key={key}
              label={CATEGORY_LABELS[key]}
              selected={!autoMode && key === activeCategory}
              onPress={() => onSelect(key)}
            />
          ))}
        </View>

        <Text style={styles.section}>Display</Text>
        <View style={styles.row}>
          <Chip
            label={`Orientation ${orientation}°`}
            selected={orientation !== '0'}
            onPress={onCycleOrientation}
          />
          <Chip
            label={muted ? 'Unmute ads' : 'Mute ads'}
            selected={muted}
            onPress={onToggleMute}
          />
        </View>

        <Text style={styles.section}>Device</Text>
        <View style={styles.row}>
          <Chip label="Wi-Fi" selected={false} onPress={openWifi} />
          <Chip label="App Settings" selected={false} onPress={openAppSettings} />
          <Chip
            label="System Settings"
            selected={false}
            onPress={openSystemSettings}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
    alignItems: 'center',
    paddingBottom: 28,
    zIndex: 50,
    elevation: 50,
  },
  panel: {
    backgroundColor: 'rgba(8, 10, 14, 0.9)',
    borderRadius: 16,
    paddingHorizontal: 22,
    paddingVertical: 16,
    maxWidth: 980,
    width: '94%',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  heading: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
  },
  closeBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  closeText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '600',
  },
  hint: {
    color: 'rgba(255,255,255,0.55)',
    fontSize: 13,
    marginBottom: 12,
  },
  section: {
    color: 'rgba(255,255,255,0.45)',
    fontSize: 11,
    letterSpacing: 1.2,
    fontWeight: '700',
    marginTop: 10,
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  btn: {
    paddingHorizontal: 16,
    paddingVertical: 11,
    borderRadius: 10,
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
  },
  btnActive: {
    backgroundColor: '#FFFFFF',
    borderColor: '#FFFFFF',
  },
  btnText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 14,
    fontWeight: '600',
  },
  btnTextActive: {
    color: '#0B0D12',
  },
});
