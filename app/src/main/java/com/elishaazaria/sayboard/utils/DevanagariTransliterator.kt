package com.elishaazaria.sayboard.utils

/**
 * Utility for converting Devanagari (Hindi) script to Roman script (Hinglish).
 * Uses character-level mapping with proper handling of:
 * - Consonants with inherent 'a' vowel
 * - Vowel marks (matras) that replace the inherent vowel
 * - Halant (virama) that suppresses the inherent vowel
 * - Schwa deletion at word-final positions
 */
object DevanagariTransliterator {

    private val charMap = mapOf(
        // Vowels (independent)
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ए' to "e", 'ऐ' to "ai",
        'ओ' to "o", 'औ' to "au", 'ऋ' to "ri",

        // Vowel marks (matras) - these REPLACE the inherent 'a'
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u",
        'ू' to "oo", 'े' to "e", 'ै' to "ai", 'ो' to "o",
        'ौ' to "au", 'ृ' to "ri",

        // Consonants (base form without inherent vowel)
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",

        // Special marks
        'ं' to "n",   // Anusvara
        'ः' to "h",   // Visarga
        '्' to "",    // Halant (virama) - suppresses inherent vowel
        'ँ' to "n",   // Chandrabindu
        '़' to "",    // Nukta (combining mark) - ignore, base consonant handles it
    )

    private val vowelMarks = setOf('ा', 'ि', 'ी', 'ु', 'ू', 'े', 'ै', 'ो', 'ौ', 'ृ')
    private val consonants = setOf(
        'क', 'ख', 'ग', 'घ', 'ङ',
        'च', 'छ', 'ज', 'झ', 'ञ',
        'ट', 'ठ', 'ड', 'ढ', 'ण',
        'त', 'थ', 'द', 'ध', 'न',
        'प', 'फ', 'ब', 'भ', 'म',
        'य', 'र', 'ल', 'व',
        'श', 'ष', 'स', 'ह'
    )
    // Nukta consonants removed - nukta mark (़) is handled separately as combining character
    private const val HALANT = '्'

    /**
     * Check if a character is in the Devanagari Unicode block (U+0900 to U+097F)
     */
    private fun isDevanagari(char: Char): Boolean {
        return char in '\u0900'..'\u097F'
    }

    /**
     * Check if character is a consonant
     */
    private fun isConsonant(char: Char): Boolean {
        return char in consonants
    }

    /**
     * Convert Devanagari text to Roman script (Hinglish).
     * Handles mixed text - non-Devanagari characters pass through unchanged.
     */
    fun transliterate(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val char = text[i]
            val nextChar = text.getOrNull(i + 1)

            if (char in charMap) {
                result.append(charMap[char])

                // Add inherent 'a' for consonants not followed by halant or vowel mark
                if (isConsonant(char)) {
                    val hasHalant = nextChar == HALANT
                    val hasVowelMark = nextChar in vowelMarks

                    if (!hasHalant && !hasVowelMark) {
                        // Check for word-final position (apply schwa deletion)
                        val isWordFinal = nextChar == null ||
                                nextChar == ' ' ||
                                !isDevanagari(nextChar)
                        if (!isWordFinal) {
                            result.append("a")
                        }
                    }
                }
            } else {
                // Non-mapped character (English, numbers, punctuation) - keep as-is
                result.append(char)
            }

            i++
        }

        return result.toString()
    }
}
