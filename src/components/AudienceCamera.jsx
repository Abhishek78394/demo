import React, {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react';
import {StyleSheet, View} from 'react-native';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
} from 'react-native-vision-camera';

/**
 * Always-on camera for audience snapshots.
 * Prefer external USB → front → back.
 *
 * Must stay on-screen with a real size — off-screen / 1×1 views often
 * produce blank frames on MediaTek / TV boxes and break detection.
 */
const AudienceCamera = forwardRef(function AudienceCamera(
  {onStatusChange, preview = true},
  ref,
) {
  const {hasPermission, requestPermission} = useCameraPermission();
  const externalDevice = useCameraDevice('external');
  const frontDevice = useCameraDevice('front');
  const backDevice = useCameraDevice('back');
  const device = hasPermission
    ? externalDevice ?? frontDevice ?? backDevice
    : undefined;
  const cameraRef = useRef(null);
  const [active, setActive] = useState(false);

  useEffect(() => {
    if (!hasPermission) {
      requestPermission();
    }
  }, [hasPermission, requestPermission]);

  useEffect(() => {
    setActive(Boolean(hasPermission && device));
    onStatusChange?.({
      hasPermission,
      hasDevice: Boolean(device),
      deviceName: device?.name ?? null,
    });
  }, [hasPermission, device, onStatusChange]);

  useImperativeHandle(
    ref,
    () => ({
      async takeSnapshot() {
        if (!cameraRef.current || !active) {
          return null;
        }
        try {
          const photo = await cameraRef.current.takePhoto({
            flash: 'off',
            enableShutterSound: false,
          });
          if (!photo?.path) {
            return null;
          }
          return photo.path.startsWith('file://')
            ? photo.path
            : `file://${photo.path}`;
        } catch (e) {
          console.warn('[AudienceCamera] takePhoto failed', e?.message || e);
          return null;
        }
      },
    }),
    [active],
  );

  if (!device || !hasPermission) {
    return null;
  }

  return (
    <View
      style={preview ? styles.previewWrap : styles.tinyWrap}
      pointerEvents="none">
      <Camera
        ref={cameraRef}
        style={styles.sensor}
        device={device}
        isActive={active}
        photo={true}
        audio={false}
        photoQualityBalance="speed"
      />
    </View>
  );
});

export default AudienceCamera;

const styles = StyleSheet.create({
  previewWrap: {
    position: 'absolute',
    right: 20,
    bottom: 140,
    width: 160,
    height: 120,
    borderRadius: 10,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
    zIndex: 20,
    backgroundColor: '#111',
  },
  // Still on-screen (tiny) so the sensor keeps delivering frames.
  tinyWrap: {
    position: 'absolute',
    right: 8,
    bottom: 8,
    width: 48,
    height: 36,
    borderRadius: 4,
    overflow: 'hidden',
    opacity: 0.15,
    zIndex: 5,
  },
  sensor: {
    width: '100%',
    height: '100%',
  },
});
