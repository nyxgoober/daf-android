# DAF95

A native Android app for the DAF0 audio format: decode, encode, and play.

- **Player** — load `.daf`, `.wav`, or any format the platform's `MediaCodec` supports; playback is
  handled by a raw `AudioTrack` writing decoded 16-bit PCM.
- **Convert** — turn a WAV/FLAC/etc. source into a `.daf` file (fixed order-2 predictor + Rice-coded
  residuals, 4096-sample blocks — no seek table, no metadata, no stereo decorrelation, matching the
  original web prototype's format exactly).
- **Codec** lives entirely in `app/src/main/kotlin/com/nyxgoober/daf95/codec/` — no native/JNI code,
  pure Kotlin, so it also serves as a readable reference implementation of the format.

## Opening `.daf` from a file manager

`MainActivity` registers `VIEW`/`SEND` intent filters matching `.daf` by both `pathPattern` and the
custom MIME type `application/x-daf`. Most file managers resolve apps for unregistered extensions by
prompting "Open with…" and listing apps whose manifest matches by extension pattern — this app will
show up there. Files forwarded this way are copied into the app's cache dir (via `AudioFileLoader`)
since `MediaExtractor` needs a real file path, not a `content://` stream.

If a specific file manager insists on MIME-type-only resolution and doesn't offer this app for `.daf`,
associate the extension with `application/x-daf` in that file manager's settings (some, like Solid
Explorer or Total Commander, allow manual extension → app mapping).

## Building

No local Android Studio required — this is built the same way the rest of nyx's projects are, via
GitHub Actions (`.github/workflows/build.yml`). Push to `main` or trigger the workflow manually; the
debug APK is uploaded as a build artifact.

To build locally if you ever do have a machine with the SDK:

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## UI

Standard Material 3 layout and navigation. The playback bar and a few key buttons use
[kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop`)
for a frosted/refractive glass treatment — see `ui/components/LiquidGlassComponents.kt`.

## Format spec (DAF0)

| offset | size | field |
|---|---|---|
| 0 | 4 | magic `"DAF0"` |
| 4 | 4 | sample rate (u32 LE) |
| 8 | 1 | channel count |
| 9 | 1 | sample width in bytes (always 2) |
| 10 | 4 | frame count (u32 LE) |
| 14 | 4 | block count (u32 LE) |
| 18 | N | per-block Rice parameter `k` (u8 each) |
| 18+N | … | Rice/zigzag-coded residual bitstream |

Prediction: `predicted[i] = 2*sample[i-1] - sample[i-2]` for `i >= 2`, with `predicted[1] = sample[0]`
and `predicted[0] = 0`. Nothing fancier than that — no LPC, no predictor search.
