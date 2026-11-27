package com.example.data.model

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiStreamState {
    data object Idle : AiStreamState
    data object Loading : AiStreamState
    data class GeneratingImage(val prompt: String, val modelLabel: String) : AiStreamState
    data class Streaming(val text: String, val grounding: VetnyGroundingPayload? = null) : AiStreamState
    data class Success(val text: String) : AiStreamState
    data class Error(val message: String) : AiStreamState
}

enum class VetnyAgentMode(val wireValue: String, val label: String, val hint: String) {
    SUPPORT("SUPPORT", "Support & Q&A", "Ask Vetny anything — remembers full context"),
    VOICE_LIVE("VOICE_LIVE", "Live Voice", "Real-time voice chat & translation via Gemini 3.8 Live"),
    SEARCH("SEARCH", "Web Search", "Up-to-date answers grounded in Google Search"),
    MAPS("MAPS", "Maps & Places", "Places, hotels & dining grounded in Google Maps"),
    TRANSLATE("TRANSLATE", "Translate", "Real-time global & China dialect translator"),
    BOOKING("BOOKING", "Bookings", "Multi-step trips, hotels, dining & appointments"),
    IMAGE("IMAGE", "Photo Studio", "Generate or edit photos & art with Nano Banana 2.1")
}

enum class VetnyChatModel(val modelId: String, val displayName: String, val badge: String) {
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Fast Default"),
    GEMINI_4_HYDROGEN("gemini-4-hydrogen", "Gemini 4 Hydrogen", "Search & Maps"),
    GEMINI_4_ARGON("gemini-4-argon", "Gemini 4 Argon", "Deep Reasoning")
}

enum class VetnyImageModel(val modelId: String, val displayName: String) {
    NANO_BANANA_2_1("gemini-nano-banana-2.1", "Nano Banana 2.1"),
    GEMINI_3_PRO_IMAGE("gemini-3-pro-image", "Nano Banana Pro")
}

enum class VetnyImageAspect(val label: String) {
    SQUARE("1:1"),
    PORTRAIT("9:16"),
    LANDSCAPE("16:9"),
    CLASSIC("4:3")
}

data class VetnyLiveSessionState(
    val isConnecting: Boolean = false,
    val isActive: Boolean = false,
    val voiceName: String = "AOEDE",
    val interpreterMode: Boolean = false,
    val userTranscript: String = "",
    val modelTranscript: String = "",
    val error: String? = null
)

data class WebGroundingSource(
    val title: String,
    val uri: String,
    val domain: String = ""
)

data class MapsGroundingSource(
    val title: String,
    val uri: String,
    val placeId: String = ""
)

data class VetnyGroundingPayload(
    val webSources: List<WebGroundingSource> = emptyList(),
    val mapsSources: List<MapsGroundingSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val searchEntryPointHtml: String? = null
) {
    fun toJsonString(): String? {
        if (webSources.isEmpty() && mapsSources.isEmpty() && searchEntryPointHtml.isNullOrBlank()) {
            return null
        }
        val root = org.json.JSONObject()
        if (webSources.isNotEmpty()) {
            val arr = org.json.JSONArray()
            webSources.take(8).forEach { w ->
                arr.put(
                    org.json.JSONObject().apply {
                        put("title", w.title)
                        put("uri", w.uri)
                        put("domain", w.domain)
                    }
                )
            }
            root.put("web", arr)
        }
        if (mapsSources.isNotEmpty()) {
            val arr = org.json.JSONArray()
            mapsSources.take(8).forEach { m ->
                arr.put(
                    org.json.JSONObject().apply {
                        put("title", m.title)
                        put("uri", m.uri)
                        put("placeId", m.placeId)
                    }
                )
            }
            root.put("maps", arr)
        }
        if (searchQueries.isNotEmpty()) {
            val arr = org.json.JSONArray()
            searchQueries.take(4).forEach { arr.put(it) }
            root.put("queries", arr)
        }
        if (!searchEntryPointHtml.isNullOrBlank() && searchEntryPointHtml.length <= 12000) {
            root.put("searchHtml", searchEntryPointHtml)
        }
        return root.toString()
    }

    companion object {
        fun fromJsonString(raw: String?): VetnyGroundingPayload? {
            if (raw.isNullOrBlank()) return null
            return try {
                val root = org.json.JSONObject(raw)
                val webArr = root.optJSONArray("web")
                val webList = buildList {
                    if (webArr != null) {
                        for (i in 0 until webArr.length()) {
                            val obj = webArr.optJSONObject(i) ?: continue
                            val uri = obj.optString("uri")
                            if (uri.isNotBlank()) {
                                add(
                                    WebGroundingSource(
                                        title = obj.optString("title").ifBlank { obj.optString("domain", "Web Source") },
                                        uri = uri,
                                        domain = obj.optString("domain")
                                    )
                                )
                            }
                        }
                    }
                }
                val mapsArr = root.optJSONArray("maps")
                val mapsList = buildList {
                    if (mapsArr != null) {
                        for (i in 0 until mapsArr.length()) {
                            val obj = mapsArr.optJSONObject(i) ?: continue
                            val uri = obj.optString("uri")
                            val title = obj.optString("title")
                            if (title.isNotBlank() || uri.isNotBlank()) {
                                add(
                                    MapsGroundingSource(
                                        title = title.ifBlank { "Google Maps Place" },
                                        uri = uri,
                                        placeId = obj.optString("placeId")
                                    )
                                )
                            }
                        }
                    }
                }
                val qArr = root.optJSONArray("queries")
                val qList = buildList {
                    if (qArr != null) {
                        for (i in 0 until qArr.length()) {
                            qArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
                val html = root.optString("searchHtml").takeIf { it.isNotBlank() }
                if (webList.isEmpty() && mapsList.isEmpty() && html == null) null
                else VetnyGroundingPayload(webList, mapsList, qList, html)
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class VetnyTranslationResult(
    val detectedSourceLanguage: String,
    val translatedText: String,
    val culturalNote: String
)

data class MomentTranslationState(
    val isLoading: Boolean = false,
    val result: VetnyTranslationResult? = null,
    val error: String? = null
)

data class ChatListItem(
    val chatId: String,
    val peerUid: String,
    val title: String,
    val handle: String,
    val qqNumber: String,
    val avatarColorHex: String,
    val peerPreferredLanguage: String,
    val peerAgeYears: Int,
    val peerGender: String,
    val peerLocationLabel: String,
    val peerTimeZoneId: String,
    val isSelfVault: Boolean,
    val lastSnippet: String,
    val lastTimestampMillis: Long,
    val isVerified: Boolean,
    val lastSenderIsMe: Boolean = false,
    val lastMessageReceiptStatus: String? = null
)

data class ActiveChatSession(
    val chatId: String,
    val peerUid: String,
    val peerName: String,
    val peerHandle: String,
    val peerQqNumber: String,
    val peerAvatarColorHex: String,
    val peerPreferredLanguage: String,
    val peerAgeYears: Int,
    val peerGender: String,
    val peerInterests: List<String>,
    val peerLocationLabel: String,
    val peerTimeZoneId: String,
    val isSelfVault: Boolean,
    val safetyNumber: String,
    val peerIdentityKey: String,
    val isVerified: Boolean
)

data class ActiveCallUiState(
    val chatId: String,
    val callId: String,
    val peerUid: String,
    val peerName: String,
    val peerAvatarColorHex: String,
    val peerPreferredLanguage: String,
    val callType: String, // "VOICE" or "VIDEO"
    val e2eeFingerprint: String,
    val incoming: Boolean,
    val status: String // "RINGING" or "ACTIVE"
)

enum class KelMiTab(val label: String) {
    CHATS("Chats"),
    CONTACTS("People"),
    REELS("Reels"),
    MOMENTS("Moments"),
    SECURITY("Me")
}

data class WorldLanguage(
    val name: String,
    val nativeLabel: String,
    val region: String
)

object SupportedLanguages {
    val worldLanguages = listOf(
        WorldLanguage("English", "English", "Global"),
        WorldLanguage("Chinese (Simplified)", "简体中文", "China"),
        WorldLanguage("Chinese (Traditional)", "繁體中文", "China / HK / TW"),
        WorldLanguage("Cantonese", "粵語", "China (Guangdong / HK)"),
        WorldLanguage("Shanghainese (Wu)", "上海话 / 吴语", "China"),
        WorldLanguage("Hokkien (Min Nan)", "閩南語", "China / SE Asia"),
        WorldLanguage("Tibetan", "བོད་ཡིག", "China / Himalayas"),
        WorldLanguage("Uyghur", "ئۇيغۇرچە", "China / Central Asia"),
        WorldLanguage("Spanish", "Español", "Americas / Europe"),
        WorldLanguage("Arabic", "العربية", "Middle East / N. Africa"),
        WorldLanguage("Hindi", "हिन्दी", "India"),
        WorldLanguage("French", "Français", "Europe / Africa / Americas"),
        WorldLanguage("Portuguese", "Português", "Brazil / Europe / Africa"),
        WorldLanguage("Russian", "Русский", "Eurasia"),
        WorldLanguage("Japanese", "日本語", "Japan"),
        WorldLanguage("Korean", "한국어", "Korea"),
        WorldLanguage("German", "Deutsch", "Europe"),
        WorldLanguage("Indonesian", "Bahasa Indonesia", "SE Asia"),
        WorldLanguage("Malay", "Bahasa Melayu", "SE Asia"),
        WorldLanguage("Vietnamese", "Tiếng Việt", "Vietnam"),
        WorldLanguage("Thai", "ไทย", "Thailand"),
        WorldLanguage("Turkish", "Türkçe", "Türkiye"),
        WorldLanguage("Swahili", "Kiswahili", "East Africa"),
        WorldLanguage("Bengali", "বাংলা", "South Asia"),
        WorldLanguage("Urdu", "اردو", "South Asia"),
        WorldLanguage("Punjabi", "ਪੰਜਾਬੀ", "South Asia"),
        WorldLanguage("Tamil", "தமிழ்", "South Asia / SE Asia"),
        WorldLanguage("Telugu", "తెలుగు", "India"),
        WorldLanguage("Marathi", "मराठी", "India"),
        WorldLanguage("Gujarati", "ગુજરાતી", "India"),
        WorldLanguage("Persian (Farsi)", "فارسی", "Iran / Central Asia"),
        WorldLanguage("Italian", "Italiano", "Europe"),
        WorldLanguage("Dutch", "Nederlands", "Europe"),
        WorldLanguage("Polish", "Polski", "Europe"),
        WorldLanguage("Ukrainian", "Українська", "Europe"),
        WorldLanguage("Romanian", "Română", "Europe"),
        WorldLanguage("Greek", "Ελληνικά", "Europe"),
        WorldLanguage("Czech", "Čeština", "Europe"),
        WorldLanguage("Swedish", "Svenska", "Europe"),
        WorldLanguage("Norwegian", "Norsk", "Europe"),
        WorldLanguage("Danish", "Dansk", "Europe"),
        WorldLanguage("Finnish", "Suomi", "Europe"),
        WorldLanguage("Hungarian", "Magyar", "Europe"),
        WorldLanguage("Hebrew", "עברית", "Middle East"),
        WorldLanguage("Tagalog / Filipino", "Tagalog", "Philippines"),
        WorldLanguage("Burmese", "မြန်မာစာ", "Myanmar"),
        WorldLanguage("Khmer", "ភាសាខ្មែរ", "Cambodia"),
        WorldLanguage("Lao", "ລາວ", "Laos"),
        WorldLanguage("Mongolian", "Монгол", "Mongolia / China"),
        WorldLanguage("Kazakh", "Қазақ тілі", "Central Asia"),
        WorldLanguage("Uzbek", "Oʻzbekcha", "Central Asia"),
        WorldLanguage("Amharic", "አማርኛ", "Ethiopia"),
        WorldLanguage("Somali", "Soomaali", "East Africa"),
        WorldLanguage("Yoruba", "Yorùbá", "West Africa"),
        WorldLanguage("Hausa", "Hausa", "West Africa"),
        WorldLanguage("Igbo", "Igbo", "West Africa"),
        WorldLanguage("Zulu", "isiZulu", "Southern Africa"),
        WorldLanguage("Afrikaans", "Afrikaans", "Southern Africa"),
        WorldLanguage("Nepali", "नेपाली", "Nepal"),
        WorldLanguage("Sinhala", "සිංහල", "Sri Lanka"),
        WorldLanguage("Pashto", "پښتو", "Central / South Asia"),
        WorldLanguage("Kurdish", "Kurdî", "Middle East"),
        WorldLanguage("Georgian", "ქართული", "Caucasus"),
        WorldLanguage("Armenian", "Հայերեն", "Caucasus"),
        WorldLanguage("Azerbaijani", "Azərbaycan", "Caucasus"),
        WorldLanguage("Serbian", "Српски", "Europe"),
        WorldLanguage("Croatian", "Hrvatski", "Europe"),
        WorldLanguage("Bulgarian", "Български", "Europe"),
        WorldLanguage("Slovak", "Slovenčina", "Europe"),
        WorldLanguage("Lithuanian", "Lietuvių", "Europe"),
        WorldLanguage("Latvian", "Latviešu", "Europe"),
        WorldLanguage("Estonian", "Eesti", "Europe"),
        WorldLanguage("Icelandic", "Íslenska", "Europe"),
        WorldLanguage("Irish", "Gaeilge", "Europe"),
        WorldLanguage("Catalan", "Català", "Europe"),
        WorldLanguage("Maori", "Te Reo Māori", "Oceania"),
        WorldLanguage("Samoan", "Gagana Sāmoa", "Oceania"),
        WorldLanguage("Hawaiian", "ʻŌlelo Hawaiʻi", "Oceania"),
        WorldLanguage("Esperanto", "Esperanto", "International")
    )

    val all: List<String> = worldLanguages.map { it.name }
}

object ProfileOptions {
    val genders = listOf("Female", "Male", "Non-binary", "Prefer not to say")
    val suggestedInterests = listOf(
        "Languages",
        "Travel",
        "Music",
        "Tech & AI",
        "Photography",
        "Gaming",
        "Cooking",
        "Fitness",
        "Cinema",
        "Books",
        "Art & Design",
        "Privacy"
    )
}

object ReelFilters {
    val options = listOf("Original", "Warm Gold", "Emerald Film", "Kyoto Cool", "Noir B&W")
}
