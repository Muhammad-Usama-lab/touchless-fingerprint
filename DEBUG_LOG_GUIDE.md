# 🔍 Debug Log Guide - Fingerprint Extraction

## What to Look For in Logs

### ✅ **SUCCESS Pattern:**
```
ROICalculator: ✓ Calculated ROI: 85x120px | FingerLength: 95px | Image: 640x480
FingerprintExtractor: ========== STARTING FINGERPRINT EXTRACTION ==========
FingerprintExtractor: → Processing THUMB...
FingerprintExtractor: ROI valid: 85x120 at (125, 89)
FingerprintExtractor: Cropped THUMB: 85x120px
FingerprintExtractor: THUMB quality score: 72/100
FingerprintExtractor: ✓ THUMB: ACCEPTED (quality: 72)
... (repeat for INDEX, MIDDLE, RING, PINKY)
FingerprintExtractor: ========== EXTRACTION COMPLETE: 5/5 fingerprints ==========
RMST Biomterics: ====================================================
RMST Biomterics: ✅ SUCCESS: Extracted 5/5 fingerprints
RMST Biomterics: Overall quality: 68/100
RMST Biomterics:   THUMB: 72/100
RMST Biomterics:   INDEX: 65/100
RMST Biomterics:   MIDDLE: 70/100
RMST Biomterics:   RING: 68/100
RMST Biomterics:   PINKY: 66/100
RMST Biomterics: ====================================================
RMST Biomterics: 💾 Saving images to gallery...
RMST Biomterics:   ✓ Saved hand image: 2025-12-11-01-23-45-123_HAND
RMST Biomterics:   ✓ Saved THUMB: 85x120px (quality: 72)
RMST Biomterics:   ✓ Saved INDEX: 80x115px (quality: 65)
...
RMST Biomterics: 💾 All images saved to Pictures/HandLandmarker/
```

---

### ❌ **FAILURE Pattern (OLD - should NOT happen now):**
```
ROICalculator: ✓ Calculated ROI: 278x13px | FingerLength: 95px | Image: 640x480
FingerprintExtractor: ========== STARTING FINGERPRINT EXTRACTION ==========
FingerprintExtractor: → Processing THUMB...
FingerprintExtractor: ROI bad aspect ratio: 4.92 (acceptable: 0.3-3.0)
FingerprintExtractor: ✗ THUMB: INVALID ROI
... (all fingers fail)
FingerprintExtractor: ========== EXTRACTION COMPLETE: 0/5 fingerprints ==========
RMST Biomterics: ====================================================
RMST Biomterics: ❌ FAILURE: 0 fingerprints passed quality check
RMST Biomterics: ====================================================
```

---

## Filter Commands (ADB Logcat)

### See all fingerprint-related logs:
```bash
adb logcat | grep -E "(ROICalculator|FingerprintExtractor|RMST Biomterics)"
```

### See only extraction results:
```bash
adb logcat | grep -E "(SUCCESS|FAILURE|EXTRACTION COMPLETE)"
```

### See only ROI dimensions:
```bash
adb logcat | grep "Calculated ROI"
```

### See quality scores:
```bash
adb logcat | grep "quality score"
```

---

## Key Metrics to Check

| Metric | Expected | Problem Indicator |
|--------|----------|-------------------|
| **ROI Dimensions** | 60-150px × 80-180px | < 30px in any dimension |
| **ROI Aspect Ratio** | 0.5 - 2.0 | > 3.0 or < 0.3 |
| **Quality Score** | 30-100 | All < 30 |
| **Extracted Count** | 3-5 fingerprints | 0 fingerprints |
| **Overall Quality** | 40-90 | < 30 |

---

## Code Changes Summary

### ✨ Created: `FingerprintROICalculator.kt`
- **Purpose**: Shared ROI calculation (DRY principle)
- **Used by**: OverlayView + FingerprintExtractor
- **Algorithm**: Perpendicular vector approach (proven to work)

### 🔧 Updated: `FingerprintExtractor.kt`
- Now uses shared calculator
- Added detailed logging for each step
- Logs ROI validation failures with reasons

### 🔧 Updated: `OverlayView.kt`
- Now uses shared calculator
- Ensures UI and extraction use identical logic

### 🔧 Updated: `CameraFragment.kt`
- Added capture trigger logs
- Added success/failure summaries
- Added file saving logs

---

## Expected Log Flow

1. **Capture Trigger**
   ```
   🎯 CAPTURE TRIGGERED
   Original bitmap from sensor: 640x480
   Rotated bitmap to match preview: 480x640  ← IMPORTANT: Should match preview!
   Processing bitmap: 480x640, format: ARGB_8888
   ```

2. **ROI Calculation** (per finger)
   ```
   ✓ Calculated ROI: 85x120px
   ```

3. **Extraction Start**
   ```
   ========== STARTING FINGERPRINT EXTRACTION ==========
   ```

4. **Per Finger Processing**
   ```
   → Processing THUMB...
   ROI valid: 85x120
   Cropped THUMB: 85x120px
   THUMB quality score: 72/100
   ✓ THUMB: ACCEPTED
   ```

5. **Extraction Complete**
   ```
   ========== EXTRACTION COMPLETE: 5/5 fingerprints ==========
   ```

6. **Success Summary**
   ```
   ✅ SUCCESS: Extracted 5/5 fingerprints
   Overall quality: 68/100
   ```

7. **File Saving**
   ```
   💾 Saving images to gallery...
   ✓ Saved THUMB: 85x120px (quality: 72)
   💾 All images saved
   ```

---

## Troubleshooting

### If you see NEGATIVE heights (e.g., 193x-61px):
❌ **Problem**: Image orientation mismatch - bitmap not rotated
✅ **Fix**: Verify `imageProxyToBitmap()` rotates bitmap 90°
```
Original bitmap from sensor: 640x480
Rotated bitmap to match preview: 480x640  ← Should see this!
```

### If you see ROI dimensions like 278x13px (very thin):
❌ **Problem**: Shared calculator not being used
✅ **Fix**: Verify imports in FingerprintExtractor.kt and OverlayView.kt

### If extraction returns 0 fingerprints:
❌ **Problem**: ROI validation failing
✅ **Fix**: Check logs for "INVALID ROI" and reason (negative height, too small, bad aspect ratio)

### If quality scores are all < 30:
❌ **Problem**: Image quality too low or processing issue
✅ **Fix**: Check lighting, hand position, focus

---

## Latest Fixes

### **2025-12-11 Session 2: Image Rotation Fix**
**Problem**: Negative heights in ROI (e.g., 193x-61px)
**Cause**: Camera sensor captures 640×480 (landscape), preview shows 480×640 (portrait). Landmarks calculated on preview but applied to unrotated bitmap.
**Solution**: Added 90° clockwise rotation in `imageProxyToBitmap()` to match preview orientation.

### **2025-12-11 Session 1: Component-based Refactoring**
**Problem**: ROI calculation mismatch between UI and extraction
**Cause**: Two different ROI calculation methods (bounding box vs perpendicular vectors)
**Solution**: Created shared `FingerprintROICalculator.kt` using DRY principle

---

**Last Updated**: 2025-12-11
**Changes by**: Image rotation fix + Component-based refactoring (DRY principle)
