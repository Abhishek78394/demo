import {useCallback, useEffect, useRef, useState} from 'react';
import {
  DETECT_INTERVAL_MS,
  STABLE_HITS_REQUIRED,
} from '../../constants/detection';
import {
  classifyAudienceImage,
  prepareFaceAudience,
} from '../nativeModules/faceAudience';
import {CATEGORIES} from '../../constants/categories';

/** Clear "face present" only after this many consecutive misses. */
const NO_FACE_HITS_TO_CLEAR = 3;

/**
 * Camera → audience category.
 *
 * - Face confirmed → facePresent=true + category (female/male/child/adult)
 * - No face → facePresent=false (AdPlayer loops all 4 videos)
 * - Sticky: same person keeps the same category; switch needs stable hits
 */
export default function useAudienceDetection({
  cameraRef,
  enabled = true,
  manualCategory = null,
}) {
  const [category, setCategory] = useState(null);
  const [facePresent, setFacePresent] = useState(false);
  const [lastDetection, setLastDetection] = useState(null);
  const [status, setStatus] = useState('booting');
  const categoryRef = useRef(null);
  const pendingRef = useRef(null);
  const hitsRef = useRef(0);
  const noFaceHitsRef = useRef(0);
  const busyRef = useRef(false);

  useEffect(() => {
    categoryRef.current = category;
  }, [category]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await prepareFaceAudience();
        if (!cancelled) {
          setStatus(s => (s === 'manual' ? s : 'ready'));
        }
      } catch (e) {
        if (!cancelled) {
          setStatus('error');
          setLastDetection({error: e?.message || 'Model load failed'});
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (manualCategory) {
      categoryRef.current = manualCategory;
      setCategory(manualCategory);
      setFacePresent(true);
      pendingRef.current = null;
      hitsRef.current = 0;
      noFaceHitsRef.current = 0;
      setStatus('manual');
    } else if (status === 'manual') {
      // Back to auto — resume idle all-4 loop until a face is seen.
      categoryRef.current = null;
      setCategory(null);
      setFacePresent(false);
      setStatus('ready');
    }
  }, [manualCategory]); // eslint-disable-line react-hooks/exhaustive-deps

  const applyStable = useCallback(nextCategory => {
    if (!nextCategory) {
      return;
    }

    noFaceHitsRef.current = 0;

    if (nextCategory === categoryRef.current) {
      setFacePresent(true);
      pendingRef.current = null;
      hitsRef.current = 0;
      return;
    }

    if (pendingRef.current === nextCategory) {
      hitsRef.current += 1;
    } else {
      pendingRef.current = nextCategory;
      hitsRef.current = 1;
    }

    if (hitsRef.current >= STABLE_HITS_REQUIRED) {
      categoryRef.current = nextCategory;
      setCategory(nextCategory);
      setFacePresent(true);
      pendingRef.current = null;
      hitsRef.current = 0;
    }
  }, []);

  useEffect(() => {
    if (!enabled || manualCategory) {
      return undefined;
    }

    const tick = async () => {
      if (busyRef.current) {
        return;
      }
      const cam = cameraRef?.current;
      if (!cam?.takeSnapshot) {
        return;
      }
      busyRef.current = true;
      setStatus(s => (s === 'manual' ? s : 'detecting'));
      try {
        const path = await cam.takeSnapshot();
        if (!path) {
          noFaceHitsRef.current += 1;
          if (noFaceHitsRef.current >= NO_FACE_HITS_TO_CLEAR) {
            setFacePresent(false);
            categoryRef.current = null;
            setCategory(null);
            pendingRef.current = null;
            hitsRef.current = 0;
            setStatus('no_face');
          }
          return;
        }

        const result = await classifyAudienceImage(path);
        setLastDetection(result);

        if (!result?.faceFound || !result?.category) {
          noFaceHitsRef.current += 1;
          if (noFaceHitsRef.current >= NO_FACE_HITS_TO_CLEAR) {
            setFacePresent(false);
            categoryRef.current = null;
            setCategory(null);
            pendingRef.current = null;
            hitsRef.current = 0;
            setStatus('no_face');
          }
          return;
        }

        applyStable(result.category);
        setStatus('ready');
      } catch (e) {
        setLastDetection({error: e?.message || 'Detection failed'});
        setStatus('error');
      } finally {
        busyRef.current = false;
      }
    };

    const startId = setTimeout(tick, 1200);
    const id = setInterval(tick, DETECT_INTERVAL_MS);
    return () => {
      clearTimeout(startId);
      clearInterval(id);
    };
  }, [enabled, manualCategory, cameraRef, applyStable]);

  return {
    category: category || CATEGORIES.FEMALE,
    targetCategory: category,
    facePresent,
    lastDetection,
    status,
  };
}
