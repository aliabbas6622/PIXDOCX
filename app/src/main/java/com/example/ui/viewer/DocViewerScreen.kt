package com.example.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfficeDocument
import com.example.ui.OfficeViewModel
import com.example.ui.common.MarkdownText
import com.example.ui.common.ExportDocumentDialog
import com.example.ui.common.RenameDocumentDialog
import com.example.ui.common.DeleteConfirmDialog
import com.example.ui.common.ViewerOverflowMenu
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800

/**
 * Read-only "viewing" experience for text-like documents (md, txt, code, pdf
 * imports, docx text). Markdown is rendered visually instead of showing raw
 * `#`/`**` syntax. The header pencil toggles to the existing editing screen.
 *
 * Code-like files (.json, .xml, .kt, .java, .py, .c, ...) are detected by
 * [looksLikeCode] and shown in a horizontally-scrollable monospace layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocViewerScreen(
    document: OfficeDocument,
    viewModel: OfficeViewModel,
    modifier: Modifier = Modifier
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isCode = remember(document.title, document.content) {
        looksLikeCode(document.title, document.content)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Column(
                    modifier = Modifier.clickable { showRenameDialog = true }
                ) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        text = "Read-only view • ${document.sizeLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.closeDocument() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Documents",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // Overflow: Export/Share, Rename, Delete
                ViewerOverflowMenu(
                    document = document,
                    onExport = { showExportDialog = true },
                    onRename = { showRenameDialog = true },
                    onDelete = { showDeleteDialog = true }
                )
                // Switch to the full editing screen
                IconButton(
                    onClick = { viewModel.setViewMode(false) },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit document",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // READING CANVAS — A4-style paper card, like the editor but read-only
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Slate100)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .width(680.dp)
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (isCode) {
                    // Monospace, horizontally scrollable for long lines
                    Text(
                        text = document.content,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                } else {
                    MarkdownText(
                        markdown = document.content,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Bottom hint bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tap ✏️ in the top-right to edit",
                style = MaterialTheme.typography.labelSmall,
                color = Slate500
            )
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            title = document.title,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteDocument(document)
            }
        )
    }

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

    if (showExportDialog) {
        ExportDocumentDialog(
            document = document,
            onDismiss = { showExportDialog = false }
        )
    }
}

/** Extensions whose content should render as monospace code, never markdown. */
private val CODE_EXTENSIONS = setOf(
    "json", "xml", "kt", "java", "py", "js", "ts", "c", "cpp", "h", "cs",
    "go", "rs", "rb", "php", "sh", "bat", "yaml", "yml", "toml", "ini",
    "gradle", "sql", "html", "css", "log"
)

fun looksLikeCode(title: String, content: String): Boolean {
    val ext = title.substringAfterLast('.', "").lowercase()
    if (ext in CODE_EXTENSIONS) return true
    // Heuristic: lines starting with common code tokens + low prose ratio
    val lines = content.lines().take(30)
    if (lines.isEmpty()) return false
    val codeish = lines.count {
        it.trimStart().startsWith("{") || it.trimStart().startsWith("<") ||
                it.trimStart().startsWith("def ") || it.trimStart().startsWith("function") ||
                it.trimStart().startsWith("import ") || it.trimStart().startsWith("package ") ||
                it.trimStart().startsWith("//") || it.trimStart().contains("; {")
    }
    return codeish * 3 > lines.size
}
