package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOfimport androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RunningSession
import com.example.ui.theme.MetricTypography
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.RunColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AudioSynth
import com.example.utils.RunningCsvExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RunningHistoryScreen(viewModel: MainViewModel) {
    val ptBr = remember { Locale("pt", "BR") }
    val sessions by viewModel.runningSessions.collectAsState()
    val userWeight = viewModel.userProfile.collectAsState().value?.weightKg ?: 70.0f

    val sortedSessions = remember(sessions) { sessions.sortedByDescending { it.date } }

    val totalDistanceMeters = sessions.sumOf { it.distanceMeters.toLong() }
    val totalDistanceKm = totalDistanceMeters / 1000f
    val totalDurationSec = sessions.sumOf { it.durationSec.toLong() }
    val totalHours = totalDurationSec / 3600
    val totalMinutes = (totalDurationSec % 3600) / 60
    val totalCalories = sessions.sumOf { session ->
        ((session.distanceMeters / 1000.0) * userWeight * 1.03).toInt()
            .coerceAtLeast((session.durationSec / 60) * 5)
    }

    val avgPaceSec = if (totalDistanceMeters > 0) {
        ((totalDurationSec * 1000) / totalDistanceMeters).toInt()
    } else {
        0
    }
    val avgPaceText = if (avgPaceSec > 0) {
        val pMin = avgPaceSec / 60
        val pSec = avgPaceSec % 60
        String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)
    } else {
        "--:--"
    }

    val monthHeaderFormat = remember { SimpleDateFormat("MMM yyyy", ptBr) }
    val sessionsByMonth = remember(sortedSessions) {
        val linked = java.util.LinkedHashMap<String, MutableList<RunningSession>>()
        sortedSessions.forEach { session ->
            val raw = monthHeaderFormat.format(Date(session.date))
            val header = raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBr) else it.toString() }
            linked.getOrPut(header) { mutableListOf() }.add(session)
        }
        linked
    }

    val context = LocalContext.current
    val runningShoes by viewModel.runningShoes.collectAsState()
    var showShoesBottomSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Histórico de Corridas",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Registro das sessões e evolução",
                    color = OnSurfaceDim,
                    fontSize = 11.sp
                )
            }
            // Action Buttons: Tênis & CSV
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = RunColors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RunColors.border),
                    modifier = Modifier.clickable {
                        AudioSynth.playTickSound()
                        showShoesBottomSheet = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "👟 Tênis",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    color = RunColors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RunColors.border),
                    modifier = Modifier.clickable {
                        AudioSynth.playTickSound()
                        RunningCsvExporter.exportHistoryToCsv(context, sessions)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📊 CSV",
                            color = RunColors.brandText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Metrics Summary Header Card (Tabular Numbers)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = RunColors.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Runs
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${sessions.size}",
                        style = MetricTypography.Headline
                    )
                    Text(
                        text = "Sessões",
                        style = MetricTypography.LabelMuted
                    )
                }
                // Total Distance
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(ptBr, "%.1f", totalDistanceKm),
                        style = MetricTypography.CyanHighlight.copy(fontSize = 20.sp)
                    )
                    Text(
                        text = "Distância (km)",
                        style = MetricTypography.LabelMuted
                    )
                }
                // Total Time
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val timeFormatted = if (totalHours > 0) {
                        "${totalHours}h ${totalMinutes}m"
                    } else {
                        "${totalMinutes}m"
                    }
                    Text(
                        text = timeFormatted,
                        style = MetricTypography.Headline.copy(fontSize = 18.sp)
                    )
                    Text(
                        text = "Tempo Total",
                        style = MetricTypography.LabelMuted
                    )
                }
                // Average Pace
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = avgPaceText,
                        style = MetricTypography.Headline.copy(fontSize = 18.sp)
                    )
                    Text(
                        text = "Pace Médio",
                        style = MetricTypography.LabelMuted
                    )
                }
                // Total Calories
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalCalories",
                        style = MetricTypography.OrangeHighlight.copy(fontSize = 18.sp)
                    )
                    Text(
                        text = "Calorias",
                        style = MetricTypography.LabelMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (sortedSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = RunColors.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RunColors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = RunColors.brand,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Nenhuma corrida registrada",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Inicie seu treino na aba Início para registrar sua primeira sessão!",
                            color = RunColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
            ) {
                sessionsByMonth.entries.forEach { entry ->
                    val monthHeader = entry.key
                    val monthSessions = entry.value
                    val monthKm = monthSessions.sumOf { it.distanceMeters.toLong() } / 1000f
                    val monthCalories = monthSessions.sumOf { session ->
                        ((session.distanceMeters / 1000.0) * userWeight * 1.03).toInt()
                            .coerceAtLeast((session.durationSec / 60) * 5)
                    }

                    item(key = "header_$monthHeader") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp, start = 2.dp, end = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthHeader,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = String.format(ptBr, "%.2f Total km", monthKm),
                                    style = MetricTypography.CyanHighlight
                                )
                                Text(
                                    text = "·",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "$monthCalories Total Calories",
                                    style = MetricTypography.SecondaryValue
                                )
                            }
                        }
                    }

                    items(monthSessions, key = { it.id }) { session ->
                        RunningHistoryItemCard(
                            session = session,
                            userWeight = userWeight,
                            onSessionClick = {
                                viewModel.selectedDetailSession.value = session
                                viewModel.activeScreen.value = AppScreen.RUNNING_DETAIL
                                AudioSynth.playTickSound()
                            },
                            onShareClick = {
                                viewModel.selectedShareSession.value = session
                                viewModel.activeScreen.value = AppScreen.SHARE_CARD
                                AudioSynth.playTickSound()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showShoesBottomSheet) {
        RunningShoesBottomSheet(
            shoes = runningShoes,
            onAddShoe = { viewModel.addRunningShoe(it) },
            onUpdateShoe = { viewModel.updateRunningShoe(it) },
            onSetDefault = { viewModel.setDefaultRunningShoe(it) },
            onRetireShoe = { viewModel.retireRunningShoe(it) },
            onDeleteShoe = { viewModel.deleteRunningShoe(it) },
            onDismiss = { showShoesBottomSheet = false }
        )
    }
}

/**
 * Single-line compact card component for running history with tabular figures
 * and modal badge (5K, 10K, RUA, ESTEIRA, LEVE)
 */
@Composable
fun RunningHistoryItemCard(
    session: RunningSession,
    userWeight: Float = 70.0f,
    onSessionClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null
) {
    val ptBr = remember { Locale("pt", "BR") }
    val dateStr = remember(session.date) {
        SimpleDateFormat("dd 'de' MMM", ptBr).format(Date(session.date))
    }

    val distKm = session.distanceMeters / 1000f
    val durationMin = session.durationSec / 60
    val durationSecRem = session.durationSec % 60
    val durationFormatted = if (durationMin >= 60) {
        "${durationMin / 60}h ${durationMin % 60}m"
    } else {
        String.format(Locale.US, "%d:%02d min", durationMin, durationSecRem)
    }

    val paceText = if (session.avgPaceSecPerKm > 0) {
        val paceMin = session.avgPaceSecPerKm / 60
        val paceSec = session.avgPaceSecPerKm % 60
        String.format(Locale.US, "%d'%02d\"/km", paceMin, paceSec)
    } else {
        "--:--"
    }

    val caloriesKcal = remember(session.distanceMeters, session.durationSec, userWeight) {
        ((session.distanceMeters / 1000.0) * userWeight * 1.03).toInt()
            .coerceAtLeast((session.durationSec / 60) * 5)
    }

    val sessionTitle = remember(session) {
        if (session.isGentleMode) "Caminhada Regenerativa"
        else if (session.weekNumber > 0 && session.dayNumber > 0) "Semana ${session.weekNumber} · Dia ${session.dayNumber}"
        else if (distKm >= 9.0f) "Sessão 10K"
        else if (distKm >= 4.5f) "Sessão 5K"
        else "Treino Livre"
    }

    val badgeLabel = remember(session) {
        if (distKm >= 9.5f) "10K"
        else if (distKm >= 4.5f) "5K"
        else if (session.isGentleMode) "LEVE"
        else if (session.environment == "TREADMILL") "ESTEIRA"
        else "RUA"
    }

    val badgeBg = if (session.isGentleMode) Color(0xFF30D158).copy(alpha = 0.18f)
    else if (badgeLabel == "5K" || badgeLabel == "10K") Color(0xFF00D4FF).copy(alpha = 0.22f)
    else Color(0xFF1C1C1E)

    val badgeTextColor = if (session.isGentleMode) Color(0xFF30D158)
    else Color(0xFF00D4FF)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onSessionClick != null) Modifier.clickable { onSessionClick() } else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Modal Badge (5K, 10K, RUA, ESTEIRA, LEVE)
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, badgeTextColor.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeLabel,
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Central Info (Title, Date, Metrics)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sessionTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateStr,
                        style = MetricTypography.DateText
                    )
                }

                // Compact metrics line: 5.2 km · 28 min · 5'23"/km · 340 kcal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = String.format(ptBr, "%.2f km", distKm),
                        style = MetricTypography.PrimaryValue
                    )
                    Text("·", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    Text(
                        text = durationFormatted,
                        style = MetricTypography.SecondaryValue
                    )
                    Text("·", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    Text(
                        text = paceText,
                        style = MetricTypography.CyanHighlight
                    )
                    Text("·", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    Text(
                        text = "$caloriesKcal kcal",
                        style = MetricTypography.SecondaryValue
                    )
                }

                if (!session.shoeName.isNullOrBlank()) {
                    Text(
                        text = "👟 ${session.shoeName}",
                        color = Color(0xFF8E8E93),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Share Action Icon
            if (onShareClick != null) {
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartilhar",
                        tint = Color(0xFFA1A1A6),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
