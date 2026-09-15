package com.example.ui.calc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CellCoordinate
import com.example.data.model.OfficeDocument
import com.example.data.model.SpreadsheetGrid
import com.example.ui.OfficeViewModel
import com.example.ui.common.ExportDocumentDialog
import com.example.ui.common.RenameDocumentDialog
import com.example.ui.theme.DocBlue
import com.example.ui.theme.DocBlueLight
import com.example.ui.theme.GridBorder
import com.example.ui.theme.SelectedCellBg
import com.example.ui.theme.SelectedCellBorder
import com.example.ui.theme.SheetGreen
import com.example.ui.theme.SheetGreenLight
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.ui.theme.WpsRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetEditorScreen(
    document: OfficeDocument,
    viewModel: OfficeViewModel,
    modifier: Modifier = Modifier
) {
    val saveState by viewModel.saveState.collectAsState()
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var maxCols by remember { mutableIntStateOf(10) } // A through J
    var maxRows by remember { mutableIntStateOf(30) }

    val grid = remember(document.id) {
        SpreadsheetGrid.deserialize(document.content, maxCols, maxRows)
    }

    var selectedCellId by remember { mutableStateOf("A1") }
    var formulaBarText by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Synchronize formula text with active selected cell
    LaunchedEffect(selectedCellId, refreshTrigger) {
        formulaBarText = grid.getRaw(selectedCellId)
    }

    fun saveGrid() {
        val serialized = grid.serialize()
        viewModel.updateDocumentContent(serialized)
        refreshTrigger++
    }

    fun applyFormulaText(text: String) {
        formulaBarText = text
        grid.setCell(selectedCellId, text)
        saveGrid()
    }

    fun appendToFormula(token: String) {
        val current = formulaBarText
        val newText = if (current.isEmpty()) "=$token" else "$current$token"
        formulaBarText = newText
        grid.setCell(selectedCellId, newText)
        saveGrid()
    }

    val selectedStyle = grid.getStyle(selectedCellId)
    val horizontalScrollState = rememberScrollState()

    // Calculate quick column/selection summary
    val selectedVal = grid.evaluateDisplayValue(selectedCellId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                            text = "• ${CellCoordinate.getColumnName(maxCols)}$maxRows cells",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.closeDocument() },
                    modifier = Modifier.testTag("sheet_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (maxCols < 16) {
                            maxCols++
                            refreshTrigger++
                        }
                    },
                    modifier = Modifier.testTag("add_col_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Column",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        val csv = grid.toCsv()
                        clipboardManager.setText(AnnotatedString(csv))
                        showExportDialog = true
                    },
                    modifier = Modifier.testTag("sheet_export_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export CSV",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // COMPACT STREAMLINED FORMULA BAR
        var showFormulaChips by remember { mutableStateOf(false) }

        Surface(
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Cell Coordinate Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = selectedCellId,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Formula Input Field
                    OutlinedTextField(
                        value = formulaBarText,
                        onValueChange = {
                            applyFormulaText(it)
                            if (it.startsWith("=")) {
                                showFormulaChips = true
                            }
                        },
                        placeholder = { Text("Enter value or formula...", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        leadingIcon = {
                            IconButton(
                                onClick = { showFormulaChips = !showFormulaChips },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Functions,
                                    contentDescription = "Formulas",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            if (formulaBarText.isNotEmpty()) {
                                IconButton(
                                    onClick = { applyFormulaText("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("formula_input")
                    )
                }

                // Quick Formula Chips (Expandable / when typing =)
                AnimatedVisibility(
                    visible = showFormulaChips,
                    enter = fadeIn(tween(110)) + expandVertically(tween(110)),
                    exit = fadeOut(tween(90)) + shrinkVertically(tween(90))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormulaChip(label = "SUM", onClick = { appendToFormula("SUM(") })
                        FormulaChip(label = "AVERAGE", onClick = { appendToFormula("AVERAGE(") })
                        FormulaChip(label = "COUNT", onClick = { appendToFormula("COUNT(") })
                        FormulaChip(label = "MAX", onClick = { appendToFormula("MAX(") })
                        FormulaChip(label = "MIN", onClick = { appendToFormula("MIN(") })
                        FormulaChip(label = "+", onClick = { appendToFormula("+") })
                        FormulaChip(label = "-", onClick = { appendToFormula("-") })
                        FormulaChip(label = "*", onClick = { appendToFormula("*") })
                        FormulaChip(label = "/", onClick = { appendToFormula("/") })
                    }
                }
            }
        }

        // COMPACT FORMATTING ACTION BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Bold
            IconButton(
                onClick = {
                    grid.updateStyle(selectedCellId) { it.copy(isBold = !it.isBold) }
                    saveGrid()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FormatBold,
                    contentDescription = "Bold",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Currency Format
            IconButton(
                onClick = {
                    val newFormat = if (selectedStyle.format == "CURRENCY") "GENERAL" else "CURRENCY"
                    grid.updateStyle(selectedCellId) { it.copy(format = newFormat) }
                    saveGrid()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = "Currency",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Percent Format
            IconButton(
                onClick = {
                    val newFormat = if (selectedStyle.format == "PERCENT") "GENERAL" else "PERCENT"
                    grid.updateStyle(selectedCellId) { it.copy(format = newFormat) }
                    saveGrid()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Percent,
                    contentDescription = "Percent",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Alignment
            IconButton(
                onClick = {
                    val newAlign = when (selectedStyle.align) {
                        "LEFT" -> "CENTER"
                        "CENTER" -> "RIGHT"
                        else -> "LEFT"
                    }
                    grid.updateStyle(selectedCellId) { it.copy(align = newAlign) }
                    saveGrid()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = when (selectedStyle.align) {
                        "CENTER" -> Icons.Default.FormatAlignCenter
                        "RIGHT" -> Icons.AutoMirrored.Filled.FormatAlignRight
                        else -> Icons.AutoMirrored.Filled.FormatAlignLeft
                    },
                    contentDescription = "Align",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Add Row
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .clickable {
                        maxRows += 5
                        refreshTrigger++
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+ 5 Rows",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // 2D HIGH-PERFORMANCE SPREADSHEET GRID
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            val cellWidth = 100.dp
            val rowHeaderWidth = 46.dp
            val rowHeight = 38.dp

            Column {
                // STICKY COLUMN HEADERS (A, B, C...)
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    // Top-Left Corner Box
                    Box(
                        modifier = Modifier
                            .size(width = rowHeaderWidth, height = rowHeight)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Column Header Labels
                    for (c in 1..maxCols) {
                        val colName = CellCoordinate.getColumnName(c)
                        val isColSelected = selectedCellId.startsWith(colName)
                        Box(
                            modifier = Modifier
                                .size(width = cellWidth, height = rowHeight)
                                .background(if (isColSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline),
                            contentAlignment = Alignment.Center
                        ) {
                        Text(
                            text = colName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        }
                    }
                }

                // GRID ROWS WITH STICKY ROW NUMBERS
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(maxRows) { rIndex ->
                        val r = rIndex + 1
                        val isRowSelected = selectedCellId.dropWhile { it in 'A'..'Z' } == r.toString()

                        Row {
                            // Row Header Number
                            Box(
                                modifier = Modifier
                                    .size(width = rowHeaderWidth, height = rowHeight)
                                    .background(if (isRowSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$r",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }

                            // Row Cells
                            for (c in 1..maxCols) {
                                val cellId = "${CellCoordinate.getColumnName(c)}$r"
                                val isSelected = cellId == selectedCellId
                                val displayValue = grid.evaluateDisplayValue(cellId)
                                val cellStyle = grid.getStyle(cellId)

                                val textAlign = when (cellStyle.align) {
                                    "CENTER" -> TextAlign.Center
                                    "RIGHT" -> TextAlign.Right
                                    else -> TextAlign.Left
                                }

                                Box(
                                    modifier = Modifier
                                        .size(width = cellWidth, height = rowHeight)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.outline
                                        )
                                        .clickable {
                                            selectedCellId = cellId
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = when (cellStyle.align) {
                                        "CENTER" -> Alignment.Center
                                        "RIGHT" -> Alignment.CenterEnd
                                        else -> Alignment.CenterStart
                                    }
                                ) {
                                    Text(
                                        text = displayValue,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (cellStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = textAlign
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM SUMMARY BAR
        Surface(
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CELL: $selectedCellId",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        text = "VAL: ${if (selectedVal.isBlank()) "(empty)" else selectedVal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "High-performance Calc",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            document = document.copy(content = grid.toCsv()),
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun FormulaChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White
        )
    }
}
