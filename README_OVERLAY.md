Overlay permission and testing

This project includes an in-app flow to request the "Display over other apps" permission.

How to test overlay behavior

1. Install the app on a device or emulator (use debug build).
2. Open the app and ensure Accessibility Service is enabled in system settings for the app.
3. If overlays are blocked, start the in-app permission screen:

   - From the app's main UI, open "Request overlay permission" (if present), or
   - The accessibility service will launch a small screen to guide the user when it detects overlays are blocked.

4. Grant the permission in the system settings UI.
5. Trigger an accessibility event (open a message with suspicious text). The overlay should appear positioned over the offending message.

Notes
- The Accessibility Service already attempts to open the in-app permission screen when overlays are not allowed.
- If overlays still don't appear, check logcat for `OverlayManager` errors (SecurityException, IllegalArgumentException).

Commands

Build and install via Gradle:

```bash
./gradlew installDebug
```