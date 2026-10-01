# Episode Pilot TV

Android/Google TV app that loads a user-supplied episode page, detects common HLS/DASH/direct-video requests, plays them in Media3/ExoPlayer, and supports Previous/Next/Auto-next for URLs containing an `episode-<number>` pattern.

## Default start URL
`https://www.wcostream.tv/naruto-shippuden-episode-18-english-dubbed-2`

## Build
GitHub Actions builds the debug APK automatically on every push.

The app does not bypass DRM, authentication, CAPTCHAs, access controls, or paywalls. It only plays media requests the loaded page itself is permitted to make.
