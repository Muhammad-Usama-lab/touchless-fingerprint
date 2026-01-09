# Fingerprint Standards - Future Implementation

**Status:** PENDING - Pipeline for future work
**Created:** 2026-01-09

---

## 1. Image Quality Requirements

| Requirement | Target | Current Status |
|-------------|--------|----------------|
| **Resolution** | 500 DPI | Not implemented |
| **Image Format** | Maximum information captured | Basic capture |
| **Finger Position** | Flat with maximum contact | Touchless (no contact) |
| **Lens Condition** | Clean, no moisture | N/A (camera-based) |

### Notes:
- Current system is **touchless** (camera-based), so "maximum contact" doesn't apply directly
- Need to calculate/simulate 500 DPI equivalent from camera resolution
- May need to add image quality checks for moisture/blur detection

---

## 2. Template Format Support

The system should support conversion to these standard formats:

| Format | Description | Priority | Status |
|--------|-------------|----------|--------|
| `ANSI` | ANSI/INCITS 378 | High | Not implemented |
| `ISO_19794_2` | ISO/IEC 19794-2 | High | Not implemented |
| `SAGEM_PKMAT` | Sagem PKMAT format | Medium | Not implemented |
| `SAGEM_PKCOMPV2` | Sagem PKComp V2 | Medium | Not implemented |
| `SAGEM_CFV` | Sagem CFV format | Medium | Not implemented |
| `RAW_IMAGE` | Raw fingerprint image | High | **Implemented** (PNG) |
| `WSQ` | Wavelet Scalar Quantization | High | Not implemented |

### Implementation Notes:
- RAW_IMAGE is currently saved as PNG (lossless)
- WSQ is FBI standard for fingerprint compression
- ISO_19794_2 and ANSI are most common interchange formats
- May need third-party library (e.g., SourceAFIS, NBIS) for template extraction

---

## 3. Finger Index Definition

### Standard Finger Codes

| Code | Finger | Hand |
|------|--------|------|
| `RIGHT_THUMB` | Thumb | Right |
| `RIGHT_INDEX` | Index Finger | Right |
| `RIGHT_MIDDLE` | Middle Finger | Right |
| `RIGHT_RING` | Ring Finger | Right |
| `RIGHT_LITTLE` | Little Finger | Right |
| `LEFT_THUMB` | Thumb | Left |
| `LEFT_INDEX` | Index Finger | Left |
| `LEFT_MIDDLE` | Middle Finger | Left |
| `LEFT_RING` | Ring Finger | Left |
| `LEFT_LITTLE` | Little Finger | Left |

### Current Implementation Mapping

```kotlin
// Current enum (needs update)
enum class FingerType {
    THUMB,      // → Should be RIGHT_THUMB or LEFT_THUMB
    INDEX,      // → Should be RIGHT_INDEX or LEFT_INDEX
    MIDDLE,     // → Should be RIGHT_MIDDLE or LEFT_MIDDLE
    RING,       // → Should be RIGHT_RING or LEFT_RING
    PINKY       // → Should be RIGHT_LITTLE or LEFT_LITTLE
}

// Proposed enum (future)
enum class FingerType {
    RIGHT_THUMB,
    RIGHT_INDEX,
    RIGHT_MIDDLE,
    RIGHT_RING,
    RIGHT_LITTLE,
    LEFT_THUMB,
    LEFT_INDEX,
    LEFT_MIDDLE,
    LEFT_RING,
    LEFT_LITTLE
}
```

### Hand Detection Required:
- Need to detect if captured hand is LEFT or RIGHT
- MediaPipe provides handedness classification
- Use `handedness` from `HandLandmarkerResult` to determine hand

---

## 4. Implementation Roadmap

### Phase 1: Hand Detection Enhancement
- [ ] Detect left vs right hand using MediaPipe handedness
- [ ] Update FingerType enum with full finger codes
- [ ] Map captured fingers to correct standard codes

### Phase 2: Image Quality
- [ ] Calculate effective DPI from camera resolution
- [ ] Add 500 DPI requirement validation
- [ ] Implement image enhancement for DPI simulation
- [ ] Add blur/quality rejection

### Phase 3: Template Conversion
- [ ] Research fingerprint template libraries (SourceAFIS, NBIS, etc.)
- [ ] Implement RAW_IMAGE export (done - PNG)
- [ ] Implement WSQ conversion
- [ ] Implement ISO_19794_2 template extraction
- [ ] Implement ANSI template extraction

### Phase 4: Server Integration
- [ ] Define API contract for template upload
- [ ] Support multiple template format uploads
- [ ] Add template format selection in SDK config

---

## 5. Technical Considerations

### DPI Calculation for Touchless
```
Effective DPI = (Finger width in pixels) / (Actual finger width in inches)

Example:
- Captured finger width: 140 pixels
- Average finger width: 0.75 inches
- Effective DPI: 140 / 0.75 = ~187 DPI

To achieve 500 DPI:
- Need 500 * 0.75 = 375 pixels finger width
- Or use super-resolution/upscaling techniques
```

### Libraries to Evaluate
1. **SourceAFIS** - Open source fingerprint matching (Java/Android compatible)
2. **NBIS** - NIST Biometric Image Software (WSQ support)
3. **OpenCV** - Image processing (already integrated)
4. **libfprint** - Linux fingerprint library

---

## 6. References

- ISO/IEC 19794-2:2011 - Biometric data interchange formats
- ANSI/INCITS 378-2004 - Finger Minutiae Format
- FBI WSQ Specification - Wavelet Scalar Quantization
- NIST SP 500-290 - Fingerprint Vendor Technology Evaluation

---

*Document created for future implementation planning*
*Last updated: 2026-01-09*
