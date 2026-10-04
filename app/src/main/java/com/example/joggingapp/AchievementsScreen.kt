package com.example.joggingapp

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.joggingapp.ui.theme.JogginTheme
import com.example.joggingapp.ui.theme.JogginColorTokens

// ── Palette helpers ───────────────────────────────────────────────────────────

private val GoldGradient   = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000)))
private val SilverGradient = Brush.linearGradient(listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E)))
private val BronzeGradient = Brush.linearGradient(listOf(Color(0xFFCD7F32), Color(0xFF8D5524)))
private val LockedColor    = Color(0xFFB0BEC5)

private fun tierGradient(tier: AchievementTier) = when (tier) {
    AchievementTier.GOLD    -> GoldGradient
    AchievementTier.SILVER  -> SilverGradient
    AchievementTier.BRONZE  -> BronzeGradient
    AchievementTier.SPECIAL -> GoldGradient // uses gold as fallback; hero gradient applied via theme
}

private fun categoryColorFromTokens(cat: AchievementCategory, c: JogginColorTokens): Color {
    return when (cat) {
        AchievementCategory.RUNNING  -> c.running
        AchievementCategory.CYCLING  -> c.cycling
        AchievementCategory.WALKING  -> c.walking
        AchievementCategory.STREAKS  -> c.gold
        AchievementCategory.SPECIAL  -> c.primary
    }
}

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun AchievementsScreen(routes: List<SavedRoute>, onBack: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val lang = LocalLanguage.current
    val progressList = remember(routes) { evaluateAchievements(routes) }
    val totalCount   = progressList.size
    val earnedCount  = progressList.count { it.earnedDate != null }
    val pct          = if (totalCount > 0) earnedCount.toFloat() / totalCount else 0f

    // Pick a random motivational quote, re-picked if the language changes
    val motivation = remember(S) { S.motivations.random() }

    var selectedCategory by remember { mutableStateOf<AchievementCategory?>(null) }
    var sortMode by remember { mutableStateOf(0) } // 0=Default, 1=Almost Complete, 2=Newest Earned, 3=Longest Outstanding
    var sortAscending by remember { mutableStateOf(false) } // false=descending (default), true=ascending

    // Personal Targets view: a separate section toggled by the top-right badge.
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPersonal by remember { mutableStateOf(false) }
    val completedTargets = remember(showPersonal) { ExerciseTargetStorage.loadCompleted(context) }

    val filtered = remember(selectedCategory, sortMode, sortAscending) {
        val base = if (selectedCategory == null) progressList
            else progressList.filter { it.achievement.category == selectedCategory }
        val sorted = when (sortMode) {
            1 -> base.sortedByDescending { if (it.earnedDate == null) it.progress else -1f }
            2 -> base.sortedWith(compareByDescending<AchievementProgress> { it.earnedDate != null }.thenByDescending { it.progress })
            3 -> base.sortedBy { if (it.earnedDate == null && it.progress > 0f) it.progress else 2f }
            else -> base
        }
        if (sortAscending && sortMode != 0) sorted.reversed() else sorted
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)
        .pointerInput(Unit) {
            detectHorizontalDragGestures { _, dragAmount ->
                if (dragAmount > 20) onBack()  // swipe right → back to tracker
            }
        }
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth()
            .background(c.heroGradient)
            .padding(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)) {

            // Top-right badge: toggle between Medals (achievements) and Personal Targets.
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable { showPersonal = !showPersonal }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(if (showPersonal) S.exBadgeAchievements else S.exBadgePersonal,
                    fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("←", fontSize = 24.sp, color = Color.White,
                        modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
                    Text("🏆", fontSize = 28.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(S.myAchievements, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Text(motivation, fontSize = 13.sp, color = c.textSecondary)
                Spacer(Modifier.height(16.dp))

                // Progress bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.surfaceVariant)) {
                        Box(modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct)
                            .clip(RoundedCornerShape(50))
                            .background(c.heroGradient))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(S.progressCount(earnedCount, totalCount), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Text(S.percentComplete((pct * 100).toInt()),
                    fontSize = 11.sp, color = c.textSecondary)
            }
        }

        if (showPersonal) {
            // ── Personal Targets section (user's completed exercise targets) ──
            PersonalTargetsSection(completed = completedTargets)
            return@Column
        }

        // ── Completed medals showcase ─────────────────────────────────────────
        val earned = progressList.filter { it.earnedDate != null }
        var selectedMedal by remember { mutableStateOf<AchievementProgress?>(null) }
        if (earned.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(S.completedMedals(earned.size), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = c.onBackground)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    earned.forEach { ap ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(56.dp).clickable { selectedMedal = ap }) {
                            Box(modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(tierGradient(ap.achievement.tier)),
                                contentAlignment = Alignment.Center) {
                                Text(ap.achievement.icon, fontSize = 20.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(localizedAchievementTitle(lang, ap.achievement.id, ap.achievement.title), fontSize = 8.sp,
                                color = c.textSecondary, textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // Detail popup when a medal is tapped
                if (selectedMedal != null) {
                    val m = selectedMedal!!
                    Spacer(Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth(),
                        elevation = 4.dp, shape = RoundedCornerShape(12.dp),
                        backgroundColor = c.surfaceVariant) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .background(tierGradient(m.achievement.tier)),
                                    contentAlignment = Alignment.Center) {
                                    Text(m.achievement.icon, fontSize = 18.sp)
                                }
                                Column {
                                    Text(localizedAchievementTitle(lang, m.achievement.id, m.achievement.title), fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold, color = c.onBackground)
                                    Text(tierLabel(S, m.achievement.tier), fontSize = 10.sp, color = c.textSecondary)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(localizedAchievementDescription(lang, m.achievement.id, m.achievement.description), fontSize = 13.sp, color = c.onBackground)
                            Spacer(Modifier.height(6.dp))
                            Text(S.earnedOn(m.earnedDate ?: ""), fontSize = 11.sp, color = c.success)
                            Spacer(Modifier.height(10.dp))
                            Text(S.dismiss, fontSize = 12.sp, color = c.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { selectedMedal = null })
                        }
                    }
                }
            }
            Divider(color = c.divider)
        }

        // ── Category filter tabs ───────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(c.surface).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            FilterTab(S.catAll, null, selectedCategory) { selectedCategory = null }
            AchievementCategory.values().forEach { cat ->
                FilterTab(categoryLabel(S, cat),
                    cat, selectedCategory) { selectedCategory = cat }
            }
        }

        // ── Sort options ──────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(S.sortLabel, fontSize = 11.sp, color = c.textSecondary)
            val sortOptions = listOf(S.sortDefault, S.sortAlmostDone, S.sortEarned, S.sortOutstanding)
            sortOptions.forEachIndexed { idx, label ->
                val selected = sortMode == idx
                val arrow = if (selected && idx != 0) (if (sortAscending) " ↑" else " ↓") else ""
                Box(modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) c.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable {
                        if (sortMode == idx && idx != 0) {
                            sortAscending = !sortAscending  // toggle direction on re-tap
                        } else {
                            sortMode = idx
                            sortAscending = false  // reset to descending when switching sort
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("$label$arrow", fontSize = 10.sp,
                        color = if (selected) c.primary else c.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // ── Grid ──────────────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            EmptyState()
        } else {
            val gridState = rememberLazyGridState()
            // Scroll to top when sort changes
            LaunchedEffect(sortMode, sortAscending) {
                gridState.animateScrollToItem(0)
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.achievement.id }) { ap ->
                    AchievementCard(ap)
                }
            }
        }
    }
}

// ── Filter tab ────────────────────────────────────────────────────────────────

@Composable
private fun FilterTab(
    label: String,
    category: AchievementCategory?,
    selected: AchievementCategory?,
    onClick: () -> Unit
) {
    val isSelected = selected == category
    val c = JogginTheme.colors
    val bg = if (isSelected) {
        if (category == null) c.heroGradient
        else Brush.horizontalGradient(listOf(categoryColorFromTokens(category, c), categoryColorFromTokens(category, c)))
    } else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))

    Box(modifier = Modifier
        .clip(RoundedCornerShape(50))
        .then(if (!isSelected) Modifier.border(1.dp, c.textSecondary, RoundedCornerShape(50)) else Modifier)
        .background(bg)
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp,
            color = if (isSelected) Color.White else c.textSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Personal Targets section (shown on the Achievements page via the badge) ─────

@Composable
private fun PersonalTargetsSection(completed: List<CompletedTarget>) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text("🎯 ${S.exPersonalTargets}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
        Spacer(Modifier.height(12.dp))
        if (completed.isEmpty()) {
            Text(S.exPersonalTargetsEmpty, fontSize = 13.sp, color = c.textSecondary, lineHeight = 20.sp)
            return@Column
        }
        val fmt = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
        completed.forEach { ct ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(c.surfaceVariant).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(c.success.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(ct.type.defaultEmoji, fontSize = 20.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    val amount = if (ct.type.isDistance) String.format("%.0f km", ct.amount) else "${ct.amount.toInt()}"
                    Text(S.exTargetSummary(amount, exerciseTypeLabel(S, ct.type), periodLabel(S, ct.period)),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
                    Text(S.exAchievedOn(fmt.format(java.util.Date(ct.achievedAt))),
                        fontSize = 11.sp, color = c.success)
                }
                Text("🏅", fontSize = 20.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Achievement card ──────────────────────────────────────────────────────────

@Composable
private fun AchievementCard(ap: AchievementProgress) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val lang = LocalLanguage.current
    val a = ap.achievement
    val isUnlocked  = ap.earnedDate != null
    val isInProgress = !isUnlocked && ap.progress > 0f

    // Bounce-in animation
    val animatedScale = remember { Animatable(0.7f) }
    LaunchedEffect(a.id) {
        animatedScale.animateTo(1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow))
    }

    // Pulse animation for in-progress
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    // Tap to expand — enlarges the card and reveals the full (unclamped) content
    var expanded by remember { mutableStateOf(false) }
    val cardHeight by animateDpAsState(
        targetValue = if (expanded) 340.dp else 240.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cardHeight"
    )
    // Slight pop in scale + elevation while expanded so it lifts above neighbours
    val expandScale by animateFloatAsState(
        targetValue = if (expanded) 1.05f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "expandScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth().height(cardHeight).scale(animatedScale.value * expandScale)
            .clickable { expanded = !expanded }
            .border(if (expanded) 3.dp else 2.dp, if (isUnlocked) Color(0xFF4CAF50) else Color(0xFF9E9E9E), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        elevation = if (expanded) 12.dp else if (isUnlocked) 6.dp else 2.dp,
        backgroundColor = if (isUnlocked) Color.Transparent else c.surface.copy(alpha = 0.6f)
    ) {
        Box(modifier = Modifier.fillMaxSize()
            .then(if (isUnlocked) Modifier.background(
                when (a.tier) {
                    AchievementTier.GOLD -> Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFD700), Color(0xFFFFA000)))
                    AchievementTier.SILVER -> Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFE0E0E0), Color(0xFF9E9E9E)))
                    AchievementTier.BRONZE -> Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFCD7F32), Color(0xFF8D5524)))
                    AchievementTier.SPECIAL -> Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFD700), Color(0xFFFFA000)))
                }
            ) else Modifier)
        ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(4.dp))

            // Badge medallion
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).scale(if (isInProgress) pulseScale else 1f)) {

                // Outer glow ring
                if (isUnlocked) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(tierGradient(a.tier)))
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(c.surface))
                }

                // Progress ring for in-progress
                if (isInProgress) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                        val stroke = Stroke(width = 5f, cap = StrokeCap.Round)
                        drawArc(color = c.divider, startAngle = -90f,
                            sweepAngle = 360f, useCenter = false, style = stroke)
                        drawArc(brush = tierGradient(a.tier), startAngle = -90f,
                            sweepAngle = 360f * ap.progress, useCenter = false, style = stroke)
                    }
                    // percentage in centre
                    Text("${(ap.progress * 100).toInt()}%",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = c.textSecondary,
                        modifier = Modifier.offset(y = 20.dp))
                }

                // Locked
                if (!isUnlocked && !isInProgress) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(c.divider))
                    Text("🔒", fontSize = 16.sp, modifier = Modifier.offset(y = 10.dp))
                }

                // Emoji icon
                Text(if (isUnlocked) a.icon else a.icon,
                    fontSize = if (isUnlocked) 28.sp else 22.sp,
                    modifier = Modifier.graphicsLayer(
                        alpha = if (isUnlocked) 1f else 0.4f
                    )
                )
            }

            // Sparkle dots for unlocked
            if (isUnlocked) {
                SparkleRow()
            }

            Spacer(Modifier.height(8.dp))

            // Tier badge pill
            Box(modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (isUnlocked) tierGradient(a.tier) else Brush.horizontalGradient(listOf(LockedColor, LockedColor)))
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(tierLabel(S, a.tier), fontSize = 10.sp, color = Color.White,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }

            Spacer(Modifier.height(6.dp))

            Text(localizedAchievementTitle(lang, a.id, a.title),
                fontSize = if (expanded) 17.sp else 15.sp, fontWeight = FontWeight.Bold,
                color = if (isUnlocked) c.onBackground else c.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                lineHeight = if (expanded) 20.sp else 17.sp,
                overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(3.dp))

            Text(localizedAchievementDescription(lang, a.id, a.description),
                fontSize = if (expanded) 14.sp else 12.sp, color = Color(0xFF3D3D3D),
                textAlign = TextAlign.Center,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                lineHeight = if (expanded) 18.sp else 15.sp,
                overflow = TextOverflow.Ellipsis)

            if (isUnlocked && ap.earnedDate != null) {
                Spacer(Modifier.height(6.dp))
                Text("✓ ${ap.earnedDate}", fontSize = 14.sp,
                    color = c.success, fontWeight = FontWeight.Medium)
            }

            if (isInProgress || (!isUnlocked && a.requirementUnit != "special")) {
                Spacer(Modifier.height(8.dp))
                val progressPct = ap.progress.coerceIn(0f, 1f)
                val current = (a.requirement * progressPct)
                val unit = if (a.requirementUnit.startsWith("km")) S.unitKm else S.unitDays
                Text("${String.format("%.1f", current)} / ${String.format("%.0f", a.requirement)} $unit",
                    fontSize = 11.sp, color = c.textSecondary)
                Spacer(Modifier.height(4.dp))

                // Shimmering blue bar when progress > 75%
                if (progressPct > 0.75f) {
                    val shimmerAnim = rememberInfiniteTransition(label = "shimmer")
                    val shimmerOffset by shimmerAnim.animateFloat(
                        initialValue = 0f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                        label = "shimmerOffset"
                    )
                    Box(modifier = Modifier.fillMaxWidth(0.88f).height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.divider)) {
                        Box(modifier = Modifier.fillMaxWidth(progressPct).fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF1565C0),
                                    Color(0xFF42A5F5),
                                    Color(0xFFBBDEFB),
                                    Color(0xFF42A5F5),
                                    Color(0xFF1565C0)
                                ),
                                startX = shimmerOffset * 600f - 200f,
                                endX = shimmerOffset * 600f + 200f
                            )))
                    }
                } else {
                    LinearProgressIndicator(
                        progress = progressPct,
                        modifier = Modifier.fillMaxWidth(0.88f).height(4.dp).clip(RoundedCornerShape(50)),
                        color = categoryColorFromTokens(a.category, c),
                        backgroundColor = c.divider
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
        // Green tick overlay for completed achievements
        if (isUnlocked) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .size(24.dp).clip(CircleShape).background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        }
    }
}

// ── Sparkle decoration for earned badges ──────────────────────────────────────

@Composable
private fun SparkleRow() {
    val c = JogginTheme.colors
    val sparkColors = listOf(c.primary, c.accent, c.gold)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp)) {
        sparkColors.forEach { col ->
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(col))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)) {
            Text("🏆", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(S.trophyCaseWaiting, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = c.onBackground,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(S.completeFirstActivity,
                fontSize = 14.sp, color = c.textSecondary,
                textAlign = TextAlign.Center, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(50))
                .background(c.primary)
                .padding(horizontal = 28.dp, vertical = 14.dp)) {
                Text(S.startAnActivity, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
