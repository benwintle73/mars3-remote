# MATVT Mars 3 Remote

Customized build of [MATVT](https://github.com/virresh/matvt) for the **Anker Nebula Mars 3** running Android 14. It adds a D-pad-first navigation mode for the Netflix mobile/tablet app, which is commonly sideloaded on the Mars 3 because the projector does not ship the Android TV Netflix app.

This build is intentionally separate from upstream MATVT:

- application ID: `io.github.virresh.matvt.mars3`
- app label: `MATVT Mars 3 Remote`
- accessibility service: `MATVT Mars 3 Input Service`
- target SDK: 34
- debug APK: `app/build/outputs/apk/debug/app-debug.apk`

**Status: built, unit-tested, and lint-clean (warnings only), but not yet verified on a physical Mars 3. Do not assume Netflix navigation works until the checklist below has been run on the projector.**

## Build

Prerequisites used for this build:

- JDK 17 (the JDK bundled with current Android Studio may be too new for this Gradle setup)
- Android SDK platform 34 and build-tools 34.0.0
- `local.properties` with `sdk.dir=/Users/benwintle/Library/Android/sdk`

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install and enable

```bash
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" devices
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n io.github.virresh.matvt.mars3/io.github.virresh.matvt.gui.GuiActivity
```

On the projector:

1. Open **MATVT Mars 3 Remote**.
2. Select **SETUP PERMISSIONS**.
3. Enable **MATVT Mars 3 Input Service** under Accessibility.
4. If Android says the service is restricted, open **Settings → Apps → MATVT Mars 3 Remote → ⋮ → Allow restricted settings**, then enable the service again.
5. Overlay permission is optional on Android 8+ because the cursor uses `TYPE_ACCESSIBILITY_OVERLAY`.

Useful read-only checks:

```bash
"$ADB" shell settings get secure enabled_accessibility_services
"$ADB" shell dumpsys accessibility | grep -i matvt
```

## Remote controls

The default engine is **gesture** and the default mode is **D-pad**.

| Control | Action |
| --- | --- |
| D-pad | Move the Netflix highlight in smart mode, or the mouse cursor in mouse mode |
| OK / Enter | Tap the highlighted item; long press for long-click |
| Back | Normal Back in D-pad mode outside Netflix; in mouse/smart mode it is replayed as Back on release |
| Hold Back ~1.2 s | Return to normal D-pad mode and suspend Netflix smart navigation |
| Boss key (default Mute) | Long press toggles D-pad ↔ mouse; short press is consumed |
| Info | Toggles D-pad ↔ mouse when the remote sends `KEYCODE_INFO` |
| Colored keys | Scroll gestures in mouse/scroll mode |

The boss key can be changed in the GUI, or with **DETECT** and a long key hold while the detection screen is open. If the boss key is disabled in the GUI, the key passes through to the system again.

## Netflix modes

### Smart D-pad (default)

When Netflix is in the foreground and **Netflix smart D-pad navigation** is enabled, D-pad presses move a highlight between actionable accessibility nodes and OK taps the node centre. The service reads the UI tree only to find clickable/focusable elements; it does not modify Netflix or bypass DRM.

- If Netflix exposes no usable nodes, the **coordinate fallback** crosshair appears.
- If a full-screen video surface is detected, D-pad and OK are passed back to Netflix so its own playback controls and seekbar work.
- If a row has no next item, the service scrolls the nearest scrollable container or performs a swipe.

### Coordinate fallback

If **Coordinate fallback if Netflix exposes no items** is enabled, the service moves an invisible point on a grid and shows a crosshair. D-pad moves the point; OK taps it. Hold a direction for repeated movement. The step size follows **Cursor Speed**.

The fallback is intentionally approximate. Netflix's mobile layout can change between releases, and a coordinate that works on one screen may not work on another.

### Mouse mode

Mouse mode is still available for apps that need a pointer. Enable **Bordered Window** to stop at the screen edges instead of wrapping. **Hide mouse cursor** keeps the pointer invisible while still allowing movement and clicks.

## Debugging

Enable **Hide toast messages** to reduce on-screen messages. Useful log tags:

```bash
"$ADB" logcat -s MATVT_SERVICE MATVT_NETFLIX MOUSE_EMULATION GESTURE_MOUSE_EMULATION MATVT_UI_DUMP
```

The GUI has a **DUMP UI TREE (DEBUG)** button. It writes the current accessibility tree to the app's private files directory:

```bash
"$ADB" exec-out run-as io.github.virresh.matvt.mars3 cat files/ui-tree.txt > ui-tree.txt
```

The dump can contain Netflix screen text and item labels. It stays on the device unless you pull it manually.

## On-device test checklist

Run this on the Mars 3 before calling the build successful:

- [ ] Install and launch the GUI.
- [ ] Enable the accessibility service; confirm status shows Running.
- [ ] Confirm engine is **gesture** and Netflix smart navigation + fallback are enabled.
- [ ] Open Netflix and wait for the home screen.
- [ ] D-pad moves a visible highlight; OK opens the highlighted item.
- [ ] Long OK performs a long press where Netflix supports it.
- [ ] Back returns to the previous Netflix screen; hold Back recovers normal D-pad mode.
- [ ] Boss key long press switches to mouse mode and back.
- [ ] Start a video: OK/D-pad reach Netflix's native playback controls rather than tapping blindly.
- [ ] If no highlight appears, the fallback crosshair appears; move it and tap a known button.
- [ ] Dump the UI tree and confirm Netflix exposes nodes or document that fallback is required.
- [ ] Reboot the projector and confirm the service and preferences persist.
- [ ] Test a second app (e.g. YouTube) in D-pad mode to confirm smart navigation only activates for Netflix.

## Known limitations

- Netflix mobile is not an Android TV app. Its layout, focus behaviour, and accessibility tree can differ from what this build expects.
- Smart navigation depends on Netflix exposing clickable/focusable nodes. If it does not, coordinate fallback is required and is less precise.
- Full-screen playback deliberately passes input to Netflix; controls may still require the projector's native pointer or a different remote key.
- The `shell` and `hybrid` engines are legacy ADB-based options and are not the recommended path on the Mars 3. Use `gesture`.
- Android 14 may restrict sideloaded accessibility services. Follow the restricted-settings steps above.
- This project does not modify Netflix, bypass DRM, spoof certification, or interfere with Netflix authentication.
- No physical Mars 3 verification has been performed yet.
