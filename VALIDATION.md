# BubbleDock v1.0.0 — লোকাল ভ্যালিডেশন

তারিখ: 2026-10-29। পুশ করা হয়নি, রেপো তৈরি করা হয়নি।

## লোকালে যাচাই হয়েছে

- ফাইল: 31টি ফাইল, Kotlin 8টি ফাইল, মোট 1,320 লাইন (ক্যামেরা/
  নোটিফিকেশন পারমিশন বাটন যোগের পর সামান্য বেড়েছে)।
- `kt_check.py`: 8টি Kotlin ফাইলই balanced, stray quote 0।
- XML: সব values/layout/drawable/xml/mipmap ফাইল well-formed।
- রিসোর্স: Kotlin থেকে রেফার করা সব drawable/color/string/layout/id/xml
  রেফারেন্সের সংজ্ঞা পাওয়া গেছে; strings-এ unescaped apostrophe নেই।
- Dotted style: সব dotted style-এ explicit parent আছে (Theme.BubbleDock-এর
  parent Theme.AppCompat.DayNight.NoActionBar, BD.*-এর parent "")।
- ম্যানিফেস্ট ক্লাস: MainActivity, BubbleService, BubbleAccessibilityService,
  BubbleAdminReceiver, BootReceiver — পাঁচটাই সোর্সে আছে।
- Import চেক: শেষ চেকে কোনো unused import নেই।
- পিন করা ভার্সন: CallShield-এর মতোই AGP 8.5.2, Kotlin 1.9.24,
  compileSdk/targetSdk 34, minSdk 29, appcompat 1.7.0।

## লোকালে যাচাই হয়নি — CI-অনলি

- Kotlin কম্পাইল আর AAPT রিসোর্স লিংক: লোকালে Android SDK 34 প্ল্যাটফর্ম
  ইনস্টল নেই, তাই এটা CI-তেই প্রমাণ হবে। লোকাল পাসকে CI পাস বলে
  দাবি করা হচ্ছে না।
- ফোন টেস্ট: Tamim-এর Infinix-এ বাবল, প্যানেল, রিসেন্ট, গ্রুপ, প্রোফাইল,
  পার-অ্যাপ লুকানো, XOS-এ বেঁচে থাকা — সব বাকি।

## সত্যি সীমা

- WiFi সরাসরি টগল নয়, সিস্টেম WiFi প্যানেল খোলে (Android 10+ সীমা)।
- ব্যাক/রিসেন্ট/স্ক্রিনশট শুধু Accessibility চালু থাকলে, লক শুধু
  Device Admin চালু থাকলে। না থাকলে টাইল কারণসহ টোস্ট দেখায়।
- v1.0-তে নেই: ম্যাক্রো/অটো-ট্যাপ, PhoneLink/Automation Lab ইন্টিগ্রেশন।
