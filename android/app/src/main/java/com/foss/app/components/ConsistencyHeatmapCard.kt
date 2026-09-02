package com.foss.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foss.app.ui.theme.AccentBlue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ConsistencyHeatmapCard(
    title: String = "Trainings",
    datesStrings: List<String>,
    activeColor: Color = AccentBlue,
    streakUnit: String = "wk",
    streakUnitPlural: String = "wks",
    emptyText: String = "No logs yet",
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }

    val loggedDates = remember(datesStrings) {
        datesStrings.mapNotNull {
            try {
                LocalDate.parse(it.take(10), formatter)
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    val lastDate = remember(loggedDates) { loggedDates.filter { !it.isAfter(today) }.maxOrNull() }
    val daysAgoText = remember(lastDate) {
        if (lastDate == null) {
            emptyText
        } else {
            val diff = ChronoUnit.DAYS.between(lastDate, today)
            when (diff) {
                0L -> "Today"
                1L -> "Yesterday"
                else -> "$diff days ago"
            }
        }
    }

    val streakWeeks = remember(loggedDates) {
        var streak = 0
        var currentMonday = today.with(DayOfWeek.MONDAY)

        val currentWeekHasWorkout = (0..6).any { currentMonday.plusDays(it.toLong()) in loggedDates }
        if (currentWeekHasWorkout) {
            streak++
            currentMonday = currentMonday.minusWeeks(1)
        } else {
            currentMonday = currentMonday.minusWeeks(1)
        }

        while (true) {
            val hasWorkout = (0..6).any { currentMonday.plusDays(it.toLong()) in loggedDates }
            if (hasWorkout) {
                streak++
                currentMonday = currentMonday.minusWeeks(1)
            } else {
                break
            }
        }
        streak
    }

    val weeksCount = 16
    val startMonday = remember { today.with(DayOfWeek.MONDAY).minusWeeks((weeksCount - 1).toLong()) }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Last: $daysAgoText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "Streak: $streakWeeks ${if (streakWeeks == 1) streakUnit else streakUnitPlural}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (streakWeeks > 0) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val spacing = 4.dp
                val squareSize = (maxWidth - (spacing * (weeksCount - 1))) / weeksCount

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (w in 0 until weeksCount) {
                        val weekStart = startMonday.plusWeeks(w.toLong())

                        Column(
                            verticalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            for (d in 0..6) {
                                val dayDate = weekStart.plusDays(d.toLong())
                                val isFuture = dayDate.isAfter(today)
                                val isLogged = dayDate in loggedDates

                                val cellColor = when {
                                    isFuture -> Color.Transparent
                                    isLogged -> activeColor
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .size(squareSize)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(cellColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}