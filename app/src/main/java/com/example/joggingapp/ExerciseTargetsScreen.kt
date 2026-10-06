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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    // A target just completed → show a congratulations dialog.
    var congratsFor by remember { mutableStateOf<ExerciseTarget?>(null) }

    // Recomputed whenever targets/repLog/routes change.
    val progress = remember(targets, repLog, routes) { evaluateTargets(targets, routes, repLog) }

    // Detect completions: any met target not yet recorded for its current period gets a
    // CompletedTarget entry (for the Personal Targets list) and triggers the congrats dialog.
    // Distance targets complete passively here too (e.g. a run pushed them over).
    LaunchedEffect(progress) {
        progress.filter { it.isMet }.forEach { tp ->
            val recorded = ExerciseTargetStorage.recordCompletionIfNew(context, tp.target)
            if (recorded != null) {
                AppLogger.log(context, LogCategory.UI, "Target completed: ${tp.target.type} ${tp.target.period}")
                if (congratsFor == null) congratsFor = tp.target
            }
        }
    }

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
                    onSaveAll = { staged ->
                        // Batch add: each staged row is its own distinct target.
                        staged.forEach { t -> targets = ExerciseTargetStorage.addTarget(context, t) }
                        showAddForm = false
                        AppLogger.log(context, LogCategory.UI, "Exercise targets added: ${staged.size} target(s)")
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

    // ── Log-reps dialog (manual types) — real Dialog so IME + focus work reliably ──
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

    // ── Congratulations dialog on completion ──
    congratsFor?.let { target ->
        CongratsDialog(type = target.type, onDismiss = { congratsFor = null })
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
                // Manual type → "Log" button to add reps (even when met, so users can keep logging).
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

/**
 * Batch add-target form. The user composes one row at a time (type + period + amount),
 * taps "Add another" to stage it, and repeats to queue several distinct targets, then
 * "Save N targets" commits them all at once. "Save" also commits the current in-progress
 * row if it's valid, so a single target doesn't require the extra "Add another" tap.
 */
@Composable
private fun AddTargetForm(
    onSaveAll: (List<ExerciseTarget>) -> Unit,
    onCancel: () -> Unit
) {
    val c = JogginTheme.colors
    val S = LocalStrings.current

    val staged = remember { mutableStateListOf<ExerciseTarget>() }
    var selectedType by remember { mutableStateOf(ExerciseType.SITUPS) }
    var selectedPeriod by remember { mutableStateOf(TargetPeriod.DAILY) }
    var amountText by remember { mutableStateOf("") }

    fun currentValidAmount(): Float = amountText.toFloatOrNull()?.takeIf { it > 0f } ?: 0f
    fun stageCurrent() {
        val amt = currentValidAmount()
        if (amt > 0f) {
            staged.add(ExerciseTarget(type = selectedType, period = selectedPeriod, amount = amt))
            amountText = ""   // reset amount for the next row; keep type/period as a sensible default
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(c.surfaceVariant).padding(16.dp)
    ) {
        Text(S.exNewTarget, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
        Spacer(Modifier.height(14.dp))

        // ── Staged rows (already-added targets in this batch) ──
        if (staged.isNotEmpty()) {
            staged.forEachIndexed { idx, t ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(t.type.defaultEmoji, fontSize = 16.sp)
                    val amount = if (t.type.isDistance) String.format("%.0f km", t.amount) else "${t.amount.toInt()}"
                    Text(
                        S.exTargetSummary(amount, exerciseTypeLabel(S, t.type), periodLabel(S, t.period)),
                        fontSize = 12.sp, color = c.onBackground, modifier = Modifier.weight(1f)
                    )
                    Text("✕", fontSize = 14.sp, color = c.textSecondary,
                        modifier = Modifier.clickable { staged.removeAt(idx) })
                }
            }
            Text(S.exStagedCount(staged.size), fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            Divider(color = c.divider)
            Spacer(Modifier.height(10.dp))
        }

        // ── Current row editor ──
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

        Spacer(Modifier.height(10.dp))

        // "Add another" stages the current row and clears the amount for the next.
        val canStage = currentValidAmount() > 0f
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .border(1.dp, if (canStage) c.primary else c.divider, RoundedCornerShape(10.dp))
                .clickable(enabled = canStage) { stageCurrent() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("➕ ${S.exAddAnother}", fontSize = 13.sp,
                color = if (canStage) c.primary else c.textSecondary, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        // ── Cancel / Save-all ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(c.divider.copy(alpha = 0.4f)).clickable { onCancel() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.cancel, fontSize = 13.sp, color = c.onBackground) }

            // Save count = staged + (1 if the current row is valid and will be auto-staged).
            val pending = staged.size + (if (currentValidAmount() > 0f) 1 else 0)
            val enabled = pending > 0
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) c.primary else c.divider)
                    .clickable(enabled = enabled) {
                        stageCurrent()                 // fold in the in-progress row if valid
                        if (staged.isNotEmpty()) onSaveAll(staged.toList())
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.exSaveAll(pending.coerceAtLeast(1)), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun LogRepsDialog(type: ExerciseType, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    var text by remember { mutableStateOf("") }

    fun append(ch: Char) {
        if (ch == '-') {
            // Toggle minus: add it if absent, remove if present.
            text = if (text.startsWith("-")) text.removePrefix("-") else "-${text}"
        } else if (text.replace("-", "").length < 5) {
            // Append digit (max 5 digits).
            text += ch
        }
    }
    fun backspace() {
        if (text.isNotEmpty()) text = text.dropLast(1)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(c.surface).padding(20.dp)
        ) {
            Text(S.exLogRepsTitle(exerciseTypeLabel(S, type)), fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = c.onSurface)
            Spacer(Modifier.height(14.dp))

            // ── Display field ──
            val count = text.toIntOrNull() ?: 0
            val isDeduction = count < 0
            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (text.isEmpty()) {
                    Text(S.exLogAmountHint, fontSize = 18.sp, color = c.textSecondary)
                } else {
                    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = if (isDeduction) c.error else c.onSurface)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Custom number pad: 0–9, minus, backspace ──
            val padRows = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf('-', '0', '⌫')
            )
            padRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        val keyColor = when (key) {
                            '-' -> c.error.copy(alpha = 0.15f)
                            '⌫' -> c.divider.copy(alpha = 0.5f)
                            else -> c.surfaceVariant
                        }
                        Box(
                            modifier = Modifier.weight(1f).height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(keyColor)
                                .clickable {
                                    if (key == '⌫') backspace() else append(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (key == '⌫') "⌫" else key.toString(),
                                fontSize = if (key == '⌫') 20.sp else 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when (key) {
                                    '-' -> c.error
                                    else -> c.onSurface
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(10.dp))

            // ── Cancel / Confirm buttons ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(c.divider.copy(alpha = 0.4f)).clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(S.cancel, fontSize = 13.sp, color = c.onSurface) }

                val enabled = count != 0
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (enabled) (if (isDeduction) c.error else c.primary) else c.divider)
                        .clickable(enabled = enabled) { onConfirm(count) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (isDeduction) S.exDeductReps else S.exLogReps, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun CongratsDialog(type: ExerciseType, onDismiss: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(c.surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🏆", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(S.exCongratsTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(S.exCongratsBody(exerciseTypeLabel(S, type)), fontSize = 13.sp,
                color = c.textSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(c.primary).clickable { onDismiss() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(S.done, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ── Small layout helpers ─────────────────────────────────────────────────────────

/** A single row of chips that scrolls horizontally if they overflow. */
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


