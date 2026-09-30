import React, {useCallback, useEffect, useMemo} from 'react';
import {
  BackHandler,
  Image,
  Linking,
  NativeModules,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  ToastAndroid,
  TouchableOpacity,
  useWindowDimensions,
  View,
} from 'react-native';
import DeviceInfo from 'react-native-device-info';
import Icon from 'react-native-vector-icons/MaterialIcons';
import {COLORS} from '../constants/COLORS';
import {CATEGORY_LABELS, CATEGORY_ORDER} from '../constants/categories';

const LOGO = require('../assets/images/screenista-dark-logo.png');
const LOGO_ASPECT = 1440 / 315;

const {EnvConfig} = NativeModules;
const IS_DEV_API = Boolean(EnvConfig?.IS_DEV_API);

function safeUniqueId() {
  try {
    return DeviceInfo.getUniqueIdSync();
  } catch {
    return '';
  }
}

function openIntent(action, failMessage) {
  Linking.sendIntent(action).catch(() => {
    Linking.openSettings().catch(() => {
      ToastAndroid.show(failMessage, ToastAndroid.LONG);
    });
  });
}

/**
 * Full-screen settings (same look as screenista-plus SettingsScreen).
 * Opened / closed by press & hold on the player.
 */
export default function SettingsScreen({
  onClose,
  activeCategory,
  autoMode,
  onAuto,
  onSelect,
  orientation = '0',
  onCycleOrientation,
  muted = false,
  onToggleMute,
  cameraPreview = false,
  onToggleCameraPreview,
  cameraStatus = '',
}) {
  const {width: screenWidth} = useWindowDimensions();

  const uniqueId = safeUniqueId();
  const appVersion = DeviceInfo.getVersion();
  const versionCode = DeviceInfo.getBuildNumber();
  const apiEnvLabel = IS_DEV_API ? 'Dev' : 'Prod';

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose?.();
      return true;
    });
    return () => sub.remove();
  }, [onClose]);

  const audienceLabel = autoMode
    ? 'Auto (camera)'
    : CATEGORY_LABELS[activeCategory] || 'Auto (camera)';

  const cycleAudience = useCallback(() => {
    // Auto → Child → Male → Female → Adult → Auto …
    if (autoMode) {
      onSelect?.(CATEGORY_ORDER[0]);
      return;
    }
    const idx = CATEGORY_ORDER.indexOf(activeCategory);
    if (idx < 0 || idx === CATEGORY_ORDER.length - 1) {
      onAuto?.();
    } else {
      onSelect?.(CATEGORY_ORDER[idx + 1]);
    }
  }, [autoMode, activeCategory, onAuto, onSelect]);

  const options = useMemo(
    () => [
      {
        label: 'Camera',
        subtitle: cameraPreview
          ? 'Preview on'
          : cameraStatus || 'Show live preview',
        onPress: onToggleCameraPreview,
        icon: 'videocam',
        highlight: cameraPreview,
      },
      {
        label: 'Audience',
        subtitle: audienceLabel,
        onPress: cycleAudience,
        icon: 'people',
        highlight: !autoMode,
      },
      {
        label: 'Orientation',
        subtitle: `${orientation || 0}°`,
        onPress: onCycleOrientation,
        icon: 'screen-rotation',
      },
      {
        label: muted ? 'Unmute Ads' : 'Mute Ads',
        subtitle: muted ? 'Sound off' : 'Sound on',
        onPress: onToggleMute,
        icon: muted ? 'volume-off' : 'volume-up',
        highlight: muted,
      },
      {
        label: 'Wi-Fi',
        subtitle: 'Connect to a network',
        onPress: () =>
          openIntent('android.settings.WIFI_SETTINGS', 'Could not open Wi-Fi settings'),
        icon: 'wifi',
      },
      {
        label: 'App Settings',
        onPress: () => Linking.openSettings(),
        icon: 'app-settings-alt',
      },
      {
        label: 'System Settings',
        subtitle: 'Wi-Fi, display, etc.',
        onPress: () =>
          openIntent('android.settings.SETTINGS', 'Could not open system settings'),
        icon: 'settings',
      },
    ],
    [
      cameraPreview,
      cameraStatus,
      onToggleCameraPreview,
      audienceLabel,
      autoMode,
      cycleAudience,
      orientation,
      onCycleOrientation,
      muted,
      onToggleMute,
    ],
  );

  const isTV = screenWidth > 900;
  const numColumns = isTV ? 3 : 2;
  const cardWidth = (screenWidth - 48 - (numColumns - 1) * 16) / numColumns;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <TouchableOpacity
            onPress={onClose}
            style={styles.backButton}
            activeOpacity={0.7}>
            <Icon name="arrow-back" size={24} color={COLORS.white} />
          </TouchableOpacity>
          <Image
            source={LOGO}
            resizeMode="contain"
            style={{width: 140, height: 140 / LOGO_ASPECT}}
          />
        </View>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>General Settings</Text>
          <View style={styles.grid}>
            {options.map(item => (
              <TouchableOpacity
                key={item.label}
                onPress={item.onPress}
                style={[
                  styles.optionCard,
                  {width: cardWidth},
                  item.highlight && styles.highlightCard,
                ]}
                activeOpacity={0.7}>
                <View
                  style={[
                    styles.iconBox,
                    item.highlight && styles.highlightIconBox,
                  ]}>
                  <Icon name={item.icon} size={32} color={COLORS.white} />
                </View>
                <View style={styles.textColumn}>
                  <Text style={styles.optionLabel} numberOfLines={1}>
                    {item.label}
                  </Text>
                  {item.subtitle ? (
                    <Text style={styles.optionSubtitle} numberOfLines={1}>
                      {item.subtitle}
                    </Text>
                  ) : null}
                </View>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Device Information</Text>
          <View style={styles.deviceInfoCard}>
            <Text style={styles.deviceIdValue} numberOfLines={1}>
              <Text style={styles.deviceIdLabel}>Device ID:- </Text>
              {uniqueId || 'Not Available'}
            </Text>

            <View style={styles.infoDivider} />

            <View style={styles.metaGrid}>
              <View style={styles.metaItem}>
                <Text style={styles.detailLabel}>App Version</Text>
                <Text style={styles.metaValue}>
                  {appVersion}
                  <Text style={styles.metaHint}> ({versionCode})</Text>
                </Text>
              </View>

              <View style={styles.metaItem}>
                <Text style={styles.detailLabel}>Environment</Text>
                <View
                  style={[
                    styles.envBadge,
                    IS_DEV_API ? styles.envBadgeDev : styles.envBadgeProd,
                  ]}>
                  <View
                    style={[
                      styles.envDot,
                      IS_DEV_API ? styles.envDotDev : styles.envDotProd,
                    ]}
                  />
                  <Text style={styles.envBadgeText}>{apiEnvLabel}</Text>
                </View>
              </View>
            </View>
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: COLORS.background,
    zIndex: 15,
    elevation: 15,
  },
  header: {
    width: '100%',
    height: 60 + (StatusBar.currentHeight || 0),
    paddingTop: StatusBar.currentHeight || 0,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: COLORS.background,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.1)',
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  backButton: {
    padding: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
  },
  scrollContent: {
    padding: 24,
    paddingBottom: 40,
  },
  section: {
    marginBottom: 32,
  },
  sectionTitle: {
    color: COLORS.white,
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    opacity: 0.8,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
  },
  optionCard: {
    backgroundColor: COLORS.card,
    borderRadius: 16,
    padding: 16,
    flexDirection: 'column',
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.2,
    shadowRadius: 8,
  },
  highlightCard: {
    borderColor: '#4CAF50',
    backgroundColor: 'rgba(76, 175, 80, 0.15)',
  },
  iconBox: {
    width: 56,
    height: 56,
    borderRadius: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  highlightIconBox: {
    backgroundColor: '#4CAF50',
  },
  textColumn: {
    alignItems: 'center',
    width: '100%',
  },
  optionLabel: {
    color: COLORS.white,
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  optionSubtitle: {
    color: 'rgba(255, 255, 255, 0.5)',
    fontSize: 13,
    marginTop: 2,
    textAlign: 'center',
  },
  deviceInfoCard: {
    backgroundColor: COLORS.card,
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  detailLabel: {
    color: 'rgba(255, 255, 255, 0.45)',
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.4,
    textTransform: 'uppercase',
    marginBottom: 6,
  },
  deviceIdValue: {
    color: COLORS.white,
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
  deviceIdLabel: {
    color: 'rgba(255, 255, 255, 0.55)',
    fontSize: 16,
    fontWeight: '600',
    letterSpacing: 0,
  },
  infoDivider: {
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    marginVertical: 18,
  },
  metaGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
  },
  metaItem: {
    flexGrow: 1,
    flexBasis: 140,
    minWidth: 120,
  },
  metaValue: {
    color: COLORS.white,
    fontSize: 17,
    fontWeight: '700',
  },
  metaHint: {
    color: 'rgba(255, 255, 255, 0.45)',
    fontSize: 14,
    fontWeight: '500',
  },
  envBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 8,
    alignSelf: 'flex-start',
  },
  envBadgeDev: {
    backgroundColor: 'rgba(255, 167, 38, 0.18)',
  },
  envBadgeProd: {
    backgroundColor: 'rgba(76, 175, 80, 0.18)',
  },
  envDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
  },
  envDotDev: {
    backgroundColor: '#FFA726',
  },
  envDotProd: {
    backgroundColor: '#4CAF50',
  },
  envBadgeText: {
    color: COLORS.white,
    fontSize: 14,
    fontWeight: '700',
  },
});
