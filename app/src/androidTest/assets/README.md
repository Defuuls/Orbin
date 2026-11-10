`playback-smoke.mp4` and `playback-smoke.webm` are original, network-independent synthetic test patterns.
They are packaged only in the instrumentation APK, never in the released app.

Recreate them with:

```bash
ffmpeg -f lavfi -i testsrc2=size=160x90:rate=15 -t 3 -c:v libx264 \
  -pix_fmt yuv420p -movflags +faststart -an playback-smoke.mp4
ffmpeg -f lavfi -i testsrc2=size=160x90:rate=15 -t 3 -c:v libvpx-vp9 \
  -b:v 120k -an playback-smoke.webm
```

The instrumentation tests render them through the app's actual `VideoPlayer`,
Hilt-provided data source, and platform decoder. A pass requires rendered video
buffers and advancing playback, not merely a mocked play-state flag.
