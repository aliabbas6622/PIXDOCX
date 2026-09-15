package com.example.data.model

enum class SlideLayout {
    TITLE_SLIDE,
    TITLE_AND_CONTENT,
    TWO_COLUMN,
    BIG_STAT,
    QUOTE,
    SECTION_HEADER
}

data class SlideItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val layout: SlideLayout = SlideLayout.TITLE_AND_CONTENT,
    val title: String = "Slide Title",
    val subtitle: String = "Subtitle or presenter details",
    val content: String = "• Key topic overview\n• Strategic milestone\n• Next steps and metrics",
    val secondaryContent: String = "• Column 2 analysis\n• Supporting arguments",
    val statValue: String = "98.4%",
    val statLabel: String = "Customer Satisfaction Score",
    val notes: String = "Presenter notes: Emphasize the quarterly growth."
)

data class SlideDeck(
    val themeColorHex: String = "#0F172A", // Default dark navy theme
    val accentColorHex: String = "#EA580C", // PPT Orange
    val slides: MutableList<SlideItem> = mutableListOf()
) {
    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("THEME:").append(themeColorHex).append("|").append(accentColorHex).append("\n---SLIDES---\n")
        for (slide in slides) {
            sb.append("ID:").append(slide.id).append("\n")
            sb.append("LAYOUT:").append(slide.layout.name).append("\n")
            sb.append("TITLE:").append(escape(slide.title)).append("\n")
            sb.append("SUBTITLE:").append(escape(slide.subtitle)).append("\n")
            sb.append("CONTENT:").append(escape(slide.content)).append("\n")
            sb.append("SEC_CONTENT:").append(escape(slide.secondaryContent)).append("\n")
            sb.append("STAT_VAL:").append(escape(slide.statValue)).append("\n")
            sb.append("STAT_LBL:").append(escape(slide.statLabel)).append("\n")
            sb.append("NOTES:").append(escape(slide.notes)).append("\n")
            sb.append("---END_SLIDE---\n")
        }
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\n", "\\n")

    companion object {
        private fun unescape(s: String): String = s.replace("\\n", "\n")

        fun deserialize(data: String): SlideDeck {
            if (data.isBlank()) {
                val deck = SlideDeck()
                deck.slides.add(
                    SlideItem(
                        layout = SlideLayout.TITLE_SLIDE,
                        title = "New Presentation",
                        subtitle = "Created with WPS Office Mobile"
                    )
                )
                return deck
            }

            var theme = "#0F172A"
            var accent = "#EA580C"
            val slides = mutableListOf<SlideItem>()

            val parts = data.split("---SLIDES---\n")
            if (parts.isNotEmpty()) {
                val header = parts[0]
                val themeMatch = Regex("THEME:(#[0-9A-Fa-f]{6})\\|(#[0-9A-Fa-f]{6})").find(header)
                if (themeMatch != null) {
                    theme = themeMatch.groupValues[1]
                    accent = themeMatch.groupValues[2]
                }
            }

            if (parts.size > 1) {
                val slidesChunk = parts[1]
                val slideBlocks = slidesChunk.split("---END_SLIDE---\n")
                for (block in slideBlocks) {
                    if (block.isBlank()) continue
                    var id = java.util.UUID.randomUUID().toString()
                    var layout = SlideLayout.TITLE_AND_CONTENT
                    var title = ""
                    var subtitle = ""
                    var content = ""
                    var secContent = ""
                    var statVal = ""
                    var statLbl = ""
                    var notes = ""

                    for (line in block.lines()) {
                        when {
                            line.startsWith("ID:") -> id = line.substring(3).trim()
                            line.startsWith("LAYOUT:") -> {
                                val name = line.substring(7).trim()
                                layout = runCatching { SlideLayout.valueOf(name) }.getOrDefault(SlideLayout.TITLE_AND_CONTENT)
                            }
                            line.startsWith("TITLE:") -> title = unescape(line.substring(6))
                            line.startsWith("SUBTITLE:") -> subtitle = unescape(line.substring(9))
                            line.startsWith("CONTENT:") -> content = unescape(line.substring(8))
                            line.startsWith("SEC_CONTENT:") -> secContent = unescape(line.substring(12))
                            line.startsWith("STAT_VAL:") -> statVal = unescape(line.substring(9))
                            line.startsWith("STAT_LBL:") -> statLbl = unescape(line.substring(9))
                            line.startsWith("NOTES:") -> notes = unescape(line.substring(6))
                        }
                    }

                    slides.add(
                        SlideItem(
                            id = id,
                            layout = layout,
                            title = title,
                            subtitle = subtitle,
                            content = content,
                            secondaryContent = secContent,
                            statValue = statVal,
                            statLabel = statLbl,
                            notes = notes
                        )
                    )
                }
            }

            if (slides.isEmpty()) {
                slides.add(SlideItem(layout = SlideLayout.TITLE_SLIDE, title = "Untitled Presentation"))
            }

            return SlideDeck(themeColorHex = theme, accentColorHex = accent, slides = slides)
        }
    }
}
