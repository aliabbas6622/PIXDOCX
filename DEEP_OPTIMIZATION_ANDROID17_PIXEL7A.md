# PixDocx - Deep Performance Optimization Guide
## Target: Android 17 (API 37) | Pixel 7a (Tensor G2)

This document provides **advanced, device-specific optimizations** for PixDocx running on Android 17 with Pixel 7a hardware. These optimizations leverage Tensor G2's architecture, Android 17's new APIs, and Jetpack Compose best practices for maximum performance.

---

## Table of Contents

1. [Tensor G2 Architecture Optimizations](#tensor-g2-architecture-optimizations)
2. [Android 17 API-Specific Enhancements](#android-17-api-specific-enhancements)
3. [Jetpack Compose Deep Optimization](#jetpack-compose-deep-optimization)
4. [Room Database Advanced Tuning](#room-database-advanced-tuning)
5. [Memory Management & GC Optimization](#memory-management--gc-optimization)
6. [Rendering Pipeline Optimization](#rendering-pipeline-optimization)
7. [Battery & Thermal Optimization](#battery--thermal-optimization)
8. [Startup Performance](#startup-performance)
9. [Network & I/O Optimization](#network--io-optimization)
10. [Profiling & Monitoring Setup](#profiling--monitoring-setup)

---

## Tensor G2 Architecture Optimizations

### 1.1 Leverage Tensor G2's Heterogeneous Computing

The Tensor G2 chip features:
- **2x Cortex-X720** (Prime cores @ 2.85 GHz)
- **2x Cortex-A710** (Performance cores @ 2.35 GHz)
- **4x Cortex-A510** (Efficiency cores @ 1.80 GHz)
- **Mali-G710 MP7** GPU
- **Titan M2** Security Coprocessor

```kotlin
// Use WorkManager constraints to schedule heavy tasks on appropriate cores
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .setRequiresBatteryNotLow(true)
    .setRequiresCharging(false) // Allow execution on battery for responsive UI
    .build()

// For spreadsheet formula calculation, use coroutines with appropriate dispatcher
fun calculateSpreadsheetFormulas(grid: SpreadsheetGrid) = viewModelScope.launch(Dispatchers.Default) {
    // Dispatchers.Default uses all available CPU cores efficiently
    // Tensor G2 will automatically schedule across prime + performance cores
    val results = grid.cells.mapValues { (_, cellData) ->
        if (cellData.rawValue.startsWith("=")) {
            grid.evaluateDisplayValue(it.key)
        } else {
            cellData.rawValue
        }
    }
    withContext(Dispatchers.Main) {
        updateUI(results)
    }
}
```

### 1.2 Neural Network Acceleration for Smart Features

Pixel 7a's Tensor Processing Unit (TPU) can accelerate ML operations:

```kotlin
// Add to build.gradle.kts
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}

// Example: Smart document categorization using on-device ML
class DocumentClassifier(private val context: Context) {
    private lateinit var interpreter: Interpreter
    
    fun initialize() {
        val options = Interpreter.Options()
            .setNumThreads(4) // Utilize Tensor G2's 8 cores efficiently
            .addDelegate(GpuDelegate()) // Offload to Mali-G710 GPU
        
        val model = FileUtil.loadMappedFile(context, "document_classifier.tflite")
        interpreter = Interpreter(model, options)
    }
    
    fun classifyDocument(content: String): DocumentType {
        // Preprocess content into tensor input
        val input = preprocessContent(content)
        val output = Array(1) { FloatArray(4) } // 4 document types
        
        interpreter.run(input, output)
        
        // Return highest probability class
        return when (output[0].indices.maxByOrNull { output[0][it] }) {
            0 -> DocumentType.DOC
            1 -> DocumentType.XLS
            2 -> DocumentType.PPT
            else -> DocumentType.PDF
        }
    }
}
```

### 1.3 Memory Bandwidth Optimization

Tensor G2 has LPDDR5 memory. Optimize access patterns:

```kotlin
// BAD: Random access pattern causes cache misses
fun processDocumentBad(docs: List<OfficeDocument>): List<String> {
    return docs.indices.map { i ->
        docs[docs.size - 1 - i].title // Reverse traversal = poor cache locality
    }
}

// GOOD: Sequential access maximizes cache hits
fun processDocumentGood(docs: List<OfficeDocument>): List<String> {
    return docs.map { it.title } // Sequential = optimal for LPDDR5
}

// Use primitive arrays for numerical computations (spreadsheet cells)
class OptimizedSpreadsheetGrid(
    private val values: DoubleArray, // Contiguous memory
    private val styles: IntArray,    // Packed style flags
    private val cols: Int,
    private val rows: Int
) {
    // Direct index calculation instead of HashMap lookup
    private fun getIndex(col: Int, row: Int): Int = col * rows + row
    
    fun getCell(col: Int, row: Int): Double = values[getIndex(col, row)]
    
    fun setCell(col: Int, row: Int, value: Double) {
        values[getIndex(col, row)] = value
    }
}
```

---

## Android 17 API-Specific Enhancements

### 2.1 Predictive Back Gesture Optimization

Android 17 enhances predictive back gesture with improved animations:

```kotlin
// In MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Enable predictive back gesture for smooth navigation
    WindowCompat.setDecorFitsSystemWindows(window, false)
    
    // Register for back gesture callbacks
    OnBackInvokedDispatcher.registerOnBackInvokedCallback(
        OnBackInvokedCallback {
            // Perform cleanup before navigation
            viewModel.saveCurrentDocumentNow()
            navigateBack()
        }
    )
}
```

### 2.2 Partial Screen Recording for Privacy

Android 17 introduces enhanced screen sharing controls:

```kotlin
// When implementing document export/sharing
val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

// Request partial screen capture excluding sensitive UI elements
val displayManager = getSystemService(DisplayManager::class.java)
val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

// Create presentation that excludes password fields
val presentation = Presentation(this, display)
presentation.setExcludeFromScreenCapture(true) // For sensitive editor views
```

### 2.3 Enhanced Graphics Interception

Leverage Android 17's improved GPU debugging for profiling:

```kotlin
// In build.gradle.kts
android {
    buildTypes {
        release {
            isProfileable = true // Enable Perfetto profiling in production
            enableAndroidTestCoverage = false
        }
    }
}

// Use GraphicsInterceptor for frame timing analysis
// Settings > Developer Options > Profile HWUI rendering > On screen as bars
```

### 2.4 Foreground Service Types for Document Sync

```kotlin
// Android 17 requires explicit foreground service type declaration
class DocumentSyncService : Service() {
    override fun onCreate() {
        super.onCreate()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Syncing Documents")
            .setSmallIcon(R.drawable.ic_sync)
            .build()
        
        // Declare specific foreground service type
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
```

Manifest declaration:
```xml
<service
    android:name=".DocumentSyncService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

---

## Jetpack Compose Deep Optimization

### 3.1 Stable Annotations & Recomposition Control

```kotlin
// Mark immutable data classes as @Stable to skip unnecessary recompositions
@Immutable
data class OfficeDocument(
    val id: Long,
    val title: String,
    val type: DocumentType,
    val content: String,
    val lastModified: Long,
    val isPinned: Boolean
)

@Stable
class EditorSaveState(
    var status: String,
    var lastSavedTime: Long
)

// Use derivedStateOf for expensive calculations
@Composable
fun DocumentList(documents: List<OfficeDocument>, searchQuery: String) {
    val filteredDocs by remember(documents, searchQuery) {
        derivedStateOf {
            documents.filter { 
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    LazyColumn {
        items(filteredDocs, key = { it.id }) { doc ->
            DocumentCard(document = doc) // Only recomposes when doc changes
        }
    }
}
```

### 3.2 Layout Optimization for Large Lists

```kotlin
// OPTIMIZED: Use contentType for LazyColumn item recycling
@Composable
fun DocumentList(
    documents: List<OfficeDocument>,
    onDocumentClick: (OfficeDocument) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = documents,
            key = { it.id },
            contentType = { it.type } // Enables separate recycling pools per type
        ) { doc ->
            when (doc.type) {
                DocumentType.DOC -> DocCard(doc, onDocumentClick)
                DocumentType.XLS -> SheetCard(doc, onDocumentClick)
                DocumentType.PPT -> SlideCard(doc, onDocumentClick)
                DocumentType.PDF -> PdfCard(doc, onDocumentClick)
            }
        }
    }
}

// Use rememberUpdatedState to avoid lambda recreation
@Composable
fun DocumentCard(
    document: OfficeDocument,
    onClick: (OfficeDocument) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { currentOnClick(document) }
    ) {
        // Card content
    }
}
```

### 3.3 Image & Icon Optimization for Pixel 7a Display

Pixel 7a has a 6.1" OLED display (1080x2400, 429 ppi):

```kotlin
// Use vector icons where possible (smaller than bitmaps)
@Composable
fun DocumentTypeIcon(type: DocumentType) {
    val icon = when (type) {
        DocumentType.DOC -> Icons.Default.Description
        DocumentType.XLS -> Icons.Default.TableChart
        DocumentType.PPT -> Icons.Default.Slideshow
        DocumentType.PDF -> Icons.Default.PictureAsPdf
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp), // Exact dp for Pixel 7a density
        tint = MaterialTheme.colorScheme.primary
    )
}

// For bitmap images, use Coil with memory caching
@Composable
fun DocumentThumbnail(imageUrl: String) {
    val imageLoader = LocalContext.current.imageLoader.newBuilder()
        .memoryCache {
            MemoryCache.Builder(LocalContext.current)
                .maxSizePercent(0.25) // 25% of available memory
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbnails"))
                .maxSizeBytes(50 * 1024 * 1024) // 50MB disk cache
                .build()
        }
        .build()
    
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .size(Size.ORIGINAL) // Load at original size for crisp display
            .build(),
        imageLoader = imageLoader,
        contentDescription = null,
        modifier = Modifier
            .size(120.dp, 80.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
```

### 3.4 Avoiding Common Compose Performance Pitfalls

```kotlin
// BAD: Creating objects in composable body triggers recomposition
@Composable
fun BadExample() {
    val colors = listOf(Color.Red, Color.Green, Color.Blue) // New list every recomposition
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) // New instance
    
    Text(formatter.format(Date()))
}

// GOOD: Hoist object creation outside composable
private val COLORS = listOf(Color.Red, Color.Green, Color.Blue)
private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

@Composable
fun GoodExample() {
    Text(dateFormatter.format(Date()))
}

// BAD: Unstable lambdas cause recomposition
@Composable
fun BadLambda(parent: ParentClass) {
    ChildComposable(onClick = { parent.doSomething() }) // Captures 'this'
}

// GOOD: Use method reference or hoisted lambda
@Composable
fun GoodLambda(parent: ParentClass) {
    ChildComposable(onClick = parent::doSomething)
}
```

---

## Room Database Advanced Tuning

### 4.1 Query Optimization with Indexes

```kotlin
@Entity(
    tableName = "office_documents",
    indices = [
        Index(value = ["type"], name = "idx_type"),
        Index(value = ["isPinned"], name = "idx_pinned"),
        Index(value = ["lastModified"], name = "idx_modified"),
        Index(value = ["category"], name = "idx_category"),
        Index(value = ["type", "isPinned"], name = "idx_type_pinned"),
        Index(value = ["title"], name = "idx_title")
    ]
)
data class OfficeDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: DocumentType,
    val content: String,
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "All",
    val wordCount: Int = 0,
    val sheetRows: Int = 0,
    val slideCount: Int = 0,
    val sizeLabel: String = "4 KB"
)

// Optimized queries using indexes
@Dao
interface OfficeDocumentDao {
    // Uses idx_type_pinned composite index
    @Query("""
        SELECT * FROM office_documents 
        WHERE type = :type AND isPinned = :pinned
        ORDER BY lastModified DESC
    """)
    fun getPinnedDocumentsByType(type: DocumentType, pinned: Boolean): Flow<List<OfficeDocument>>
    
    // Uses idx_title for LIKE search (prefix matching only)
    @Query("""
        SELECT * FROM office_documents 
        WHERE title LIKE :query || '%'
        ORDER BY lastModified DESC
        LIMIT 50
    """)
    fun searchDocumentsByTitlePrefix(query: String): Flow<List<OfficeDocument>>
    
    // Batch operations for better transaction performance
    @Transaction
    suspend fun bulkInsertDocuments(documents: List<OfficeDocument>) {
        documents.forEach { insertDocument(it) }
    }
    
    @Transaction
    suspend fun bulkUpdateLastModified(ids: List<Long>, timestamp: Long = System.currentTimeMillis()) {
        ids.forEach { id ->
            updateLastModified(id, timestamp)
        }
    }
    
    @Query("UPDATE office_documents SET lastModified = :timestamp WHERE id = :id")
    suspend fun updateLastModified(id: Long, timestamp: Long)
}
```

### 4.2 Pagination for Large Document Lists

```kotlin
// Implement Paging 3 with Room
@Dao
interface OfficeDocumentDao {
    @Query("""
        SELECT * FROM office_documents 
        ORDER BY isPinned DESC, lastModified DESC
    """)
    fun getAllDocumentsPaged(): PagingSource<Int, OfficeDocument>
    
    @Query("""
        SELECT * FROM office_documents 
        WHERE type = :type 
        ORDER BY isPinned DESC, lastModified DESC
    """)
    fun getDocumentsByTypePaged(type: DocumentType): PagingSource<Int, OfficeDocument>
}

// Repository with Pager configuration
class OfficeRepository(private val dao: OfficeDocumentDao) {
    fun getDocumentsFlow(category: String? = null): Flow<PagingData<OfficeDocument>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20, // Optimal for Pixel 7a's screen height
                prefetchDistance = 10,
                initialLoadSize = 40,
                maxSize = 100,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (category == null) {
                    dao.getAllDocumentsPaged()
                } else {
                    dao.getDocumentsByCategoryPaged(category)
                }
            }
        ).flow
    }
}
```

### 4.3 Database Connection Pooling

```kotlin
// Optimal Room database configuration for Pixel 7a
fun getDatabase(context: Context, scope: CoroutineScope): OfficeDatabase {
    return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
            context.applicationContext,
            OfficeDatabase::class.java,
            "pixdocx_database.db"
        )
        .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING) // WAL for concurrent reads
        .setMaxConnectionPoolSize(4) // Match Tensor G2's performance core count
        .setQueryExecutor(Executors.newFixedThreadPool(2)) // Dedicated query threads
        .setTransactionExecutor(Executors.newSingleThreadExecutor()) // Serialize writes
        .addCallback(OfficeDatabaseCallback(scope))
        .build()
        
        INSTANCE = instance
        instance
    }
}
```

---

## Memory Management & GC Optimization

### 5.1 Object Pooling for Frequent Allocations

```kotlin
// Object pool for spreadsheet cell calculations
object CellCalculationPool {
    private val pool = ArrayDeque<CellCalculationResult>(64)
    private const val MAX_POOL_SIZE = 64
    
    fun obtain(): CellCalculationResult {
        return if (pool.isNotEmpty()) {
            pool.removeLast().apply { reset() }
        } else {
            CellCalculationResult()
        }
    }
    
    fun recycle(result: CellCalculationResult) {
        if (pool.size < MAX_POOL_SIZE) {
            result.reset()
            pool.addLast(result)
        }
    }
}

data class CellCalculationResult(
    var value: Double = 0.0,
    var isError: Boolean = false,
    var errorMessage: String = ""
) {
    fun reset() {
        value = 0.0
        isError = false
        errorMessage = ""
    }
}

// Usage in formula evaluation
fun evaluateFormula(formula: String): CellCalculationResult {
    val result = CellCalculationPool.obtain()
    try {
        // ... calculation logic
        result.value = calculatedValue
        return result
    } catch (e: Exception) {
        result.isError = true
        result.errorMessage = e.message
        return result
    } finally {
        // Note: Caller must recycle after using the result
        // Or use try-finally pattern
    }
}
```

### 5.2 Bitmap Memory Optimization

```kotlin
// Efficient bitmap loading for document thumbnails
fun loadOptimizedBitmap(context: Context, imagePath: String, targetSize: Int): Bitmap {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inPreferredConfig = Bitmap.Config.RGB_565 // Half memory vs ARGB_8888
    }
    
    BitmapFactory.decodeFile(imagePath, options)
    
    // Calculate inSampleSize for downscaling
    val (height, width) = options.outHeight to options.outWidth
    var sampleSize = 1
    
    while (height / sampleSize > targetSize * 2 && width / sampleSize > targetSize * 2) {
        sampleSize *= 2
    }
    
    options.inSampleSize = sampleSize
    options.inJustDecodeBounds = false
    options.inMutable = false
    
    return BitmapFactory.decodeFile(imagePath, options)
        ?: Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.RGB_565)
}

// Use ImageDecoder for Android 10+ (better performance than BitmapFactory)
@RequiresApi(Build.VERSION_CODES.Q)
fun loadBitmapWithDecoder(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    
    return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        decoder.setTargetDensity(context.resources.displayMetrics.densityDpi)
    }
}
```

### 5.3 Leak Prevention with Lifecycle Awareness

```kotlin
// Use LifecycleAware components to prevent leaks
class DocumentEditorViewModel(application: Application) : AndroidViewModel(application) {
    
    // BAD: Static reference can cause memory leak
    // companion object {
    //     private var instance: DocumentEditorViewModel? = null
    // }
    
    // GOOD: Use WeakReference if singleton pattern needed
    private val _activeDocuments = mutableMapOf<Long, OfficeDocument>()
    private val autoSaveJob = MutableStateFlow<Job?>(null)
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup all resources
        autoSaveJob.value?.cancel()
        _activeDocuments.clear()
    }
    
    // Use Flow with lifecycle-aware collection in UI
    fun observeDocumentChanges(documentId: Long): Flow<OfficeDocument?> {
        return callbackFlow {
            val listener = DocumentChangeListener { doc ->
                trySend(doc)
            }
            
            repository.addListener(documentId, listener)
            
            awaitClose {
                repository.removeListener(documentId, listener)
            }
        }.flowOn(Dispatchers.IO)
    }
}
```

---

## Rendering Pipeline Optimization

### 6.1 Frame Timing Analysis for 120Hz Display

Pixel 7a supports up to 90Hz refresh rate. Optimize for smooth scrolling:

```kotlin
// Enable frame timing metrics
class FrameMetricsObserver(private val activity: Activity) {
    private var frameMetricsAvailable = false
    
    @RequiresApi(Build.VERSION_CODES.N)
    fun startMonitoring() {
        val window = activity.window
        val frameMetricsCallback = SurfaceControl.FrameMetricsCallback { _, data ->
            val frameDurationNanos = data.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
            
            if (frameDurationNanos > 16_666_666) { // > 60 FPS threshold
                Log.w("FrameMetrics", "Jank detected: ${frameDurationNanos / 1_000_000}ms")
            }
        }
        
        window.addFrameMetricsAvailableListener(
            frameMetricsCallback,
            Handler(Looper.getMainLooper())
        )
        frameMetricsAvailable = true
    }
}

// Optimize LazyColumn for 90Hz scrolling
@Composable
fun OptimizedDocumentList(documents: List<OfficeDocument>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = documents,
            key = { it.id },
            contentType = { it.type }
        ) { doc ->
            // Use remember to avoid recreating modifier chain
            val cardModifier = remember {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
            }
            
            DocumentCard(
                document = doc,
                modifier = cardModifier
            )
        }
    }
}
```

### 6.2 Hardware Acceleration for Animations

```kotlin
// Use graphics layers for complex animations
@Composable
fun AnimatedDocumentCard(document: OfficeDocument) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Enable hardware acceleration
                alpha = if (isExpanded) 1f else 0.8f
                scaleX = if (isExpanded) 1.02f else 1f
                scaleY = if (isExpanded) 1.02f else 1f
                shadowElevation = if (isExpanded) 16.dp.toPx() else 4.dp.toPx()
                shape = RoundedCornerShape(if (isExpanded) 16.dp else 12.dp)
                clip = true
                cameraDistance = 8 * density // For 3D effects
            }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .clickable { isExpanded = !isExpanded }
    ) {
        // Card content with smooth expansion animation
    }
}

// Use RenderEffect for blur effects (Android 12+)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BlurredBackground(content: @Composable () -> Unit) {
    val renderEffect = remember {
        RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.renderEffect = renderEffect
            }
    ) {
        content()
    }
}
```

### 6.3 Compose Compiler Metrics

Enable compiler metrics to identify unstable types:

```kotlin
// In build.gradle.kts
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.buildDir}/compose-metrics"
        )
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${project.rootDir}/compose_stability_config.conf"
        )
    }
}
```

Create `compose_stability_config.conf`:
```
com.example.data.model.OfficeDocument
com.example.data.model.DocumentType
com.example.ui.EditorSaveState
```

---

## Battery & Thermal Optimization

### 7.1 Adaptive Refresh Rate Management

```kotlin
// Detect and adapt to Pixel 7a's adaptive refresh rate
class DisplayOptimizer(private val context: Context) {
    
    @RequiresApi(Build.VERSION_CODES.R)
    fun getCurrentRefreshRate(): Float {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        return display.refreshRate
    }
    
    // Reduce update frequency when on battery
    fun getOptimalUpdateInterval(): Long {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val isCharging = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) 
            == BatteryManager.BATTERY_STATUS_CHARGING
        
        val refreshRate = getCurrentRefreshRate()
        
        return when {
            isCharging -> 16 // ~60 FPS updates
            refreshRate > 60 -> 33 // ~30 FPS on high refresh to save battery
            else -> 50 // ~20 FPS on standard display
        }
    }
}
```

### 7.2 WorkManager for Background Sync

```kotlin
// Schedule document sync during optimal conditions
fun scheduleDocumentSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(false)
        .setRequiresDeviceIdle(false)
        .build()
    
    val syncWork = OneTimeWorkRequestBuilder<DocumentSyncWorker>()
        .setConstraints(constraints)
        .setInitialDelay(1, TimeUnit.MINUTES)
        .addTag("document_sync")
        .build()
    
    WorkManager.getInstance(context).enqueue(syncWork)
}

// Worker implementation with exponential backoff
class DocumentSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val repository = OfficeRepository(getDatabase(applicationContext, scope).officeDocumentDao())
            
            withContext(Dispatchers.IO) {
                repository.syncPendingChanges()
            }
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= 3) {
                Result.failure()
            } else {
                Result.retry() // Exponential backoff built-in
            }
        }
    }
}
```

### 7.3 Thermal Throttling Detection

```kotlin
// Monitor thermal status and reduce workload
@RequiresApi(Build.VERSION_CODES.Q)
class ThermalMonitor(private val context: Context) {
    
    private val thermalManager = context.getSystemService(ThermalService::class.java)
    
    fun getThermalStatus(): Int {
        return thermalManager?.currentThermalStatus ?: THERMAL_STATUS_NONE
    }
    
    fun shouldReduceWorkload(): Boolean {
        val status = getThermalStatus()
        return status >= THERMAL_STATUS_LIGHT || status >= THERMAL_STATUS_MODERATE
    }
    
    fun getRecommendedAction(): ThermalAction {
        return when (getThermalStatus()) {
            THERMAL_STATUS_NONE -> ThermalAction.NORMAL
            THERMAL_STATUS_LIGHT -> ThermalAction.REDUCE_ANIMATIONS
            THERMAL_STATUS_MODERATE -> ThermalAction.LIMIT_BACKGROUND
            THERMAL_STATUS_SEVERE -> ThermalAction.PAUSE_NON_ESSENTIAL
            THERMAL_STATUS_CRITICAL -> ThermalAction.EMERGENCY_COOLDOWN
            else -> ThermalAction.NORMAL
        }
    }
    
    enum class ThermalAction {
        NORMAL,
        REDUCE_ANIMATIONS,
        LIMIT_BACKGROUND,
        PAUSE_NON_ESSENTIAL,
        EMERGENCY_COOLDOWN
    }
}

// Usage in ViewModel
@Composable
fun SpreadsheetEditor(viewModel: OfficeViewModel) {
    val thermalAction = remember { ThermalMonitor(LocalContext.current).getRecommendedAction() }
    
    val calculationDelay = when (thermalAction) {
        ThermalAction.NORMAL -> 0L
        ThermalAction.REDUCE_ANIMATIONS -> 100L
        ThermalAction.LIMIT_BACKGROUND -> 500L
        else -> 1000L
    }
    
    // Adjust formula recalculation frequency based on thermal status
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(calculationDelay)
            viewModel.recalculateVisibleFormulas()
        }
    }
}
```

---

## Startup Performance

### 8.1 App Startup Library Integration

```kotlin
// Add to build.gradle.kts
dependencies {
    implementation("androidx.startup:startup-runtime:1.1.1")
}

// Initialize only essential components at startup
class PixDocxInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Essential initialization only
        initializeCrashReporting(context)
        setupDependencyInjection(context)
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

Manifest registration:
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false">
    <meta-data
        android:name="com.example.PixDocxInitializer"
        android:value="androidx.startup" />
</provider>
```

### 8.2 Splash Screen Optimization

```kotlin
// Android 12+ splash screen with brand animation
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        val splashScreen = installSplashScreen()
        
        // Keep splash screen until data is loaded
        var uiState: UiState by mutableStateOf(UiState.Loading)
        
        splashScreen.setKeepOnScreenCondition {
            uiState == UiState.Loading
        }
        
        super.onCreate(savedInstanceState)
        
        // Load initial data
        lifecycleScope.launch {
            loadData()
            uiState = UiState.Ready
        }
        
        setContent {
            PixDocxTheme {
                // App content
            }
        }
    }
}
```

### 8.3 Class Loading Optimization

```kotlin
// Use R8/ProGuard optimization rules
// In proguard-rules.pro:

# Keep data classes for serialization
-keep class com.example.data.model.** { *; }

# Optimize enum usage
-assumenosideeffects class kotlin.enums.EnumEntriesKt {
    public static ** getEntries(java.lang.Class);
}

# Inline small methods
-assumevalues class com.example.ui.theme.ColorsKt {
    public static *** accesses$lambda*(...);
}
```

---

## Network & I/O Optimization

### 9.1 OkHttp Connection Pooling

```kotlin
// Optimized OkHttpClient for document sync
val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectionPool(ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        ))
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .cache(Cache(
            directory = File(context.cacheDir, "http_cache"),
            maxSize = 10 * 1024 * 1024 // 10MB
        ))
        .build()
}
```

### 9.2 Efficient File I/O with NIO

```kotlin
// Use NIO for large document file operations
suspend fun saveDocumentToFile(document: OfficeDocument, file: File) {
    withContext(Dispatchers.IO) {
        file.outputStream().use { outputStream ->
            Channels.newChannel(outputStream).use { channel ->
                val buffer = ByteBuffer.allocate(8192) // 8KB buffer
                
                val content = document.toJson()
                val bytes = content.toByteArray(Charsets.UTF_8)
                
                buffer.put(bytes)
                buffer.flip()
                
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
            }
        }
    }
}

// Memory-mapped files for large spreadsheets
suspend fun loadLargeSpreadsheet(file: File): SpreadsheetGrid {
    return withContext(Dispatchers.IO) {
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
            
            val charset = Charsets.UTF_8
            val content = charset.decode(buffer).toString()
            
            SpreadsheetGrid.deserialize(content)
        }
    }
}
```

---

## Profiling & Monitoring Setup

### 10.1 Perfetto Configuration

Create `perfetto_config.pb` for detailed tracing:
```protobuf
buffers {
  size_kb: 8192
  fill_policy: DISCARD
}
buffers {
  size_kb: 4096
  fill_policy: DISCARD
}

data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      ftrace_events: "sched/sched_switch"
      ftrace_events: "gpu_scheduler"
    }
  }
}

data_sources {
  config {
    name: "android.surfaceflinger"
  }
}

data_sources {
  config {
    name: "android.view"
  }
}

duration_ms: 30000
```

Record with:
```bash
adb shell perfetto -c /path/to/perfetto_config.pb -o /data/misc/perfetto-traces/trace.pb
```

### 10.2 Custom Performance Metrics

```kotlin
// Track key performance indicators
object PerformanceMetrics {
    private val metrics = mutableMapOf<String, MutableList<Long>>()
    
    fun record(metricName: String, durationMs: Long) {
        metrics.getOrPut(metricName) { mutableListOf() }.add(durationMs)
        
        // Keep only last 100 measurements
        if (metrics[metricName]?.size ?: 0 > 100) {
            metrics[metricName]?.removeAt(0)
        }
    }
    
    fun getAverage(metricName: String): Double {
        return metrics[metricName]?.average() ?: 0.0
    }
    
    fun get95thPercentile(metricName: String): Double {
        val sorted = metrics[metricName]?.sorted() ?: return 0.0
        val index = (sorted.size * 0.95).toInt()
        return sorted.getOrElse(index) { sorted.lastOrNull() ?: 0.0 }
    }
    
    // Key metrics to track
    object Keys {
        const val DOCUMENT_LOAD_TIME = "document_load_time_ms"
        const val FORMULA_CALCULATION_TIME = "formula_calc_time_ms"
        const val LIST_SCROLL_FRAME_TIME = "scroll_frame_time_ms"
        const val DATABASE_QUERY_TIME = "db_query_time_ms"
        const val SAVE_OPERATION_TIME = "save_operation_time_ms"
    }
}

// Usage example
fun loadDocument(id: Long) {
    val startTime = System.currentTimeMillis()
    
    val document = repository.getDocumentById(id)
    
    val duration = System.currentTimeMillis() - startTime
    PerformanceMetrics.record(PerformanceMetrics.Keys.DOCUMENT_LOAD_TIME, duration)
    
    // Update UI
}
```

### 10.3 Firebase Performance Monitoring

```kotlin
// Add to build.gradle.kts
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-perf-ktx")
}

// Trace custom operations
suspend fun performDatabaseOperation() {
    val trace = Firebase.performance.newTrace("database_bulk_insert")
    trace.start()
    
    try {
        // Database operation
        repository.bulkInsertDocuments(documents)
        
        // Add custom metric
        trace.putMetric("documents_count", documents.size.toLong())
        
    } catch (e: Exception) {
        trace.putAttribute("error", e.message ?: "unknown")
        throw e
    } finally {
        trace.stop()
    }
}

// Trace HTTP requests automatically
// OkHttp interceptor included in Firebase Perf SDK
```

---

## Summary Checklist

### Pre-Launch Optimization Review

- [ ] Enable R8 full mode with optimized ProGuard rules
- [ ] Configure App Bundle for dynamic delivery
- [ ] Set up Firebase Performance Monitoring
- [ ] Test on Pixel 7a with thermal throttling scenarios
- [ ] Verify 90Hz smooth scrolling on document lists
- [ ] Measure cold start time (< 2 seconds target)
- [ ] Profile memory usage during large spreadsheet operations
- [ ] Test background sync with Doze mode
- [ ] Validate offline-first functionality
- [ ] Run LeakCanary analysis

### Continuous Monitoring

- [ ] Set up Crashlytics alerts for ANR detection
- [ ] Monitor Play Console Vitals for battery usage
- [ ] Track 95th percentile frame times
- [ ] Review weekly performance regression reports
- [ ] A/B test optimization experiments

---

## References

- [Android Performance Patterns](https://developer.android.com/topic/performance)
- [Jetpack Compose Performance](https://developer.android.com/jetpack/compose/performance)
- [Tensor G2 Architecture Whitepaper](https://blog.google/products/pixel/google-pixel-7-pro-processor/)
- [Pixel 7a Technical Specifications](https://store.google.com/product/pixel_7a_tech_specs)
- [Room Database Best Practices](https://developer.android.com/training/data-storage/room)
- [Perfetto Tracing Documentation](https://perfetto.dev/docs/)

---

*Last Updated: December 2024*  
*Target Platform: Android 17 (API 37)*  
*Device Focus: Google Pixel 7a*
