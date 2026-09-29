package converter.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import converter.android.ocr.OcrEngine
import converter.android.ocr.PaddleOnnxEngine
import converter.android.ocr.StillImages
import converter.android.ocr.UnwiredEngine
import converter.android.rates.RatesRepository
import converter.android.rates.CurrencyStore
import converter.android.share.Exports
import converter.android.ui.CalculatorScreen
import converter.android.ui.CameraScreen
import converter.android.ui.HomeScreen
import converter.android.ui.Origin
import converter.android.ui.renderConversions
import converter.android.ui.CurrencyPicker
import converter.android.ui.GalleryScreen
import converter.android.ui.embeddedPickerAvailable
import converter.android.ui.Still
import converter.android.ui.StillScreen
import converter.core.LocatedPrice
import converter.core.ProblemReport
import converter.core.RateTable
import converter.core.problemReportText
import converter.core.PriceContext
import converter.core.RatesOutcome
import converter.core.locatePrices
import converter.core.resolveCurrencies
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    /**
     * A picture another app shared with this one, waiting to be opened.
     *
     * Only taken from the intent that started the activity, not from a saved
     * state, so coming back to the app does not open the same picture again.
     */
    private var shared by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) shared = sharedImage(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color.Black) {
                    val context = LocalContext.current
                    var engine by remember { mutableStateOf<OcrEngine>(UnwiredEngine) }
                    // Whether loading the models has finished, either way.
                    var engineSettled by remember { mutableStateOf(false) }
                    var rates by remember { mutableStateOf<RateTable?>(null) }
                    val currencies = remember { CurrencyStore(context) }
                    val detectedLocal = remember { currencies.detectLocal() }
                    val detectedHome = remember { currencies.detectHome() }
                    var chosenLocal by remember { mutableStateOf(currencies.local) }
                    var chosenHome by remember { mutableStateOf(currencies.home) }
                    var picking by remember { mutableStateOf<Picking?>(null) }
                    var still by remember { mutableStateOf<Still?>(null) }
                    var receipt by rememberSaveable { mutableStateOf(false) }
                    var browsing by remember { mutableStateOf(false) }
                    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                    val scope = rememberCoroutineScope()

                    // What the prices are in, and what to turn them into. Not
                    // the same question: abroad, the first is the country's and
                    // the second is the reader's.
                    // Never one currency twice: see resolveCurrencies.
                    val (source, target) = resolveCurrencies(
                        chosenSource = chosenLocal,
                        detectedSource = detectedLocal,
                        chosenTarget = chosenHome,
                        detectedTarget = detectedHome,
                    )

                    // Reading two ONNX models out of assets costs a second or
                    // more; nothing waits for it but a picture to read, and
                    // that waits in read() below.
                    LaunchedEffect(Unit) {
                        engine = withContext(Dispatchers.IO) {
                            runCatching { PaddleOnnxEngine.create(context) }
                                .onFailure { Log.e(TAG, "could not load the OCR models", it) }
                                .getOrDefault(UnwiredEngine)
                        }
                        engineSettled = true
                    }

                    // Rates load in parallel. Prices are shown as read until
                    // they arrive, rather than held back behind the network.
                    LaunchedEffect(Unit) {
                        rates = withContext(Dispatchers.IO) {
                            when (val outcome = RatesRepository(context).load()) {
                                is RatesOutcome.Fresh -> outcome.table
                                is RatesOutcome.Stale -> outcome.table
                                is RatesOutcome.Unavailable -> {
                                    Log.w(TAG, "no rates: ${outcome.reason}")
                                    null
                                }
                            }
                        }
                    }

                    // Reading a still photograph, in one place, so the shutter
                    // and the gallery cannot drift apart.
                    fun read(image: Bitmap?, origin: Origin, uri: Uri? = null) {
                        if (image == null) {
                            still = Still.Failed("Could not open that picture")
                            return
                        }
                        still = Still.Working(image)
                        scope.launch {
                            // A picture can arrive before the models have
                            // loaded — shared from another app, most often.
                            // Reading it with the placeholder would report no
                            // price in a picture full of them.
                            snapshotFlow { engineSettled }.first { it }
                            if (!engine.ready) {
                                still = Still.Failed("The recognizer could not be loaded")
                                return@launch
                            }
                            val lines = withContext(Dispatchers.Default) {
                                val current = engine
                                current.reset()
                                // Thorough: a still has no next frame to defer
                                // the rest of the reading to.
                                current.recognize(image, thorough = true)
                            }
                            still = Still.Read(image, lines, origin, uri)
                        }
                    }

                    fun open(uri: Uri, origin: Origin) {
                        still = Still.Working(null)
                        scope.launch {
                            val image = withContext(Dispatchers.IO) {
                                StillImages.loadUpright(context, uri)
                            }
                            read(image, origin, uri)
                        }
                    }

                    // The standalone picker, for where the embedded one is not
                    // available.
                    val fromGallery = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? -> uri?.let { open(it, Origin.Gallery) } }

                    fun pickStandalone() = fromGallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )

                    // A picture shared from another app opens as if it had
                    // been picked from the gallery.
                    LaunchedEffect(shared) {
                        shared?.let { uri ->
                            shared = null
                            browsing = false
                            open(uri, Origin.Shared)
                        }
                    }

                    fun pickFromGallery() {
                        if (embeddedPickerAvailable()) browsing = true else pickStandalone()
                    }

                    // A picture opened from here is shown over whichever
                    // screen it was opened from, and back returns to it: to
                    // the camera for another photo, or home.
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            source = source,
                            target = target,
                            onChangeSource = { picking = Picking.Source },
                            onChangeTarget = { picking = Picking.Target },
                            onCalculator = { screen = Screen.Calculator },
                            onCamera = { screen = Screen.Camera },
                            onGallery = ::pickFromGallery,
                        )

                        Screen.Calculator -> CalculatorScreen(
                            rates = rates,
                            source = source,
                            target = target,
                            onChangeSource = { picking = Picking.Source },
                            onChangeTarget = { picking = Picking.Target },
                            onClose = { screen = Screen.Home },
                        )

                        Screen.Camera -> CameraScreen(
                            engine = engine,
                            live = LIVE_RECOGNITION,
                            rates = rates,
                            source = source,
                            target = target,
                            onChangeSource = { picking = Picking.Source },
                            onChangeTarget = { picking = Picking.Target },
                            onPhoto = { read(it, Origin.Camera) },
                            onPickFromGallery = ::pickFromGallery,
                            onClose = { screen = Screen.Home },
                        )
                    }

                    if (browsing && embeddedPickerAvailable()) {
                        GalleryScreen(
                            onPicked = { uri ->
                                browsing = false
                                open(uri, Origin.Gallery)
                            },
                            onUnavailable = {
                                Log.w(TAG, "embedded photo picker failed; using the standalone one")
                                browsing = false
                                pickStandalone()
                            },
                            onClose = { browsing = false },
                        )
                    }

                    // The picture as the screen shows it, labels and all, into
                    // the gallery. Before Android 10 that needs storage asked
                    // for first; the save waits for the answer.
                    var savePending by remember { mutableStateOf<(() -> Unit)?>(null) }
                    val askStorage = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        val pending = savePending
                        savePending = null
                        if (granted) pending?.invoke()
                        else toast(context, "Saving needs access to storage")
                    }

                    fun save(read: Still.Read, prices: List<LocatedPrice>) {
                        val work = {
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    val picture = renderConversions(context, read.image, prices, rates, target)
                                    Exports.saveToGallery(context, picture).also { picture.recycle() }
                                }
                                toast(
                                    context,
                                    if (saved) "Saved to Pictures/${Exports.ALBUM}" else "Could not save the picture",
                                )
                            }
                            Unit
                        }
                        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        if (legacy && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            savePending = work
                            askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            work()
                        }
                    }

                    fun report(read: Still.Read, prices: List<LocatedPrice>, note: String) {
                        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US)
                        val details = ProblemReport(
                            appVersion = appVersion(context),
                            device = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                                "${Build.MANUFACTURER} ${Build.MODEL}",
                            writtenAt = stamp.format(Date()),
                            origin = read.origin.label,
                            readWidth = read.image.width,
                            readHeight = read.image.height,
                            source = source,
                            detectedSource = detectedLocal,
                            target = target,
                            detectedTarget = detectedHome,
                            receipt = receipt,
                            rates = rates,
                            ratesFetchedAt = rates?.let { stamp.format(Date(it.fetchedAt)) },
                            lines = read.lines,
                            prices = prices,
                            note = note,
                        )
                        scope.launch {
                            val zip = runCatching {
                                withContext(Dispatchers.IO) {
                                    val shown = renderConversions(context, read.image, prices, rates, target)
                                    Exports.writeReport(
                                        context,
                                        text = problemReportText(details),
                                        read = read.image,
                                        original = read.uri,
                                        shown = shown,
                                    ).also { shown.recycle() }
                                }
                            }.onFailure { Log.e(TAG, "could not write a report", it) }.getOrNull()
                            if (zip == null) {
                                toast(context, "Could not write the report")
                            } else {
                                val summary = "How Much? ${details.appVersion}: " +
                                    note.trim().ifEmpty { "a picture that did not convert right" }
                                Exports.shareReport(context, zip, summary)
                            }
                        }
                    }

                    still?.let { current ->
                        // Worked out again when a currency or the receipt
                        // switch changes; the reading itself is kept.
                        val lines = (current as? Still.Read)?.lines.orEmpty()
                        val prices = remember(lines, source, receipt) {
                            locatePrices(
                                lines,
                                PriceContext(
                                    pageCurrency = source,
                                    bareAmounts = if (receipt) source else null,
                                ),
                            )
                        }
                        StillScreen(
                            still = current,
                            prices = prices,
                            rates = rates,
                            source = source,
                            target = target,
                            receipt = receipt,
                            onReceiptChange = { on ->
                                receipt = on
                                // A receipt names no currency, so there is
                                // nothing to read its amounts in until one is
                                // chosen.
                                if (on && source == null) picking = Picking.Source
                            },
                            onChangeSource = { picking = Picking.Source },
                            onChangeTarget = { picking = Picking.Target },
                            onClose = { still = null },
                            onSave = { (current as? Still.Read)?.let { save(it, prices) } },
                            onReport = { note -> (current as? Still.Read)?.let { report(it, prices, note) } },
                        )
                    }

                    when (picking) {
                        Picking.Source -> CurrencyPicker(
                            title = "Prices are in",
                            current = source,
                            automatic = chosenLocal == null,
                            detected = detectedLocal,
                            automaticSubtitle = "where the phone is",
                            excluded = target,
                            excludedRole = "what you convert into",
                            onPick = { code ->
                                currencies.local = code
                                chosenLocal = code
                                picking = null
                            },
                            onDismiss = { picking = null },
                        )

                        Picking.Target -> CurrencyPicker(
                            title = "Convert into",
                            current = target,
                            automatic = chosenHome == null,
                            detected = detectedHome,
                            automaticSubtitle = "where the phone is from",
                            excluded = source,
                            excludedRole = "what the prices are in",
                            onPick = { code ->
                                currencies.home = code
                                chosenHome = code
                                picking = null
                            },
                            onDismiss = { picking = null },
                        )

                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedImage(intent)?.let { shared = it }
    }

    /** The image in a share, if this intent is one. */
    private fun sharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }

    private fun toast(context: Context, text: String) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /** Which of the two currencies the picker is open for. */
    private enum class Picking { Source, Target }

    /** The screen under any picture being shown. */
    private enum class Screen { Home, Calculator, Camera }

    private companion object {
        const val TAG = "HowMuch"

        /**
         * Whether the camera reads its viewfinder as it goes, drawing the
         * conversions over the live picture, rather than only the photograph
         * once it is taken. Off for now; everything it needs is kept —
         * frame voting, box tracking, the per-frame budget — so turning it
         * back on is this one line.
         */
        const val LIVE_RECOGNITION = false
    }
}
