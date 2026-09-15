package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfficeDocument
import com.example.ui.HubTab
import com.example.ui.OfficeViewModel
import com.example.ui.SortOrder
import com.example.ui.common.DeleteConfirmDialog
import com.example.ui.common.DocumentCard
import com.example.ui.common.ExportDocumentDialog
import com.example.ui.common.ImportCsvDialog
import com.example.ui.common.RenameDocumentDialog
import com.example.ui.theme.DocBlue
import com.example.ui.theme.DocBlueLight
import com.example.ui.theme.SheetGreen
import com.example.ui.theme.SheetGreenLight
import com.example.ui.theme.SlideOrange
import com.example.ui.theme.SlideOrangeLight
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.WpsRed
import com.example.ui.theme.WpsRedLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: OfficeViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documentList.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val context = LocalContext.current

    var showNewDocSheet by remember { mutableStateOf(false) }
    var showImportCsvDialog by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<OfficeDocument?>(null) }
    var docToDelete by remember { mutableStateOf<OfficeDocument?>(null) }
    var docToExport by remember { mutableStateOf<OfficeDocument?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // SAF picker: accepts md, txt, csv, tsv, pdf, docx, xlsx, pptx and more
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Take a persistable grant so the file can be re-read later
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Provider may not offer persistable grants; import still works now
            }
            viewModel.importFile(it)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDocSheet = true },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("fab_create_doc")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create New Document",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // STREAMLINED TOP APP BAR WITH SEARCH & SORT
            var searchActive by remember { mutableStateOf(false) }

            Surface(
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            Text(
                                text = "PixDocx",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Open File From Device
                            IconButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.testTag("open_file_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Open file from device",
                                    tint = Color.White
                                )
                            }

                            // Search Toggle
                            IconButton(
                                onClick = { searchActive = !searchActive }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }

                            // Sort Menu Button
                            Box {
                                IconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.testTag("sort_menu_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort Documents",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Recent First") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.DATE_DESC)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Oldest First") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.DATE_ASC)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (A-Z)") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.NAME_ASC)
                                            sortMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Document Type") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.TYPE)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // COMPACT SEARCH BAR (Shown when active or has text)
                    if (searchActive || searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search files...", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_search_input")
                        )
                    }
                }
            }

            // MAIN SCROLLABLE BODY
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // CATEGORY FILTER TABS
                item {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedLabelColor = Color.White
                    )

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTab == HubTab.ALL,
                                onClick = { viewModel.selectTab(HubTab.ALL) },
                                label = { Text("All") },
                                colors = chipColors,
                                modifier = Modifier.testTag("tab_all")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedTab == HubTab.DOCS,
                                onClick = { viewModel.selectTab(HubTab.DOCS) },
                                label = { Text("Word") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                colors = chipColors,
                                modifier = Modifier.testTag("tab_docs")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedTab == HubTab.SHEETS,
                                onClick = { viewModel.selectTab(HubTab.SHEETS) },
                                label = { Text("Sheets") },
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                colors = chipColors,
                                modifier = Modifier.testTag("tab_sheets")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedTab == HubTab.SLIDES,
                                onClick = { viewModel.selectTab(HubTab.SLIDES) },
                                label = { Text("Slides") },
                                leadingIcon = { Icon(Icons.Default.Slideshow, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                colors = chipColors,
                                modifier = Modifier.testTag("tab_slides")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedTab == HubTab.PINNED,
                                onClick = { viewModel.selectTab(HubTab.PINNED) },
                                label = { Text("Pinned") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                colors = chipColors,
                                modifier = Modifier.testTag("tab_pinned")
                            )
                        }
                    }
                }

                // HEADER FOR DOCUMENTS
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedTab) {
                                HubTab.ALL -> "Documents"
                                HubTab.DOCS -> "Word Documents"
                                HubTab.SHEETS -> "Spreadsheets"
                                HubTab.SLIDES -> "Presentations"
                                HubTab.PINNED -> "Pinned"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "${documents.size} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // EMPTY STATE
                if (documents.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching documents" else "No documents found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Try a different search term" else "Tap + to create a new document or spreadsheet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // DOCUMENT ITEMS LIST
                // contentType gives the lazy layout one recycling pool per
                // document type, improving scroll performance on long lists.
                items(documents, key = { it.id }, contentType = { it.type }) { doc ->
                    DocumentCard(
                        document = doc,
                        onClick = { viewModel.openDocument(doc) },
                        onTogglePin = { viewModel.togglePin(doc) },
                        onRename = { docToRename = doc },
                        onDuplicate = { viewModel.duplicateDocument(doc) },
                        onDelete = { docToDelete = doc },
                        onExport = { docToExport = doc },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // DIALOGS & SHEETS
    if (showNewDocSheet) {
        NewDocumentSheet(
            onDismiss = { showNewDocSheet = false },
            onCreate = { title, type, content ->
                viewModel.createNewDocument(title, type, content)
            },
            onImportCsvClick = {
                showImportCsvDialog = true
            }
        )
    }

    if (showImportCsvDialog) {
        ImportCsvDialog(
            onDismiss = { showImportCsvDialog = false },
            onImport = { title, csvContent ->
                viewModel.importCsv(csvContent, title)
                showImportCsvDialog = false
            }
        )
    }

    docToRename?.let { doc ->
        RenameDocumentDialog(
            initialTitle = doc.title,
            onDismiss = { docToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameDocument(doc.id, newTitle)
                docToRename = null
            }
        )
    }

    docToDelete?.let { doc ->
        DeleteConfirmDialog(
            title = doc.title,
            onDismiss = { docToDelete = null },
            onConfirm = {
                viewModel.deleteDocument(doc)
                docToDelete = null
            }
        )
    }

    docToExport?.let { doc ->
        ExportDocumentDialog(
            document = doc,
            onDismiss = { docToExport = null }
        )
    }
}
