package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TasbihaTree
import com.example.data.FamilyMemberWithTasbiha
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun TasbihaScreen(viewModel: FamilyViewModel, onOpenDrawer: () -> Unit = {}) {
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
                familyMembers = familyMembers,
                onOpenDrawer = onOpenDrawer
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
            modifier = Modifier.scale(enterScale)
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
                "بستان التسبيحة",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = glowAlpha
                }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "نمِ شجرتك بالتسبيحة",
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
                    "ادخل البستان",
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
    familyMembers: List<FamilyMemberWithTasbiha>,
    onOpenDrawer: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("بستاني", "أشجار العائلة", "التحديات")

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
        Column(modifier = Modifier.fillMaxSize().background(background)) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(primary, primaryLight)
                        )
                    )
                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "بستان التسبيحة",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${myAllTrees.size} أشجار | ${myAllTrees.sumOf { it.score }} تسبيحة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = surface,
                contentColor = primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

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
                2 -> ChallengesTab(challenges = viewModel.activeChallenges)
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
                    "أشجاري (${myAllTrees.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("شجرة جديدة")
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

        item { Spacer(Modifier.height(24.dp)) }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (tree.treeType) {
                "golden" -> Color(0xFFFFF8E1)
                "special" -> Color(0xFFF3E5F5)
                else -> surfaceContainer
            }
        )
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
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF5722).copy(alpha = 0.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF5722))
                            Spacer(Modifier.width(4.dp))
                            Text("${tree.streakDays} أيام", fontSize = 12.sp, color = Color(0xFFFF5722), fontWeight = FontWeight.Bold)
                        }
                    }
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
                                    "special" -> listOf(Color(0xFF9C27B0).copy(alpha = animatedGlow), Color.Transparent)
                                    else -> listOf(primary.copy(alpha = animatedGlow), Color.Transparent)
                                }
                            )
                        )
                )

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
                            // Vibrate
                            try {
                                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                vib?.vibrate(android.os.VibrationEffect.createOneShot(30, 200))
                            } catch (_: Exception) {}
                            onTap()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Park,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (showLevelUpAnim.value) 80.dp else 56.dp)
                                .scale(if (showLevelUpAnim.value) 1.4f else animatedScale)
                                .graphicsLayer {
                                    rotationZ = idleSway + animatedRotation
                                }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${tree.score}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = primary)
                        Text("تسبيحة", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress bar
            val progress = tree.progressToNext()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when (tree.treeType) { "golden" -> Color(0xFFFFD700); "special" -> Color(0xFF9C27B0); else -> primary },
                trackColor = onSurface.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (tree.level < 5) "${tree.score} / ${tree.nextLevelAt()} للمرحلة التالية" else "اكتملت!",
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
                    Text("مبروك! شجرتك كبرت!", fontWeight = FontWeight.Bold, color = primary)
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
        StatCard("المستوى", "${tree.level}/5") { Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF2E7D32)) }
        StatCard("النقاط", "${tree.score}") { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp)) }
        StatCard("إجمالي", "${tree.totalClicks}") { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp)) }
        StatCard("السلسلة", "${tree.streakDays}") { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFFF5722)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer)
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
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) primaryContainer else surfaceContainer
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFF2E7D32))
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
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color(0xFFFFD700))
                Spacer(Modifier.width(8.dp))
                Text(
                    "لوحة المتصدرين",
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

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FamilyMemberTreeCard(memberData: FamilyMemberWithTasbiha) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer)
    ) {
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
                        "${memberData.trees.size} أشجار | ${memberData.matureTrees} مثمرة",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${memberData.totalScore}", fontWeight = FontWeight.Bold, color = primary, fontSize = 20.sp)
                    Text("تسبيحة", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
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
            Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF2E7D32))
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
private fun ChallengesTab(challenges: List<com.example.data.TasbihaChallenge>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrackChanges, contentDescription = null, modifier = Modifier.size(24.dp), tint = primary)
                Text("التحديات العائلية", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "تحدَ أفراد عائلتك في التسبيحة!",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        if (challenges.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.TrackChanges, contentDescription = null, modifier = Modifier.size(48.dp), tint = onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "لا توجد تحديات حالياً",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurface
                        )
                        Text(
                            "اطلب من المدير إنشاء تحدي جديد!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(challenges) { challenge ->
                ChallengeCard(challenge)
                Spacer(Modifier.height(8.dp))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChallengeCard(challenge: com.example.data.TasbihaChallenge) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(challenge.title, fontWeight = FontWeight.Bold, color = onSurface)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        challenge.challengeType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (challenge.description != null) {
                Spacer(Modifier.height(8.dp))
                Text(challenge.description, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الهدف: ${challenge.targetClicks} تسبيحة",
                    style = MaterialTheme.typography.labelMedium,
                    color = primary
                )
                Text(
                    "⏰ ${challenge.endDate?.take(10) ?: "مستمر"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسمية الشجرة") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("اسم الشجرة") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun CreateTreeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("شجرة جديدة") },
        text = {
            Column {
                Text(
                    "أضف شجرة جديدة لبستانك!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم الشجرة") },
                    placeholder = { Text("مثال: شجرة التفاح") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("إضافة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
