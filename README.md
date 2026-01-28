# BurpRedirect

Android app to redirect all device HTTP/HTTPS traffic to Burp Suite proxy via iptables NAT rules. Solves the problem of Flutter apps (and other apps) ignoring system proxy settings.

## Requirements

- Rooted Android device (Magisk recommended)
- Android 8.0+ (API 26)
- Burp Suite with "invisible proxying" enabled

## Features

- Simple UI: IP input, Port input, ON/OFF toggle
- Quick Settings tile for fast toggling
- Persistent notification when active
- Saves last used IP/port
- Redirects ports 80 (HTTP) and 443 (HTTPS)

## How It Works

Uses iptables NAT rules to force redirect traffic:

```bash
# Enable
iptables -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to-destination <IP>:<PORT>
iptables -t nat -A OUTPUT -p tcp --dport 443 -j DNAT --to-destination <IP>:<PORT>

# Disable
iptables -t nat -F OUTPUT
```

## Burp Suite Configuration

1. Go to **Proxy → Options**
2. Enable **"Support invisible proxying"**
3. Bind to all interfaces (0.0.0.0) or your network IP
4. Install Burp CA certificate on your device for HTTPS interception

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Open BurpRedirect app
2. Enter Burp Suite IP (your computer's IP on same network)
3. Enter port (default: 8080)
4. Toggle ON - grant root access when prompted
5. All HTTP/HTTPS traffic now routes through Burp

Add the Quick Settings tile for convenient toggling from notification shade.

## Tech Stack

- Kotlin
- Jetpack Compose
- DataStore Preferences
- Target SDK 34

## License

MIT
