package ai.milf.client.session

data class LanguageOption(
    val code: String,
    val label: String
)

val selectableLanguages = listOf(
    LanguageOption("en", "English"),
    LanguageOption("zh", "Chinese"),
    LanguageOption("yue", "Cantonese")
)
