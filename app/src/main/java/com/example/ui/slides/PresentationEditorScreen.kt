package com.example.ui.slides

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfficeDocument
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.ui.OfficeViewModel
import com.example.ui.common.ExportDocumentDialog
import com.example.ui.common.RenameDocumentDialog
import com.example.ui.theme.SheetGreen
import com.example.ui.theme.SlideOrange
import com.example.ui.theme.SlideOrangeLight
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.WpsRed
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationEditorScreen(
    document: OfficeDocument,
    viewModel: OfficeViewModel,
    modifier: Modifier = Modifier
) {
    val saveState by viewModel.saveState.collectAsState()
    val isPresenting by viewModel.isPresenting.collectAsState()

    val deck = remember(document.id) {
        SlideDeck.deserialize(document.content)
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddLayoutMenu by remember { mutableStateOf(false) }
    var showNotesDrawer by remember { mutableStateOf(false) }

    fun saveDeck() {
        val serialized = deck.serialize()
        viewModel.updateDocumentContent(serialized)
        refreshKey++
    }

    val currentSlide = deck.slides.getOrNull(selectedIndex) ?: deck.slides.firstOrNull()

    // FULLSCREEN PRESENTATION MODE
    if (isPresenting) {
        PresentationModeView(
            deck = deck,
            initialIndex = selectedIndex,
            onExit = { viewModel.togglePresentationMode(false) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate100)
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
                            text = "• ${deck.slides.size} slides",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.closeDocument() },
                    modifier = Modifier.testTag("ppt_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // PRESENT BUTTON
                Button(
                    onClick = { viewModel.togglePresentationMode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 6.dp).testTag("play_slideshow_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Present", color = Color.White)
                }

                IconButton(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.testTag("ppt_share_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // MAIN SLIDE PREVIEW & EDITOR CANVAS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentSlide != null) {
                    // 16:9 SLIDE CANVAS
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .shadow(1.dp, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .testTag("slide_canvas_${currentSlide.id}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = parseColor(deck.themeColorHex)
                        )
                    ) {
                        SlideContentPreview(
                            slide = currentSlide,
                            accentColor = parseColor(deck.accentColorHex)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SLIDE ELEMENT EDITORS
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Slide ${selectedIndex + 1} Content (${currentSlide.layout.name})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Row {
                                    // Duplicate Slide
                                    IconButton(
                                        onClick = {
                                            val clone = currentSlide.copy(id = java.util.UUID.randomUUID().toString())
                                            deck.slides.add(selectedIndex + 1, clone)
                                            selectedIndex++
                                            saveDeck()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate slide", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // Delete Slide (if > 1)
                                    if (deck.slides.size > 1) {
                                        IconButton(
                                            onClick = {
                                                deck.slides.removeAt(selectedIndex)
                                                if (selectedIndex >= deck.slides.size) selectedIndex = deck.slides.size - 1
                                                saveDeck()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete slide", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Slide Title
                            OutlinedTextField(
                                value = currentSlide.title,
                                onValueChange = {
                                    deck.slides[selectedIndex] = currentSlide.copy(title = it)
                                    saveDeck()
                                },
                                label = { Text("Slide Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("slide_title_input")
                            )

                            when (currentSlide.layout) {
                                SlideLayout.TITLE_SLIDE -> {
                                    OutlinedTextField(
                                        value = currentSlide.subtitle,
                                        onValueChange = {
                                            deck.slides[selectedIndex] = currentSlide.copy(subtitle = it)
                                            saveDeck()
                                        },
                                        label = { Text("Subtitle / Presenter Name") },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth().testTag("slide_subtitle_input")
                                    )
                                }
                                SlideLayout.BIG_STAT -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = currentSlide.statValue,
                                            onValueChange = {
                                                deck.slides[selectedIndex] = currentSlide.copy(statValue = it)
                                                saveDeck()
                                            },
                                            label = { Text("Stat (e.g. 98%)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("slide_stat_val")
                                        )
                                        OutlinedTextField(
                                            value = currentSlide.statLabel,
                                            onValueChange = {
                                                deck.slides[selectedIndex] = currentSlide.copy(statLabel = it)
                                                saveDeck()
                                            },
                                            label = { Text("Stat Description") },
                                            singleLine = true,
                                            modifier = Modifier.weight(2f).testTag("slide_stat_lbl")
                                        )
                                    }
                                }
                                SlideLayout.TWO_COLUMN -> {
                                    OutlinedTextField(
                                        value = currentSlide.content,
                                        onValueChange = {
                                            deck.slides[selectedIndex] = currentSlide.copy(content = it)
                                            saveDeck()
                                        },
                                        label = { Text("Left Column Points") },
                                        minLines = 3,
                                        modifier = Modifier.fillMaxWidth().testTag("slide_col1")
                                    )
                                    OutlinedTextField(
                                        value = currentSlide.secondaryContent,
                                        onValueChange = {
                                            deck.slides[selectedIndex] = currentSlide.copy(secondaryContent = it)
                                            saveDeck()
                                        },
                                        label = { Text("Right Column Points") },
                                        minLines = 3,
                                        modifier = Modifier.fillMaxWidth().testTag("slide_col2")
                                    )
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = currentSlide.content,
                                        onValueChange = {
                                            deck.slides[selectedIndex] = currentSlide.copy(content = it)
                                            saveDeck()
                                        },
                                        label = { Text("Slide Points & Content") },
                                        minLines = 4,
                                        modifier = Modifier.fillMaxWidth().testTag("slide_content_input")
                                    )
                                }
                            }

                            // Presenter Notes toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showNotesDrawer = !showNotesDrawer }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showNotesDrawer) "Hide Presenter Notes" else "Edit Presenter Notes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            AnimatedVisibility(visible = showNotesDrawer) {
                                OutlinedTextField(
                                    value = currentSlide.notes,
                                    onValueChange = {
                                        deck.slides[selectedIndex] = currentSlide.copy(notes = it)
                                        saveDeck()
                                    },
                                    label = { Text("Speaker Notes (Visible during Presentation)") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("slide_notes_input")
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM SLIDE THUMBNAIL CAROUSEL
        Surface(
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outline).navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Slides (${deck.slides.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Add Slide Button with Layout Menu
                    Box {
                        TextButton(
                            onClick = { showAddLayoutMenu = true },
                            modifier = Modifier.testTag("add_slide_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add Slide", color = Color.White)
                        }

                        DropdownMenu(
                            expanded = showAddLayoutMenu,
                            onDismissRequest = { showAddLayoutMenu = false }
                        ) {
                            SlideLayout.values().forEach { layout ->
                                DropdownMenuItem(
                                    text = { Text(layout.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }) },
                                    onClick = {
                                        showAddLayoutMenu = false
                                        val newSlide = when (layout) {
                                            SlideLayout.TITLE_SLIDE -> SlideItem(layout = layout, title = "New Section", subtitle = "Presenter details")
                                            SlideLayout.BIG_STAT -> SlideItem(layout = layout, title = "Key Metric", statValue = "95%", statLabel = "Performance Benchmark")
                                            SlideLayout.TWO_COLUMN -> SlideItem(layout = layout, title = "Comparison", content = "• Feature A\n• Feature B", secondaryContent = "• Alternative 1\n• Alternative 2")
                                            SlideLayout.QUOTE -> SlideItem(layout = layout, title = "Testimonial", content = "\"This native office engine feels instantly responsive.\"", subtitle = "Enterprise Customer")
                                            else -> SlideItem(layout = layout, title = "Slide Title", content = "• Bullet point 1\n• Bullet point 2\n• Bullet point 3")
                                        }
                                        deck.slides.add(newSlide)
                                        selectedIndex = deck.slides.lastIndex
                                        saveDeck()
                                    }
                                )
                            }
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(deck.slides) { idx, slide ->
                        val isSelected = idx == selectedIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedIndex = idx }
                                .testTag("slide_thumb_$idx")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(parseColor(deck.themeColorHex))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = slide.title.take(12),
                                    fontSize = 7.sp,
                                    color = Color.White,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${idx + 1}",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
            document = document,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun SlideContentPreview(
    slide: SlideItem,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (slide.layout) {
            SlideLayout.TITLE_SLIDE -> {
                Text(
                    text = slide.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                )
                if (slide.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = slide.subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }

            SlideLayout.BIG_STAT -> {
                Text(
                    text = slide.title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = slide.statValue,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = slide.statLabel,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }

            SlideLayout.TWO_COLUMN -> {
                Text(
                    text = slide.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = slide.content,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = slide.secondaryContent,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            else -> {
                Text(
                    text = slide.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = slide.content,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * FULLSCREEN SLIDE SHOW PRESENTATION MODE
 * Includes slide counter, elapsed presentation timer, touch navigation, and virtual laser pointer!
 */
@Composable
private fun PresentationModeView(
    deck: SlideDeck,
    initialIndex: Int,
    onExit: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var laserPos by remember { mutableStateOf<Offset?>(null) }
    var showNotesOverlay by remember { mutableStateOf(false) }

    // Timer loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val currentSlide = deck.slides.getOrNull(currentIndex) ?: deck.slides.first()

    val formattedTime = remember(elapsedSeconds) {
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", min, sec)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Tap left third = prev, tap right two thirds = next
                        if (offset.x < size.width * 0.33f) {
                            if (currentIndex > 0) currentIndex--
                        } else {
                            if (currentIndex < deck.slides.size - 1) currentIndex++
                        }
                    },
                    onLongPress = { offset ->
                        laserPos = offset
                    }
                )
            }
    ) {
        // PRESENTATION SLIDE DISPLAY
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = parseColor(deck.themeColorHex))
        ) {
            SlideContentPreview(
                slide = currentSlide,
                accentColor = parseColor(deck.accentColorHex)
            )
        }

        // VIRTUAL LASER POINTER
        laserPos?.let { pos ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.Red, CircleShape)
                    .align(Alignment.TopStart)
            )
        }

        // TOP PRESENTATION CONTROLS (OVERLAY)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Slide ${currentIndex + 1} / ${deck.slides.size}",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Text(
                    text = "• $formattedTime",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (currentSlide.notes.isNotBlank()) {
                    IconButton(
                        onClick = { showNotesOverlay = !showNotesOverlay },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(Icons.Default.Notes, contentDescription = "Toggle Notes", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = onExit,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape).testTag("exit_presentation_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Presentation", tint = Color.White)
                }
            }
        }

        // SPEAKER NOTES OVERLAY
        AnimatedVisibility(
            visible = showNotesOverlay,
            enter = fadeIn(tween(120)) + slideInVertically(tween(150)) { it / 3 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SPEAKER NOTES:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSlide.notes,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // BOTTOM TAP GUIDE
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Tap left for previous • Tap right for next",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF0F172A)
    }
}
