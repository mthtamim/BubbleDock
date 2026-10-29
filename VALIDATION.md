# BubbleDock v1.1.0 — লোকাল ভ্যালিডেশন

তারিখ: 2026-10-29 রাত। v1.1 কোড লোকালে তৈরি, এখনো পুশ হয়নি।

## v1.1-তে বদল

- গ্রিড প্যানেল বাদ, BubbleService-এ ডক + অ্যাকশন পপআপ এসেছে।
- ডাবল ট্যাপ (350ms-এর মধ্যে দুই ট্যাপ) → এক লাইনের ডক, HorizontalScrollView-এ
  একটাই Horizontal LinearLayout, কখনো দ্বিতীয় লাইনে ভাঙে না।
- ডক বড়: 64dp আইকন + লেবেল, প্রতিটা আইটেম 76dp চওড়া, নিচে বড় কার্ড।
- এক ট্যাপ (360ms অপেক্ষার পর, ডাবল ট্যাপ না হলে) → ঐচ্ছিক অ্যাকশন পপআপ।
- ডকে: ফেভারিট + রিসেন্ট, গ্রুপের অ্যাপ, সার্চে সব অ্যাপ।
- MainActivity-এ প্যানেল গ্রিড/লিস্ট আর ডাবল-ট্যাপ বাছাই সরানো হয়েছে;
  pickDoubleTap() ফাংশনটাও সরানো হয়েছে, কারণ v1.1-এ ডাবল ট্যাপ
  সবসময় ডক খোলে।

## লোকালে যাচাই হয়েছে

- `kt_check.py`: BubbleService.kt আর MainActivity.kt balanced, stray quote 0;
  বাকি 6টি Kotlin ফাইলও আগের চেকে balanced ছিল, সেগুলোতে হাত দেওয়া হয়নি।
- XML: সব values/layout/drawable/xml/mipmap ফাইল well-formed।
- রিসোর্স: v1.1-এ ব্যবহৃত সব drawable/color/string রেফারেন্সের সংজ্ঞা আছে
  (bg_panel, bg_chip, bg_bubble, ic_bubble, bd_text, bd_text_secondary,
  search_hint, close, actions_title, no_fav)।
- সার্ভিসে আর কোনো panelView/panelOpen/showPanel/refreshPanelContent
  রেফারেন্স নেই।
- ভার্সন: versionCode 2, versionName 1.1.0।

## লোকালে যাচাই হয়নি — CI-অনলি

- Kotlin কম্পাইল আর AAPT রিসোর্স লিংক: লোকালে Android SDK 34 প্ল্যাটফর্ম
  ইনস্টল নেই, তাই এটা CI-তেই প্রমাণ হবে। লোকাল পাসকে CI পাস বলে
  দাবি করা হচ্ছে না।
- ফোন টেস্ট: ডাবল ট্যাপে ডক, এক ট্যাপে অ্যাকশন, এক লাইনে স্ক্রল,
  XOS-এ বাবল বেঁচে থাকা — সব বাকি।

## পুশের সময় সতর্কতা

- GitHub App-এর Workflows পারমিশন নেই, তাই workflow ফাইল পুশ করা
  যাবে না। v1.1 পুশের আগে ব্রাউজারে পার্সোনাল অ্যাকাউন্ট দিয়ে
  workflow ফাইলটা v1.1.0-এ আপডেট করতে হবে, তারপর কোড পুশ করতে হবে
  (workflow ছাড়া)। না হলে পুরোনো workflow v1.0.0 ট্যাগে পাবলিশ করবে।

## সত্যি সীমা

- WiFi সরাসরি টগল নয়, সিস্টেম WiFi প্যানেল খোলে (Android 10+ সীমা)।
- ব্যাক/রিসেন্ট/স্ক্রিনশট শুধু Accessibility চালু থাকলে, লক শুধু
  Device Admin চালু থাকলে।
- v1.1-তে নেই: ম্যাক্রো/অটো-ট্যাপ, PhoneLink/Automation Lab ইন্টিগ্রেশন।
