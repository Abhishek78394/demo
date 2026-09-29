# Screenista Demo

Offline digital-signage demo: the camera identifies **Child / Male / Female** on-device and plays matching ads. **No backend / internet required** after install.

## How detection + playback works

1. Hidden camera takes a snapshot every ~1s  
2. **ML Kit** finds faces (on-device)  
3. **TFLite** age + gender models classify the largest face  
4. Category mapping:
   - age ≤ 15 → **Child**
   - otherwise → **Male** or **Female**
5. Target category updates after **2 matching reads (~1–2 seconds)**  
6. **Ads never cut mid-play** — when the target changes (female → male, female → child, …), the current creative finishes its full duration, then the new category starts  
7. **No face** — after the current ad completes, rotate **female → male → child → …**

Manual **Auto / Child / Male / Female / Adult** controls remain for demos.

## Run

```bash
cd screenista-demo
npm install
npm start
# other terminal
npm run android
```

Grant camera permission when prompted. Prefer a front or USB camera facing the audience.

## Models (bundled)

- `android/app/src/main/assets/model_lite_age_q.tflite`
- `android/app/src/main/assets/model_lite_gender_q.tflite`

From [shubham0204/Age-Gender_Estimation_TF-Android](https://github.com/shubham0204/Age-Gender_Estimation_TF-Android) (UTKFace-trained). Accuracy is demo-grade, especially on TV/USB cameras.

## Ads (Carrefour + LuLu) and click-through

| Detected | Ads shown |
|----------|-----------|
| Female / girl | `assets/ads/female/carrefour-women.png`, `lulu-women.png` |
| Male / boy | `assets/ads/male/carrefour-men.png`, `lulu-men.png` |
| Child | `assets/ads/child/carrefour-kids.png`, `lulu-kids.png` |
| No face / uncertain | all six, in rotation |

Each image shows for `IMAGE_AD_DURATION_MS` (`src/constants/categories.js`), then the next creative in that category plays.

**Tap the ad** → opens the advertiser website in the browser. **Press & hold** still opens settings.

### Change URLs / add advertisers

Edit `src/data/ads.js`:

- `ADVERTISER_URLS` — one URL per brand (`carrefour`, `lulu`).
- Per-creative override: pass `destinationUrl` to `creative({...})`.
- New advertiser/category: add an image under `src/assets/ads/<category>/`, then a `creative({...})` entry in `ADS_BY_CATEGORY`.
