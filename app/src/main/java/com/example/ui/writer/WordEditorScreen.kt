package com.example.ui.writer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfficeDocument
import com.example.ui.EditorSaveState
import com.example.ui.OfficeViewModel
import com.example.ui.common.ExportDocumentDialog
import com.example.ui.common.RenameDocumentDialog
import com.example.ui.theme.DocBlue
import com.example.ui.theme.DocBlueLight
import com.example.ui.theme.SheetGreen
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.WpsRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(
    document: OfficeDocument,
    viewModel: OfficeViewModel,
    modifier: Modifier = Modifier
) {
    val saveState by viewModel.saveState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    var textFieldValue by remember(document.id) {
        mutableStateOf(TextFieldValue(document.content, selection = TextRange(document.content.length)))
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var isPaperMode by remember { mutableStateOf(true) }

    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var searchMatchCount by remember { mutableStateOf(0) }

    // Synchronize content with viewmodel
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != document.content) {
            viewModel.updateDocumentContent(textFieldValue.text)
        }
    }

    // Keep textFieldValue in sync if undo/redo happens
    LaunchedEffect(document.content) {
        if (document.content != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = document.content,
                selection = TextRange(minOf(textFieldValue.selection.start, document.content.length))
            )
        }
    }

    // Update match count for search
    LaunchedEffect(findQuery, textFieldValue.text) {
        if (findQuery.isNotBlank()) {
            // rememberUpdatedState-style hoisting done via companion: compiling
            // a regex per recomposition is wasteful (see optimization guides).
            searchMatchCount = findRegex(findQuery).findAll(textFieldValue.text).count()
        } else {
            searchMatchCount = 0
        }
    }

    fun insertFormatting(prefix: String, suffix: String = "") {
        val sel = textFieldValue.selection
        val text = textFieldValue.text
        val selectedText = if (sel.start != sel.end) {
            text.substring(minOf(sel.start, sel.end), maxOf(sel.start, sel.end))
        } else ""

        val newText = text.substring(0, minOf(sel.start, sel.end)) +
                prefix + selectedText + suffix +
                text.substring(maxOf(sel.start, sel.end))

        val newCursor = minOf(sel.start, sel.end) + prefix.length + selectedText.length + suffix.length
        textFieldValue = TextFieldValue(newText, selection = TextRange(newCursor))
    }

    fun applyLinePrefix(linePrefix: String) {
        val sel = textFieldValue.selection
        val text = textFieldValue.text
        val start = minOf(sel.start, sel.end)
        val lineStart = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }

        val newText = text.substring(0, lineStart) + linePrefix + text.substring(lineStart)
        val newCursor = sel.start + linePrefix.length
        textFieldValue = TextFieldValue(newText, selection = TextRange(newCursor))
    }

    fun performReplace(all: Boolean) {
        if (findQuery.isBlank()) return
        val currentText = textFieldValue.text
        val newText = if (all) {
            currentText.replace(findQuery, replaceQuery, ignoreCase = true)
        } else {
            val idx = currentText.indexOf(findQuery, ignoreCase = true)
            if (idx != -1) {
                currentText.substring(0, idx) + replaceQuery + currentText.substring(idx + findQuery.length)
            } else currentText
        }
        textFieldValue = TextFieldValue(newText)
    }

    val wordCount = remember(textFieldValue.text) {
        OfficeViewModel.countWords(textFieldValue.text)
    }
    val charCount = remember(textFieldValue.text) {
        textFieldValue.text.length
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isPaperMode) Slate100 else MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR
        TopAppBar(
            title = {
                Column(
                    modifier = Modifier.clickable { showRenameDialog = true }
                ) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (saveState.status == "Saved") Color.White else Color.Gray)
                        )
                        Text(
                            text = saveState.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• $wordCount words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.closeDocument() },
                    modifier = Modifier.testTag("doc_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Documents",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.performUndo() },
                    enabled = canUndo,
                    modifier = Modifier.testTag("doc_undo_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }

                IconButton(
                    onClick = { viewModel.performRedo() },
                    enabled = canRedo,
                    modifier = Modifier.testTag("doc_redo_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }

                IconButton(
                    onClick = { showFindReplace = !showFindReplace },
                    modifier = Modifier.testTag("doc_search_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = "Find & Replace",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { isPaperMode = !isPaperMode },
                    modifier = Modifier.testTag("doc_page_mode_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalSplit,
                        contentDescription = "Page Layout Mode",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.testTag("doc_share_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // FIND & REPLACE BANNER
        AnimatedVisibility(visible = showFindReplace) {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            placeholder = { Text("Find in document...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f).testTag("find_input")
                        )
                        if (findQuery.isNotBlank()) {
                            Text(
                                text = "$searchMatchCount found",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showFindReplace = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace with...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("replace_input")
                        )
                        TextButton(
                            onClick = { performReplace(all = false) },
                            enabled = findQuery.isNotBlank(),
                            modifier = Modifier.testTag("replace_one_btn")
                        ) {
                            Text("Replace", color = Color.White)
                        }
                        TextButton(
                            onClick = { performReplace(all = true) },
                            enabled = findQuery.isNotBlank(),
                            modifier = Modifier.testTag("replace_all_btn")
                        ) {
                            Text("All", color = Color.White)
                        }
                    }
                }
            }
        }

        // MAIN DOCUMENT CANVAS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isPaperMode) {
                // A4 PAPER SIMULATION VIEW (DARK GRAY CARD)
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(4.dp)),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 26.sp,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 600.dp)
                                .testTag("doc_editor_textarea")
                        )
                    }
                }
            } else {
                // MOBILE CONTINUOUS FLOW VIEW
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 500.dp)
                            .testTag("doc_editor_textarea")
                    )
                }
            }
        }

        // STREAMLINED DOCKED FORMATTING TOOLBAR
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Headings
                ToolbarButton(label = "H1", onClick = { applyLinePrefix("# ") })
                ToolbarButton(label = "H2", onClick = { applyLinePrefix("## ") })
                ToolbarButton(label = "H3", onClick = { applyLinePrefix("### ") })

                Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline))

                // In-line styles
                ToolbarIconButton(
                    icon = Icons.Default.FormatBold,
                    desc = "Bold",
                    onClick = { insertFormatting("**", "**") }
                )
                ToolbarIconButton(
                    icon = Icons.Default.FormatItalic,
                    desc = "Italic",
                    onClick = { insertFormatting("*", "*") }
                )
                ToolbarIconButton(
                    icon = Icons.Default.FormatUnderlined,
                    desc = "Underline",
                    onClick = { insertFormatting("<u>", "</u>") }
                )
                ToolbarButton(label = "S", isStrike = true, onClick = { insertFormatting("~~", "~~") })

                Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline))

                // Lists
                ToolbarIconButton(
                    icon = Icons.Default.FormatListBulleted,
                    desc = "Bullet List",
                    onClick = { applyLinePrefix("• ") }
                )
                ToolbarIconButton(
                    icon = Icons.Default.FormatListNumbered,
                    desc = "Numbered List",
                    onClick = { applyLinePrefix("1. ") }
                )
                ToolbarButton(
                    label = "[x]",
                    onClick = { applyLinePrefix("[ ] ") }
                )
                ToolbarIconButton(
                    icon = Icons.Default.FormatQuote,
                    desc = "Quote",
                    onClick = { applyLinePrefix("> ") }
                )
                ToolbarIconButton(
                    icon = Icons.Default.HorizontalRule,
                    desc = "Divider",
                    onClick = { insertFormatting("\n---\n") }
                )
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        RenameDocumentDialog(
            initialTitle = document.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                viewModel.updateDocumentTitle(newTitle)
                showRenameDialog = false
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDocumentDialog(
            document = document,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    isStrike: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White,
            textDecoration = if (isStrike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
        )
    }
}

// Hoisted: building a case-insensitive regex per LaunchedEffect run is
// wasteful; a small LRU-style cache keeps the last compiled patterns.
private var cachedFindPattern: Pair<String, Regex>? = null

private fun findRegex(query: String): Regex {
    cachedFindPattern?.let { (q, regex) ->
        if (q == query) return regex
    }
    val regex = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE)
    cachedFindPattern = query to regex
    return regex
}
