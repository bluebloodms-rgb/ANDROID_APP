# 📋 HANDOFF — ادامه کار جلسه بعد

> **برای عامل/دستیار فردا:** این سند وضعیت دقیق پروژه است. کاربر فقط می‌گوید «ادامه بده» —
> بر اساس بخش «کارهای بعدی به ترتیب» شروع کن و نیازی به پرسیدن مجدد زمینه نیست.

**آخرین به‌روزرسانی:** 2026-08-26 شب
**آخرین commit:** `7a407d7`
**APK نصب‌شده روی گوشی:** همگام با `7a407d7`

---

## ✅ وضعیت فعلی (تست‌شده روی دستگاه واقعی)

### سخت‌افزار کاربر
- گوشی: Samsung Galaxy A54 (SM-A546E)، Android 16 / API 36، از طریق USB متصل است (adb آماده)
- فلایت‌کنترلر: دستگاه با نام بلوتوث `SIYI-5902210770` که **فریمور ArduPilot دارد**
  (تأییدشده از روی پیام‌های استریم‌شده)
- ماک MAC FC: `41:42:83:D2:50:E0` (یک SIYI دیگر با MAC `...27:94` هم paired است)

### آنچه الان کار می‌کند (تأیید میدانی)
| قابلیت | وضعیت |
|---|---|
| نصب/اجرای بدون کرش روی Android 16 | ✅ |
| دوربین گوشی تمام‌صفحه (CameraX, FILL_CENTER) | ✅ |
| Runtime permissions (CAMERA/BT_CONNECT/POST_NOTIFICATIONS) | ✅ |
| اتصال بلوتوث SPP پایدار + دیالوگ انتخاب دستگاه | ✅ |
| badge اتصال (قرمز/نارنجی/آبی) صحیح آپدیت می‌شود | ✅ |
| پارس MAVLink v2 استاندارد (~۹۵٪+ فریم‌ها) | ✅ |
| تلمتری SAT (از GPS_RAW_INT@29) | ✅ عدد واقعی: ۱۲ |
| تلمتری HDOP (eph@20 ÷100) | ✅ عدد واقعی: 2.33 |
| تلمتری BAT (SYS_STATUS@14 یا BATTERY_STATUS) | ⚠️ کد هست، عدد نمایشی هنوز توسط کاربر تأیید نشده |
| تلمتری ALT (RANGEFINDER=173) | ⚠️ اگر رنج‌فایندر سخت‌افزاری نباشد `--` می‌ماند |
| START/CANCEL دکمه فعال + ارسال STATUSTEXT سفارشی | ⚠️ ارسال می‌شود ولی ArduPilot عمل اجرا نمی‌کند |

### تست‌ها
- ۲۶ unit test پاس (CommandTest=12, MavlinkProtocolTest=13 شامل RealFrameTest با بایت‌های واقعی FC, CommandTest قبلی)

---

## 🛠 محیط توسعه (نکات حیاتی — در این sandbox)

```bash
# بیلد
cd /home/mohammad/Desktop/ai-priject-folder/ANDROID_APP/android
./gradlew :app:assembleDebug --no-daemon        # ~2-3 min
./gradlew :app:testDebugUnitTest --no-daemon

# نصب و تست روی گوشی (متصل USB)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dronegcs.app/.MainActivity
adb logcat -c && adb logcat -v time > /tmp/opencode/test.log &   # ضبط پس‌زمینه
```

- **dl.google.com فیلتر است!** مخازن: Aliyun google mirror + Tencent + MavenCentral
  (در `android/settings.gradle.kts`). هرگز `google()` اضافه نکن.
- SDK در `/home/mohammad/android-sdk` (platform-34 + build-tools 34.0.3 دستی نصب شدند؛
  sdkmanager کار نمی‌کند — پکیج‌ها از GitHub releases)
- kotlinc مستقل: `/tmp/opencode/kotlinc/` (ممکن است با ریبوت پاک شود؛ بازسازی:
  JetBrains/kotlin release v1.9.22)
- toolchain: Gradle 8.5 wrapper / AGP 8.2.2 / Kotlin 1.9.22 / compose-bom **2024.05.00** /
  Hilt 2.50 / KSP 1.9.22-1.0.17 / targetSdk 34 (عمداً — روی Android 15/16 edge-to-edge اجباری نمی‌شود)
- git identity: `-c user.name="qwen.ai[bot]" -c user.email="qwenlm-intl@service.alibaba.com"`

### معماری کلیدی (فایل‌های مهم)
```
data/bluetooth/BluetoothSppService.kt   سرویس Foreground؛ startForeground فوری در onCreate؛
                                        RX hex dump (۶ چانک اول) برای دیباگ
data/bluetooth/BluetoothLink.kt         پل singleton بین سرویس و مصرف‌کنندگان
data/mavlink/MavlinkFlightRepository.kt پارس استریم (بافر ماندگار cross-chunk + resync)،
                                        هندلرهای تلمتری با آفست‌های تأییدشده
domain/protocol/MavlinkProtocol.kt      encode/parse v2 استاندارد؛ CRC_EXTRA تک‌بایتی!
domain/protocol/MavlinkCrcExtra.kt      جدول رسمی ۳۰۷ پیام (common + ardupilotmega)
viewmodel/ConnectionViewModel.kt        connect/disconnect/reconnect/auto-reconnect
ui/screens/MainScreen.kt                صفحه اصلی فاز ۱ + permissions + دیالوگ BT
```

### درس‌های دیباگ امروز (برای جلوگیری از تکرار)
1. همه فراخوانی CameraX باید main thread باشد
2. CRC_EXTRA یک بایت است — جمع‌کردن بایت دوم همه فریم‌های واقعی را خراب می‌کند
   (و چون انکودر هم همین باگ را داشت، رندتریپ تست‌ها پاس می‌شد!)
3. فریم‌های MAVLink مرز چانک ۶۴ بایتی BT را قطع می‌کنند → بافر ماندگار ضروری است
4. در MAVLink v2 ترتیب فیلدها بر اساس اندازه مرتب شده (نه ترتیب XML) — آفست‌ها را
   با دیکد دستی داده واقعی تأیید کن (lat=35.70, lon=51.22, alt=1332m = تهران)
5. UI compose: material3 و animation باید هم‌قطار باشند (BOM واحد)
6. تشخیص بدون لاگ = حدس کور؛ همیشه اول `adb logcat`

---

## 📌 کارهای بعدی به ترتیب (شروع فردا)

### گام ۰ — احراز وضعیت (۱ دقیقه)
- `adb devices`؛ اگر گوشی وصل بود: نصب آخرین APK، اجرا، گرفتن لاگ ۳۰ ثانیه‌ای و چک
  اینکه تلمتری (BAT/SAT/ALT/HDOP) زنده است.

### گام ۱ — تصمیم معلقِ ALT (نیازمند پاسخ کاربر، در انتها بپرس)
- کاربر تأیید نکرده رنج‌فایندر دارد یا نه. اگر ALT روی UI همیشه `--` بود:
  ارتفاع را از بارومتر بگیر — پیام 33 GLOBAL_POSITION_INT (relative_alt i32 mm @20،
  alt @24) که در استریم FC موجود است. هندلرش نوشته نشده.

### گام ۲ — مهاجرت START/CANCEL به COMMAND_LONG استاندارد (اصلی‌ترین کار)
مشکل: فعلاً `START` یک STATUSTEXT سفارشی (`"START:TRUE"`) می‌فرستد که ArduPilot
فقط به‌عنوان متن لاگ ثبت می‌کند و هیچ عملی نمی‌کند.

سناریوی پیشنهادی برای پیاده‌سازی (تا کاربر دقیق‌تر بگوید):
- START  → `COMMAND_LONG(MAV_CMD_COMPONENT_ARM_DISARM=400, param1=1)` سپس
           `COMMAND_LONG(MAV_CMD_NAV_TAKEOFF=22, param7=min_pitch/alt)` — یا اگر
           مأموریت از قبل روی FC بارگذاری شده: `MAV_CMD_MISSION_START=300`
- CANCEL → `COMMAND_LONG(MAV_CMD_NAV_RETURN_TO_LAUNCH=20)` (RTL) — امن‌ترین تفسیر
           «لغو» ؛ گزینه دوم Disarm(400,0) در زمین
- ACK پیام 77 (COMMAND_ACK) را دریافت و در UI/log نشان بده (موفق/رد شدن)
- نکته: COMMAND_LONG نیاز به retry/resend مثل STATUSTEXT فعلی دارد (repeat(5){delay(50)})
- ⚠️ قبل از merge از کاربر سؤال دقیق سناریو: TAKEOFF خودکار؟ مأموریت؟ RTL یا LAND؟

### گام ۳ — پاک‌سازی دیباگ (پس از پایداری)
- hex dump RX و لاگ GPS_RAW_INT را پشت BuildConfig.DEBUG نگه دار یا حذف کن
- 516→~۴ خطای CRC باقی‌مانده در دقیقه = نویز resync؛ قابل قبول، لاگ WARN را نرخ‌بندی کن

### گام ۴ (بعداً، طبق خواسته اولیه کاربر)
- بازگرداندن کنترل‌های کامل (PID، سرعت، هدف، pitch/gimbal) به UI — کامپوننت‌ها موجودند:
  `BottomControlPanel/PitchSlider/DirectionalPad`
- Settings/Phase1Test دوباره به ناوبری وصل شوند
- RTSP video source

### سؤالات باز از کاربر (جواب داده نشده — در فرصت مناسب بپرس)
1. مدل دقیق FC (Pixhawk؟ SIYI N7؟) و نسخه ArduPilot
2. رنج‌فایندر سخت‌افزاری دارد؟ (برای منبع ALT)
3. BAT چه عددی نشان می‌دهد؟ (تأیید نهایی هندلر باتری)
4. سناریوی دقیق START و CANCEL در پرواز (برای گام ۲)
