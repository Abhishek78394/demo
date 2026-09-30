import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  ActivityIndicator,
  BackHandler,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {WebView} from 'react-native-webview';
import {WEB_INACTIVITY_TIMEOUT_MS} from '../constants/categories';

/**
 * Reports user activity from inside the page (touch / scroll / key / click)
 * so the inactivity timer also resets for interaction the RN layer can't see.
 */
const ACTIVITY_SCRIPT = `
(function () {
  var last = 0;
  function ping() {
    var now = Date.now();
    if (now - last < 1000) { return; }
    last = now;
    window.ReactNativeWebView && window.ReactNativeWebView.postMessage('activity');
  }
  ['touchstart', 'touchmove', 'scroll', 'click', 'keydown', 'mousemove']
    .forEach(function (evt) {
      window.addEventListener(evt, ping, {passive: true, capture: true});
    });
})();
true;
`;

const isWebUrl = url => /^https?:\/\//i.test(String(url || ''));

/**
 * Full-screen in-app browser for an ad's destination URL.
 *
 * - Stays inside the app (no Chrome hand-off).
 * - Auto-closes after WEB_INACTIVITY_TIMEOUT_MS without interaction, so the
 *   ad loop underneath simply carries on.
 * - Back key / Close button also dismiss it.
 */
export default function AdWebViewOverlay({url, onClose}) {
  const lastActivityRef = useRef(Date.now());
  const closedRef = useRef(false);
  const [remainingSec, setRemainingSec] = useState(
    Math.ceil(WEB_INACTIVITY_TIMEOUT_MS / 1000),
  );
  const [loading, setLoading] = useState(true);

  const close = useCallback(() => {
    if (closedRef.current) {
      return;
    }
    closedRef.current = true;
    onClose?.();
  }, [onClose]);

  const markActivity = useCallback(() => {
    lastActivityRef.current = Date.now();
  }, []);

  // Fresh timer each time a new URL is opened.
  useEffect(() => {
    closedRef.current = false;
    lastActivityRef.current = Date.now();
    setRemainingSec(Math.ceil(WEB_INACTIVITY_TIMEOUT_MS / 1000));
    setLoading(true);
  }, [url]);

  useEffect(() => {
    const id = setInterval(() => {
      const idleMs = Date.now() - lastActivityRef.current;
      const left = WEB_INACTIVITY_TIMEOUT_MS - idleMs;
      if (left <= 0) {
        close();
        return;
      }
      setRemainingSec(Math.ceil(left / 1000));
    }, 500);
    return () => clearInterval(id);
  }, [close]);

  // Remote / hardware Back closes the page instead of exiting the app.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      close();
      return true;
    });
    return () => sub.remove();
  }, [close]);

  if (!isWebUrl(url)) {
    return null;
  }

  return (
    <View
      style={styles.root}
      // Observe touches without consuming them (page still receives them).
      onStartShouldSetResponderCapture={() => {
        markActivity();
        return false;
      }}
      onMoveShouldSetResponderCapture={() => {
        markActivity();
        return false;
      }}>
      <View style={styles.bar}>
        <Text style={styles.urlText} numberOfLines={1}>
          {url}
        </Text>
        <Text style={styles.timerText}>Closes in {remainingSec}s</Text>
        <Pressable onPress={close} style={styles.closeBtn} hitSlop={12}>
          <Text style={styles.closeText}>Close</Text>
        </Pressable>
      </View>

      <View style={styles.webWrap}>
        <WebView
          source={{uri: url}}
          style={styles.web}
          originWhitelist={['http://*', 'https://*']}
          // Keep everything in this WebView; never jump to other apps.
          onShouldStartLoadWithRequest={req => isWebUrl(req.url)}
          setSupportMultipleWindows={false}
          javaScriptEnabled
          domStorageEnabled
          injectedJavaScript={ACTIVITY_SCRIPT}
          onMessage={markActivity}
          onNavigationStateChange={markActivity}
          onLoadStart={() => {
            markActivity();
            setLoading(true);
          }}
          onLoadEnd={() => {
            markActivity();
            setLoading(false);
          }}
          onScroll={markActivity}
          // Page failed (offline, DNS, …): drop back to the ads.
          onError={close}
          onHttpError={e => {
            if (e?.nativeEvent?.statusCode >= 500) {
              close();
            }
          }}
        />
        {loading ? (
          <View style={styles.loader} pointerEvents="none">
            <ActivityIndicator size="large" color="#FFFFFF" />
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 40,
    elevation: 40,
    backgroundColor: '#000',
  },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: 'rgba(12, 14, 20, 0.96)',
  },
  urlText: {
    flex: 1,
    color: 'rgba(255,255,255,0.7)',
    fontSize: 13,
  },
  timerText: {
    color: 'rgba(255,255,255,0.55)',
    fontSize: 12,
    marginHorizontal: 14,
  },
  closeBtn: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: 'rgba(255,255,255,0.14)',
  },
  closeText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  webWrap: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  web: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  loader: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
});
