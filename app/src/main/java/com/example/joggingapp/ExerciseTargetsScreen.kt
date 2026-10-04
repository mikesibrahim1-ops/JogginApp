package com.example.joggingapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.joggingapp.ui.theme.JogginColorTokens
import com.example.joggingapp.ui.theme.JogginTheme

// Colour for a target type: distance types reuse the activity tokens; manual types use accent/primary.
private fun targetColor(type: ExerciseType, c: JogginColorTokens): Color = when (type) {
    ExerciseType.WALKING -> c.walking
    ExerciseType.RUNNING -> c.running
    ExerciseType.SITUPS  -> c.primary
    ExerciseType.PUSHUPS -> c.accent
    ExerciseType.SQUATS  -> c.gold
}

@Composable
fun ExerciseTargetsScreen(routes: List<SavedRoute>, onBack: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current

    BackHandler { onBack() }

    var targets by remember { mutableStateOf(ExerciseTargetStorage.loadTargets(context)) }
    var repLog by remember { mutableStateOf(ExerciseTargetStorage.loadRepLog(context)) }
    var showAddForm by remember { mutableStateOf(false) }
    // Which target currently has its "log reps" dialog open (manual types only).
    var logFor by remember { mutableStateOf<ExerciseTarget?>(null) }

    // Recomputed whenever targets/repLog/routes change.
    val progress = remember(targets, repLog, routes) { evaluateTargets(targets, routes, repLog) }

    Column(
        modifier = Modifier.fillMaxSize().background(c.background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Hero header with back affordance ──
        Box(
            modifier = Modifier.fillMaxWidth().background(c.heroGradient)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text("←", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
            Column(modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎯 ${S.exerciseTargets}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(S.exerciseTargetsSubtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // ── Add-target button / form ──
            if (!showAddForm) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(c.primary.copy(alpha = 0.15f))
                        .clickable { showAddForm = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("➕", fontSize = 20.sp)
                    Text(S.exAddTarget, fontSize = 14.sp, color = c.onBackground, fontWeight = FontWeight.SemiBold)
                }
            } else {
                AddTargetForm(
                    onAdd = { type, period, amount ->
                        targets = ExerciseTargetStorage.addTarget(
                            context, ExerciseTarget(type = type, period = period, amount = amount)
                        )
                        showAddForm = false
                        AppLogger.log(context, LogCategory.UI, "Exercise target added: $type $period $amount")
                    },
                    onCancel = { showAddForm = false }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Targets list / empty state ──
            if (progress.isEmpty()) {
                Text(S.exerciseTargetsEmpty, fontSize = 13.sp, color = c.textSecondary, lineHeight = 20.sp)
            } else {
                progress.forEach { tp ->
                    TargetCard(
                        tp = tp,
                        onLog = { logFor = tp.target },
                        onDelete = {
                            targets = ExerciseTargetStorage.deleteTarget(context, tp.target.id)
                            AppLogger.log(context, LogCategory.UI, "Exercise target deleted")
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    // ── Log-reps dialog (manual types) ──
    logFor?.let { target ->
        LogRepsDialog(
            type = target.type,
            onConfirm = { count ->
                repLog = ExerciseTargetStorage.logReps(context, target.type, count)
                logFor = null
                AppLogger.log(context, LogCategory.UI, "Logged $count ${target.type}")
            },
            onDismiss = { logFor = null }
        )
    }
}

@Composable
private fun TargetCard(tp: TargetProgress, onLog: () -> Unit, onDelete: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val t = tp.target
    val col = targetColor(t.type, c)

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(c.surfaceVariant).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(col.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center) {
                Text(t.type.defaultEmoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(exerciseTypeLabel(S, t.type), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
                Text(periodLabel(S, t.period), fontSize = 11.sp, color = c.textSecondary)
            }
            // Delete (✕) in the corner.
            Text("✕", fontSize = 16.sp, color = c.textSecondary, modifier = Modifier.clickable { onDelete() })
        }

        Spacer(Modifier.height(12.dp))

        // Progress bar.
        LinearProgressIndicator(
            progress = tp.fraction,
            color = if (tp.isMet) c.success else col,
            backgroundColor = c.divider,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            val label = if (t.type.isDistance) {
                S.exProgressKm(String.format("%.1f", tp.current), String.format("%.1f", tp.goal))
            } else {
                S.exProgressReps(tp.current.toInt(), tp.goal.toInt())
            }
            Text(label, fontSize = 12.sp, color = c.onBackground, fontWeight = FontWeight.Medium)

            if (tp.isMet) {
                Text(S.exDone, fontSize = 12.sp, color = c.success, fontWeight = FontWeight.Bold)
            } else if (t.type.isDistance) {
                Text(S.exAutoTracked, fontSize = 10.sp, color = c.textSecondary)
            } else {
                // Manual type → "Log" button to add reps.
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(col)
                        .clickable { onLog() }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("+", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(S.exLogReps, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AddTargetForm(
    onAdd: (ExerciseType, TargetPeriod, Float) -> Unit,
    onCancel: () -> Unit
) {
    val c = JogginTheme.colors
    val S = LocalStrings.current

    var selectedType by remember { mutableStateOf(ExerciseType.SITUPS) }
    var selectedPeriod by remember { mutableStateOf(TargetPeriod.DAILY) }
    var amountText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(c.surfaceVariant).padding(16.dp)
    ) {
        Text(S.exNewTarget, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
        Spacer(Modifier.height(14.dp))

        // Exercise type chips.
        Text(S.exExerciseLabel, fontSize = 12.sp, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        ChipFlowRow {
            ExerciseType.values().forEach { type ->
                SelectChip(
                    label = "${type.defaultEmoji} ${exerciseTypeLabel(S, type)}",
                    selected = type == selectedType,
                    color = targetColor(type, c),
                    onClick = { selectedType = type }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Period chips.
        Text(S.exPeriodLabel, fontSize = 12.sp, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        ChipFlowRow {
            TargetPeriod.values().forEach { period ->
                SelectChip(
                    label = periodLabel(S, period),
                    selected = period == selectedPeriod,
                    color = c.primary,
                    onClick = { selectedPeriod = period }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Amount field (reps or km depending on type).
        Text(if (selectedType.isDistance) S.exAmountKm else S.exAmountReps, fontSize = 12.sp, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = amountText,
            onValueChange = { new -> amountText = new.filter { it.isDigit() || it == '.' }.take(6) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = c.surface,
                textColor = c.onSurface,
                cursorColor = c.primary,
                focusedIndicatorColor = c.primary,
                unfocusedIndicatorColor = c.divider
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(c.divider.copy(alpha = 0.4f)).clickable { onCancel() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.cancel, fontSize = 13.sp, color = c.onBackground) }

            val amount = amountText.toFloatOrNull() ?: 0f
            val enabled = amount > 0f
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) c.primary else c.divider)
                    .clickable(enabled = enabled) { onAdd(selectedType, selectedPeriod, amount) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.exAdd, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun LogRepsDialog(type: ExerciseType, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    var text by remember { mutableStateOf("") }

    // Simple overlay dialog (matches BuddyDialog idiom: dim scrim + centered card).
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(16.dp))
                .background(c.surface).padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            Text(S.exLogRepsTitle(exerciseTypeLabel(S, type)), fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = c.onSurface)
            Spacer(Modifier.height(14.dp))
            TextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = c.surfaceVariant,
                    textColor = c.onSurface,
                    cursorColor = c.primary,
                    focusedIndicatorColor = c.primary,
                    unfocusedIndicatorColor = c.divider
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(c.divider.copy(alpha = 0.4f)).clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(S.cancel, fontSize = 13.sp, color = c.onSurface) }

                val count = text.toIntOrNull() ?: 0
                val enabled = count > 0
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (enabled) c.primary else c.divider)
                        .clickable(enabled = enabled) { onConfirm(count) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(S.exLogReps, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ── Small layout helpers ─────────────────────────────────────────────────────────

/** A single row of chips that scrolls horizontally if they overflow (chips are few). */
@Composable
private fun ChipFlowRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val c = JogginTheme.colors
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color else Color.Transparent)
            .border(1.dp, if (selected) color else c.divider, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp,
            color = if (selected) Color.White else c.onBackground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}
