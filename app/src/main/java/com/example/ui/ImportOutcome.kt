package com.example.ui

import com.example.data.model.DocumentType

/**
 * What happened to a single file the user picked.
 *
 * Imports used to fail silently: a duplicate file did nothing at all, and an
 * unreadable file threw inside the view-model coroutine, so the progress row
 * vanished with no explanation. Every file now produces exactly one outcome,
 * which is summarised into a sentence the user actually sees.
 */
sealed interface ImportOutcome {

    /** Parsed and stored; [title] is the document name shown in the library. */
    data class Imported(val title: String, val type: DocumentType) : ImportOutcome

    /** Same name and size as a document already in the library — nothing to do. */
    data class SkippedDuplicate(val title: String) : ImportOutcome

    /** Could not be read or parsed; [reason] is the raw message, if any. */
    data class Failed(val fileName: String, val reason: String?) : ImportOutcome
}

/**
 * Collapses one import batch into the single message shown in the snackbar,
 * or null when there is nothing worth telling the user.
 *
 * A batch always reads as one sentence ("Imported 3 files · Failed 1 file")
 * rather than one snackbar per file.
 */
fun importSummary(outcomes: List<ImportOutcome>): String? {
    if (outcomes.isEmpty()) return null

    outcomes.singleOrNull()?.let { only ->
        return when (only) {
            is ImportOutcome.Imported -> "Imported \"${only.title}\""
            is ImportOutcome.SkippedDuplicate -> "\"${only.title}\" is already in PixDocx"
            is ImportOutcome.Failed ->
                "Couldn't import \"${only.fileName}\"" + (only.reason?.let { ": ${it.take(80)}" } ?: "")
        }
    }

    val imported = outcomes.count { it is ImportOutcome.Imported }
    val duplicates = outcomes.count { it is ImportOutcome.SkippedDuplicate }
    val failed = outcomes.count { it is ImportOutcome.Failed }

    val parts = buildList {
        if (imported > 0) add("Imported $imported file${plural(imported)}")
        if (duplicates > 0) add("Skipped $duplicates duplicate${plural(duplicates)}")
        if (failed > 0) add("Failed $failed file${plural(failed)}")
    }
    return parts.joinToString(" \u00b7 ")
}

private fun plural(count: Int): String = if (count == 1) "" else "s"
