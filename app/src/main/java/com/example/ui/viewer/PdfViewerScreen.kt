package com.example.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.OfficeDocument
import com.example.ui.OfficeViewModel
import java.io.File

/**
 * Real PDF rendering with the platform [PdfRenderer] (no external dependency).
 * Pages are rasterized off the main thread into bitmaps and shown in a
 * vertically scrolling list, with the original page aspect ratio preserved.
 *
 * If the original PDF file is not available (e.g. imported before file
 * preservation was added), a friendly notice with the extracted-text notes
 * is shown instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    document: OfficeDocument,
    viewModel: OfficeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var pdfFile by remember(document.id) { mutableStateOf<File?>(null) }
    var pageCount by remember(document.id) { mutableIntStateOf(0) }
    var loadError by remember(document.id) { mutableStateOf<String?>(null) }

    // Locate the preserved PDF file
    LaunchedEffect(document.id) {
        val path = document.localFilePath
        if (path.isNotBlank()) {
            val f = File(path)
            if (f.exists() && f.length() > 0) pdfFile = f
        }
        if (pdfFile == null) {
            // Legacy fallback: check the imports dir for a file with a matching name
            val importsDir = File(context.filesDir, "imports")
            val candidate = importsDir.listFiles()
                ?.filter { it.name.endsWith(".pdf", ignoreCase = true) }
                ?.firstOrNull { it.nameWithoutExtension == document.title }
            if (candidate != null) pdfFile = candidate
        }
        if (pdfFile == null && path.isNotBlank()) {
            loadError = "The original PDF file is no longer available on this device."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        text = if (pageCount > 0) "PDF • $pageCount pages • ${document.sizeLabel}"
                        else "PDF • ${document.sizeLabel}",
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        val file = pdfFile
        when {
            file != null -> PdfPages(
                file = file,
                onLoaded = { pageCount = it },
                onError = { loadError = it }
            )

            loadError != null -> PdfUnavailable(
                message = loadError!!,
                notes = document.content
            )

            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PdfPages(
    file: File,
    onLoaded: (Int) -> Unit,
    onError: (String) -> Unit
) {
    val pageBits = remember(file.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(file.absolutePath) {
        try {
            val bits = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val result = ArrayList<Bitmap>(renderer.pageCount)
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                // Render at 2x for crisp text on high-DPI screens,
                                // capped to keep memory reasonable.
                                val scale = 2f
                                val w = (page.width * scale).toInt().coerceAtMost(2048)
                                val h = (page.height * scale).toInt().coerceAtMost(2048)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                result.add(bmp)
                            }
                        }
                        result
                    }
                }
            }
            pageBits.value = bits
            onLoaded(bits.size)
        } catch (e: Exception) {
            onError(e.message ?: "Could not render this PDF.")
        }
    }

    val bits = pageBits.value
    if (bits.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(bits) { index, bmp ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(6.dp))
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${index + 1} / ${bits.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PdfUnavailable(message: String, notes: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Extracted notes:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notes.take(600),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
