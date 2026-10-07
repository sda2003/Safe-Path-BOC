# SafePath

<div style="display: flex; flex-wrap: wrap; gap: 10px;">
  <img src="./output images/front page.png" alt="Front Page" width="200" />
  <img src="./output images/sighup.png" alt="Sign Up" width="200" />
  <img src="./output images/sign in page.png" alt="Sign In Page" width="200" />
  <img src="./output images/sealect emegncy.png" alt="Select Emergency" width="200" />
  <img src="./output images/map.png" alt="Offline Map & Routing" width="200" />
  <img src="./output images/emegency pack .png" alt="Emergency Pack" width="200" />
  <img src="./output images/survial guide 1.png" alt="Survival Guide 1" width="200" />
  <img src="./output images/survial guide 2.png" alt="Survival Guide 2" width="200" />
  <img src="./output images/survial list.png" alt="Survival List" width="200" />
  <img src="./output images/chat.png" alt="Chat" width="200" />
  <img src="./output images/settings.png" alt="Settings" width="200" />
  <img src="./output images/veryfi login.jpeg" alt="Phone / Email verification" width="200" />
  <img src="./output images/AI.jpeg" alt="AI personalized emergency pack" width="200" />
  <img src="./output images/AI constome fication in pro version.jpeg" alt="AI customization / pro personalization" width="200" />
</div>

SafePath is an offline-first disaster response app for **Sri Lanka**. It helps a person evacuate, tell family they are safe, follow a survival guide, and see what a nearby camera node is looking at when mobile data and the local Wi-Fi network are gone.

**Repo:** https://github.com/godragun/SafePath

---

## Problem statement

When a flood, landslide, cyclone, or other emergency hits Sri Lanka, the things people need first are local and immediate: which road is still usable, whether a neighbourhood is already under water, what to pack, and how to tell family they are alive. Those needs collide with how phones normally work.

- Mobile networks and home internet fail, slow down, or become overloaded.
- Cloud maps and chat apps then stop being reliable.
- A person standing near a blocked road still cannot see around the next bend.
- A camera on a pole only helps if the phone can reach it without a router and without the internet.
- Warnings that are always “red” lose trust. A node that is online and calm should say **Normal**, and a photo should be a real picture from that place, not a stock icon.

SafePath is built for that gap. The phone keeps evacuation, checklists, survival steps, and short local notes on the device. When a network exists, it adds accounts, family messages, community chat, and AI answers. At the edge, an ESP32-CAM node named `SafePath-CAM-001` sends one JPEG over **Classic Bluetooth Serial**, so someone standing next to the node can see the scene and place that photo on the node dashboard with an ordinary temperature, humidity, Colombo location, and a **Normal** status.

---

## How this helps people

| Need during an emergency | What SafePath does |
|--------------------------|--------------------|
| “Which way do I walk?” | Dual-mode evacuation map: online roads when the network works, a Sri Lanka offline map and hazard-aware route when it does not. |
| “Is the road ahead flooded?” | A nearby camera node sends a real JPEG over Bluetooth. The node dashboard shows that photo. |
| “Is this sensor alarmed or just online?” | The demo injection can show the node **ONLINE** with **Normal** risk, normal temperature, and normal humidity, while the picture is the Bluetooth photo. |
| “I have no signal. What do I do?” | Survival guides and the emergency pack stay on the phone. |
| “Tell my family I am safe.” | Family Circle sends **I’m Safe** with location, and queues the message if the phone is offline. |
| “Warn people standing next to me.” | Mesh Notes hop short messages phone-to-phone when there is no internet. |
| “What should my household pack?” | Checklists, plus an AI pack that is saved on the phone after one online request. |

---

## What the system does

### On the phone

- Sign up, sign in, guest use, and phone or email verification.
- Home dashboard with alerts, family, map, nearby nodes, and the node dashboard.
- Evacuation map that switches between online tiles with OSRM routing and the bundled offline Sri Lanka map with hazard-aware Dijkstra routing.
- Report an emergency alert with type, district, severity, description, and GPS.
- Emergency pack checklists stored on the phone, plus an AI-personalized pack.
- Ask SafePath AI, with answers cached so they can be read again offline.
- Survival library and step-by-step guides, fully offline.
- Family Circle and **I’m Safe**.
- Community chat when online, and Mesh Notes over Nearby Connections when offline.
- Settings, including dark mode.

### On the camera node

The current ESP32-CAM firmware (`SafePath-CAM-001`) does **not** use BLE or a Wi-Fi hotspot for the photo. Wi-Fi, Classic Bluetooth, the camera, and a web server together do not fit reliably in the ESP32’s memory, and the phone could join the hotspot without ever receiving a JPEG.

The working path is:

1. The board boots the camera first, then starts Classic Bluetooth under the name `SafePath-CAM-001`.
2. The phone scans for that Classic Bluetooth name. Location must be on, because many Android phones hide nearby Bluetooth devices when it is off.
3. **Capture New Image** sends the letter `c`.
4. The node replies with `SPIC,<byte length>`, the raw JPEG, then `SPEND`.
5. The photo appears on the camera screen.
6. **Inject demo ESP32 packet** on the node dashboard keeps the node **ONLINE**, sets risk to **Normal** (not Warning), shows a normal temperature (`28.4 °C`) and humidity (`64%`), keeps the Colombo / Kalambu coordinates (`6.927, 79.861`), and puts that Bluetooth JPEG in the camera card.

---

## System architecture

```
Person's phone  (SafePath, Android)
        │
        ├── Room database          maps, packs, guides, queued family/mesh messages
        ├── WorkManager            send those queues when a network returns
        │
        ├── Online, when a network exists
        │     Supabase             accounts, family, alerts, community chat
        │     Gemini               pack personalization and Q&A (then cached)
        │     OSM tiles + OSRM     live map and road route
        │
        ├── Offline, on the phone
        │     Sri Lanka MBTiles    basemap without internet
        │     Local road graph     hazard-aware evacuation route
        │
        ├── People nearby, no internet
        │     Nearby Connections   Mesh Notes, short hops between phones
        │
        └── Camera node beside the person
              Classic Bluetooth Serial
              name: SafePath-CAM-001
              phone sends "c"
              node sends SPIC,<length> + JPEG + SPEND

ESP32-CAM node
        Camera (JPEG, QVGA)  →  Classic Bluetooth only
        No photo over BLE
        No dependence on http://192.168.4.1/capture

Later, if LoRa is added (not built yet)
        ESP32 node  --LoRa-->  other nodes / a gateway
        Gateway or a passing phone still uses the SafePath app
```

Inside the app the screens talk to ViewModels and repositories (Hilt). Local facts live in Room. Network calls are optional. The camera path is a separate Classic Bluetooth client so a failed Wi-Fi request cannot mark the node disconnected.

```
app/src/main/java/com/hades/safepath/
├── auth/ family/ ai/           accounts, I'm Safe, Gemini
├── map/                        online/offline map and routing
├── mesh/ p2p/ worker/          nearby notes and offline queues
├── camera/                     Classic Bluetooth photo + node discovery
├── ui/hardware/                node dashboard and demo injection
├── ui/home map pack survival chat family
└── di/
```

Firmware for the camera node lives in the separate repository [esp32-cam-in-app](https://github.com/godragun/esp32-cam-in-app).

---

## Features added to the system

- **Nearby SafePath nodes.** The phone looks for `SafePath-CAM-001` with Classic Bluetooth, not BLE. Scan and Connect no longer depend on the old camera Wi-Fi hotspot.
- **Capture New Image.** One framed JPEG over Classic Bluetooth Serial (`00001101-0000-1000-8000-00805F9B34FB`). Errors show under the image. The placeholder stays if the bytes are not a JPEG.
- **Node dashboard.** Shows online or offline, node id, temperature, humidity, risk badge, place name, coordinates, last update, and the camera card.
- **Inject demo ESP32 packet.** Injects a calm demo: node online, risk **Normal**, temperature `28.4 °C`, humidity `64%`, Colombo coordinates. Image 1 is the Bluetooth photo, not a download from `192.168.4.1`.
- **Dual-mode evacuation map.** Auto / online / offline, with a road route drawn inside the app.
- **Mesh Notes, Family Circle, emergency pack, survival library, and Ask SafePath AI**, so the same phone is useful before, during, and after a network outage.

---

## If LoRa is added

LoRa is **not in the app or the camera firmware today**. It is the right next radio for the same nodes.

Classic Bluetooth only works for a person standing next to the camera, on the order of a few metres to a few tens of metres. A flood or landslide does not wait for someone to walk up to the node. LoRa (for example LoRaWAN or a simple private LoRa link) can carry a short packet for kilometres on little power: node id, temperature, humidity, risk, and a GPS fix. A gateway in a town, or another node on a hill, can collect those packets when phones and cell towers cannot.

That helps people in three concrete ways:

- A village can hear that a node upstream is still **Normal**, or that it has changed to **Warning**, without anyone standing beside it.
- Several nodes can form a chain along a river or a hillside road, so the app’s map can show more than one place.
- The JPEG can stay on Bluetooth or a later short-range link. LoRa is too slow for a full photo. It should move the small status packet; the picture stays a separate, nearby capture. Someone who reaches the node still sees the real image. Everyone else still sees whether that place is calm or not.

A future LoRa packet would match the dashboard fields already on screen: node, temperature, humidity, risk, latitude, longitude. The phone would treat a LoRa warning the same way it treats a mesh note or a dashboard risk above zero: show it, and keep evacuation available offline.

---

## Status

| Area | Status |
|------|--------|
| Auth, verification, Family Circle | Working |
| Emergency pack, survival library, AI pack and Q&A | Working; AI needs one online call, then the result stays on the phone |
| Evacuation map, online and offline | Working |
| Community chat | Working when online |
| Mesh Notes | Working on two physical phones |
| ESP32-CAM photo over Classic Bluetooth | Working with the Bluetooth firmware |
| Node dashboard + Normal demo injection using that photo | Working |
| Camera photo over Wi-Fi HTTP | Not the live path. The board does not serve the JPEG that way anymore |
| LoRa | Not built. Described above as the long-range next step |

---

## Architecture notes for the map

SafePath keeps map and route drawing inside the app.

| Mode | Basemap | Routing |
|------|---------|---------|
| Online | Map tiles (no Google Maps redirect) | OSRM road geometry |
| Offline | Bundled `srilanka_map.mbtiles` | Hazard-aware Dijkstra on local roads |
| Auto | Follows the network. An offline choice stays offline | Same |

Guide: [docs/OFFLINE_MAP.md](docs/OFFLINE_MAP.md). Notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## How to run

### Phone app

- Android Studio, JDK 17, device or emulator **API 26+**.
- Copy `local.properties.example` to `local.properties`.

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key
GEMINI_API_KEY=your_gemini_api_key
```

```bash
./gradlew :app:assembleDebug
```

Do not commit real keys.

### Camera node

- AI-Thinker ESP32-CAM, PSRAM enabled, Huge APP partition.
- Flash the Bluetooth sketch from [esp32-cam-in-app](https://github.com/godragun/esp32-cam-in-app).
- Serial log must reach: `Bluetooth device "SafePath-CAM-001" started.`
- On the phone, turn Location on, open **Nearby SafePath Nodes**, scan, connect, then **Capture New Image**.
- Open **Node Dashboard** and press **Inject demo ESP32 packet** to show that photo with a Normal reading.

Mesh Notes need two real phones. Emulators usually cannot exercise Bluetooth or Nearby Connections.

---

## Environment variables

| Variable | Purpose |
|----------|---------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Supabase anon/public key |
| `GEMINI_API_KEY` | Gemini for the AI pack and Q&A |
| `sdk.dir` | Android SDK path, in `local.properties` only |

---

## Troubleshooting

- **Sign-up email rate limit:** wait, or turn off Confirm email in Supabase while testing.
- **Phone OTP fails:** see [supabase/PHONE_AUTH_SETUP.md](supabase/PHONE_AUTH_SETUP.md).
- **AI fails:** set `GEMINI_API_KEY`. Cached answers still open offline.
- **Map is blank offline:** add MBTiles under `assets/tiles/`. See [docs/OFFLINE_MAP.md](docs/OFFLINE_MAP.md).
- **Camera node never appears:** Location on, Bluetooth on, firmware log shows the device name, and the scan must be Classic Bluetooth (this app does that). Pair `SafePath-CAM-001` in the phone’s Bluetooth settings if the list stays empty.
- **Dashboard image is still the placeholder:** capture a photo on the camera screen first, then inject the demo packet.
- **Mesh Notes find nobody:** two physical phones, Bluetooth on, Nearby permissions granted.
