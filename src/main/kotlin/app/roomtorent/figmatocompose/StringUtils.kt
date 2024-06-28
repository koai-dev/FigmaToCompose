package app.roomtorent.figmatocompose

import java.text.Normalizer

private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun CharSequence.unAccent(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return REGEX_UNACCENT.replace(temp, "")
        .replace("đ", "d")
        .replace("Đ", "D")
}

fun String.cleanValueAndLowercase() = this.cleanValue().lowercase()

fun String.cleanValue() =
    this.unAccent()
        .replace("\\s+".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .replace(" ", "_")
        .replace(Regex("[^A-Za-z0-9_]"), "")
        .replace("__", "_")
        .replace("__", "_")
