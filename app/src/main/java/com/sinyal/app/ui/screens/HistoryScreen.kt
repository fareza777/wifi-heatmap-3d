package com.sinyal.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinyal.app.data.ScanRepository
import com.sinyal.app.data.ScanStore
import com.sinyal.app.data.ScanSummary
import com.sinyal.app.ui.components.BackBar
import com.sinyal.app.ui.components.GlassCard
import com.sinyal.app.ui.components.signalColorFor
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.sinyal.app.R
import com.sinyal.app.ui.components.LinkRow

data class HistoryUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val scansByDay: Map<LocalDate, List<ScanSummary>> = emptyMap(),
    val readyToOpen: Boolean = false,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScanRepository(app)

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val scans = withContext(Dispatchers.IO) { repository.list() }
        val grouped = scans.groupBy { it.savedAtMs.toLocalDate() }
        // Land on the most recent month that has anything in it, not on an empty
        // "today" the user would have to page backwards from.
        val latest = grouped.keys.maxOrNull()
        _state.update {
            it.copy(
                scansByDay = grouped,
                month = latest?.let(YearMonth::from) ?: YearMonth.now(),
                selectedDay = latest,
            )
        }
    }

    fun showMonth(month: YearMonth) {
        _state.update { it.copy(month = month, selectedDay = null) }
    }

    fun selectDay(day: LocalDate) {
        _state.update { it.copy(selectedDay = if (it.selectedDay == day) null else day) }
    }

    fun openScan(id: String) = viewModelScope.launch {
        val scan = withContext(Dispatchers.IO) { repository.load(id) } ?: return@launch
        ScanStore.put(scan)
        _state.update { it.copy(readyToOpen = true) }
    }

    fun consumeOpenSignal() {
        _state.update { it.copy(readyToOpen = false) }
    }

    fun delete(id: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repository.delete(id) }
        refresh()
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Scans laid out on a calendar.
 *
 * A flat list answers "what was the last scan"; a month grid answers "did I
 * measure this room before I moved the router", which is the question that
 * makes a second scan worth taking.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenCompare: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(state.readyToOpen) {
        if (state.readyToOpen) {
            viewModel.consumeOpenSignal()
            onOpenScan()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.Base),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            BackBar(title = stringResource(R.string.history_title), onBack = onBack)

            Spacer(Modifier.height(12.dp))
            LinkRow(
                text = stringResource(R.string.history_open_compare),
                onClick = onOpenCompare,
            )

            Spacer(Modifier.height(18.dp))
            MonthHeader(
                month = state.month,
                onPrevious = { viewModel.showMonth(state.month.minusMonths(1)) },
                onNext = { viewModel.showMonth(state.month.plusMonths(1)) },
            )

            Spacer(Modifier.height(14.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                WeekdayHeader()
                Spacer(Modifier.height(6.dp))
                MonthGrid(
                    month = state.month,
                    scansByDay = state.scansByDay,
                    selectedDay = state.selectedDay,
                    onSelectDay = viewModel::selectDay,
                )
            }

            val listed = state.selectedDay
                ?.let { day -> state.scansByDay[day].orEmpty() }
                ?: state.scansByDay
                    .filterKeys { YearMonth.from(it) == state.month }
                    .values
                    .flatten()
                    .sortedByDescending { it.savedAtMs }

            Spacer(Modifier.height(14.dp))
            if (listed.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextTone.Tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    )
                }
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.selectedDay?.let { day ->
                            day.format(DAY_TITLE).uppercase()
                        } ?: stringResource(R.string.history_this_month),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTone.Tertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                    listed.forEach { scan ->
                        ScanRow(
                            scan = scan,
                            onOpen = { viewModel.openScan(scan.id) },
                            onDelete = { viewModel.delete(scan.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(
            Icons.Rounded.ChevronLeft,
            stringResource(R.string.history_prev_month),
            onPrevious,
        )
        Text(
            text = month.format(MONTH_TITLE).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            color = TextTone.Primary,
        )
        ArrowButton(
            Icons.Rounded.ChevronRight,
            stringResource(R.string.history_next_month),
            onNext,
        )
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = TextTone.Secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            R.string.history_day_mon,
            R.string.history_day_tue,
            R.string.history_day_wed,
            R.string.history_day_thu,
            R.string.history_day_fri,
            R.string.history_day_sat,
            R.string.history_day_sun,
        ).map { stringResource(it) }.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTone.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Six week-rows, Monday first.
 *
 * Leading blanks come from the first of the month's own weekday, so the grid
 * lines up with a wall calendar rather than starting at column one.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    scansByDay: Map<LocalDate, List<ScanSummary>>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leadingBlanks = first.dayOfWeek.value - 1
    val totalCells = leadingBlanks + month.lengthOfMonth()
    val rows = (totalCells + 6) / 7

    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayNumber = cellIndex - leadingBlanks + 1

                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        if (dayNumber in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                count = scansByDay[date]?.size ?: 0,
                                selected = date == selectedDay,
                                onClick = { onSelectDay(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val hasScans = count > 0
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxSize()
            .clip(shape)
            .background(if (selected) Accent.Glow else androidx.compose.ui.graphics.Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, Accent.Base, shape) else Modifier,
            )
            .then(if (hasScans) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    hasScans -> TextTone.Primary
                    else -> TextTone.Tertiary
                },
            )
            if (hasScans) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(count.coerceAtMost(3)) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Accent.Base),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanRow(scan: ScanSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scan.ssid ?: stringResource(R.string.history_unknown_network),
                style = MaterialTheme.typography.titleMedium,
                color = TextTone.Primary,
            )
            Text(
                // Square metres come out of the same drifting coordinates the
                // result screen stopped quoting, so the list does not quote them
                // either.
                text = stringResource(
                    R.string.history_scan_detail_v2,
                    scan.savedAtMs.toLocalDate().format(DAY_TITLE),
                    scan.cellCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTone.Tertiary,
            )
        }
        Text(
            text = scan.weakestDbm?.let { stringResource(R.string.unit_dbm, it) }
                ?: stringResource(R.string.value_none),
            style = MaterialTheme.typography.titleMedium,
            color = scan.weakestDbm?.let { signalColorFor(it) } ?: TextTone.Tertiary,
        )
        Text(
            text = stringResource(R.string.history_delete),
            style = MaterialTheme.typography.labelMedium,
            color = TextTone.Tertiary,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        )
    }
}

private val MONTH_TITLE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val DAY_TITLE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
