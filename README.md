# Wvoiz Music Hub — Android app

Android WebView wrapper for https://wvoiz-music-hub.edgeone.dev

Pushing to `main` triggers GitHub Actions, which builds an unsigned release
APK and attaches it to a GitHub release. The APK is then signed with the
Wvoiz release key (kept off GitHub) before it is shared, so every update
installs cleanly over the previous one.
