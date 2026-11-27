# Ascent-style page translation integration plan

## Goal and build target

Add an on-device page-translation feature to the existing Komikku/Kodomo reader. The first deliverable is a single APK split for `arm64-v8a`; no universal APK or other ABI is intended.

This is an integration plan, not a claim that the translation engine has already been ported. The Ascent APK supplied for analysis contains translation-related DEX symbols and native/OCR dependencies, but its bytecode has not yet been decompiled and its internal contracts are not yet verified.

## Verified host-app integration points

- `ReaderActivity` owns the reader UI and opens `ReaderSettingsDialog`.
- `ReaderViewModel` exposes reader state and the active chapter/pages.
- `ReaderPage` carries the page index, image URL, and an image stream provider.
- `HttpPageLoader` obtains page images and stores the source images in `ChapterCache`.
- `ReaderPageImageView` renders the page image and supports both tiled static images and animated images.
- The app already uses Compose, Kotlin coroutines, Coil, and WorkManager.
- The current CI workflow previously expected a universal APK. It has been updated to look specifically for the signed `arm64-v8a` preview APK.

## Proposed feature architecture

Keep translation code separate from the reader's existing loading and rendering logic.

### 1. Translation domain

Create a translation package under the app's reader feature with these contracts:

- `PageTranslationRequest`: page identity, source bytes, source language, target language, OCR engine, translation engine, and cancellation token.
- `PageTranslationResult`: translated image bytes, detected text regions, language metadata, and diagnostics.
- `TextRegion`: normalized bounding box, recognized text, confidence, and reading order.
- `OcrEngine`: detects text regions and returns recognized strings.
- `TextTranslator`: translates a batch of strings while preserving item order.
- `PageImageRenderer`: masks original text regions, paints a suitable background, and lays out translated text.
- `PageTranslationRepository`: coordinates OCR, translation, rendering, and cache reads/writes.

All coordinates should be normalized to the original image dimensions so rendering remains correct when the reader zooms or resizes a page.

### 2. OCR adapters

Implement adapters behind `OcrEngine`, not directly inside the reader view.

Initial adapter candidates based on strings found in the supplied Ascent APK:
- Google ML Kit text recognition.
- Paddle OCR through ONNX Runtime.

The APK contains symbols referring to these components, but this alone does not prove which models, model versions, language packs, or preprocessing steps Ascent uses. Those details must be confirmed from decompiled code or runtime tests before attempting a compatible port. OCR models must be downloaded or bundled deliberately, with licensing and size checked.

### 3. Translation providers

Use a provider interface so the reader does not depend on a single service. Provider configuration must contain user-entered credentials only; do not copy private keys or embedded credentials from the supplied APK.

Initial provider design:
- Built-in/free provider adapters only where their current APIs and terms are verified.
- User-configured API providers (including OpenAI-compatible endpoints) behind a common request/response contract.
- Per-provider timeout, retry, rate-limit handling, and clear error reporting.

### 4. Image reconstruction

The renderer must:
1. Preserve an untouched original image.
2. Use detected text boxes and nearby pixels to estimate the bubble/background fill.
3. Mask the source text without damaging bubble outlines or artwork.
4. Wrap translated text to fit the region, with font size and line spacing adapted to the box.
5. Produce a separate translated bitmap; never overwrite the source page in the chapter cache.
6. Fall back to the original image if OCR, translation, or rendering fails.

The first implementation should prioritize legible text and stable positioning. Advanced inpainting and font/style matching can be improved after real-page tests.

### 5. Cache and pretranslation

Use a dedicated translation cache, separate from the original `ChapterCache`.

Cache identity should include:
- source image content hash;
- OCR engine and model version;
- source/target language;
- translation provider and relevant non-secret settings;
- renderer version.

A change to any of these should invalidate the translated result. Pretranslation should run as a cancellable background job with progress, network constraints where appropriate, and a user-visible pause/cancel action. It must not block normal page loading.

### 6. Reader UI

Integrate controls into the existing reader settings and page actions:
- Enable/disable page translation.
- OCR engine selection.
- Source language (automatic detection where supported).
- Target language.
- Translation provider and provider settings.
- Translate current page / retry.
- Pretranslate current chapter with progress.
- Show original / show translated image.
- Clear translated cache.

The original page remains the default fallback. The reader must remain navigable while translation is running.

## Implementation sequence

1. Confirm the exact reader rendering callback and settings-dialog source files on `master`.
2. Add domain models and interfaces with unit tests; no UI changes yet.
3. Implement the translation cache and cancellation-safe repository.
4. Add one verified OCR adapter and one verified translation provider.
5. Implement the first renderer and test with representative manga pages.
6. Add reader overlay/settings and original/translated switching.
7. Add chapter pretranslation and progress/cancellation.
8. Run formatting, unit tests, SQLDelight verification, and the arm64-only preview build.
9. Test the resulting APK on the target phone before claiming end-to-end functionality.

## Acceptance criteria

- Original pages still load normally when translation is disabled or unavailable.
- Translation never overwrites the original downloaded image.
- Current-page translation can be cancelled and retried.
- Original/translated switching does not reset page position or zoom unexpectedly.
- Cache invalidation is deterministic.
- Pretranslation does not freeze the reader and can be cancelled.
- CI produces only the `arm64-v8a` APK artifact.
- End-to-end success is claimed only after the built APK translates real pages on-device.

## Known blockers / verification still required

- The supplied Ascent DEX files have not yet been decompiled; string extraction is not enough to reproduce its algorithms or class dependencies.
- The exact OCR model files and versions used by Ascent are not yet confirmed.
- The exact reader settings UI source file and image-rendering handoff need a complete source review before wiring translated images into the viewer.
- Translation service API compatibility, authentication, usage limits, and licensing must be checked per provider.
