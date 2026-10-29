# BubbleDock v1.3.0 — লোকাল ভ্যালিডেশন

তারিখ: 2026-10-29 রাত। v1.3 কোড লোকালে তৈরি, এখনো পুশ হয়নি।

## v1.3-তে বদল

- AndroidManifest: <queries> যোগ — MAIN + LAUNCHER intent। এটাই
  থার্ড-পার্টি অ্যাপ না আসার আসল কারণ ছিল (targetSdk 34, Android 11+
  package visibility)। QUERY_ALL_PACKAGES লাগেনি।
- BubbleService: ডক আর অ্যাকশন ছোট লাইনে — এক সারি, ~৫৬dp, ৩৬dp
  আইকন, TextView আইকন বাটন (Button-এর ডিফল্ট উঁচুতা বাদ)। সার্চ
  একই সারির ভেতরে স্ক্রলের জায়গা নেবে, উঁচুতা বাড়বে না।
- dockApps fallback: ফেভারিট + রিসেন্ট খালি থাকলে সব অ্যাপ।

## লোকালে যাচাই হয়েছে

- kt_check.py: ৮টি Kotlin ফাইলই balanced, stray quote 0।
- XML: manifest + সব res ফাইল well-formed, queries আছে।
- ভার্সন: versionCode 4, versionName 1.3.0।

## লোকালে যাচাই হয়নি — CI-অনলি

- Kotlin কম্পাইল আর AAPT: লোকালে Android SDK 34 নেই, CI-তেই প্রমাণ হবে।
- ফোন টেস্ট: থার্ড-পার্টি অ্যাপ, ছোট লাইন, ডক খালি না থাকা — সব বাকি।

## পুশের সময় সতর্কতা

- Workflow App দিয়ে পুশ করা যাবে না। v1.3 পুশের আগে ব্রাউজারে
  workflow v1.3.0-এ আপডেট করতে হবে, তারপর কোড পুশ (workflow ছাড়া)।

## v1.4.0 local validation

- kt_check: 8 Kotlin files, balanced, stray quotes 0
- All res XML + manifest well-formed
- Kotlin compile + AAPT CI-only (no Android SDK 34 locally)
- Phone test pending: circular actions, double-tap previous app, long-press dock, H/V orientation, floating window on XOS
