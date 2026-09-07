package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.TasbihaTree
import com.example.data.FamilyMemberWithTasbiha
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun TasbihaScreen(viewModel: FamilyViewModel) {
    val selectedTree = viewModel.selectedTree
    val myAllTrees = viewModel.myAllTrees
    val familyTrees = viewModel.familyTasbiha
    val familyMembers = viewModel.getFamilyMembersWithTrees()

    LaunchedEffect(Unit) {
        viewModel.loadTasbiha()
    }

    var showSplash by remember { mutableStateOf(true) }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = {
            (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.8f, animationSpec = tween(600)))
                .togetherWith(fadeOut(animationSpec = tween(400)))
        },
        label = "splash"
    ) { isSplash ->
        if (isSplash) {
            TasbihaSplashScreen(onEnter = { showSplash = false })
        } else {
            TasbihaMainContent(
                viewModel = viewModel,
                selectedTree = selectedTree,
                myAllTrees = myAllTrees,
                familyMembers = familyMembers
            )
        }
    }
}

@Composable
private fun TasbihaSplashScreen(onEnter: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    var enterPressed by remember { mutableStateOf(false) }
    val enterScale by animateFloatAsState(
        targetValue = if (enterPressed) 0.9f else 1f,
        animationSpec = tween(150),
        label = "enterScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B5E20),
                        Color(0xFF2E7D32),
                        Color(0xFF43A047)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Floating particles in background
        val particles = remember { List(20) { kotlin.random.Random.nextFloat() } }
        particles.forEachIndexed { index, seed ->
            val x by infiniteTransition.animateFloat(
                initialValue = (seed * 400f) - 200f,
                targetValue = (seed * 400f) - 200f + 60f * sin(index.toFloat()),
                animationSpec = infiniteRepeatable(
                    animation = tween(3000 + index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "px$index"
            )
            val y by infiniteTransition.animateFloat(
                initialValue = -200f,
                targetValue = 800f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000 + index * 300, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "py$index"
            )
            Box(
                modifier = Modifier
                    .offset(x = (x + 200).dp, y = y.dp)
                    .size((4 + index % 4).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f + (index % 3) * 0.05f))
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // حشو سفلي بارتفاع شريط التنقل العايم (74.dp في ZadShell) + هامش.
            // المحتوى متوسّط في Box بملء الشاشة، والشريط بيعوم فوقه — فزر "ادخل
            // البستان" كان بيتقص تحته. اتشاف في أول اختبار على جهاز حقيقي
            // 2026-09-06: الشاشة بتفتح والزر شبه مختفي، فالعميل يفتكر إنه مش موجود.
            modifier = Modifier
                .scale(enterScale)
                .padding(bottom = 90.dp)
        ) {
            // Rotating ring behind tree
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.4f),
                                Color.Transparent,
                                Color(0xFF4CAF50).copy(alpha = 0.3f),
                                Color.Transparent,
                                Color(0xFFFFD700).copy(alpha = 0.4f)
                            )
                        )
                    )
            )

            // Tree emoji (centered over the ring)
            Box(
                modifier = Modifier
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = glowAlpha),
                                    Color(0xFF4CAF50).copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    Icons.Default.Park,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).scale(breatheScale),
                    tint = Color(0xFF2E7D32)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                stringResource(R.string.tasbiha_garden),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = glowAlpha
                }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.tasbiha_splash_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(48.dp))

            // Enter button
            Button(
                onClick = {
                    enterPressed = true
                    onEnter()
                },
                modifier = Modifier
                    .width(220.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF2E7D32)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.enter_garden_action),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TasbihaMainContent(
    viewModel: FamilyViewModel,
    selectedTree: TasbihaTree?,
    myAllTrees: List<TasbihaTree>,
    familyMembers: List<FamilyMemberWithTasbiha>
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tasbiha_tab_my_garden),
        stringResource(R.string.tasbiha_tab_family_garden),
        stringResource(R.string.tasbiha_tab_challenges)
    )

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        contentVisible = true
    }

    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(tween(400)) + slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The design has no full-bleed per-screen header — the title lives in
            // ZadTopHeader, and a screen's own headline number goes in the same inset
            // mesh-gradient banner Subscriptions and Shopping use. This was a
            // hard-edged green band butted against the top of the content.
            com.example.ui.components.ZadScreenBanner(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                contentPadding = 18.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.tasbiha_garden),
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.tasbiha_trees_and_score, myAllTrees.size, myAllTrees.sumOf { it.score }),
                            style = Typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            com.example.ui.components.ZadSegmentedTabs(
                tabs = tabs,
                selectedIndex = selectedTabIndex,
                onSelect = { selectedTabIndex = it }
            )

            when (selectedTabIndex) {
                0 -> MyGardenTab(
                    selectedTree = selectedTree,
                    myAllTrees = myAllTrees,
                    onSelectTree = { viewModel.selectTree(it) },
                    onTap = { viewModel.tasbihaClick() },
                    onRename = { viewModel.renameTasbiha(it) },
                    onCreateNew = { viewModel.createNewTree(it) }
                )
                1 -> FamilyGardenTab(familyMembers = familyMembers)
                2 -> ChallengesTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun MyGardenTab(
    selectedTree: TasbihaTree?,
    myAllTrees: List<TasbihaTree>,
    onSelectTree: (TasbihaTree) -> Unit,
    onTap: () -> Unit,
    onRename: (String) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = ZadHubListBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            if (selectedTree != null) {
                AnimatedTreeDisplay(
                    tree = selectedTree,
                    onTap = onTap,
                    onRenameClick = { showRename = true }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            if (selectedTree != null) {
                TasbihaStats(selectedTree)
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.my_trees_count, myAllTrees.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.new_tree_action))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        item {
            val chunked = myAllTrees.chunked(3)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                chunked.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { tree ->
                            Box(modifier = Modifier.weight(1f)) {
                                TreeMiniCard(
                                    tree = tree,
                                    isSelected = tree.id == selectedTree?.id,
                                    onClick = { onSelectTree(tree) }
                                )
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

    }

    if (showRename && selectedTree != null) {
        RenameDialog(
            currentName = selectedTree.treeName,
            onConfirm = { newName ->
                onRename(newName)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    if (showCreateDialog) {
        CreateTreeDialog(
            onConfirm = { name ->
                onCreateNew(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun AnimatedTreeDisplay(
    tree: TasbihaTree,
    onTap: () -> Unit,
    onRenameClick: () -> Unit
) {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Tap bounce
    var scaleAnim by remember { mutableStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = scaleAnim,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        finishedListener = { scaleAnim = 1f }
    )

    // Idle tree sway
    val infiniteTransition = rememberInfiniteTransition(label = "tree")
    val idleSway by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    // Tap rotation kick
    var tapRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = tapRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        finishedListener = { tapRotation = 0f }
    )

    // نمو مستمر — الشجرة تكبر شوية بشوية مع كل تسبيحة جوه نفس المرحلة، مش قفزة مفاجئة بس عند تغيير level
    val growthProgress = tree.progressToNext()
    val animatedGrowth by animateFloatAsState(
        targetValue = 0.82f + (growthProgress * 0.35f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "growth"
    )

    // Pulse glow intensity on tap
    var glowPulse by remember { mutableFloatStateOf(0.3f) }
    val animatedGlow by animateFloatAsState(
        targetValue = glowPulse,
        animationSpec = tween(300),
        finishedListener = { glowPulse = 0.3f }
    )

    // Level up
    val showLevelUpAnim = remember { mutableStateOf(false) }
    var prevLevel by remember { mutableIntStateOf(tree.level) }
    LaunchedEffect(tree.level) {
        if (tree.level > prevLevel) {
            showLevelUpAnim.value = true
            try {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
            delay(2500)
            showLevelUpAnim.value = false
        }
        prevLevel = tree.level
    }

    // Level-based background
    val levelBgColors = when {
        tree.level >= 5 -> listOf(Color(0xFFE8F5E9).copy(alpha = 0.6f), Color(0xFFC8E6C9).copy(alpha = 0.3f))
        tree.level >= 4 -> listOf(Color(0xFFF1F8E9).copy(alpha = 0.6f), Color(0xFFDCEDC8).copy(alpha = 0.3f))
        tree.level >= 3 -> listOf(Color(0xFFE8F5E9).copy(alpha = 0.4f), Color(0xFFE0F2F1).copy(alpha = 0.2f))
        tree.level >= 2 -> listOf(Color(0xFFE0F2F1).copy(alpha = 0.3f), Color(0xFFE8EAF6).copy(alpha = 0.2f))
        else -> listOf(primaryContainer.copy(alpha = 0.2f), Color.Transparent)
    }

    // Particle offset
    val particleOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "particle"
    )

    // أوراق متساقطة زخرفية — بتزيد مع نمو الشجرة، إحساس بستان حي مش أيقونة ثابتة
    val ambientLeafCount = (tree.level - 1).coerceIn(0, 4)
    val leafCycle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "leafCycle"
    )

    // Floating emojis — fixed overflow bug
    data class TapParticle(val id: Int, val angle: Float, val distance: Float, val size: Float, val color: Color)
    val particles = remember { mutableStateListOf<TapParticle>() }
    var particleId by remember { mutableIntStateOf(0) }

    // Add particles immediately (no LaunchedEffect race condition)
    fun spawnParticles() {
        val count = if (tapCount % 50 == 0) 8 else 4
        repeat(count) { i ->
            particleId++
            particles.add(
                TapParticle(
                    id = particleId,
                    angle = (i * (360f / count)) + kotlin.random.Random.nextFloat() * 20f,
                    distance = 40f + kotlin.random.Random.nextFloat() * 50f,
                    size = 14f + kotlin.random.Random.nextFloat() * 10f,
                    color = listOf(Color(0xFFFFD700), Color(0xFF4CAF50), Color(0xFF81C784), Color(0xFFFF9800))[kotlin.random.Random.nextInt(4)]
                )
            )
        }
        // Keep max 20 particles — remove oldest
        while (particles.size > 20) {
            if (particles.isNotEmpty()) particles.removeAt(0)
        }
    }

    // Auto-remove particles after timeout (non-cancellable)
    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            if (particles.isNotEmpty()) {
                particles.removeAt(0)
            }
        }
    }

    com.example.ui.components.ZadListCard(
        shape = RoundedCornerShape(24.dp),
        containerColor = when (tree.treeType) {
            "golden" -> Color(0xFFFFF8E1)
            "special" -> Color(0xFFF3E5F5)
            else -> surface
        },
        contentPadding = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(levelBgColors))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onRenameClick() }
            ) {
                if (tree.treeType != "normal") {
                    Text(tree.typeEmoji(), fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(tree.treeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Edit, null, tint = primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tree.stageName(), style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
                if (tree.streakDays > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    com.example.ui.components.ZadStatusPill(
                        text = stringResource(R.string.streak_days_count, tree.streakDays),
                        color = onStatusPill,
                        containerColor = coral.copy(alpha = 0.12f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Tap area
            Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                // Pulsing glow ring
                Box(
                    modifier = Modifier
                        .size((180 + animatedGlow * 40).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = when (tree.treeType) {
                                    "golden" -> listOf(Color(0xFFFFD700).copy(alpha = animatedGlow), Color.Transparent)
                                    "special" -> listOf(lilac.copy(alpha = animatedGlow), Color.Transparent)
                                    else -> listOf(primary.copy(alpha = animatedGlow), Color.Transparent)
                                }
                            )
                        )
                )

                // أوراق متساقطة زخرفية — عدد ثابت حسب مستوى الشجرة، كل ورقة بمرحلة زمنية مختلفة
                if (ambientLeafCount > 0) {
                    repeat(ambientLeafCount) { i ->
                        val phase = (leafCycle + i.toFloat() / ambientLeafCount) % 1f
                        val xOffset = (-50 + i * 34).dp
                        val yOffset = (-100 + phase * 190).dp
                        Text(
                            "🍃",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .offset(x = xOffset, y = yOffset)
                                .graphicsLayer {
                                    alpha = (1f - phase) * 0.7f
                                    rotationZ = phase * 180f
                                }
                        )
                    }
                }

                // Level up burst
                if (showLevelUpAnim.value) {
                    for (i in 0..7) {
                        val angle = (i * 45f) * Math.PI / 180f
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .offset(
                                    x = (100 * cos(particleOffset * 2 * Math.PI + angle)).dp,
                                    y = (100 * sin(particleOffset * 2 * Math.PI + angle)).dp
                                )
                                .clip(CircleShape)
                                .background(listOf(Color(0xFFFFD700), Color(0xFF4CAF50), Color(0xFFFF9800))[i % 3])
                        )
                    }

                    // احتفال المستوى (milestone): كونفيتي لحظة ترقية الشجرة لمستوى جديد
                    ZadLottieAsset(
                        resId = R.raw.lottie_confetti_burst,
                        iterations = 1,
                        modifier = Modifier.size(240.dp),
                        contentDescription = stringResource(R.string.tree_level_up_celebration)
                    )
                }

                // Tap particles
                particles.forEach { p ->
                    val rad = Math.toRadians(p.angle.toDouble())
                    val dist = p.distance * particleOffset
                    Box(
                        modifier = Modifier
                            .offset(x = (dist * cos(rad).toFloat()).dp, y = (dist * sin(rad).toFloat()).dp)
                            .size(p.size.dp)
                            .clip(CircleShape)
                            .background(p.color)
                    )
                }

                // The Tree
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.1f), primaryContainer)))
                        .clickable {
                            scaleAnim = 1.4f
                            tapRotation += 8f
                            glowPulse = 0.8f
                            tapCount++
                            spawnParticles()
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            // Vibrate — VibrationEffect ماوصلش غير في API 26، وminSdk 24.
                            // الـ try/catch مش كان بيحمي: كلاس ناقص بيرمي NoClassDefFoundError
                            // وده Error مش Exception، فكان بيعدي منه ويكسّر التطبيق على 24-25
                            // مع كل ضغطة تسبيحة. نفس الحارس اللي CameraScreen مستعمله.
                            try {
                                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    vib?.vibrate(android.os.VibrationEffect.createOneShot(30, 200))
                                } else {
                                    @Suppress("DEPRECATION") vib?.vibrate(30)
                                }
                            } catch (_: Exception) {}
                            onTap()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tree.stageEmoji(),
                            fontSize = if (showLevelUpAnim.value) 56.sp else 40.sp,
                            modifier = Modifier
                                .scale((if (showLevelUpAnim.value) 1.4f else animatedScale) * animatedGrowth)
                                .graphicsLayer {
                                    rotationZ = idleSway + animatedRotation
                                }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${tree.score}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = primary)
                        Text(stringResource(R.string.tasbiha_short_label), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress bar
            val progress = tree.progressToNext()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when (tree.treeType) { "golden" -> Color(0xFFFFD700); "special" -> lilac; else -> primary },
                trackColor = onSurface.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (tree.level < 5) stringResource(R.string.progress_to_next_stage, tree.score, tree.nextLevelAt()) else stringResource(R.string.tree_completed_label),
                style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant
            )

            // Celebration
            AnimatedVisibility(
                visible = showLevelUpAnim.value,
                enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.5f, animationSpec = tween(400)),
                exit = fadeOut(tween(300))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(20.dp), tint = primary)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tree_level_up_celebration), fontWeight = FontWeight.Bold, color = primary)
                }
            }
        }
    }
}

@Composable
private fun TasbihaStats(tree: TasbihaTree) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCard(stringResource(R.string.stat_level_label), "${tree.level}/5") { Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF2E7D32)) }
        StatCard(stringResource(R.string.stat_points_label), "${tree.score}") { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp)) }
        StatCard(stringResource(R.string.stat_total_label), "${tree.totalClicks}") { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp)) }
        StatCard(stringResource(R.string.stat_streak_label), "${tree.streakDays}") { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFFF5722)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: @Composable () -> Unit) {
    // 16dp radius and the two-layer card shadow, like every other tile in the design —
    // this was a flat 12dp Material Card with no elevation on a same-white canvas.
    val statShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .zadCardShadow(statShape)
            .clip(statShape)
            .background(surface)
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

@Composable
private fun TreeMiniCard(
    tree: TasbihaTree,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // white tile with the shared shadow; selection is a green ring, not a grey fill
    val miniShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .zadCardShadow(miniShape)
            .clip(miniShape)
            .background(surface)
            .then(if (isSelected) Modifier.border(2.dp, primary, miniShape) else Modifier)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(tree.stageEmoji(), fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                tree.treeName.take(8),
                fontSize = 10.sp,
                color = onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                "${tree.score}",
                fontSize = 10.sp,
                color = primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FamilyGardenTab(familyMembers: List<FamilyMemberWithTasbiha>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = ZadHubListBottomPadding)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color(0xFFFFD700))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.leaderboard_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        items(familyMembers) { memberData ->
            FamilyMemberTreeCard(memberData)
            Spacer(Modifier.height(8.dp))
        }

    }
}

@Composable
private fun FamilyMemberTreeCard(memberData: FamilyMemberWithTasbiha) {
    com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(memberData.member.alias.take(1), color = primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(memberData.member.alias, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(
                        stringResource(R.string.member_trees_and_mature, memberData.trees.size, memberData.matureTrees),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${memberData.totalScore}", fontWeight = FontWeight.Bold, color = primary, fontSize = 20.sp)
                    Text(stringResource(R.string.tasbiha_short_label), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memberData.trees.take(5)) { tree ->
                    MiniTreeCard(tree)
                }
                if (memberData.trees.size > 5) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "+${memberData.trees.size - 5}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTreeCard(tree: TasbihaTree) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tree.stageEmoji(), fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                tree.treeName.take(6),
                fontSize = 8.sp,
                color = onSurface,
                maxLines = 1
            )
            Text(
                "${tree.score}",
                fontSize = 8.sp,
                color = primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChallengesTab(viewModel: FamilyViewModel) {
    val familyState by viewModel.state.collectAsState()
    val isAdmin = (familyState as? com.example.ui.viewmodels.FamilyState.Active)?.myMemberInfo?.role == "admin"
    val challenges = viewModel.activeChallenges
    val progress = viewModel.tasbihaChallengeProgress
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = ZadHubListBottomPadding)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TrackChanges, contentDescription = null, modifier = Modifier.size(24.dp), tint = primary)
                    Text(stringResource(R.string.family_challenges_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                }
                // UI_ARCHITECTURE_SPEC.md §7.4 — createChallenge() كان مفيهوش زرار خالص،
                // الشاشة الفاضية كانت بتقول "اسأل المشرف" بدون ما تدّي المشرف نفسه وسيلة.
                if (isAdmin) {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_challenge_action), tint = primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.challenge_family_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        if (challenges.isEmpty()) {
            item {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.TrackChanges,
                    title = stringResource(R.string.no_challenges_yet),
                    subtitle = if (isAdmin) null else stringResource(R.string.ask_admin_new_challenge),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        } else {
            items(challenges) { challenge ->
                ChallengeCard(challenge, progress[challenge.id])
                Spacer(Modifier.height(8.dp))
            }
        }

    }

    if (showCreateDialog) {
        CreateTasbihaChallengeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, challengeType, targetClicks ->
                viewModel.createTasbihaChallenge(title, description, challengeType, targetClicks, null)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ChallengeCard(challenge: com.example.data.TasbihaChallenge, progress: com.example.data.TasbihaChallengeProgress?) {
    // white card, like every other row in the design — this was a 10%-primary tint,
    // the only card in the app filled with a wash of the brand colour
    com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(challenge.title, fontWeight = FontWeight.Bold, color = onSurface)
                com.example.ui.components.ZadStatusPill(
                    text = challenge.challengeType,
                    color = primary
                )
            }
            if (challenge.description != null) {
                Spacer(Modifier.height(8.dp))
                Text(challenge.description, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            // UI_ARCHITECTURE_SPEC.md §7.4 — كان بيعرض target_clicks/endDate بس، مفيش
            // تقدم المستخدم الحالي ظاهر خالص رغم إن getChallengeProgress() موجودة.
            val currentClicks = progress?.currentClicks ?: 0
            val fraction = (currentClicks.toFloat() / challenge.targetClicks.coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (fraction >= 1f) successColor else primary,
                trackColor = primary.copy(alpha = 0.12f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.challenge_progress_label, currentClicks, challenge.targetClicks),
                    style = MaterialTheme.typography.labelMedium,
                    color = primary
                )
                Text(
                    "⏰ ${challenge.endDate?.take(10) ?: stringResource(R.string.challenge_ongoing_label)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateTasbihaChallengeDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, challengeType: String, targetClicks: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var challengeType by remember { mutableStateOf("weekly") }
    var targetClicksStr by remember { mutableStateOf("100") }
    val types = listOf("weekly", "monthly")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_challenge_action), style = Typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.financial_challenge_title_hint)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.tasbiha_challenge_description_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetClicksStr,
                    onValueChange = { v -> if (v.all { it.isDigit() }) targetClicksStr = v },
                    label = { Text(stringResource(R.string.tasbiha_challenge_target_hint)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t ->
                        FilterChip(selected = challengeType == t, onClick = { challengeType = t }, label = { Text(t) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = targetClicksStr.toIntOrNull() ?: 100
                    if (title.isNotBlank()) onCreate(title.trim(), description.trim().ifBlank { null }, challengeType, target)
                },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_tree_title)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.tree_name_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().imePadding()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CreateTreeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_tree_action)) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                Text(
                    stringResource(R.string.add_tree_to_garden_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tree_name_hint)) },
                    placeholder = { Text(stringResource(R.string.tree_name_placeholder_example)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
