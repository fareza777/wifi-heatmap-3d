package com.sinyal.app.marketing

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinyal.app.ar.*
import com.sinyal.app.core.AppLanguage
import com.sinyal.app.data.*
import com.sinyal.app.export.MapRenderer
import com.sinyal.app.net.PingReply
import com.sinyal.app.net.PingReport
import com.sinyal.app.ui.screens.*
import com.sinyal.app.ui.theme.*
import com.sinyal.app.wifi.*
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.math.sin

/** Debug-only reproducible capture harness. No fixture code enters release builds.
 * Production screen composables are rendered directly, using disclosed sample data.
 */
class StoreScreenshotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val page = intent.getIntExtra("page", 0).coerceIn(0, 7)
        val framed = intent.getBooleanExtra("framed", true)
        ScanStore.put(sampleScan())
        setContent {
            SinyalTheme(mode = ThemeMode.DARK, accent = AccentPalette.TEAL) {
                if (framed) StoreFrame(page) { Screen(page) } else Screen(page)
            }
        }
    }

    @Composable private fun Screen(page: Int) {
        when (page) {
            0 -> {
                // User-supplied real device capture. Crop only system chrome and test banner;
                // the native frame clips excess vertical black space, retaining the real model.
                val phoneImage = remember {
                    val source = assets.open("store-hero-phone.png").use(BitmapFactory::decodeStream)
                    Bitmap.createBitmap(source, 0, 62, source.width, 1316).asImageBitmap()
                }
                Image(phoneImage, "Actual 3D room captured on a phone", Modifier.fillMaxSize().background(Color.Black),
                    contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
            }
            1 -> {
                val vm = remember { RoomViewModel(application) }
                val state by vm.state.collectAsState()
                val bitmap = remember(state.building) {
                    state.scale?.let { scale -> MapRenderer.render(resources, state.scan!!, state.tiles,
                        scale, state.advice, "Sample survey", "Living space  /  42 m²  /  168 readings") }
                }
                bitmap?.let { Image(it.asImageBitmap(), "Actual app map export", Modifier.fillMaxSize().background(Color.Black), contentScale = ContentScale.Fit) }
            }
            2 -> {
                val vm = remember { SignalGraphViewModel(application) }
                Freeze(vm) {
                    setFlow(vm, "_tracked", networks.take(4).mapIndexed { n, ap ->
                        TrackedAp(ap.ssid, ap.bssid, ap.band, ap.channel, ap.security, ap.isCurrent,
                            (0..79).map { t -> ap.rssiDbm + (sin(t * .21 + n) * (3 + n)).roundToInt() + (sin(t * .72) * 2).roundToInt() })
                    })
                    setFlow(vm, "_elapsed", 158)
                }
                SignalGraphScreen({}, viewModel = vm)
            }
            3 -> {
                val vm = remember { AnalysisViewModel(application) }
                Freeze(vm) { setFlow(vm, "_state", AnalysisUiState(link, networks, ChannelAnalysis.analyse(networks, link))) }
                AnalysisScreen({}, viewModel = vm)
            }
            4 -> {
                val vm = remember { SecurityViewModel(application) }
                Freeze(vm) { setFlow(vm, "_state", SecurityUiState(
                    report = SecurityAudit.run(resources, networks, link.ssid),
                    graded = SecurityGrading.gradeAll(networks), mix = SecurityGrading.mix(networks),
                    gateway = "192.168.1.1", running = false)) }
                SecurityScreen({}, viewModel = vm)
            }
            5 -> {
                val vm = remember { PingViewModel(application) }
                Freeze(vm) {
                    setFlow(vm, "_gateway", "192.168.1.1")
                    setFlow(vm, "_report", PingReport("192.168.1.1", "192.168.1.1",
                        listOf(4.2, 3.8, 4.6, 5.2, 3.9, 4.1, 6.4, 4.6, 3.8, 4.4).mapIndexed { n, ms -> PingReply(n + 1, ms) }))
                }
                PingScreen({}, initialHost = "192.168.1.1", viewModel = vm)
            }
            6 -> {
                val vm = remember { HistoryViewModel(application) }
                Freeze(vm) {
                    val dates = listOf(3, 5, 8, 10, 12).map { LocalDate.of(2026, 9, it) }
                    setFlow(vm, "_state", HistoryUiState(YearMonth.of(2026, 9), dates.last(),
                        dates.associateWith { day -> listOf(ScanSummary("sample-$day", day.atTime(10, 30).toInstant(ZoneOffset.UTC).toEpochMilli(), "Sample home", 42f, 168, -78)) }))
                }
                HistoryScreen({}, {}, viewModel = vm)
            }
            7 -> SettingsScreen(ThemeMode.DARK, {}, AccentPalette.TEAL, {}, AppLanguage.ENGLISH, {},
                false, null, {}, {}, {}, {}, {})
        }
    }

    @Composable private fun Freeze(vm: ViewModel, fill: () -> Unit) {
        LaunchedEffect(vm) {
            delay(350)
            vm.viewModelScope.coroutineContext.cancelChildren()
            while (true) { fill(); delay(500) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(target: Any, fieldName: String, value: T) {
        val field = target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        (field.get(target) as MutableStateFlow<T>).value = value
    }
}

private val networks = listOf(
    NearbyAp("Sample home", "02:00:00:00:00:01", -47, 2437, true, 20, SecurityType.WPA3, WifiGeneration.AX, false, false, false),
    NearbyAp("Studio", "02:00:00:00:00:02", -62, 2412, false, 20, SecurityType.WPA2, WifiGeneration.N, false, false, false),
    NearbyAp("Garden office", "02:00:00:00:00:03", -70, 2462, false, 20, SecurityType.WPA3, WifiGeneration.AX, false, false, false),
    NearbyAp("Guest network", "02:00:00:00:00:04", -79, 2417, false, 20, SecurityType.OPEN, WifiGeneration.N, false, false, false),
    NearbyAp("Upstairs", "02:00:00:00:00:05", -73, 2422, false, 20, SecurityType.WPA2, WifiGeneration.N, false, false, false),
)
private val link = WifiSnapshot(true, "Sample home", networks[0].bssid, -47, 2437, 286, WifiGeneration.AX, 0L)

private fun sampleScan(): CompletedScan {
    val cells = (0 until 14).flatMap { ix -> (0 until 12).map { iz ->
        val x = ix * .5f + .25f
        val z = iz * .5f + .25f
        val dbm = (-43 - x * 2.5 - z * 3.0 - if (x > 4f && z > 3f) 7 else 0).roundToInt()
        GridCell(ix, 0, iz, x, .25f, z, dbm, 1)
    } }
    val bounds = FloorBounds(0f, 0f, 7f, 6f)
    return CompletedScan("marketing-sample", 1789209000000L,
        RoomModel(bounds, RoomModel.perimeterOf(bounds) + listOf(
            WallSegment(4f, 0f, 4f, 2.5f), WallSegment(4f, 3.5f, 4f, 6f), WallSegment(4f, 3f, 7f, 3f)),
            0f, 2.5f, false),
        SampleGrid.fromCells(.5f, cells), "Sample home", 146000,
        WalkPath(emptyList()), 0f, null, emptyList())
}

private data class Story(val eyebrow: String, val title: String, val subtitle: String)
private val stories = listOf(
    Story("01  /  SEE YOUR SPACE", "Your Wi-Fi.\nIn another dimension.", "Explore signal coverage in a 3D room view."),
    Story("02  /  MAP THE WEAK SPOTS", "Make every\ncorner count.", "Export a clear, colour-coded coverage map."),
    Story("03  /  FOLLOW THE SIGNAL", "See the ups.\nCatch the drops.", "Compare signal strength as it changes."),
    Story("04  /  UNDERSTAND THE AIRWAVES", "Find room\nto connect.", "See channel overlap and nearby networks."),
    Story("05  /  CHECK YOUR NETWORK", "Know what\nyou connect to.", "Review security grades and useful warnings."),
    Story("06  /  INVESTIGATE THE LAG", "Get closer\nto the cause.", "Check latency, jitter and packet loss."),
    Story("07  /  KEEP YOUR PROGRESS", "Your surveys.\nAll in one place.", "Revisit saved scans from your calendar."),
    Story("08  /  MAKE IT YOURS", "A clearer view.\nYour way.", "Choose your theme, accent and language."),
)

/** Original native layout: the app is rendered live below the editorial caption. */
@Composable private fun StoreFrame(page: Int, content: @Composable () -> Unit) {
    val story = stories[page]
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A252C), Color(0xFF070F1A))))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WI-FI HEATMAP 3D", fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB5F4ED))
            Text("F7 DEVELOPER", fontSize = 7.sp, letterSpacing = 1.sp, color = Color(0xFF7DA5AF))
        }
        Spacer(Modifier.height(4.dp))
        Text(story.eyebrow, fontSize = 8.sp, letterSpacing = 1.6.sp, color = Color(0xFF48D6BE), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(story.title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp), color = Color(0xFFF1FFF9))
        Spacer(Modifier.height(4.dp))
        Text(story.subtitle, fontSize = 10.5.sp, lineHeight = 15.sp, color = Color(0xFFB2CBD0))
        Spacer(Modifier.height(5.dp))
        val density = LocalDensity.current
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .border(.6.dp, Color(0xFF35616D), RoundedCornerShape(11.dp)).background(Color(0xFF080B11))) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * .83f, density.fontScale)) { content() }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when(page) { 0 -> "ACTUAL APP  ·  USER CAPTURE"; 7 -> "ACTUAL APP INTERFACE"; else -> "SAMPLE SURVEY  ·  ILLUSTRATIVE DATA" }, fontSize = 7.sp, letterSpacing = .6.sp, color = Color(0xFF9DBFC6))
            Text("0${page + 1} / 08", fontSize = 7.sp, color = Color(0xFF48D6BE))
        }
    }
}
