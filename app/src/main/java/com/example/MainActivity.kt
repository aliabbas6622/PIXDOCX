package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.DocumentType
import com.example.ui.OfficeViewModel
import com.example.ui.calc.SpreadsheetEditorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.slides.PresentationEditorScreen
import com.example.ui.theme.PixDocxTheme
import com.example.ui.viewer.DocViewerScreen
import com.example.ui.viewer.PdfViewerScreen
import com.example.ui.writer.WordEditorScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PixDocxTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          OfficeApp()
        }
      }
    }
  }
}

@Composable
fun OfficeApp(viewModel: OfficeViewModel = viewModel()) {
  val currentDoc by viewModel.currentDocument.collectAsState()
  val isPresenting by viewModel.isPresenting.collectAsState()

  // Handle system back gesture
  BackHandler(enabled = currentDoc != null) {
    if (isPresenting) {
      viewModel.togglePresentationMode(false)
    } else {
      viewModel.closeDocument()
    }
  }

  val activeDoc = currentDoc
  val viewMode by viewModel.viewMode.collectAsState()
  if (activeDoc == null) {
    HomeScreen(viewModel = viewModel)
  } else {
    when (activeDoc.type) {
      DocumentType.DOC -> if (viewMode) {
        DocViewerScreen(document = activeDoc, viewModel = viewModel)
      } else {
        WordEditorScreen(document = activeDoc, viewModel = viewModel)
      }
      DocumentType.XLS -> SpreadsheetEditorScreen(document = activeDoc, viewModel = viewModel)
      DocumentType.PPT -> PresentationEditorScreen(document = activeDoc, viewModel = viewModel)
      // Real PDF rendering with PdfRenderer instead of dumping text into the
      // word editor (which made PDFs look broken).
      DocumentType.PDF -> PdfViewerScreen(document = activeDoc, viewModel = viewModel)
    }
  }
}

