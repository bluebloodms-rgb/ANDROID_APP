# برنامه‌ریزی پیاده‌سازی اپ اندروید کنترل پهاداد (GCS)

> این سند برای یک **عامل هوش مصنوعی** طراحی شده تا مرحله‌به‌مرحله یک اپ اندروید بسازد که معادلِ نسخه‌ی PySide6 پروژه‌ی `GUI_UAV-just_slider_pitch` است.
> سند شامل تصمیمات استراتژیک، معماری، فازبندی، پروتکل دقیق ارتباطی و معیارهای تأیید است.
> عامل می‌تواند خودش جزئیات پیاده‌سازی را تصمیم بگیرد؛ این سند **محدوده، ترتیب و معیارهای پذیرش** را مشخص می‌کند.

---

## ۰. تصمیمات استراتژیک نهایی

| بُعد | تصمیم | دلیل |
|---|---|---|
| **پلتفرم** | **Native Kotlin + Jetpack Compose** | Bluetooth SPP نیاز به RFCOMM دارد که مستقیماً در Android SDK موجود است؛ در React Native/Flutter به native module نیاز دارد. پرفورمنس ویدئوی زنده + overlay تلمتری در Compose بسیار خوب است. MAVLink کتابخانه‌ی رسمی Kotlin دارد. |
| **زبان برنامه‌نویسی** | Kotlin 2.x + Coroutines + Flow | استاندارد مدرن Android؛ Flow برای stream تلمتری و heartbeat ایده‌آل است. |
| **معماری** | Single-Activity + Compose + MVVM + Hilt | تطبیق‌پذیر با چرخه‌ی حیات Activity و Background Service. |
| **حداقل SDK** | Android 8.0 (API 26) | پوشش بازار + دسترسی به CameraX + Modern Bluetooth APIs. |
| **اتصال فلایت کنترلر** | Bluetooth Classic SPP (RFCOMM, SPP UUID `00001101-0000-1000-8000-00805F9B34FB`) | دقیقاً مطابق نسخه‌ی اصلی که `dronekit.connect(port, baud=115200)` روی پورت BT می‌زد. |
| **Baud rate** | `115200` | مطابق `flight_interface.py`. |
| **منبع ویدئو** | **هر دو**: 1) دوربین گوشی (CameraX)  2) استریم وایرلس کنار MAVLink (RTSP/UDP از طریق ExoPlayer) | کاربر هر دو را خواست. Toggle در UI برای انتخاب منبع. |
| **پروتکل فرمان/تلمتری** | MAVLink1/2 به‌علاوان STATUSTEXT-based custom protocol | کاملاً منطبق با نسخه‌ی PySide6 — برای سازگاری سخت‌افزاری حیاتی است. |
| **محدوده (Scope)** | **فازبندی‌شده** | پیاده‌سازی کامل اما در ۶ فاز، با قابلیت تست هر فاز. |
| **فرمت خروجی این سند** | Markdown | مطابق درخواست. |

### کتابخانه‌های کلیدی

| هدف | کتابخانه | نسخه |
|---|---|---|
| MAVLink | `org.mavlink:mavlink-kotlin` (یا `org.mavlink:mavlink` اگر در Maven Central نبود، از GitHub build) | آخرین stable |
| Bluetooth SPP | Android BluetoothManager (نیازی به کتابخانه‌ی خارجی نیست) | Built-in |
| دوربین گوشی | `androidx.camera:camera-camera2` + `camera-view` | 1.3+ |
| ویدئوی RTSP/UDP | `androidx.media3:media3-exoplayer` با ماژول RTSP | 1.2+ |
| DI | `com.google.dagger:hilt-android` | 2.50+ |
| Async | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7+ |
| Logging | `com.jakewharton.timber:timber` | 5.x |
| Navigation | Compose Navigation | built-in |
| Testing | JUnit + Turbine + MockK + Robolectric | latest |

### دسترسی‌های AndroidManifest

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>
<uses-feature android:name="android.hardware.bluetooth" android:required="true"/>
```

---

## ۱. معماری کلی

### ۱.۱ لایه‌بندی

```
┌──────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                              │
│  - Screens, Widgets, Overlays                            │
│  - State<TelemetryUiState>, State<ConnectionUiState>     │
└──────────────────────────────────────────────────────────┘
                         │  collectAsState()
┌──────────────────────────────────────────────────────────┐
│  Presentation / ViewModel Layer                           │
│  - ConnectionViewModel, TelemetryViewModel,             │
│    ControlViewModel, CameraViewModel                     │
│  - Hilt @HiltViewModel                                    │
└──────────────────────────────────────────────────────────┘
                         │  Flow / suspend
┌──────────────────────────────────────────────────────────┐
│  Domain Layer (Pure Kotlin)                              │
│  - FlightState, Telemetry, Command value objects         │
│  - MavlinkProtocol: encode()/decode() STATUSTEXT         │
│  - Ports: FlightRepository, CameraRepository             │
└──────────────────────────────────────────────────────────┘
                         │
┌──────────────────────────────────────────────────────────┐
│  Data / Service Layer                                     │
│  - BluetoothSppService (Foreground Service)              │
│  - MavlinkFlightRepository (mavlink-kotlin)               │
│  - CameraXPreviewRepository                              │
│  - RtspVideoRepository (ExoPlayer)                       │
└──────────────────────────────────────────────────────────┘
                         │
┌──────────────────────────────────────────────────────────┐
│  Hardware / OS                                            │
│  Bluetooth RFCOMM  |  Camera2  |  MediaCodec  |  Network │
└──────────────────────────────────────────────────────────┘
```

### ۱.۲ نمودار Mermaid — جریان داده

```mermaid
flowchart LR
  FC[Flight Controller\nBT SPP] -->|MAVLink frames| Svc[BluetoothSppService\nForeground]
  Svc -->|Flow<MAVLinkMessage>| Repo[MavlinkFlightRepository]
  Repo -->|Flow<FlightState>| VM1[TelemetryViewModel]
  Repo -->|Flow<ConnectionState>| VM2[ConnectionViewModel]
  VM1 --> UI1[HUD Widgets]
  VM2 --> UI2[Status Badge]

  UI3[Controls\nSliders, D-Pad, Buttons] --> VM3[ControlViewModel]
  VM3 -->|Command| Repo
  Repo -->|STATUSTEXT| Svc
  Svc --> FC

  CAM[Phone Camera\nCameraX] -->|Preview Surface| CV1[CameraPreview Composable]
  RTSP[RTSP/UDP Stream] -->|Media3 ExoPlayer| CV2[VideoPlayer Composable]
  CV1 & CV2 --> UI[Video Background]
  UI -->|Tap (x,y)| VM3
```

### ۱.۳ مدیریت State

- تمام stateها به‌صورت `StateFlow<UiState>` در ViewModel نگهداری می‌شوند.
- `FlightState` یک data class immutable است؛ هر آپدیت تلمتری یک کپی جدید می‌سازد (مطابق الگوی `FlightState` در نسخه‌ی پایتون).
- `ConnectionState` یک sealed enum است: `Disconnected | Connecting | Connected | Error(reason)`.

---

## ۲. پروتکل ارتباطی (دقیقاً مطابق نسخه‌ی اصلی)

این پروتکل حیاتی است — عامل **باید** بدون تغییر پیاده‌سازی کند تا با فلایت کنترلر موجود سازگار باشد.

### ۲.۱ ارسال فرمان‌ها — همه از طریق MAVLink `STATUSTEXT` (severity = 6)

| عمل | رشته‌ی ارسالی | خروجی کد پایتون اصلی |
|---|---|---|
| Start با PID | `START:TRUE,{pid_values}` | `send_start(pid_values)` |
| Start بدون PID | `START:TRUE` | `send_start(None)` |
| Cancel | `CANCEL:TRUE,Notcare:TRUE` | `send_cancel()` |
| Mode Manual | `MANUAL` | `send_mode(1)` |
| Mode Auto | `AUTOMAT` | `send_mode(2)` |
| Speed | `Speed {spd:.1f} → Pitch {spd:.1f}` | `send_speed(spd)` |
| Class | `CLASS:{cls},Notcare:{cls}` | `send_class(cls)` |
| Zoom | `Zoom:{zoom:.1f}` (۱.۰ تا ۱۰.۰) | `send_zoom(zoom)` |
| Pitch | `Pitch:{pitch:.1f}` (-۹۰ تا ۹۰) | `send_pitch(pitch)` |
| Position (tap) | `Pos:{x},{y}` | `send_target_position(x, y)` |

> محدودیت STATUSTEXT: طول پیام حداکثر ۵۰ بایت؛ در صورت طولانی‌تر، truncate شود.
> Retry policy: ۵ تلاش با تأخیر ۵۰ms بین هر تلاش.
> همه با `severity = 6 (INFO)` ارسال شوند.

### ۲.۲ دریافت تلمتری — اشتراک در پیام‌های MAVLink

| پیام MAVLink | فیلدهای استخراج‌شده |
|---|---|
| `BATTERY_STATUS` | `voltages[0] / 1000.0` → battery (ولت) |
| `RANGEFINDER` | `distance + 0.05` → altitude (متر، گرد به ۴ رقم اعشار) |
| `GPS_RAW_INT` | `eph / 100.0` → hdop  •  `satellites_visible` → satellites |
| `HEARTBEAT` | دریافت شد → `_last_heartbeat = now` |
| `STATUSTEXT` | فقط `severity == 6` → پاس به `FlightState.update_from_message()` |

### ۲.۳ پارس پیام STATUSTEXT برگشتی — فرمت `Key:Value,Key:Value`

فرمت ورودی (بدون فاصله): `Op:1,St:1,Md:1,Cls:0,Spd:19.0,Zoom:1.0,Pitch:0.0,Can:0`

| کلید | نوع | معنا |
|---|---|---|
| `Op` | int | 1=Ready, 2=In operation |
| `St` | int | 1=First, 2=Track |
| `Md` | int | 1=Manual, 2=Automatic |
| `Cls` | int | 0=Person, 2=Car |
| `Spd` | float | m/s |
| `Zoom` | float | 1.0–10.0 |
| `Pitch` | float | -90 تا 90 |
| `Can` | int (optional) | 0=inactive, 1=cancelled/active |

> نکته: هر بخشی که `:` ندارد باید نادیده گرفته شود (مطابق کد اصلی `if ":" not in p: continue`).
> نکته: مقدار خالی `Can` باید به‌صورت `0` تفسیر شود.

### ۲.۴ Watchdog

- هر ۱ ثانیه بررسی: اگر `now - last_heartbeat > 5s` → state به `DISCONNECTED` تغییر کند.
- پایش تغییر mode هر ۵۰ms (polling `vehicle.mode.name`) — در نسخه‌ی اندروید با Firebase-style listener یا polling سبک جایگزین شود (مثلاً `Flow` با `delay(50)`).
- در تغییر state اتصال، UI به‌صورت واکنشی آپدیت شود.

---

## ۳. فازبندی پیاده‌سازی

### Phase 0 — Bootstrap پروژه (۰.۵ روز)

**هدف:** پروژه‌ی Android Studio بالا بیاید و زیرساخت DI/Logging/Theme آماده شود.

دستورات به عامل:
1. یک پروژه‌ی **Empty Compose Activity (Kotlin)** بساز با `minSdk = 26`, `targetSdk = 34`.
2. Gradle (Kotlin DSL) را تنظیم کن: Compose BOM، Hilt، Coroutines، Timber، Media3، CameraX، MAVLink-Kotlin.
3. تم Material3 را با پالت رنگی مطابق نسخه‌ی اصلی بساز:
   - پس‌زمینه‌ی تیره (`#0E0E0E`)
   - پنل نیمه‌شفاف (`#333333` با alpha 0.6)
   - آبی الکتریک (`#00BFFF`) — اکشن‌های اصلی
   - نارنجی ایمنی (`#FF8C00`) — D-Pad
   - قرمز هشدار (`#FF4444`) — Cancel/Alert
4. `AndroidManifest.xml` را با همه‌ی permission های بالا پیکربندی کن.
5. Application class با `@HiltAndroidApp` بساز و Timber را init کن.
6. `MainActivity` را به‌صورت `ComponentActivity()` با `setContent {}` پیاده کن؛ فعلاً فقط یک `Text("GCS bootstrap OK")` نشان بده.
7. فایل `app_icon.ico` اصلی را به adaptive icon اندروید تبدیل کن (foreground + background vector).
8. ProGuard rules برای MAVLink و Hilt اضافه کن.

**DoD:** اپ روی دستگاه/امولاتور نصب و اجرا می‌شود، آیکون در launcher دیده می‌شود، Timber لاگ می‌دهد.

---

### Phase 1 — اتصال Bluetooth SPP + پروتکل MAVLink (۲ روز)

**هدف:** اپ بتواند به فلایت کنترلر متصل شود، تلمتری دریافت کند، heartbeat watchdog کار کند و فرمان STATUSTEXT ارسال کند — **بدون UI جز یک صفحه‌ی ساده‌ی state**.

دستورات به عامل:

1. **Domain model**: data class های `FlightState`, `Telemetry`, `ConnectionState` (sealed) و `Command` (sealed class با subclass های `Start`, `Cancel`, `SetMode`, `SetSpeed`, `SetClass`, `SetZoom`, `SetPitch`, `SendPosition`) را در پکیج `domain` بساز. این کلاس‌ها immutable و pure Kotlin باشند.

2. **پروتکل MAVLink**: یک object به نام `MavlinkProtocol` بساز که:
   - `encodeStatustext(text: String, severity: Int = 6): ByteArray` — کدگذاری MAVLink v2 STATUSTEXT (طول ≤ ۵۰، truncate با احترام به UTF-8 multi-byte).
   - `decodeStatustextPayload(payload: ByteArray): String`
   - `parseStateString(text: String): FlightStateUpdate` — منطبق با جدول §۲.۳.

3. **BluetoothSppService** (Foreground Service):
   - SPP UUID ثابت: `00001101-0000-1000-8000-00805F9B34FB`.
   - `BluetoothSocket` با `createRfcommSocketToServiceRecord` بساز.
   - `connect()` در یک coroutine با timeout ۱۰s.
   - جریان خواندن: `InputStream.read()` در حلقه با buffer ۱۰۲۴ → emit به `MutableStateFlow<ByteArray>`.
   - جریان نوشتن: یک `Channel<ByteArray>` که write را سریال می‌کند.
   - آپدیت state: `Flow<ConnectionState>` به بیرون emit کند.
   - Notification کانال: `CHANNEL_CONNECTION` با `IMPORTANCE_LOW`، notification با وضعیت اتصال.
   - در `onDestroy`: socket بسته شود، notification لغو شود.

4. **MavlinkFlightRepository**:
   - MAVLink-Kotlin parser را روی جریان بایت‌ها از سرویس اعمال کن.
   - Listener های جدول §۲.۲ را ثبت کن و `Flow<FlightState>` بساز.
   - متدهای `send*` (مطابق جدول §۲.۱) — همه به `sendStatustext(text)` ختم شوند.
   - Retry: ۵ بار با ۵۰ms delay.
   - Polling mode: هر ۵۰ms `vehicle.mode.name` → اگر تغییر کرده، emit کن.
   - Heartbeat watchdog:_job هر ۱s → اگر `now - lastHb > 5s` → `Disconnected`.

5. **ConnectionViewModel**:
   - `uiState: StateFlow<ConnectionUiState>` (Idle | Connecting | Connected(port) | Error(msg)).
   - متدها: `connect(device: BluetoothDevice)`, `disconnect()`, `reconnect()`.
   - expose `availableDevices: Flow<List<BluetoothDevice>>` (از `BluetoothManager.adapter.bondedDevices` فیلتر شده).

6. **TelemetryViewModel**:
   - `flightState: StateFlow<FlightState>` — collect از repository.

7. **UI ساده‌ی Phase 1**: یک `LazyColumn` که تمام فرمان‌های تست را دکمه دارد + آخرین state را به‌صورت متن نشان می‌دهد. این فقط برای اعتبارسنجی است.

8. **Permissions runtime flow**: BT_CONNECT/BT_SCAN/ACCESS_FINE_LOCATION را با rationale dialog درخواست کن.

**DoD:**
- اپ به یک HC-05 یا فلایت کنترلر واقعی متصل می‌شود و "CONNECTED to <port>" لاگ می‌خورد.
- اگر پینگ PYTHON-ArduPilot در یک لپتاپ تست بزنی، heartbeat دریافت می‌شود.
- باتری/GPS/altitude به‌صورت زنده آپدیت می‌شود.
- با زدن دکمه‌ی تست، پیام `MANUAL` به‌صورت STATUSTEXT ارسال می‌شود و در سمت پرواز قابل دیدن است.
- قطع کابل/خروج از برد BT → اپ در ≤ ۵ ثانیه به حالت DISCONNECTED برود.

---

### Phase 2 — ویدئوی زنده (دو منبع) (۱.۵ روز)

**هدف:** پس‌زمینه‌ی اپ ویدئوی زنده باشد؛ کاربر بتواند بین دوربین گوشی و استریم RTSP جابجا شود و tap روی ویدئو مختصات را به فلایت کنترلر ارسال کند.

دستورات به عامل:

1. **CameraXPreviewRepository**: با `ProcessCameraProvider`، `Preview` use case و `PreviewView` (با `IMPLEMENTATION_MODE_TEXTURE_VIEW` برای امکان overlay). lifecycle-aware باشد.
2. **RtspVideoRepository**: با `ExoPlayer.Builder(context).build()` و `MediaItem.fromUri(rtspUrl)`؛ از `RTSP` در Media3 پشتیبانی کن. سطح خروجی به `PlayerView` یا `TextureView` بده.
3. **CameraViewModel**: `source: StateFlow<VideoSource>` که `PhoneCamera(index)` یا `RtspStream(url)` یا `None` باشد.
4. **VideoSurface composable**: یک `AndroidView` که با توجه به source، یا `PreviewView` یا `PlayerView` را render می‌کند. تمام‌صفحه با `Modifier.fillMaxSize()`.
5. **Tap-to-position**: یک `Modifier.pointerInput { detectTapGestures { offset -> val (x, y) = offset; controlViewModel.sendPosition(x, y) } }` روی surface. مختصات را به pixel-space فعلی map کن (می‌توان نرمالایز کرد به ۰–۱۰۰ یا همان px خام بفرستی — مطابق نسخه‌ی اصلی `Pos:{x},{y}`).
6. **CameraSelector UI**: یک چپ‌سوی بالا (Top-Trailing) که با کلیک روی آن یک dropdown باز می‌شود با گزینه‌های "دوربین جلو / دوربین پشت / استریم RTSP". وقتی RTSP انتخاب شد، یک TextField برای URL نمایش داده شود.
7. **خطای ویدئو**: اگر منبع باز نشد، یک placeholder با آیکون drone (از assets اصلی) و پیام "Camera not available" نشان داده شود.
8. **Lifecycle ویدئو**: در `onPause` سرویس ضبط متوقف شود؛ در `onResume` دوباره شروع.

**DoD:**
- دوربین گوشی به‌صورت تمام‌صفحه نمایش داده می‌شود و روی آن overlay‌ها (فاز بعد) قابل گذاشتن است.
- اگر یک سرور RTSP محلی (مثلاً `rtsp://<ip>:8554/test`) داری، استریم نمایش داده می‌شود.
- Tap روی ویدئو در لاگ تولید می‌کند: `Pos:540,960`.
- بین دو منبع بدون crash جابجا می‌شوی.

---

### Phase 3 — HUD تلمتری و Top Bar (۱ روز)

**هدف:** نوار بالای صفحه با tabs، ۵ ویجت تلمتری و crosshair در مرکز ویدئو پیاده شود.

دستورات به عامل:

1. **Top Bar composable**: یک `Row` با ارتفاع ۵۶dp و پس‌زمینه‌ی نیمه‌شفاف `#CC1A1A1A`:
   - **Tabs**: دو `Text` با underline indicator: `Camera` | `Flight Controller`.
   - **Telemetry Widgets**: ۵ ویجت در یک `Row` با `horizontalArrangement = spacedBy(8.dp)`:
     - BAT — آیکون باتری + مقدار ولتاژ (`%.1f`)
     - SAT — آیکون ماهواره + عدد
     - ALT — آیکون ارتفاع + `%.1f` متر
     - HDOP — آیکون pin + `%.1f`
     - MODE — آیکون هواپیما + نام mode یا `---`
   - آیکون‌ها را از `assets/` اصلی (altitude.png, satellite.png, plane-mode.png, ...) به vector drawable تبدیل کن.
   - هر ویجت یک `Surface` با corner-radius 8dp و پس‌زمینه‌ی نیمه‌شفاف.
2. **Status badge**: یک دایره کوچک کنار tabs که رنگش بر اساس `ConnectionState` تغییر کند (قرمز/زرد/سبز/خاکستری).
3. **Crosshair overlay**: در مرکز `Box` ویدئو، یک composable با `Modifier.align(Alignment.Center)` که یک crosshair سبز (دو خط متقاطع + یک حلقه) رسم می‌کند با `Canvas`.
4. **Reconnect button**: وقتی state = Disconnected یا Error، یک FAB با آیکون "refresh" در گوشه ظاهر شود؛ کلیک → `reconnect()`.
5. همه‌ی مقادیر از `TelemetryViewModel.flightState` به‌صورت `collectAsStateWithLifecycle()` خوانده شوند.

**DoD:**
- باتری/GPS/altitude/mode به‌صورت زنده آپدیت می‌شوند.
- Crosshair در مرکز ویدئو دیده می‌شود.
- با قطع اتصال، badge قرمز و دکمه‌ی reconnect ظاهر می‌شود.

---

### Phase 4 — کنترل‌های پرواز و دوربین (۲ روز)

**هدف:** همه‌ی کنترل‌های نسخه‌ی اصلی پیاده شوند: اسلایدر pitch عمودی، D-pad، پنل پایین شامل PID + ماموریت + سرعت + هدف.

دستورات به عامل:

#### ۴.۱ اسلایدر pitch عمودی (سمت چپ)

1. یک `PitchSlider` composable بساز با:
   - ارتفاع ۶۰٪ صفحه، عرض ۵۶dp.
   - Track با gradient عمودی از آبی (`#00BFFF`) بالا → بنفش (`#9B59B6`) وسط → قرمز (`#FF4444`) پایین (از `Brush.verticalGradient`).
   - Thumb: یک `Surface` سفید با corner-radius 12dp، شامل یک آیکون hamburger و متن زاویه (`%.1f°`).
   - محدوده: -90 تا +90.
   - Marking های -90° و +90° در دو انتها.
   - دو دکمه‌ی مستطیلی در بالا و پایین track: دکمه‌ی بالا آبی با فلش `▲`، دکمه‌ی پایین آبی با فلش `▼`. هر کلیک، مقدار pitch را ۱° افزایش/کاهش می‌دهد.
2. تعامل: در حین drag، هر `onValueChange` با throttle ۱۰۰ms به `send_pitch(value)` ارسال شود (جلوگیری از spam STATUSTEXT).
3. مقدار pitch در `FlightState` از سمت فلایت کنترلر برگردانده شود → اسلایدر از آن sync شود (two-way binding با اولویت دریافت‌کننده).

#### ۴.۲ D-Pad (سمت راست)

1. یک `DirectionalPad` composable با چینش صلیبی:
   - Center: دایره‌ی بزرگ با gradient نارنجی → زرد.
   - Up/Down/Left/Right: ۴ فلش مثلثی نارنجی.
   - بالای D-Pad یک badge با پس‌زمینه‌ی مشکی و متن زرد `%.1fX` (zoom level).
2. چهار جهت به فرمان‌های pitch (دوافقUpDown) و steer (دوافقLeftRight) map شود:
   - مطابق `ui/slider_buttons_pitch.py` و `ui/slider_buttons_zoom.py` در نسخه‌ی اصلی (نام فایل‌ها نشانه‌ی هدفشان است).
   - اگر steer slider جداگانه نیاز است، یک slider افقی کوچک زیر D-Pad اضافه کن (مطابق نام `steer_slider.py` در cache).
3. Center button → `send_cancel()` یا `send_target_position(centerX, centerY)`.

#### ۴.۳ پنل پایین (Bottom Panel)

1. یک `BottomControlPanel` با ارتفاع ~۲۰٪ صفحه و پس‌زمینه‌ی `#CC222222`:
2. **ردیف اول (PID Inputs)**: ۳ TextField با فونت monospace و پس‌زمینه‌ی خاکستری روشن (`#DDDDDD`):
   - `kp_yaw_2`, `kp_roll`, `kp_pitch`
3. **ردیف دوم (D-Gains + Mission + Speed)**:
   - ۳ TextField: `kd_yaw_1`, `kd_yaw_2`, `kd_roll`
   - ۴ دکمه‌ی ماموریت: `start`, `cancel`, `manual`, `auto` (با پس‌زمینه‌ی خاکستری تیره؛ `start` سبز، `cancel` قرمز).
   - ۳ دکمه‌ی سرعت: `12 m/s`, `19 m/s`, `22 m/s`.
4. **ردیف سوم (Limits + Targets)**:
   - ۳ TextField: `limit_yaw_1`, `limit_yaw_2`, `limit_roll`
   - ۴ دکمه‌ی target با آیکون (drawable های مربوطه):
     - Person (assets/people.png)
     - Car (assets/sedan.png)
     - Balloon (assets/balloon.png)
     - UAV (assets/drone.png)
5. **Behavior**:
   - start button: مقادیر PID فعلی را گرفته و `START:TRUE,{kp_yaw_2},{kp_roll},...` را به `send_start(pid_values)` پاس بدهد (فرمت دقیق PID را مطابق کد اصلی بساز).
   - cancel button: `send_cancel()`.
   - manual/auto: `send_mode(1)` / `send_mode(2)`.
   - speed buttons: `send_speed(spd)`.
   - target icons: `send_class(0|2|3|4)` — مطابق نگاشت: Person=0, Car=2 (مطابق کد اصلی `Cls:0=Person, Cls:2=Car`); برای Balloon/UAV مقادیر ۳ و ۴ را تعریف کن (به‌عنوان توسعه‌ی آینده، همان کلاس است که در نسخه‌ی اصلی هدف در `cls` قرار می‌گیرد).

**DoD:**
- اسلایدر pitch از -90 تا +90 قابل drag است و مقادیر در UI به‌صورت `%.1f°` نمایش داده می‌شود.
- هر drag، پیام `Pitch:X.X` به فلایت کنترلر ارسال می‌شود (در لاگ visible).
- D-Pad هر چهار جهت قابل کلیک است و به فرمان‌های مناسب map می‌شود.
- همه‌ی دکمه‌های پنل پایین کار می‌کنند: start پیام `START:TRUE,...` را با مقادیر PID فعلی ارسال می‌کند.
- روی صفحه‌های کوچک (۳.۵ اینچ) هم چینش به‌هم نمی‌ریزد (ConstraintLayout-like با weight).

---

### Phase 5 — پولیش، قابلیت اطمینان و Settings (۱.۵ روز)

**هدف:** اپ production-ready شود: Settings، Auto-reconnect، مدیریت خطا، Onboarding.

دستورات به عامل:

1. **Settings screen** با DataStore:
   - انتخاب BT device پیش‌فرض (با dropdown از paired devices).
   - Baud rate (default 115200) — اگرچه برای BT SPP ثابت است اما قابل تغییر باشد.
   - URL پیش‌فرض RTSP.
   - دوربین پیش‌فرض (front/back).
   - Speed preset پیش‌فرض.
   - Target class پیش‌فرض.
2. **Auto-reconnect**: وقتی disconnected شد، با exponential backoff (1s, 2s, 4s, 8s, max 30s) تلاش مجدد. اگر کاربر manually `disconnect()` زد، auto-reconnect غیرفعال شود.
3. **Foreground service notification** با محتوای پویا: `<connection state> • <mode> • <battery>V`.
4. **Keep screen on**: `FLAG_KEEP_SCREEN_ON` وقتی وصل است.
5. **Screen orientation**: قفل به `landscape` در `AndroidManifest` برای Activity اصلی (با `screenOrientation="landscape"`).
6. **Onboarding**: اگر اولین اجرا، یک Dialog با توضیح permission های لازم و دکمه‌ی "اجازه می‌دهم".
7. **Error UI**: اگر ویدئو قطع شد، یک overlay با آیکون و دکمه retry. اگر BT قطع شد، یک snackbar.
8. **Accessibility**: همه‌ی دکمه‌ها `contentDescription` داشته باشند.
9. **Splash screen**: با آیکون اپ و نام "Drone GCS" (SplashScreen API).
10. **ProGuard**: قواعد برای mavlink-kotlin و reflection-based libs.
11. **Build variants**: `debug` با Timber logging، `release` با minify و strip logging.

**DoD:**
- اپ برای ۱۰ دقیقه بدون crash با فلایت کنترلر وصل می‌ماند.
- بعد از قطع موقت BT، اپ به‌صورت خودکار دوباره وصل می‌شود.
- Settings ذخیره می‌شود بین اجراها.
- در حالت landscape روی دستگاه‌های مختلف تست می‌شود.

---

## ۴. نقشه‌ی فایل‌های پروژه (پیشنهادی)

```
app/
├── src/main/
│   ├── java/com/khosrooo/dronegcs/
│   │   ├── DroneGcsApp.kt                    # @HiltAndroidApp
│   │   ├── MainActivity.kt
│   │   ├── di/
│   │   │   ├── BluetoothModule.kt
│   │   │   ├── MavlinkModule.kt
│   │   │   └── CoroutineModule.kt
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── FlightState.kt
│   │   │   │   ├── Telemetry.kt
│   │   │   │   ├── ConnectionState.kt
│   │   │   │   └── Command.kt
│   │   │   └── protocol/
│   │   │       └── MavlinkProtocol.kt
│   │   ├── data/
│   │   │   ├── bluetooth/
│   │   │   │   ├── BluetoothSppService.kt
│   │   │   │   └── BluetoothDeviceScanner.kt
│   │   │   ├── mavlink/
│   │   │   │   └── MavlinkFlightRepository.kt
│   │   │   ├── camera/
│   │   │   │   └── CameraXPreviewRepository.kt
│   │   │   ├── video/
│   │   │   │   └── RtspVideoRepository.kt
│   │   │   └── datastore/
│   │   │       └── SettingsRepository.kt
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   ├── components/
│   │   │   │   ├── video/VideoSurface.kt
│   │   │   │   ├── video/CrosshairOverlay.kt
│   │   │   │   ├── hud/TopBar.kt
│   │   │   │   ├── hud/TelemetryWidget.kt
│   │   │   │   ├── controls/PitchSlider.kt
│   │   │   │   ├── controls/DirectionalPad.kt
│   │   │   │   ├── controls/BottomControlPanel.kt
│   │   │   │   ├── controls/PidInputRow.kt
│   │   │   │   └── controls/TargetSelector.kt
│   │   │   ├── screens/
│   │   │   │   ├── MainScreen.kt           # ترکیب همه‌ی overlays روی ویدئو
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── OnboardingScreen.kt
│   │   │   └── navigation/
│   │   │       └── AppNavGraph.kt
│   │   └── viewmodel/
│   │       ├── ConnectionViewModel.kt
│   │       ├── TelemetryViewModel.kt
│   │       ├── ControlViewModel.kt
│   │       └── CameraViewModel.kt
│   ├── res/
│   │   ├── drawable/                         # vector از assets اصلی
│   │   ├── mipmap-anydpi-v26/                # adaptive icon
│   │   └── values/strings.xml + themes.xml
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
```

---

## ۵. معیارهای پذیرش نهایی (Definition of Done — کل پروژه)

- [ ] اپ در `release` build بدون crash نصب و اجرا می‌شود.
- [ ] به فلایت کنترلر (یا شبیه‌ساز SITL ArduPilot با پل BT) متصل می‌شود.
- [ ] تلمتری BAT/SAT/ALT/HDOP/MODE به‌صورت زنده آپدیت می‌شوند.
- [ ] هر دو منبع ویدئو (دوربین گوشی و RTSP) کار می‌کنند و قابل toggle هستند.
- [ ] همه‌ی ۶ فرمان ارسالی جدول §۲.۱ به‌درستی ارسال می‌شوند و در سمت پرواز receive می‌شوند.
- [ ] اسلایدر pitch با drag و دکمه‌های ▲▼ کار می‌کند.
- [ ] D-Pad هر ۴ جهت + center کار می‌کند.
- [ ] پنل پایین شامل PID + mission + speed + target کامل است.
- [ ] heartbeat watchdog در ≤ ۵s قطع را تشخیص می‌دهد.
- [ ] auto-reconnect با backoff کار می‌کند.
- [ ] Settings ذخیره می‌شود بین اجراها.
- [ ] در landscape روی حداقل ۲ اندازه‌ی صفحه (phone + tablet) تست شده.
- [ ] foreground service notification نشان داده می‌شود.

---

## ۶. نکات کلیدی برای عامل هوش مصنوعی

### ۶.۱ حقایق مهم که نباید فراموش کنی

1. **این یک نسخه‌ی port است، نه بازطراحی.** پروتکل STATUSTEXT، مقادیر پیش‌فرض، و رفتار watchdog باید دقیقاً منطبق با نسخه‌ی پایتون باشد تا با فلایت کنترلر موجود کار کند. کد اصلی `flight_interface.py` را به‌عنوان reference نگه دار.

2. **MAVLink STATUSTEXT حداکثر ۵۰ بایت است.** هرگز بیشتر نفرست؛ اگر رشته طولانی‌تر شد، هنگام truncate مراقب کاراکترهای چندبایتی UTF-8 باش (مثلاً نصف شدن یک کاراکتر فارسی نباید رخ دهد؛ اگرچه پروتکل فعلی فقط ASCII استفاده می‌کند).

3. **Bluetooth SPP ≠ BLE.** از `BluetoothSocket.createRfcommSocketToServiceRecord(SPP_UUID)` استفاده کن، نه `BluetoothGatt`. بسیاری از کتابخانه‌های RN/Flutter فقط BLE دارند — به همین دلیل Kotlin را انتخاب کردیم.

4. **Compose روی Android 8+ کاملاً پشتیبانی می‌شود**، اما برخی API ها (مثل `pointerInput` جدید) نیازمند بررسی نسخه هستند.

5. **دسترسی به دوربین در Android 14+ نیاز به foreground service type دارد** اگر در پس‌زمینه هم استفاده می‌شود؛ در این اپ چون preview فقط در foreground است، permission معمولی `CAMERA` کافی است.

6. **استریم RTSP/UDP از "MAVLink" نیازی به خود MAVLink ندارد** — MAVLink فقط فرمان/تلمتری را حمل می‌کند. در عمل، ویدئوی زنده از یک ویدئو فرستنده جدا (مثلاً روی کامپیوتر همراه روی پهاداد) از طریق RTSP/UDP می‌آید. این جداسازی مهم است.

### ۶.۲ تله‌های رایج که باید از آن‌ها دوری کنی

- ❌ **از کتابخانه‌ی `dronekit-android` قدیمی استفاده نکن** — دیگر نگهداری نمی‌شود. به‌جای آن از `mavlink-kotlin` رسمی استفاده کن.
- ❌ **Bluetooth I/O روی main thread ممنوع** — حتماً در coroutine یا dedicated thread.
- ❌ **Compose state ها را از background thread آپدیت نکن** — از `Flow.flowOn(Dispatchers.IO).stateIn(...)` استفاده کن.
- ❌ **هرگز frame ویدئو را به‌صورت Bitmap در Compose state نگه ندار** — از `PreviewView` (AndroidView) یا `SurfaceView` استفاده کن، نه publish فریم به Composable.
- ❌ **چندین آپدیت pitch در ثانیه را raw نفرست** — throttle کن (مثلاً ۱۰۰ms).
- ❌ **permission ها را در MainActivity چک نکن** — از Accompanist Permissions یا دستی با `ActivityResultContracts` استفاده کن.
- ❌ **Target/Class values را به دلخواه تغییر نده** — `Cls:0=Person, Cls:2=Car` در سمت فلایت کنترلر hard-coded است. برای Balloon/UAV می‌توانی مقادیر ۳ و ۴ را اضافه کنی اما این نیاز به تغییر سمت پرواز دارد — با کاربر هماهنگ کن.

### ۶.۳ استراتژی تست

- **Unit tests** برای `MavlinkProtocol`: encode/decode/parseStateString — با sample های واقعی از کد پایتون.
- **Integration test** با `Robolectric` برای BluetoothSppService (با mock BluetoothAdapter).
- **Manual smoke test** با شبیه‌ساز MAVLink روی لپتاپ (مثلاً `mavproxy.py` با output به SITL).

### ۶.۴ توالی پیشنهادی اجرا

```
Phase 0 → Phase 1 (مستقل) → Phase 2 (مستقل)
                              ↓
                       Phase 3 (وابسته به 1, 2)
                              ↓
                       Phase 4 (وابسته به 1, 2, 3)
                              ↓
                       Phase 5 (وابسته به همه)
```

Phase 1 و Phase 2 **می‌توانند موازی اجرا شوند** (یکی روی Bluetooth/MAVLink، دیگری روی Camera/RTSP) — اگر عامل می‌تواند چند کار را موازی مدیریت کند.

---

## ۷. فایل‌های مرجع اصلی

این فایل‌ها را از پوشه‌ی `GUI_UAV-just_slider_pitch/` به‌عنوان منبع حقیقت استفاده کن:

| فایل | محتوا | چه چیزی برداشت کنیم |
|---|---|---|
| `core/flight_interface.py` | منطق اتصال DroneKit + STATUSTEXT | پروتکل کامل (جداول §۲.۱ و §۲.۲) |
| `core/camera_interface.py` | OpenCV camera loop | الگوی lifecycle دوربین |
| `core/app_controller.py` | هماهنگ‌کننده | الگوی controller |
| `assets/*.png` | آیکون‌ها | تبدیل به vector drawable |
| `assets/app_icon.ico` | آیکون اپ | تبدیل به adaptive icon |
| `assets/gui_preview.png` | تصویر UI | مرجع چیدمان نهایی |
| `requirements.txt` | نسخه‌های کتابخانه پایتون | معادل‌های اندروید را در §۰ ببین |

---

**پایان سند برنامه‌ریزی.**
