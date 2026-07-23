package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountBalance
import com.airbnb.lottie.compose.LottieConstants
import com.example.data.ZadInventory
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadTransitions
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.data.SupabaseRepo
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    onNavigateToInventory: () -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    "إعدادات زاد", 
                    style = MaterialTheme.typography.titleLarge, 
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("الملف الشخصي") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    label = { Text("تغيير كلمة المرور") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    label = { Text("تسجيل الخروج") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )
                NavigationDrawerItem(
                    label = { Text("الدعم الفني") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val selectedColor = primary
                    val unselectedColor = Color.Gray

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("الرئيسية", fontWeight = FontWeight.SemiBold) },
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selectedColor,
                            selectedTextColor = selectedColor,
                            unselectedIconColor = unselectedColor,
                            unselectedTextColor = unselectedColor,
                            indicatorColor = primaryContainer
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = "Inventory") },
                        label = { Text("المخزون", fontWeight = FontWeight.SemiBold) },
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selectedColor,
                            selectedTextColor = selectedColor,
                            unselectedIconColor = unselectedColor,
                            unselectedTextColor = unselectedColor,
                            indicatorColor = primaryContainer
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.FamilyRestroom, contentDescription = "Family") },
                        label = { Text("العائلة", fontWeight = FontWeight.SemiBold) },
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selectedColor,
                            selectedTextColor = selectedColor,
                            unselectedIconColor = unselectedColor,
                            unselectedTextColor = unselectedColor,
                            indicatorColor = primaryContainer
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.SmartToy, contentDescription = "AI") },
                        label = { Text("الذكاء", fontWeight = FontWeight.SemiBold) },
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selectedColor,
                            selectedTextColor = selectedColor,
                            unselectedIconColor = unselectedColor,
                            unselectedTextColor = unselectedColor,
                            indicatorColor = primaryContainer
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Outlined.AccountBalance, contentDescription = "Transactions") },
                        label = { Text("المعاملات", fontWeight = FontWeight.SemiBold) },
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selectedColor,
                            selectedTextColor = selectedColor,
                            unselectedIconColor = unselectedColor,
                            unselectedTextColor = unselectedColor,
                            indicatorColor = primaryContainer
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Menu, contentDescription = "Menu") },
                        label = { Text("المزيد", fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick = { scope.launch { drawerState.open() } },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = unselectedColor,
                            unselectedTextColor = unselectedColor
                        )
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToCamera, 
                    containerColor = primary, 
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Scan", modifier = Modifier.size(24.dp))
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                val viewModel: ZadViewModel = viewModel()
                
                when (currentTab) {
                    0 -> HomeScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                    1 -> InventoryScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } }, onNavigateToAssistant = { currentTab = 3 }, onNavigateToCamera = { /* Handle camera from ZadScreens */ })
                    2 -> FamilyScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                    3 -> AssistantScreen(viewModel = viewModel)
                    4 -> TransactionsScreen(
                        viewModel = viewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToAssistant = { currentTab = 3 },
                        onNavigateToCamera = { }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    familyId: String = "default-family-id",
    senderId: String? = null
) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<com.example.data.ChatMessage>() }
    val scope = rememberCoroutineScope()
    val sender = senderId ?: "user-id"

    LaunchedEffect(familyId) {
        try {
            val result = SupabaseRepo.client.postgrest["chat_messages"]
                .select { filter { eq("family_id", familyId) } }
                .decodeList<com.example.data.ChatMessage>()
            messages.addAll(result)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AppearOnEntry {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                    Text("الدردشة العائلية", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = onBack, modifier = Modifier.pressableScale()) { Text("← رجوع") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ZadLottieAsset(
                        resId = R.raw.lottie_empty_chat,
                        modifier = Modifier.size(140.dp),
                        iterations = LottieConstants.IterateForever,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.no_messages_yet),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(messages) { index, msg ->
                        AnimatedVisibility(visible = true, enter = ZadTransitions.listItemEnter(index)) {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(msg.senderId ?: "Unknown", fontWeight = FontWeight.Bold)
                                    Text(msg.message ?: "")
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اكتب رسالة...") }
                )
                Button(
                    onClick = {
                        if (messageText.isBlank()) return@Button
                        val text = messageText
                        messages.add(com.example.data.ChatMessage(familyId = familyId, senderId = sender, message = text))
                        scope.launch {
                            try {
                                SupabaseRepo.sendMessage(familyId, sender, text)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        messageText = ""
                    },
                    modifier = Modifier.pressableScale()
                ) { Text("إرسال") }
            }
        }
    }
}

@Composable
fun AddItemDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عنصر") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم العنصر") })
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("الكمية") })
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("الوحدة") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = quantity.toDoubleOrNull() ?: 0.0
                onConfirm(name, qty, unit)
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
