package com.example.ghostgrid.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

@Immutable
data class AppLanguageOption(
    val tag: String, // "" means System Default, otherwise BCP-47 tag like "en", "hi", "gu", "zh-CN"
    val englishName: String,
    val nativeName: String,
    val codeLabel: String,
    val isRtl: Boolean = false
)

object WorldLanguageCatalog {

    val SYSTEM_DEFAULT = AppLanguageOption(
        tag = "",
        englishName = "System Default",
        nativeName = "Device Language",
        codeLabel = "AUTO",
        isRtl = false
    )

    private val featuredTags = listOf(
        "en", "hi", "gu", "mr", "ta", "te", "bn", "kn", "ml", "pa", "ur",
        "es", "fr", "de", "pt", "ru", "ar", "zh-CN", "zh-TW", "ja", "ko",
        "it", "tr", "id", "vi", "th", "nl", "pl", "uk", "fa", "he", "sw", "ms", "fil"
    )

    private val rtlLanguageCodes = setOf("ar", "arc", "dv", "fa", "ha", "he", "iw", "khw", "ks", "ku", "ps", "sd", "ug", "ur", "yi")

    val featuredLanguages: List<AppLanguageOption> by lazy {
        listOf(SYSTEM_DEFAULT) + featuredTags.map { buildOptionForTag(it) }
    }

    /**
     * Contains EVERY world language supported by ISO-639 and the Android/JVM ICU locale catalog (180+ languages),
     * with System Default and widely spoken global + Indian languages featured first, followed by all remaining
     * world languages sorted alphabetically by English name.
     */
    val allLanguages: List<AppLanguageOption> by lazy {
        val seenKeys = LinkedHashSet<String>()
        val result = ArrayList<AppLanguageOption>(220)

        result.add(SYSTEM_DEFAULT)
        seenKeys.add("")

        // Featured world & Indian languages first
        for (tag in featuredTags) {
            val opt = buildOptionForTag(tag)
            if (seenKeys.add(tag.lowercase(Locale.ROOT))) {
                result.add(opt)
            }
        }
        // Mark base "zh" as covered since zh-CN and zh-TW are already featured
        seenKeys.add("zh")

        // Collect every ISO-639 language + every distinct available ICU locale language
        val extraCodes = LinkedHashSet<String>()
        Locale.getISOLanguages().forEach { code ->
            val norm = normalizeIsoCode(code)
            if (norm.isNotBlank() && norm !in seenKeys) {
                extraCodes.add(norm)
            }
        }
        Locale.getAvailableLocales().forEach { loc ->
            val norm = normalizeIsoCode(loc.language)
            if (norm.isNotBlank() && norm !in seenKeys) {
                extraCodes.add(norm)
            }
        }

        val extras = extraCodes
            .map { buildOptionForTag(it) }
            .filter { it.englishName.isNotBlank() && it.englishName != it.tag }
            .sortedBy { it.englishName.lowercase(Locale.ENGLISH) }

        result.addAll(extras)
        result
    }

    fun resolveOption(tag: String): AppLanguageOption {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return SYSTEM_DEFAULT
        return allLanguages.firstOrNull { it.tag.equals(trimmed, ignoreCase = true) }
            ?: allLanguages.firstOrNull { it.tag.equals(trimmed.substringBefore('-'), ignoreCase = true) }
            ?: buildOptionForTag(trimmed)
    }

    fun resolveLocale(tag: String): Locale {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return Locale.getDefault()
        return Locale.forLanguageTag(trimmed)
    }

    fun isRtlTag(tag: String): Boolean {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) {
            return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
        }
        val base = trimmed.substringBefore('-').lowercase(Locale.ROOT)
        if (base in rtlLanguageCodes) return true
        val loc = Locale.forLanguageTag(trimmed)
        return TextUtils.getLayoutDirectionFromLocale(loc) == View.LAYOUT_DIRECTION_RTL
    }

    fun layoutDirectionFor(tag: String): LayoutDirection =
        if (isRtlTag(tag)) LayoutDirection.Rtl else LayoutDirection.Ltr

    private fun normalizeIsoCode(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
        "in" -> "id"
        "iw" -> "he"
        "ji" -> "yi"
        "tl" -> "fil"
        else -> raw.lowercase(Locale.ROOT)
    }

    private fun buildOptionForTag(tag: String): AppLanguageOption {
        val locale = Locale.forLanguageTag(tag)
        val english = when (tag) {
            "zh-CN" -> "Chinese (Simplified)"
            "zh-TW" -> "Chinese (Traditional)"
            "fil" -> "Filipino (Tagalog)"
            else -> locale.getDisplayName(Locale.ENGLISH)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        }
        val native = when (tag) {
            "zh-CN" -> "中文 (简体)"
            "zh-TW" -> "中文 (繁體)"
            "fil" -> "Filipino"
            else -> locale.getDisplayName(locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        }
        val base = tag.substringBefore('-').lowercase(Locale.ROOT)
        val rtl = base in rtlLanguageCodes ||
            TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        return AppLanguageOption(
            tag = tag,
            englishName = english.ifBlank { tag.uppercase(Locale.ENGLISH) },
            nativeName = native.ifBlank { english },
            codeLabel = tag.uppercase(Locale.ENGLISH),
            isRtl = rtl
        )
    }
}

object AppLanguageApplier {
    private val initialSystemLocale: Locale = Locale.getDefault()

    fun applyAppLanguage(context: Context, languageTag: String) {
        val trimmed = languageTag.trim()
        val targetLocale = if (trimmed.isEmpty()) initialSystemLocale else Locale.forLanguageTag(trimmed)
        runCatching { Locale.setDefault(targetLocale) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val lm = context.getSystemService(LocaleManager::class.java)
                if (lm != null) {
                    val locales = if (trimmed.isEmpty()) {
                        LocaleList.getEmptyLocaleList()
                    } else {
                        LocaleList.forLanguageTags(trimmed)
                    }
                    lm.applicationLocales = locales
                }
            }
        }
    }
}

@Immutable
data class GhostStrings(
    val searchPlaces: String = "Search places",
    val navExplore: String = "Explore",
    val navSky: String = "Sky",
    val navSaved: String = "Saved",
    val navMaps: String = "Maps",
    val navTools: String = "Tools",
    val navVault: String = "Vault",
    val catFood: String = "Food",
    val catHospitals: String = "Hospitals",
    val catPharmacy: String = "Pharmacy",
    val catFuel: String = "Fuel",
    val catTransit: String = "Transit",
    val catSaved: String = "Saved",
    val weatherAndMoon: String = "Weather & Moon",
    val hourlyForecast: String = "HOURLY FORECAST",
    val dailyForecast: String = "DAILY FORECAST",
    val offlineLabel: String = "Offline",
    val systemAndSettings: String = "System & Settings",
    val systemLanguageTitle: String = "System Language",
    val systemLanguageSubtitle: String = "Choose any language worldwide. Updates interface, dates, voice guidance & RTL layout instantly.",
    val browseAllLanguages: String = "Browse All World Languages",
    val searchLanguagesHint: String = "Search language (English, native, or code)",
    val localProfile: String = "Local Profile",
    val strictOfflineMode: String = "Strict Offline Mode",
    val offlineVoiceGuidance: String = "Offline Voice Guidance (TTS)",
    val mapAndVehicle: String = "Map & Vehicle",
    val saveAreaOffline: String = "Save Current Area Offline (Dark, Map, Satellite, Terrain)",
    val saveLabel: String = "Save",
    val closeLabel: String = "Close",
    val endLabel: String = "End",
    val blockedLabel: String = "Blocked"
) {
    companion object {
        val ENGLISH = GhostStrings()

        fun forLanguageTag(tag: String): GhostStrings {
            val effectiveTag = tag.trim().ifEmpty { Locale.getDefault().toLanguageTag() }
            val lower = effectiveTag.lowercase(Locale.ROOT)
            val base = lower.substringBefore('-')
            return when {
                lower == "zh-tw" || lower == "zh-hk" || lower == "zh-hant" -> CHINESE_TRADITIONAL
                base == "zh" -> CHINESE_SIMPLIFIED
                base == "hi" -> HINDI
                base == "gu" -> GUJARATI
                base == "mr" -> MARATHI
                base == "ta" -> TAMIL
                base == "te" -> TELUGU
                base == "bn" -> BENGALI
                base == "kn" -> KANNADA
                base == "ml" -> MALAYALAM
                base == "pa" -> PUNJABI
                base == "ur" -> URDU
                base == "es" -> SPANISH
                base == "fr" -> FRENCH
                base == "de" -> GERMAN
                base == "pt" -> PORTUGUESE
                base == "ru" -> RUSSIAN
                base == "ar" -> ARABIC
                base == "ja" -> JAPANESE
                base == "ko" -> KOREAN
                base == "it" -> ITALIAN
                base == "tr" -> TURKISH
                base == "id" -> INDONESIAN
                base == "vi" -> VIETNAMESE
                base == "th" -> THAI
                base == "nl" -> DUTCH
                base == "pl" -> POLISH
                base == "uk" -> UKRAINIAN
                base == "fa" -> PERSIAN
                base == "he" || base == "iw" -> HEBREW
                base == "sw" -> SWAHILI
                base == "ms" -> MALAY
                base == "fil" || base == "tl" -> FILIPINO
                else -> ENGLISH
            }
        }

        private val HINDI = GhostStrings(
            searchPlaces = "स्थान खोजें",
            navExplore = "खोजें",
            navSky = "आकाश",
            navSaved = "सहेजे",
            navMaps = "नक्शे",
            navTools = "उपकरण",
            navVault = "वॉल्ट",
            catFood = "भोजन",
            catHospitals = "अस्पताल",
            catPharmacy = "फार्मेसी",
            catFuel = "ईंधन",
            catTransit = "परिवहन",
            catSaved = "सहेजे",
            weatherAndMoon = "मौसम और चंद्रमा",
            hourlyForecast = "प्रति घंटा पूर्वानुमान",
            dailyForecast = "दैनिक पूर्वानुमान",
            offlineLabel = "ऑफ़लाइन",
            systemAndSettings = "सिस्टम और सेटिंग्स",
            systemLanguageTitle = "सिस्टम भाषा",
            systemLanguageSubtitle = "दुनिया की कोई भी भाषा चुनें। इंटरफ़ेस, दिनांक और वॉयस तुरंत बदलें।",
            browseAllLanguages = "सभी विश्व भाषाएँ देखें",
            searchLanguagesHint = "भाषा खोजें (नाम या कोड)",
            localProfile = "स्थानीय प्रोफ़ाइल",
            strictOfflineMode = "पूर्ण ऑफ़लाइन मोड",
            offlineVoiceGuidance = "ऑफ़लाइन वॉयस मार्गदर्शन",
            mapAndVehicle = "नक्शा और वाहन",
            saveAreaOffline = "वर्तमान क्षेत्र ऑफ़लाइन सहेजें",
            saveLabel = "सहेजें",
            closeLabel = "बंद करें",
            endLabel = "समाप्त",
            blockedLabel = "अवरुद्ध"
        )

        private val GUJARATI = GhostStrings(
            searchPlaces = "સ્થળો શોધો",
            navExplore = "શોધો",
            navSky = "આકાશ",
            navSaved = "સાચવેલ",
            navMaps = "નકશા",
            navTools = "સાધનો",
            navVault = "વોલ્ટ",
            catFood = "ખોરાક",
            catHospitals = "હોસ્પિટલ",
            catPharmacy = "ફાર્મસી",
            catFuel = "ઇંધણ",
            catTransit = "પરિવહન",
            catSaved = "સાચવેલ",
            weatherAndMoon = "હવામાન અને ચંદ્ર",
            hourlyForecast = "કલાકદીઠ આગાહી",
            dailyForecast = "દૈનિક આગાહી",
            offlineLabel = "ઓફલાઇન",
            systemAndSettings = "સિસ્ટમ અને સેટિંગ્સ",
            systemLanguageTitle = "સિસ્ટમ ભાષા",
            systemLanguageSubtitle = "વિશ્વની કોઈપણ ભાષા પસંદ કરો. ઇન્ટરફેસ અને તારીખ તરત બદલાશે.",
            browseAllLanguages = "બધી વિશ્વ ભાષાઓ જુઓ",
            searchLanguagesHint = "ભાષા શોધો (નામ અથવા કોડ)",
            localProfile = "સ્થાનિક પ્રોફાઇલ",
            strictOfflineMode = "સંપૂર્ણ ઓફલાઇન મોડ",
            offlineVoiceGuidance = "ઓફલાઇન વૉઇસ માર્ગદર્શન",
            mapAndVehicle = "નકશો અને વાહન",
            saveAreaOffline = "વર્તમાન વિસ્તાર ઓફલાઇન સાચવો",
            saveLabel = "સાચવો",
            closeLabel = "બંધ કરો",
            endLabel = "સમાપ્ત",
            blockedLabel = "અવરોધિત"
        )

        private val MARATHI = GhostStrings(
            searchPlaces = "ठिकाणे शोधा",
            navExplore = "शोधा",
            navSky = "आकाश",
            navSaved = "जतन",
            navMaps = "नकाशे",
            navTools = "साधने",
            navVault = "व्हॉल्ट",
            catFood = "अन्न",
            catHospitals = "रुग्णालय",
            catPharmacy = "फार्मसी",
            catFuel = "इंधन",
            catTransit = "वाहतूक",
            catSaved = "जतन",
            weatherAndMoon = "हवामान आणि चंद्र",
            hourlyForecast = "तासानुसार अंदाज",
            dailyForecast = "दैनिक अंदाज",
            offlineLabel = "ऑफलाइन",
            systemAndSettings = "सिस्टम आणि सेटिंग्ज",
            systemLanguageTitle = "सिस्टम भाषा",
            systemLanguageSubtitle = "जगातील कोणतीही भाषा निवडा.",
            browseAllLanguages = "सर्व जागतिक भाषा पहा",
            searchLanguagesHint = "भाषा शोधा",
            localProfile = "स्थानिक प्रोफाइल",
            strictOfflineMode = "संपूर्ण ऑफलाइन मोड",
            offlineVoiceGuidance = "ऑफलाइन व्हॉइस मार्गदर्शन",
            mapAndVehicle = "नकाशा आणि वाहन",
            saveAreaOffline = "सध्याचा परिसर ऑफलाइन जतन करा",
            saveLabel = "जतन करा",
            closeLabel = "बंद",
            endLabel = "थांबा",
            blockedLabel = "बंद"
        )

        private val TAMIL = GhostStrings(
            searchPlaces = "இடங்களைத் தேடு",
            navExplore = "ஆராய்",
            navSky = "வானம்",
            navSaved = "சேமிப்பு",
            navMaps = "வரைபடம்",
            navTools = "கருவிகள்",
            navVault = "பெட்டகம்",
            catFood = "உணவு",
            catHospitals = "மருத்துவம்",
            catPharmacy = "மருந்தகம்",
            catFuel = "எரிபொருள்",
            catTransit = "போக்குவரத்து",
            catSaved = "சேமிப்பு",
            weatherAndMoon = "வானிலை & நிலா",
            hourlyForecast = "மணிநேர முன்னறிவிப்பு",
            dailyForecast = "தினசரி முன்னறிவிப்பு",
            offlineLabel = "ஆஃப்லைன்",
            systemAndSettings = "அமைப்பு & மொழி",
            systemLanguageTitle = "அமைப்பு மொழி",
            systemLanguageSubtitle = "உலகின் எந்த மொழியையும் தேர்ந்தெடுக்கவும்.",
            browseAllLanguages = "அனைத்து உலக மொழிகளும்",
            searchLanguagesHint = "மொழியைத் தேடு",
            localProfile = "உள்ளூர் சுயவிவரம்",
            strictOfflineMode = "முழு ஆஃப்லைன் நிலை",
            offlineVoiceGuidance = "ஆஃப்லைன் குரல் வழிகாட்டி",
            mapAndVehicle = "வரைபடம் & வாகனம்",
            saveAreaOffline = "இந்தப் பகுதியை ஆஃப்லைனில் சேமி",
            saveLabel = "சேமி",
            closeLabel = "மூடு",
            endLabel = "முடி",
            blockedLabel = "தடை"
        )

        private val TELUGU = GhostStrings(
            searchPlaces = "స్థలాలను వెతకండి",
            navExplore = "అన్వేషించు",
            navSky = "ఆకాశం",
            navSaved = "సేవ్",
            navMaps = "మ్యాప్స్",
            navTools = "సాధనాలు",
            navVault = "వాల్ట్",
            catFood = "ఆహారం",
            catHospitals = "ఆసుపత్రి",
            catPharmacy = "ఫార్మసీ",
            catFuel = "ఇంధనం",
            catTransit = "రవాణా",
            catSaved = "సేవ్",
            weatherAndMoon = "వాతావరణం & చంద్రుడు",
            hourlyForecast = "గంటల వారీ అంచనా",
            dailyForecast = "రోజువారీ అంచనా",
            offlineLabel = "ఆఫ్‌లైన్",
            systemAndSettings = "సిస్టమ్ & సెట్టింగ్స్",
            systemLanguageTitle = "సిస్టమ్ భాష",
            systemLanguageSubtitle = "ప్రపంచంలోని ఏ భాషనైనా ఎంచుకోండి.",
            browseAllLanguages = "అన్ని ప్రపంచ భాషలు",
            searchLanguagesHint = "భాషను వెతకండి",
            localProfile = "స్థానిక ప్రొఫైల్",
            strictOfflineMode = "పూర్తి ఆఫ్‌లైన్ మోడ్",
            offlineVoiceGuidance = "ఆఫ్‌లైన్ వాయిస్ గైడ్",
            mapAndVehicle = "మ్యాప్ & వాహనం",
            saveAreaOffline = "ఈ ప్రాంతాన్ని ఆఫ్‌లైన్‌లో సేవ్ చేయండి",
            saveLabel = "సేవ్",
            closeLabel = "మూసివేయి",
            endLabel = "ముగించు",
            blockedLabel = "అడ్డంకి"
        )

        private val BENGALI = GhostStrings(
            searchPlaces = "জায়গা খুঁজুন",
            navExplore = "অন্বেষণ",
            navSky = "আকাশ",
            navSaved = "সংরক্ষিত",
            navMaps = "মানচিত্র",
            navTools = "টুলস",
            navVault = "ভল্ট",
            catFood = "খাবার",
            catHospitals = "হাসপাতাল",
            catPharmacy = "ফার্মেসি",
            catFuel = "জ্বালানি",
            catTransit = "পরিবহন",
            catSaved = "সংরক্ষিত",
            weatherAndMoon = "আবহাওয়া ও চাঁদ",
            hourlyForecast = "ঘণ্টাভিত্তিক পূর্বাভাস",
            dailyForecast = "দৈনিক পূর্বাভাস",
            offlineLabel = "অফলাইন",
            systemAndSettings = "সিস্টেম ও সেটিংস",
            systemLanguageTitle = "সিস্টেম ভাষা",
            systemLanguageSubtitle = "বিশ্বের যেকোনো ভাষা নির্বাচন করুন।",
            browseAllLanguages = "সব বিশ্ব ভাষা দেখুন",
            searchLanguagesHint = "ভাষা খুঁজুন",
            localProfile = "স্থানীয় প্রোফাইল",
            strictOfflineMode = "সম্পূর্ণ অফলাইন মোড",
            offlineVoiceGuidance = "অফলাইন ভয়েস নির্দেশনা",
            mapAndVehicle = "মানচিত্র ও যানবাহন",
            saveAreaOffline = "বর্তমান এলাকা অফলাইনে সংরক্ষণ করুন",
            saveLabel = "সংরক্ষণ",
            closeLabel = "বন্ধ",
            endLabel = "শেষ",
            blockedLabel = "অবরুদ্ধ"
        )

        private val KANNADA = GhostStrings(
            searchPlaces = "ಸ್ಥಳಗಳನ್ನು ಹುಡುಕಿ",
            navExplore = "ಅನ್ವೇಷಿಸಿ",
            navSky = "ಆಕಾಶ",
            navSaved = "ಉಳಿಸಿದ",
            navMaps = "ನಕ್ಷೆಗಳು",
            navTools = "ಸಾಧನಗಳು",
            navVault = "ವಾಲ್ಟ್",
            weatherAndMoon = "ಹವಾಮಾನ & ಚಂದ್ರ",
            offlineLabel = "ಆಫ್‌ಲೈನ್",
            systemAndSettings = "ಸಿಸ್ಟಮ್ ಮತ್ತು ಸೆಟ್ಟಿಂಗ್ಸ್",
            systemLanguageTitle = "ಸಿಸ್ಟಮ್ ಭಾಷೆ",
            browseAllLanguages = "ಎಲ್ಲಾ ವಿಶ್ವ ಭಾಷೆಗಳು",
            searchLanguagesHint = "ಭಾಷೆ ಹುಡುಕಿ"
        )

        private val MALAYALAM = GhostStrings(
            searchPlaces = "സ്ഥലങ്ങൾ തിരയുക",
            navExplore = "പര്യവേക്ഷണം",
            navSky = "ആകാശം",
            navSaved = "സേവ്",
            navMaps = "മാപ്പുകൾ",
            navTools = "ടൂളുകൾ",
            navVault = "വോൾട്ട്",
            weatherAndMoon = "കാലാവസ്ഥ & ചന്ദ്രൻ",
            offlineLabel = "ഓഫ്‌ലൈൻ",
            systemAndSettings = "സിസ്റ്റം & ക്രമീകരണങ്ങൾ",
            systemLanguageTitle = "സിസ്റ്റം ഭാഷ",
            browseAllLanguages = "എല്ലാ ലോക ഭാഷകളും",
            searchLanguagesHint = "ഭാഷ തിരയുക"
        )

        private val PUNJABI = GhostStrings(
            searchPlaces = "ਥਾਵਾਂ ਖੋਜੋ",
            navExplore = "ਖੋਜੋ",
            navSky = "ਅਸਮਾਨ",
            navSaved = "ਸੰਭਾਲੇ",
            navMaps = "ਨਕਸ਼ੇ",
            navTools = "ਔਜ਼ਾਰ",
            navVault = "ਵਾਲਟ",
            weatherAndMoon = "ਮੌਸਮ ਅਤੇ ਚੰਦਰਮਾ",
            offlineLabel = "ਆਫ਼ਲਾਈਨ",
            systemAndSettings = "ਸਿਸਟਮ ਅਤੇ ਸੈਟਿੰਗਾਂ",
            systemLanguageTitle = "ਸਿਸਟਮ ਭਾਸ਼ਾ",
            browseAllLanguages = "ਸਾਰੀਆਂ ਵਿਸ਼ਵ ਭਾਸ਼ਾਵਾਂ",
            searchLanguagesHint = "ਭਾਸ਼ਾ ਖੋਜੋ"
        )

        private val URDU = GhostStrings(
            searchPlaces = "مقامات تلاش کریں",
            navExplore = "تلاش",
            navSky = "آسمان",
            navSaved = "محفوظ",
            navMaps = "نقشے",
            navTools = "آلات",
            navVault = "والٹ",
            catFood = "کھانا",
            catHospitals = "ہسپتال",
            catPharmacy = "فارمیسی",
            catFuel = "ایندھن",
            catTransit = "ٹرانزٹ",
            catSaved = "محفوظ",
            weatherAndMoon = "موسم اور چاند",
            hourlyForecast = "گھنٹہ وار پیشگوئی",
            dailyForecast = "روزانہ پیشگوئی",
            offlineLabel = "آف لائن",
            systemAndSettings = "سسٹم اور ترتیبات",
            systemLanguageTitle = "سسٹم کی زبان",
            systemLanguageSubtitle = "دنیا کی کوئی بھی زبان منتخب کریں۔",
            browseAllLanguages = "تمام عالمی زبانیں دیکھیں",
            searchLanguagesHint = "زبان تلاش کریں",
            localProfile = "مقامی پروفائل",
            strictOfflineMode = "مکمل آف لائن موڈ",
            offlineVoiceGuidance = "آف لائن آواز رہنمائی",
            mapAndVehicle = "نقشہ اور گاڑی",
            saveLabel = "محفوظ کریں",
            closeLabel = "بند",
            endLabel = "ختم",
            blockedLabel = "بند راستہ"
        )

        private val SPANISH = GhostStrings(
            searchPlaces = "Buscar lugares",
            navExplore = "Explorar",
            navSky = "Cielo",
            navSaved = "Guardado",
            navMaps = "Mapas",
            navTools = "Herram.",
            navVault = "Bóveda",
            catFood = "Comida",
            catHospitals = "Hospitales",
            catPharmacy = "Farmacia",
            catFuel = "Combustible",
            catTransit = "Tránsito",
            catSaved = "Guardado",
            weatherAndMoon = "Clima y Luna",
            hourlyForecast = "PRONÓSTICO POR HORA",
            dailyForecast = "PRONÓSTICO DIARIO",
            offlineLabel = "Sin conexión",
            systemAndSettings = "Sistema y Ajustes",
            systemLanguageTitle = "Idioma del Sistema",
            systemLanguageSubtitle = "Elige cualquier idioma del mundo. Actualiza interfaz, fechas y voz al instante.",
            browseAllLanguages = "Ver Todos los Idiomas del Mundo",
            searchLanguagesHint = "Buscar idioma (nombre o código)",
            localProfile = "Perfil Local",
            strictOfflineMode = "Modo Sin Conexión Estricto",
            offlineVoiceGuidance = "Guía de Voz Sin Conexión",
            mapAndVehicle = "Mapa y Vehículo",
            saveAreaOffline = "Guardar Área Actual Sin Conexión",
            saveLabel = "Guardar",
            closeLabel = "Cerrar",
            endLabel = "Fin",
            blockedLabel = "Bloqueado"
        )

        private val FRENCH = GhostStrings(
            searchPlaces = "Rechercher un lieu",
            navExplore = "Explorer",
            navSky = "Ciel",
            navSaved = "Favoris",
            navMaps = "Cartes",
            navTools = "Outils",
            navVault = "Coffre",
            catFood = "Restos",
            catHospitals = "Hôpitaux",
            catPharmacy = "Pharmacie",
            catFuel = "Carburant",
            catTransit = "Transit",
            catSaved = "Favoris",
            weatherAndMoon = "Météo & Lune",
            hourlyForecast = "PRÉVISIONS HORAIRES",
            dailyForecast = "PRÉVISIONS QUOTIDIENNES",
            offlineLabel = "Hors ligne",
            systemAndSettings = "Système & Paramètres",
            systemLanguageTitle = "Langue du Système",
            systemLanguageSubtitle = "Choisissez n'importe quelle langue du monde.",
            browseAllLanguages = "Parcourir Toutes les Langues",
            searchLanguagesHint = "Rechercher une langue",
            localProfile = "Profil Local",
            strictOfflineMode = "Mode Hors Ligne Strict",
            offlineVoiceGuidance = "Guidage Vocal Hors Ligne",
            mapAndVehicle = "Carte & Véhicule",
            saveAreaOffline = "Enregistrer la Zone Hors Ligne",
            saveLabel = "Enregistrer",
            closeLabel = "Fermer",
            endLabel = "Arrêter",
            blockedLabel = "Bloqué"
        )

        private val GERMAN = GhostStrings(
            searchPlaces = "Orte suchen",
            navExplore = "Karte",
            navSky = "Himmel",
            navSaved = "Gespeichert",
            navMaps = "Karten",
            navTools = "Tools",
            navVault = "Tresor",
            catFood = "Essen",
            catHospitals = "Kliniken",
            catPharmacy = "Apotheke",
            catFuel = "Tanken",
            catTransit = "ÖPNV",
            catSaved = "Gespeichert",
            weatherAndMoon = "Wetter & Mond",
            hourlyForecast = "STÜNDLICHE VORHERSAGE",
            dailyForecast = "TAGESVORHERSAGE",
            offlineLabel = "Offline",
            systemAndSettings = "System & Einstellungen",
            systemLanguageTitle = "Systemsprache",
            systemLanguageSubtitle = "Wähle eine beliebige Weltsprache.",
            browseAllLanguages = "Alle Weltsprachen anzeigen",
            searchLanguagesHint = "Sprache suchen",
            localProfile = "Lokales Profil",
            strictOfflineMode = "Strikter Offline-Modus",
            offlineVoiceGuidance = "Offline-Sprachführung",
            mapAndVehicle = "Karte & Fahrzeug",
            saveAreaOffline = "Aktuellen Bereich Offline speichern",
            saveLabel = "Speichern",
            closeLabel = "Schließen",
            endLabel = "Ende",
            blockedLabel = "Gesperrt"
        )

        private val PORTUGUESE = GhostStrings(
            searchPlaces = "Buscar locais",
            navExplore = "Explorar",
            navSky = "Céu",
            navSaved = "Salvos",
            navMaps = "Mapas",
            navTools = "Ferram.",
            navVault = "Cofre",
            catFood = "Comida",
            catHospitals = "Hospitais",
            catPharmacy = "Farmácia",
            catFuel = "Combustível",
            catTransit = "Transporte",
            catSaved = "Salvos",
            weatherAndMoon = "Clima e Lua",
            hourlyForecast = "PREVISÃO POR HORA",
            dailyForecast = "PREVISÃO DIÁRIA",
            offlineLabel = "Offline",
            systemAndSettings = "Sistema e Ajustes",
            systemLanguageTitle = "Idioma do Sistema",
            systemLanguageSubtitle = "Escolha qualquer idioma do mundo.",
            browseAllLanguages = "Ver Todos os Idiomas",
            searchLanguagesHint = "Pesquisar idioma",
            localProfile = "Perfil Local",
            strictOfflineMode = "Modo Offline Estrito",
            offlineVoiceGuidance = "Voz Offline (TTS)",
            mapAndVehicle = "Mapa e Veículo",
            saveAreaOffline = "Salvar Área Atual Offline",
            saveLabel = "Salvar",
            closeLabel = "Fechar",
            endLabel = "Fim",
            blockedLabel = "Bloqueado"
        )

        private val RUSSIAN = GhostStrings(
            searchPlaces = "Поиск мест",
            navExplore = "Обзор",
            navSky = "Небо",
            navSaved = "Места",
            navMaps = "Карты",
            navTools = "Инстр.",
            navVault = "Сейф",
            catFood = "Еда",
            catHospitals = "Больницы",
            catPharmacy = "Аптеки",
            catFuel = "АЗС",
            catTransit = "Транспорт",
            catSaved = "Места",
            weatherAndMoon = "Погода и Луна",
            hourlyForecast = "ПОЧАСОВОЙ ПРОГНОЗ",
            dailyForecast = "ПРОГНОЗ ПО ДНЯМ",
            offlineLabel = "Офлайн",
            systemAndSettings = "Система и Настройки",
            systemLanguageTitle = "Язык системы",
            systemLanguageSubtitle = "Выберите любой язык мира.",
            browseAllLanguages = "Все языки мира",
            searchLanguagesHint = "Поиск языка",
            localProfile = "Локальный профиль",
            strictOfflineMode = "Строгий офлайн-режим",
            offlineVoiceGuidance = "Офлайн-озвучка (TTS)",
            mapAndVehicle = "Карта и транспорт",
            saveAreaOffline = "Сохранить область офлайн",
            saveLabel = "Сохранить",
            closeLabel = "Закрыть",
            endLabel = "Стоп",
            blockedLabel = "Перекрыто"
        )

        private val ARABIC = GhostStrings(
            searchPlaces = "ابحث عن الأماكن",
            navExplore = "استكشاف",
            navSky = "السماء",
            navSaved = "المحفوظات",
            navMaps = "الخرائط",
            navTools = "الأدوات",
            navVault = "الخزنة",
            catFood = "طعام",
            catHospitals = "مستشفيات",
            catPharmacy = "صيدلية",
            catFuel = "وقود",
            catTransit = "مواصلات",
            catSaved = "المحفوظات",
            weatherAndMoon = "الطقس والقمر",
            hourlyForecast = "التوقعات بالساعة",
            dailyForecast = "التوقعات اليومية",
            offlineLabel = "غير متصل",
            systemAndSettings = "النظام والإعدادات",
            systemLanguageTitle = "لغة النظام",
            systemLanguageSubtitle = "اختر أي لغة في العالم مع دعم فوري لاتجاه النص.",
            browseAllLanguages = "تصفح جميع لغات العالم",
            searchLanguagesHint = "ابحث عن لغة",
            localProfile = "الملف المحلي",
            strictOfflineMode = "وضع عدم الاتصال الصارم",
            offlineVoiceGuidance = "التوجيه الصوتي دون اتصال",
            mapAndVehicle = "الخريطة والمركبة",
            saveAreaOffline = "حفظ المنطقة الحالية دون اتصال",
            saveLabel = "حفظ",
            closeLabel = "إغلاق",
            endLabel = "إنهاء",
            blockedLabel = "مغلق"
        )

        private val CHINESE_SIMPLIFIED = GhostStrings(
            searchPlaces = "搜索地点",
            navExplore = "探索",
            navSky = "星空",
            navSaved = "收藏",
            navMaps = "地图",
            navTools = "工具",
            navVault = "保险库",
            catFood = "餐饮",
            catHospitals = "医院",
            catPharmacy = "药店",
            catFuel = "加油站",
            catTransit = "交通",
            catSaved = "收藏",
            weatherAndMoon = "天气与月相",
            hourlyForecast = "逐小时预报",
            dailyForecast = "每日预报",
            offlineLabel = "离线",
            systemAndSettings = "系统与设置",
            systemLanguageTitle = "系统语言",
            systemLanguageSubtitle = "选择全球任意语言，立即生效。",
            browseAllLanguages = "浏览全部世界语言",
            searchLanguagesHint = "搜索语言（名称或代码）",
            localProfile = "本地资料",
            strictOfflineMode = "严格离线模式",
            offlineVoiceGuidance = "离线语音导航",
            mapAndVehicle = "地图与车辆",
            saveAreaOffline = "离线保存当前区域",
            saveLabel = "保存",
            closeLabel = "关闭",
            endLabel = "结束",
            blockedLabel = "道路受阻"
        )

        private val CHINESE_TRADITIONAL = GhostStrings(
            searchPlaces = "搜尋地點",
            navExplore = "探索",
            navSky = "星空",
            navSaved = "收藏",
            navMaps = "地圖",
            navTools = "工具",
            navVault = "保險庫",
            catFood = "餐飲",
            catHospitals = "醫院",
            catPharmacy = "藥局",
            catFuel = "加油站",
            catTransit = "交通",
            catSaved = "收藏",
            weatherAndMoon = "天氣與月相",
            hourlyForecast = "逐小時預報",
            dailyForecast = "每日預報",
            offlineLabel = "離線",
            systemAndSettings = "系統與設定",
            systemLanguageTitle = "系統語言",
            systemLanguageSubtitle = "選擇全球任意語言，立即生效。",
            browseAllLanguages = "瀏覽全部世界語言",
            searchLanguagesHint = "搜尋語言",
            localProfile = "本地資料",
            strictOfflineMode = "嚴格離線模式",
            offlineVoiceGuidance = "離線語音導航",
            mapAndVehicle = "地圖與車輛",
            saveAreaOffline = "離線儲存目前區域",
            saveLabel = "儲存",
            closeLabel = "關閉",
            endLabel = "結束",
            blockedLabel = "受阻"
        )

        private val JAPANESE = GhostStrings(
            searchPlaces = "場所を検索",
            navExplore = "探索",
            navSky = "星空",
            navSaved = "保存",
            navMaps = "地図",
            navTools = "ツール",
            navVault = "保管庫",
            catFood = "飲食",
            catHospitals = "病院",
            catPharmacy = "薬局",
            catFuel = "給油",
            catTransit = "交通",
            catSaved = "保存",
            weatherAndMoon = "天気と月齢",
            hourlyForecast = "1時間ごとの予報",
            dailyForecast = "日間予報",
            offlineLabel = "オフライン",
            systemAndSettings = "システムと設定",
            systemLanguageTitle = "システム言語",
            systemLanguageSubtitle = "世界中のすべての言語から選択できます。",
            browseAllLanguages = "すべての世界の言語を表示",
            searchLanguagesHint = "言語を検索",
            localProfile = "ローカルプロフィール",
            strictOfflineMode = "完全オフラインモード",
            offlineVoiceGuidance = "オフライン音声案内",
            mapAndVehicle = "地図と車両",
            saveAreaOffline = "現在のエリアをオフライン保存",
            saveLabel = "保存",
            closeLabel = "閉じる",
            endLabel = "終了",
            blockedLabel = "通行止め"
        )

        private val KOREAN = GhostStrings(
            searchPlaces = "장소 검색",
            navExplore = "탐색",
            navSky = "별자리",
            navSaved = "저장",
            navMaps = "지도",
            navTools = "도구",
            navVault = "보관함",
            catFood = "음식",
            catHospitals = "병원",
            catPharmacy = "약국",
            catFuel = "주유소",
            catTransit = "교통",
            catSaved = "저장",
            weatherAndMoon = "날씨 및 달 위상",
            hourlyForecast = "시간별 예보",
            dailyForecast = "일별 예보",
            offlineLabel = "오프라인",
            systemAndSettings = "시스템 및 설정",
            systemLanguageTitle = "시스템 언어",
            systemLanguageSubtitle = "전 세계 모든 언어를 선택할 수 있습니다.",
            browseAllLanguages = "모든 세계 언어 보기",
            searchLanguagesHint = "언어 검색",
            localProfile = "로컬 프로필",
            strictOfflineMode = "엄격한 오프라인 모드",
            offlineVoiceGuidance = "오프라인 음성 안내",
            mapAndVehicle = "지도 및 차량",
            saveAreaOffline = "현재 지역 오프라인 저장",
            saveLabel = "저장",
            closeLabel = "닫기",
            endLabel = "종료",
            blockedLabel = "차단됨"
        )

        private val ITALIAN = GhostStrings(
            searchPlaces = "Cerca luoghi",
            navExplore = "Esplora",
            navSky = "Cielo",
            navSaved = "Salvati",
            navMaps = "Mappe",
            navTools = "Strumenti",
            navVault = "Vault",
            weatherAndMoon = "Meteo e Luna",
            hourlyForecast = "PREVISIONI ORARIE",
            dailyForecast = "PREVISIONI GIORNALIERE",
            offlineLabel = "Offline",
            systemAndSettings = "Sistema e Impostazioni",
            systemLanguageTitle = "Lingua di Sistema",
            browseAllLanguages = "Sfoglia Tutte le Lingue",
            searchLanguagesHint = "Cerca lingua"
        )

        private val TURKISH = GhostStrings(
            searchPlaces = "Yer ara",
            navExplore = "Keşfet",
            navSky = "Gökyüzü",
            navSaved = "Kayıtlı",
            navMaps = "Haritalar",
            navTools = "Araçlar",
            navVault = "Kasa",
            weatherAndMoon = "Hava & Ay",
            hourlyForecast = "SAATLİK TAHMİN",
            dailyForecast = "GÜNLÜK TAHMİN",
            offlineLabel = "Çevrimdışı",
            systemAndSettings = "Sistem ve Ayarlar",
            systemLanguageTitle = "Sistem Dili",
            browseAllLanguages = "Tüm Dünya Dillerini Gör",
            searchLanguagesHint = "Dil ara"
        )

        private val INDONESIAN = GhostStrings(
            searchPlaces = "Cari tempat",
            navExplore = "Jelajah",
            navSky = "Langit",
            navSaved = "Tersimpan",
            navMaps = "Peta",
            navTools = "Alat",
            navVault = "Brankas",
            weatherAndMoon = "Cuaca & Bulan",
            hourlyForecast = "PRAKIRAAN PER JAM",
            dailyForecast = "PRAKIRAAN HARIAN",
            offlineLabel = "Offline",
            systemAndSettings = "Sistem & Pengaturan",
            systemLanguageTitle = "Bahasa Sistem",
            browseAllLanguages = "Lihat Semua Bahasa Dunia",
            searchLanguagesHint = "Cari bahasa"
        )

        private val VIETNAMESE = GhostStrings(
            searchPlaces = "Tìm địa điểm",
            navExplore = "Khám phá",
            navSky = "Bầu trời",
            navSaved = "Đã lưu",
            navMaps = "Bản đồ",
            navTools = "Công cụ",
            navVault = "Kho",
            weatherAndMoon = "Thời tiết & Mặt trăng",
            offlineLabel = "Ngoại tuyến",
            systemAndSettings = "Hệ thống & Cài đặt",
            systemLanguageTitle = "Ngôn ngữ hệ thống",
            browseAllLanguages = "Xem tất cả ngôn ngữ",
            searchLanguagesHint = "Tìm ngôn ngữ"
        )

        private val THAI = GhostStrings(
            searchPlaces = "ค้นหาสถานที่",
            navExplore = "สำรวจ",
            navSky = "ท้องฟ้า",
            navSaved = "บันทึก",
            navMaps = "แผนที่",
            navTools = "เครื่องมือ",
            navVault = "ห้องนิรภัย",
            weatherAndMoon = "สภาพอากาศและดวงจันทร์",
            offlineLabel = "ออฟไลน์",
            systemAndSettings = "ระบบและการตั้งค่า",
            systemLanguageTitle = "ภาษาระบบ",
            browseAllLanguages = "ดูภาษาทั้งหมดทั่วโลก",
            searchLanguagesHint = "ค้นหาภาษา"
        )

        private val DUTCH = GhostStrings(
            searchPlaces = "Plaatsen zoeken",
            navExplore = "Verken",
            navSky = "Hemel",
            navSaved = "Opgeslagen",
            navMaps = "Kaarten",
            navTools = "Tools",
            navVault = "Kluis",
            weatherAndMoon = "Weer & Maan",
            systemAndSettings = "Systeem & Instellingen",
            systemLanguageTitle = "Systeemtaal",
            browseAllLanguages = "Alle wereldtalen bekijken",
            searchLanguagesHint = "Taal zoeken"
        )

        private val POLISH = GhostStrings(
            searchPlaces = "Szukaj miejsc",
            navExplore = "Odkrywaj",
            navSky = "Niebo",
            navSaved = "Zapisane",
            navMaps = "Mapy",
            navTools = "Narzędzia",
            navVault = "Sejf",
            weatherAndMoon = "Pogoda i Księżyc",
            systemAndSettings = "System i Ustawienia",
            systemLanguageTitle = "Język systemu",
            browseAllLanguages = "Wszystkie języki świata",
            searchLanguagesHint = "Szukaj języka"
        )

        private val UKRAINIAN = GhostStrings(
            searchPlaces = "Пошук місць",
            navExplore = "Огляд",
            navSky = "Небо",
            navSaved = "Збережені",
            navMaps = "Карти",
            navTools = "Інструм.",
            navVault = "Сейф",
            weatherAndMoon = "Погода та Місяць",
            systemAndSettings = "Система та Налаштування",
            systemLanguageTitle = "Мова системи",
            browseAllLanguages = "Усі мови світу",
            searchLanguagesHint = "Пошук мови"
        )

        private val PERSIAN = GhostStrings(
            searchPlaces = "جستجوی مکان‌ها",
            navExplore = "کاوش",
            navSky = "آسمان",
            navSaved = "ذخیره",
            navMaps = "نقشه‌ها",
            navTools = "ابزارها",
            navVault = "گاوصندوق",
            weatherAndMoon = "آب‌وهوا و ماه",
            offlineLabel = "آفلاین",
            systemAndSettings = "سیستم و تنظیمات",
            systemLanguageTitle = "زبان سیستم",
            browseAllLanguages = "مشاهده تمام زبان‌های جهان",
            searchLanguagesHint = "جستجوی زبان"
        )

        private val HEBREW = GhostStrings(
            searchPlaces = "חפש מקומות",
            navExplore = "גלה",
            navSky = "שמיים",
            navSaved = "שמורים",
            navMaps = "מפות",
            navTools = "כלים",
            navVault = "כספת",
            weatherAndMoon = "מזג אוויר וירח",
            offlineLabel = "לא מקוון",
            systemAndSettings = "מערכת והגדרות",
            systemLanguageTitle = "שפת מערכת",
            browseAllLanguages = "עיין בכל שפות העולם",
            searchLanguagesHint = "חפש שפה"
        )

        private val SWAHILI = GhostStrings(
            searchPlaces = "Tafuta maeneo",
            navExplore = "Chunguza",
            navSky = "Anga",
            navSaved = "Zilizohifadhiwa",
            navMaps = "Ramani",
            navTools = "Vifaa",
            navVault = "Hifadhi",
            weatherAndMoon = "Hali ya Hewa & Mwezi",
            systemAndSettings = "Mfumo na Mipangilio",
            systemLanguageTitle = "Lugha ya Mfumo",
            browseAllLanguages = "Lugha Zote za Dunia",
            searchLanguagesHint = "Tafuta lugha"
        )

        private val MALAY = GhostStrings(
            searchPlaces = "Cari tempat",
            navExplore = "Teroka",
            navSky = "Langit",
            navSaved = "Disimpan",
            navMaps = "Peta",
            navTools = "Alatan",
            navVault = "Peti",
            weatherAndMoon = "Cuaca & Bulan",
            systemAndSettings = "Sistem & Tetapan",
            systemLanguageTitle = "Bahasa Sistem",
            browseAllLanguages = "Lihat Semua Bahasa Dunia",
            searchLanguagesHint = "Cari bahasa"
        )

        private val FILIPINO = GhostStrings(
            searchPlaces = "Maghanap ng lugar",
            navExplore = "Tuklasin",
            navSky = "Langit",
            navSaved = "Naka-save",
            navMaps = "Mapa",
            navTools = "Kagamitan",
            navVault = "Vault",
            weatherAndMoon = "Panahon at Buwan",
            systemAndSettings = "Sistema at Mga Setting",
            systemLanguageTitle = "Wika ng Sistema",
            browseAllLanguages = "Tingnan ang Lahat ng Wika",
            searchLanguagesHint = "Maghanap ng wika"
        )
    }
}

val LocalGhostStrings = staticCompositionLocalOf { GhostStrings.ENGLISH }
