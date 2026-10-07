# SafePath ESP32-CAM Disaster Observation Nodes

Offline-first local camera workflow:

```
Phone  --BLE discover-->  SafePath-CAM-00N
Phone  --Classic BT---->  "c" then SPIC,<length> + JPEG + SPEND
Phone  --Wi-Fi SoftAP-->  SSID SafePath-CAM-00N (open, no password; optional status)
```

Bluetooth LE is used for discovery. Capture New Image sends `c` over Classic Bluetooth Serial (UUID `00001101-0000-1000-8000-00805F9B34FB`) and reads `SPIC,<length>`, then that many JPEG bytes, then `SPEND`. A Wi-Fi HTTP failure does not disconnect the node.

## Entry

Home → **Nearby SafePath Nodes** → Scan → Connect → Camera screen.

## Firmware expectations

- BLE advertise name: `SafePath-CAM-001` (or 002, 003, …)
- Optional BLE service UUID: `0000FFC1-0000-1000-8000-00805F9B34FB`
- SoftAP SSID = device name, **no password** (`WiFi.softAP(ssid)` only). `SafePath123` is unused in the sketch.
- SoftAP IP `192.168.4.1`
- `GET /capture` → `image/jpeg`
- `GET /info` and `GET /status` → JSON

## Android notes

- targetSdk 34: runtime `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (and location on API ≤30)
- Wi-Fi join scans for the real hotspot first (open or WPA2, exact name or any `SafePath*` SSID) and passes its BSSID to Android. That avoids the system dialog “Unable to select device”, which appears when Location is off or the requested security/name does not match the ESP32.
- If the hotspot is not visible, reset the ESP32. BLE startup can drop the SoftAP.
- After a manual join, tap “Use connected camera Wi-Fi” so image requests stay on the camera network instead of mobile data.
- Cleartext HTTP allowed for `192.168.4.1` via `network_security_config.xml`
