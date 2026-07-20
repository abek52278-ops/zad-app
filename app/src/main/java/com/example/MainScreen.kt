package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.*
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TasbihaScreen
import com.example.ui.screens.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    // Drawer specific screens (12 items)
    object Home : Screen("home", R.string.nav_home, Icons.Filled.Home)
    object MotherTools : Screen("mother_tools", R.string.nav_mother_tools, Icons.Filled.Face)
    object Inventory : Screen("inventory", R.string.nav_inventory, Icons.Filled.Inventory2)
    object Shopping : Screen("shopping", R.string.nav_shopping, Icons.Filled.ShoppingCart)
    object Budget : Screen("budget", R.string.nav_budget, Icons.Filled.AccountBalanceWallet)
    object Wishlist : Screen("wishlist", R.string.nav_wishlist, Icons.Filled.Favorite)
    object Assistant : Screen("assistant", R.string.nav_assistant, Icons.Filled.SmartToy)
    object Family : Screen("family", R.string.nav_family, Icons.Filled.FamilyRestroom)
    object Reports : Screen("reports", R.string.nav_reports, Icons.Filled.Assessment)
    object Store : Screen("store", R.string.nav_store, Icons.Filled.Store)
    object HelpSupport : Screen("help_support", R.string.nav_help, Icons.Filled.Help)
    object Profile : Screen("profile", R.string.nav_profile, Icons.Filled.Person)
    
    // Bottom Bar specific screens
    object Features : Screen("features", R.string.app_name, Icons.Filled.GridView)
    object Camera : Screen("camera", R.string.app_name, Icons.Filled.CameraAlt)
    object Subscriptions : Screen("subscriptions", R.string.app_name, Icons.Filled.CalendarToday)
    object Analytics : Screen("analytics", R.string.app_name, Icons.Filled.PieChart)
    
    // Sub-screens
    object EditProfile : Screen("edit_profile", R.string.nav_profile, Icons.Filled.Person)
    object FamilyManagement : Screen("family_management", R.string.nav_family, Icons.Filled.FamilyRestroom)
    object PaymentBudget : Screen("payment_budget", R.string.nav_budget, Icons.Filled.AccountBalanceWallet)
    object AssistantAlerts : Screen("assistant_alerts", R.string.nav_assistant, Icons.Filled.SmartToy)
    object TermsOfService : Screen("terms_of_service", R.string.nav_profile, Icons.Filled.Description)
    object RecipeDetail : Screen("recipe_detail", R.string.app_name, Icons.Filled.RestaurantMenu)
    object Tasbiha : Screen("tasbiha", R.string.app_name, Icons.Filled.Spa)
}

@Composable
fun MainScreen(onLogout: () -> Unit = {}, pendingInviteCode: String? = null) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val viewModel: ZadViewModel = viewModel()
    val familyViewModel: FamilyViewModel = viewModel()

    // تغذية سياق العائلة لشات زاد — عشان يعرف كل حاجة عن العيلة
    val familyStateForChat by familyViewModel.state.collectAsState()
    LaunchedEffect(familyStateForChat) {
        viewModel.updateFamilyContext(familyStateForChat)
    }

    val drawerScreens = listOf(
        Screen.Home,
        Screen.Inventory,
        Screen.Assistant,
        Screen.Shopping,
        Screen.Family,
        Screen.Budget,
        Screen.Profile
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = surface,
                drawerContentColor = onSurface
            ) {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Spacer(Modifier.height(40.dp))
                    // Logo + Brand
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_carrot_logo),
                        contentDescription = "ZAD Logo",
                        modifier = Modifier.size(44.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = Typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp),
                        color = Color(0xFF0D5C3F) // Requested Green
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                drawerScreens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationDrawerItem(
                        label = {
                            Text(
                                stringResource(id = screen.titleRes),
                                style = Typography.titleMedium.copy(fontSize = 15.sp),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF0D5C3F).copy(alpha = 0.12f) else surfaceContainerLow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = null,
                                    tint = if (selected) Color(0xFF0D5C3F) else onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF0D5C3F).copy(alpha = 0.08f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color(0xFF0D5C3F),
                            unselectedTextColor = onSurfaceVariant,
                            selectedIconColor = Color(0xFF0D5C3F),
                            unselectedIconColor = onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f)) // Push profile to the bottom
                HorizontalDivider(color = outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
                
                // User Profile Section — Live from ViewModel
                val drawerUserName by viewModel.userName.collectAsState()
                val drawerAvatarUri by viewModel.avatarUri.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { drawerState.close() }
                            navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(2.dp, primaryFixed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!drawerAvatarUri.isNullOrBlank()) {
                            AsyncImage(
                                model = drawerAvatarUri,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    .background(primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (drawerUserName ?: "Z").take(1).uppercase(),
                                    style = Typography.titleLarge,
                                    color = primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            drawerUserName ?: "مستخدم زاد",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurface
                        )
                        Text("اضغط لعرض الملف الشخصي", style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Go",
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { BottomBar(navController = navController) { scope.launch { drawerState.open() } } }
        ) { innerPadding ->
            // Floating Chat Bubble
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    enterTransition = { com.example.ui.components.ZadTransitions.enter },
                    exitTransition = { com.example.ui.components.ZadTransitions.exit },
                    popEnterTransition = { com.example.ui.components.ZadTransitions.popEnter },
                    popExitTransition = { com.example.ui.components.ZadTransitions.popExit }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            viewModel = viewModel,
                            familyViewModel = familyViewModel,
                            onNavigateToAssistant = {
                                navController.navigate(Screen.Assistant.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToInventory = {
                                navController.navigate(Screen.Inventory.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToSubscriptions = {
                                navController.navigate(Screen.Subscriptions.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToShopping = {
                                navController.navigate(Screen.Shopping.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToFamily = {
                                navController.navigate(Screen.Family.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToBudget = {
                                navController.navigate(Screen.Budget.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToTasbiha = {
                                navController.navigate(Screen.Tasbiha.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToProfile = {
                                navController.navigate(Screen.Profile.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable(Screen.Inventory.route) {
                        InventoryScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigateToAssistant = {
                                navController.navigate(Screen.Assistant.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToCamera = {
                                navController.navigate(Screen.Camera.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(Screen.Subscriptions.route) { SubscriptionsScreen(viewModel) { scope.launch { drawerState.open() } } }
                    composable(Screen.Assistant.route) {
                        ZadIntelligenceScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable(Screen.Tasbiha.route) {
                        TasbihaScreen(
                            viewModel = familyViewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable(Screen.Family.route) {
                        val notifications by viewModel.appNotifications.collectAsState()
                        val unreadCount = notifications.count { !it.isRead }
                        FamilyScreen(
                            pendingInviteCode = pendingInviteCode,
                            viewModel = familyViewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            unreadNotificationCount = unreadCount,
                            onNotificationsClick = { navController.navigate(Screen.Profile.route) }
                        )
                    }
                    composable(Screen.Camera.route) { CameraScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
                    composable(Screen.Profile.route) { 
                        ProfileScreen(
                            viewModel = viewModel,
                            familyViewModel = familyViewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onLogout = onLogout,
                            navController = navController
                        ) 
                    }

                    composable(Screen.Budget.route) {
                        BudgetScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigateToAssistant = {
                                navController.navigate(Screen.Assistant.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToCamera = {
                                navController.navigate(Screen.Camera.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    
                    // Dummy screens for the new items so it doesn't crash
                    composable(Screen.MotherTools.route) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text("أدوات الأم") } }
                    composable(Screen.Shopping.route) { 
                        ShoppingListScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigateToAssistant = {
                                navController.navigate(Screen.Assistant.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        ) 
                    }
                    composable(Screen.Wishlist.route) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text("الطيبات") } }
                    composable(Screen.Reports.route) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text("التقارير") } }
                    composable(Screen.Store.route) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text("متجر زاد") } }
                    composable(Screen.HelpSupport.route) { 
                        HelpSupportScreen(onBack = { navController.popBackStack() }) 
                    }
                    composable(Screen.Features.route) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text("الميزات") } }
                    composable(Screen.Analytics.route) { ZadIntelligenceScreen(viewModel) { scope.launch { drawerState.open() } } }

                    // Sub-screens
                    composable(Screen.EditProfile.route) {
                        EditProfileScreen(viewModel) { navController.popBackStack() }
                    }
                    composable(Screen.FamilyManagement.route) {
                        FamilyManagementScreen(familyViewModel) { navController.popBackStack() }
                    }
                    composable(Screen.PaymentBudget.route) {
                        PaymentAndBudgetScreen(viewModel) { navController.popBackStack() }
                    }
                    composable(Screen.AssistantAlerts.route) {
                        AssistantAlertsScreen { navController.popBackStack() }
                    }
                    composable(Screen.TermsOfService.route) {
                        TermsOfServiceScreen { navController.popBackStack() }
                    }
                }
                
                // Draggable Floating Chat Bubble for Family Chat
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }

                FloatingActionButton(
                    onClick = {
                        navController.navigate(Screen.Family.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 100.dp) // Above bottom bar
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                        .size(56.dp),
                    containerColor = secondary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.FamilyRestroom, contentDescription = "شات العائلة", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun BottomBar(navController: NavHostController, onOpenDrawer: () -> Unit) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp) // Floating pill effect
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val screens = listOf(Screen.Home, Screen.Inventory, Screen.Assistant, Screen.Subscriptions)
            screens.forEachIndexed { index, screen ->
                if (index == 2) {
                    // Inject Camera button in the middle
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(primaryFixed, primary)
                                )
                            )
                            .clickable { navController.navigate(Screen.Camera.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                
                AddItemAnimated(screen, currentDestination, navController)
            }
            
            // Drawer Menu Button
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "المزيد", tint = onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RowScope.AddItemAnimated(
    screen: Screen,
    currentDestination: NavDestination?,
    navController: NavHostController
) {
    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
    
    val background by animateColorAsState(
        targetValue = if (selected) primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "bg_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) primary else onSurfaceVariant,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "content_color"
    )
    
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable {
                navController.navigate(screen.route) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
            .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = "Navigation Icon",
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(visible = selected) {
            Text(
                text = stringResource(id = screen.titleRes),
                color = contentColor,
                style = Typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
