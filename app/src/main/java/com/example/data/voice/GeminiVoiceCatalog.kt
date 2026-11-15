package com.example.data.voice

data class GeminiFemaleVoice(
    val voiceName: String,
    val displayName: String,
    val toneLabel: String,
    val personalityFit: String,
    val accentColorHex: Long
)

object GeminiVoiceCatalog {
    const val DEFAULT_VOICE_NAME = "Aoede"

    /**
     * Official Google Gemini Live API / Native Audio female prebuilt voices.
     */
    val officialFemaleVoices: List<GeminiFemaleVoice> = listOf(
        GeminiFemaleVoice(
            voiceName = "Aoede",
            displayName = "Aoede",
            toneLabel = "Breezy & Expressive",
            personalityFit = "Warm, natural, playful everyday companion (Default for Rit)",
            accentColorHex = 0xFF8B5CF6
        ),
        GeminiFemaleVoice(
            voiceName = "Kore",
            displayName = "Kore",
            toneLabel = "Firm & Poised",
            personalityFit = "Confident, articulate, thoughtful problem solver",
            accentColorHex = 0xFFA855F7
        ),
        GeminiFemaleVoice(
            voiceName = "Leda",
            displayName = "Leda",
            toneLabel = "Youthful & Bright",
            personalityFit = "Lively, witty, upbeat conversational energy",
            accentColorHex = 0xFFF472B6
        ),
        GeminiFemaleVoice(
            voiceName = "Zephyr",
            displayName = "Zephyr",
            toneLabel = "Bright & Clear",
            personalityFit = "Crisp, energetic, fast-paced Hinglish & English banter",
            accentColorHex = 0xFF38BDF8
        ),
        GeminiFemaleVoice(
            voiceName = "Sulafat",
            displayName = "Sulafat",
            toneLabel = "Warm & Caring",
            personalityFit = "Empathetic, affectionate, comforting late-night listener",
            accentColorHex = 0xFFFB7185
        ),
        GeminiFemaleVoice(
            voiceName = "Achird",
            displayName = "Achird",
            toneLabel = "Friendly & Approachable",
            personalityFit = "Casual best-friend vibe, effortless conversational flow",
            accentColorHex = 0xFFC084FC
        ),
        GeminiFemaleVoice(
            voiceName = "Vindemiatrix",
            displayName = "Vindemiatrix",
            toneLabel = "Gentle & Empathetic",
            personalityFit = "Soft-spoken, calm, deeply attentive companion",
            accentColorHex = 0xFFE879F9
        ),
        GeminiFemaleVoice(
            voiceName = "Callirrhoe",
            displayName = "Callirrhoe",
            toneLabel = "Easy-Going & Relaxed",
            personalityFit = "Laid-back, natural rhythm with unhurried pauses",
            accentColorHex = 0xFF818CF8
        ),
        GeminiFemaleVoice(
            voiceName = "Autonoe",
            displayName = "Autonoe",
            toneLabel = "Bright & Lively",
            personalityFit = "Sparkling curiosity, enthusiastic brainstorming partner",
            accentColorHex = 0xFFF43F5E
        ),
        GeminiFemaleVoice(
            voiceName = "Erinome",
            displayName = "Erinome",
            toneLabel = "Clear & Composed",
            personalityFit = "Balanced, crisp pronunciation for bilingual clarity",
            accentColorHex = 0xFF60A5FA
        ),
        GeminiFemaleVoice(
            voiceName = "Laomedeia",
            displayName = "Laomedeia",
            toneLabel = "Upbeat & Cheerful",
            personalityFit = "Sunny optimism, playful teasing and humor",
            accentColorHex = 0xFFEC4899
        ),
        GeminiFemaleVoice(
            voiceName = "Enceladus",
            displayName = "Enceladus",
            toneLabel = "Breathy & Intimate",
            personalityFit = "Soft, close-mic emotional nuance",
            accentColorHex = 0xFFA78BFA
        ),
        GeminiFemaleVoice(
            voiceName = "Algieba",
            displayName = "Algieba",
            toneLabel = "Smooth & Melodic",
            personalityFit = "Fluid cadence, expressive storytelling",
            accentColorHex = 0xFF7C3AED
        ),
        GeminiFemaleVoice(
            voiceName = "Achernar",
            displayName = "Achernar",
            toneLabel = "Soft & Soothing",
            personalityFit = "Relaxing voice for reflection and daily check-ins",
            accentColorHex = 0xFFD946EF
        ),
        GeminiFemaleVoice(
            voiceName = "Gacrux",
            displayName = "Gacrux",
            toneLabel = "Mature & Thoughtful",
            personalityFit = "Grounded, wise, steady conversational anchor",
            accentColorHex = 0xFF6366F1
        ),
        GeminiFemaleVoice(
            voiceName = "Sadaltager",
            displayName = "Sadaltager",
            toneLabel = "Knowledgeable & Calm",
            personalityFit = "Composed mentor-like warmth for deep topics",
            accentColorHex = 0xFF2DD4BF
        ),
        GeminiFemaleVoice(
            voiceName = "Despina",
            displayName = "Despina",
            toneLabel = "Smooth & Warm",
            personalityFit = "Polished, gracious, emotionally attuned",
            accentColorHex = 0xFFF9A8D4
        ),
        GeminiFemaleVoice(
            voiceName = "Pulcherrima",
            displayName = "Pulcherrima",
            toneLabel = "Forward & Confident",
            personalityFit = "Bold, witty, sharp conversational spark",
            accentColorHex = 0xFFA855F7
        )
    )

    fun findByName(voiceName: String): GeminiFemaleVoice {
        return officialFemaleVoices.firstOrNull {
            it.voiceName.equals(voiceName, ignoreCase = true)
        } ?: officialFemaleVoices.first()
    }
}
