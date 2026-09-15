package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.data.model.SpreadsheetGrid
import com.example.ui.theme.DocBlue
import com.example.ui.theme.DocBlueLight
import com.example.ui.theme.SheetGreen
import com.example.ui.theme.SheetGreenLight
import com.example.ui.theme.SlideOrange
import com.example.ui.theme.SlideOrangeLight
import com.example.ui.theme.WpsRed

data class OfficeTemplate(
    val title: String,
    val description: String,
    val type: DocumentType,
    val initialContent: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDocumentSheet(
    onDismiss: () -> Unit,
    onCreate: (title: String, type: DocumentType, content: String) -> Unit,
    onImportCsvClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var customTitle by remember { mutableStateOf("") }

    val currentType = when (selectedTypeIndex) {
        0 -> DocumentType.DOC
        1 -> DocumentType.XLS
        else -> DocumentType.PPT
    }

    val templates = remember(currentType) {
        when (currentType) {
            DocumentType.DOC -> listOf(
                OfficeTemplate(
                    title = "Blank Document",
                    description = "Clean slate for notes and writing",
                    type = DocumentType.DOC,
                    initialContent = "# Untitled Document\n\nStart writing here..."
                ),
                OfficeTemplate(
                    title = "Meeting Minutes",
                    description = "Agenda, attendees, decisions, action items",
                    type = DocumentType.DOC,
                    initialContent = """
# Team Meeting Minutes
**Date:** Today • **Host:** Project Lead

### Agenda
1. Project milestones review
2. Technical architecture discussion
3. Action items & deadlines

### Attendees
• Alex Morgan (Engineering)
• Sarah Chen (Product)
• Jordan Taylor (Design)

### Key Decisions
• Approved native offline architecture
• Scheduled beta release for next week

### Action Items
[ ] Implement spreadsheet formula engine
[ ] Polish slide presentation controls
[ ] Complete regression testing
                    """.trimIndent()
                ),
                OfficeTemplate(
                    title = "Project Proposal",
                    description = "Executive summary, scope, timeline",
                    type = DocumentType.DOC,
                    initialContent = """
# Project Proposal: Mobile Productivity Suite
*Author: Engineering Core Team*

## 1. Executive Summary
This proposal details the design and implementation of a zero-latency native office application on Android.

## 2. Problem Statement
Mobile users require responsive, reliable document editing that functions completely offline without cloud dependencies.

## 3. Scope & Deliverables
• Word Processor with rich typography and export
• High-performance Spreadsheet with calculation engine
• Presentation deck editor with presentation mode

## 4. Timeline & Milestones
• Phase 1: Core native engines & SQLite Room schema
• Phase 2: User interaction polish & gesture navigation
• Phase 3: Offline QA & release packaging
                    """.trimIndent()
                ),
                OfficeTemplate(
                    title = "Resume & CV",
                    description = "Professional career overview",
                    type = DocumentType.DOC,
                    initialContent = """
# Johnathan Doe
*Senior Software Engineer • San Francisco, CA*
*john.doe@example.com • (555) 019-2834*

---

## Experience
**Lead Mobile Engineer — Apex Technologies** (2022 - Present)
• Architected high-performance Jetpack Compose applications
• Reduced UI render time by 40% with optimized state caching

**Android Developer — CloudScale Systems** (2019 - 2022)
• Developed offline-first data sync engines
• Integrated Room database and coroutines flow

## Education
**B.S. in Computer Science** — University of California, Berkeley

## Skills
Kotlin, Jetpack Compose, Room Database, SQLite, Architecture Components
                    """.trimIndent()
                )
            )

            DocumentType.XLS -> listOf(
                OfficeTemplate(
                    title = "Blank Spreadsheet",
                    description = "Empty grid for calculations",
                    type = DocumentType.XLS,
                    initialContent = SpreadsheetGrid().serialize()
                ),
                OfficeTemplate(
                    title = "Monthly Budget",
                    description = "Income, fixed & variable expenses, net savings",
                    type = DocumentType.XLS,
                    initialContent = run {
                        val g = SpreadsheetGrid()
                        g.setCell("A1", "Item")
                        g.setCell("B1", "Planned")
                        g.setCell("C1", "Actual")
                        g.setCell("D1", "Diff")

                        g.setCell("A2", "Salary Income")
                        g.setCell("B2", "6500")
                        g.setCell("C2", "6500")
                        g.setCell("D2", "=C2-B2")

                        g.setCell("A3", "Housing & Rent")
                        g.setCell("B3", "1800")
                        g.setCell("C3", "1800")
                        g.setCell("D3", "=B3-C3")

                        g.setCell("A4", "Groceries & Food")
                        g.setCell("B4", "600")
                        g.setCell("C4", "650")
                        g.setCell("D4", "=B4-C4")

                        g.setCell("A5", "Utilities")
                        g.setCell("B5", "250")
                        g.setCell("C5", "220")
                        g.setCell("D5", "=B5-C5")

                        g.setCell("A6", "Total Expenses")
                        g.setCell("B6", "=SUM(B3:B5)")
                        g.setCell("C6", "=SUM(C3:C5)")
                        g.setCell("D6", "=SUM(D3:D5)")

                        g.setCell("A7", "Net Savings")
                        g.setCell("B7", "=B2-B6")
                        g.setCell("C7", "=C2-C6")
                        g.setCell("D7", "=D2-D6")
                        g.serialize()
                    }
                ),
                OfficeTemplate(
                    title = "Sales & Revenue Tracker",
                    description = "Products, unit price, quantity, total",
                    type = DocumentType.XLS,
                    initialContent = run {
                        val g = SpreadsheetGrid()
                        g.setCell("A1", "Product")
                        g.setCell("B1", "Unit Price")
                        g.setCell("C1", "Units Sold")
                        g.setCell("D1", "Total Revenue")

                        g.setCell("A2", "Office Pro License")
                        g.setCell("B2", "49.99")
                        g.setCell("C2", "150")
                        g.setCell("D2", "=B2*C2")

                        g.setCell("A3", "Cloud Storage Add-on")
                        g.setCell("B3", "9.99")
                        g.setCell("C3", "320")
                        g.setCell("D3", "=B3*C3")

                        g.setCell("A4", "Enterprise Support")
                        g.setCell("B4", "199.00")
                        g.setCell("C4", "25")
                        g.setCell("D4", "=B4*C4")

                        g.setCell("A5", "Grand Total")
                        g.setCell("D5", "=SUM(D2:D4)")
                        g.serialize()
                    }
                )
            )

            DocumentType.PPT -> listOf(
                OfficeTemplate(
                    title = "Blank Presentation",
                    description = "Fresh slide deck ready for ideas",
                    type = DocumentType.PPT,
                    initialContent = run {
                        val d = SlideDeck()
                        d.slides.add(
                            SlideItem(
                                layout = SlideLayout.TITLE_SLIDE,
                                title = "New Presentation",
                                subtitle = "Created with WPS Office Mobile"
                            )
                        )
                        d.serialize()
                    }
                ),
                OfficeTemplate(
                    title = "Pitch Deck",
                    description = "Vision, problem, solution, traction",
                    type = DocumentType.PPT,
                    initialContent = run {
                        val d = SlideDeck(themeColorHex = "#0F172A", accentColorHex = "#EA580C")
                        d.slides.addAll(
                            listOf(
                                SlideItem(
                                    layout = SlideLayout.TITLE_SLIDE,
                                    title = "NextGen Mobile Office",
                                    subtitle = "Series A Investor Presentation • Q3 2026",
                                    notes = "Introduce company mission and high growth potential."
                                ),
                                SlideItem(
                                    layout = SlideLayout.BIG_STAT,
                                    title = "Rapid Market Adoption",
                                    statValue = "1.2M+",
                                    statLabel = "Active offline professionals using the native Android suite",
                                    notes = "Point out user retention and organic recommendation."
                                ),
                                SlideItem(
                                    layout = SlideLayout.TITLE_AND_CONTENT,
                                    title = "The Problem",
                                    content = "• Existing suites are heavy web wrappers with high battery drain\n• Sluggish touch input makes mobile formula editing painful\n• Mandatory cloud sync breaks when working offline on flights or transit",
                                    notes = "Address pain points felt by all mobile users."
                                ),
                                SlideItem(
                                    layout = SlideLayout.TWO_COLUMN,
                                    title = "Our Native Solution",
                                    content = "• Sub-10ms UI latency\n• 100% offline Room database\n• Native Android gesture controls",
                                    secondaryContent = "• Zero tracking or data extraction\n• Low battery consumption\n• Instant startup time",
                                    notes = "Highlight core value proposition."
                                )
                            )
                        )
                        d.serialize()
                    }
                )
            )
            else -> emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Create New",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )

                if (currentType == DocumentType.XLS) {
                    Button(
                        onClick = {
                            onDismiss()
                            onImportCsvClick()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.testTag("import_csv_sheet_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import CSV", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Document Type Tabs
            TabRow(
                selectedTabIndex = selectedTypeIndex,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = Color.White
            ) {
                Tab(
                    selected = selectedTypeIndex == 0,
                    onClick = { selectedTypeIndex = 0 },
                    text = { Text("Word Doc", color = Color.White) },
                    icon = { Icon(Icons.Default.Description, contentDescription = null, tint = Color.White) }
                )
                Tab(
                    selected = selectedTypeIndex == 1,
                    onClick = { selectedTypeIndex = 1 },
                    text = { Text("Spreadsheet", color = Color.White) },
                    icon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White) }
                )
                Tab(
                    selected = selectedTypeIndex == 2,
                    onClick = { selectedTypeIndex = 2 },
                    text = { Text("Presentation", color = Color.White) },
                    icon = { Icon(Icons.Default.Slideshow, contentDescription = null, tint = Color.White) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = customTitle,
                onValueChange = { customTitle = it },
                label = { Text("Document Title (Optional)") },
                placeholder = { Text(when (currentType) {
                    DocumentType.DOC -> "e.g., Weekly Project Report"
                    DocumentType.XLS -> "e.g., Q3 Financial Plan"
                    DocumentType.PPT -> "e.g., Keynote Presentation"
                    else -> "Document Name"
                }) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_doc_title_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose a Template:",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                templates.forEach { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable {
                                val finalTitle = if (customTitle.isNotBlank()) customTitle.trim() else template.title
                                onCreate(finalTitle, template.type, template.initialContent)
                                onDismiss()
                            }
                            .testTag("template_card_${template.title}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (template.type) {
                                        DocumentType.DOC -> Icons.Default.Description
                                        DocumentType.XLS -> Icons.Default.TableChart
                                        DocumentType.PPT -> Icons.Default.Slideshow
                                        DocumentType.PDF -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
