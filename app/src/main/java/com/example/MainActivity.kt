package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.writer.WordEditorScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
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
  if (activeDoc == null) {
    HomeScreen(viewModel = viewModel)
  } else {
    when (activeDoc.type) {
      DocumentType.DOC -> WordEditorScreen(document = activeDoc, viewModel = viewModel)
      DocumentType.XLS -> SpreadsheetEditorScreen(document = activeDoc, viewModel = viewModel)
      DocumentType.PPT -> PresentationEditorScreen(document = activeDoc, viewModel = viewModel)
      DocumentType.PDF -> WordEditorScreen(document = activeDoc, viewModel = viewModel)
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

