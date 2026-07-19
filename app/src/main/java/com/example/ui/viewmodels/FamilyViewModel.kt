package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

private val json = Json { ignoreUnknownKeys = true }

sealed class FamilyState {
    object Loading : FamilyState()
    data class Active(
        val familyGroup: FamilyGroup,
        val myMemberInfo: FamilyMember,
        val members: List<FamilyMember>,
        val messages: List<ChatMessage>,
        val groceries: List<SharedGroceryItem>,
        val goals: List<FamilyGoal>,
        val chores: List<Chore>
    ) : FamilyState()
    object NoFamily : FamilyState() // User hasn't created or joined a family yet
    data class Error(val message: String) : FamilyState()
}

class FamilyViewModel : ViewModel() {
    private val _state = MutableStateFlow<FamilyState>(FamilyState.Loading)
    val state: StateFlow<FamilyState> = _state.asStateFlow()
    
    private val _toastMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()
    
    private var currentFamilyId: String? = null

    init {
        loadFamilyData()
        updatePresence()
    }

    private fun updatePresence() {
        viewModelScope.launch {
            while (isActive) {  // stops when ViewModel is cleared
                try {
                    SupabaseRepo.updateLastSeen()
                } catch (e: Exception) {
                    android.util.Log.w("FamilyVM", "updateLastSeen failed: ${e.message}")
                }
                kotlinx.coroutines.delay(60_000) // كل دقيقة
            }
        }
    }

    private fun loadFamilyData() {
        viewModelScope.launch {
            _state.value = FamilyState.Loading
            val myMember = SupabaseRepo.getMyFamilyMember()
            if (myMember != null) {
                currentFamilyId = myMember.familyId
                fetchFamilyDetails(myMember)
                startRealtimeChat(myMember.familyId)
            } else {
                _state.value = FamilyState.NoFamily
            }
        }
    }
    
    private suspend fun fetchFamilyDetails(myMember: FamilyMember) {
        val fid = myMember.familyId
        try {
            // Core data — required
            val group = SupabaseRepo.client.postgrest["family_groups"].select {
                filter { eq("id", fid) }
            }.decodeSingle<FamilyGroup>()

            val members = SupabaseRepo.client.postgrest["family_members"].select {
                filter { eq("family_id", fid) }
            }.decodeList<FamilyMember>()

            val messages = RealtimeChatRepo.getChatHistory(fid)

            // Optional data — degraded gracefully if tables missing
            val groceries = try {
                SupabaseRepo.client.postgrest["shared_grocery_list"].select {
                    filter { eq("family_id", fid) }
                }.decodeList<SharedGroceryItem>()
            } catch (e: Exception) {
                android.util.Log.w("FamilyVM", "shared_grocery_list not available: ${e.message}")
                emptyList()
            }

            val goals = try {
                SupabaseRepo.client.postgrest["family_goals"].select {
                    filter { eq("family_id", fid) }
                }.decodeList<FamilyGoal>()
            } catch (e: Exception) {
                android.util.Log.w("FamilyVM", "family_goals not available: ${e.message}")
                emptyList()
            }

            val chores = try {
                SupabaseRepo.client.postgrest["family_chores"].select {
                    filter { eq("family_id", fid) }
                }.decodeList<Chore>()
            } catch (e: Exception) {
                android.util.Log.w("FamilyVM", "family_chores not available: ${e.message}")
                emptyList()
            }

            _state.value = FamilyState.Active(
                familyGroup = group,
                myMemberInfo = myMember,
                members = members,
                messages = messages,
                groceries = groceries,
                goals = goals,
                chores = chores
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = FamilyState.Error("لم نتمكن من جلب بيانات العائلة. يرجى التأكد من إعداد قاعدة البيانات.")
        }
    }

    private fun startRealtimeChat(familyId: String) {
        viewModelScope.launch {
            RealtimeChatRepo.subscribeToChat(familyId).collectLatest { newMsg ->
                val curr = _state.value
                if (curr is FamilyState.Active) {
                    val updatedMsgs = curr.messages + newMsg
                    _state.value = curr.copy(messages = updatedMsgs)
                }
            }
        }
    }

    fun sendMessage(message: String, type: String = "TEXT", meta: String? = null) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                SupabaseRepo.sendMessage(curr.familyGroup.id, curr.myMemberInfo.id, message, type, meta)
                
                // If the message mentions the AI
                if (type == "TEXT" && (message.contains("@Zad", ignoreCase = true) || message.contains("@زاد"))) {
                    val cleanMessage = message.replace(Regex("@(Zad|زاد)\\s*"), "").trim()
                    val aiResponse = com.example.data.ZadAiRepository.askFamilyAssistant(cleanMessage)
                    SupabaseRepo.sendMessage(curr.familyGroup.id, "zad_ai", aiResponse, "TEXT", null)
                } else if (type == "TEXT" && (message.contains("أضف") || message.contains("نقص") || message.contains("شراء"))) {
                    val cleanMsg = message.replace(Regex("(أضف|نقص|احتاج|شراء|إلى القائمة|للقائمة)"), "").trim()
                    if (cleanMsg.isNotEmpty()) {
                        val newItem = com.example.data.SharedGroceryItem(
                            familyId = curr.familyGroup.id,
                            addedBy = curr.myMemberInfo.id,
                            itemName = cleanMsg,
                            category = "عام",
                            isPurchased = false
                        )
                        val inserted = SupabaseRepo.addGroceryItem(newItem)
                        if (inserted != null) {
                            SupabaseRepo.sendMessage(curr.familyGroup.id, "zad_ai", "تم إضافة '$cleanMsg' إلى قائمة التسوق بنجاح ✅", "TEXT", null)
                            val updatedGroceries = curr.groceries + inserted
                            _state.value = curr.copy(groceries = updatedGroceries)
                        } else {
                            SupabaseRepo.sendMessage(curr.familyGroup.id, "zad_ai", "عذراً، حدث خطأ أثناء إضافة '$cleanMsg' ❌", "TEXT", null)
                        }
                    } else {
                        SupabaseRepo.sendMessage(curr.familyGroup.id, "zad_ai", "الرجاء تحديد اسم العنصر بوضوح. مثال: 'أضف حليب'", "TEXT", null)
                    }
                }
            }
        }
    }
    
    fun createFamily() {
        viewModelScope.launch {
            _state.value = FamilyState.Loading
            val group = SupabaseRepo.createFamilyGroup()
            if (group != null) {
                val success = SupabaseRepo.joinFamilyGroup(group.inviteCode, "رب الأسرة", "admin")
                if (success) {
                    loadFamilyData()
                } else {
                    _state.value = FamilyState.Error("Failed to join created family.")
                }
            } else {
                _state.value = FamilyState.Error("Failed to create family group.")
            }
        }
    }
    
    fun updateRequestStatus(messageId: String, newStatus: String, replyMsg: String) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                // Update metadata locally
                val updatedMessages = curr.messages.map { msg ->
                    if (msg.id == messageId) {
                        val oldMeta = msg.metadata ?: "{}"
                        val newMetadata = if (oldMeta.contains("status")) {
                            oldMeta.replace("\"status\":\"PENDING\"", "\"status\":\"$newStatus\"")
                        } else {
                            "{\"status\":\"$newStatus\"}"
                        }
                        msg.copy(metadata = newMetadata)
                    } else {
                        msg
                    }
                }
                _state.value = curr.copy(messages = updatedMessages)
                
                // Find the old message
                val msgToUpdate = curr.messages.find { it.id == messageId }
                
                // If it is APPROVED, subtract the amount
                if (newStatus == "APPROVED" && msgToUpdate != null) {
                    val metaJson = msgToUpdate.metadata ?: "{}"
                    val amountStr = metaJson.substringAfter("\"amount\":").substringBefore(",").trim()
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val memberToUpdate = curr.members.find { it.id == msgToUpdate.senderId }
                    if (memberToUpdate != null && amount > 0) {
                        val newBalance = memberToUpdate.balance - amount
                        SupabaseRepo.updateFamilyMemberBalance(memberToUpdate.id, newBalance)
                        
                        SupabaseRepo.sendAppNotification(
                            userId = memberToUpdate.userId,
                            title = "موافق! ✅",
                            message = "تمت الموافقة على طلبك وتم خصم $amount ريال."
                        )
                        
                        val updatedMembers = curr.members.map { 
                            if (it.id == memberToUpdate.id) it.copy(balance = newBalance) else it 
                        }
                        _state.value = curr.copy(messages = updatedMessages, members = updatedMembers)
                    } else {
                        _state.value = curr.copy(messages = updatedMessages)
                    }
                } else {
                    _state.value = curr.copy(messages = updatedMessages)
                }
                
                // Find the new metadata to send to remote
                val newMetaToRemote = updatedMessages.find { it.id == messageId }?.metadata ?: "{\"status\":\"$newStatus\"}"
                
                // Update remotely
                SupabaseRepo.updateMessageMetadata(messageId, newMetaToRemote)
                
                // Send automated reply
                sendMessage(replyMsg, "TEXT", null)
            }
        }
    }

    fun joinFamily(inviteCode: String, alias: String) {
        viewModelScope.launch {
            _state.value = FamilyState.Loading
            val success = SupabaseRepo.joinFamilyGroup(inviteCode, alias, "member")
            if (success) {
                loadFamilyData()
            } else {
                _state.value = FamilyState.Error("Invalid Invite Code or Connection Error.")
            }
        }
    }

    fun kickMember(memberId: String) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                // Wait for remote operation
                val success = SupabaseRepo.removeFamilyMember(memberId)
                if (success) {
                    // Update locally
                    val updatedMembers = curr.members.filter { it.id != memberId }
                    _state.value = curr.copy(members = updatedMembers)
                } else {
                    _toastMessage.emit("فشل طرد العضو، يرجى المحاولة لاحقاً.")
                }
            }
        }
    }

    fun changeMemberRole(memberId: String, newRole: String) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                // Wait for remote operation
                val success = SupabaseRepo.updateFamilyMemberRole(memberId, newRole)
                if (success) {
                    // Update locally
                    val updatedMembers = curr.members.map { 
                        if (it.id == memberId) it.copy(role = newRole) else it 
                    }
                    _state.value = curr.copy(members = updatedMembers)
                } else {
                    _toastMessage.emit("فشل تغيير الصلاحية، يرجى المحاولة لاحقاً.")
                }
            }
        }
    }
    
    fun toggleGroceryItem(id: String, isPurchased: Boolean) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                // Optimistic UI update
                val updatedList = curr.groceries.map { 
                    if (it.id == id) it.copy(isPurchased = isPurchased) else it 
                }
                _state.value = curr.copy(groceries = updatedList)
                
                // Update remote
                SupabaseRepo.updateGroceryPurchased(id, isPurchased)
            }
        }
    }
    
    fun toggleChore(id: String, isCompleted: Boolean) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val choreToUpdate = curr.chores.find { it.id == id }
                val updatedList = curr.chores.map { 
                    if (it.id == id) it.copy(isCompleted = isCompleted) else it 
                }
                
                // If it's being marked as completed, add reward to balance
                var updatedMembers = curr.members
                if (isCompleted && choreToUpdate != null && choreToUpdate.rewardAmount > 0) {
                    val memberToUpdate = curr.members.find { it.id == choreToUpdate.assignedTo }
                    if (memberToUpdate != null) {
                        val newBalance = memberToUpdate.balance + choreToUpdate.rewardAmount
                        SupabaseRepo.updateFamilyMemberBalance(memberToUpdate.id, newBalance)
                        
                        SupabaseRepo.sendAppNotification(
                            userId = memberToUpdate.userId,
                            title = "عمل رائع! 🌟",
                            message = "أنجزت المهمة: ${choreToUpdate.title}. تمت إضافة ${choreToUpdate.rewardAmount} ريال لرصيدك."
                        )
                        updatedMembers = curr.members.map {
                            if (it.id == memberToUpdate.id) it.copy(balance = newBalance) else it
                        }
                        // Also update myMemberInfo if it's the current user
                        if (curr.myMemberInfo.id == memberToUpdate.id) {
                            val newMyMemberInfo = curr.myMemberInfo.copy(balance = newBalance)
                            _state.value = curr.copy(chores = updatedList, members = updatedMembers, myMemberInfo = newMyMemberInfo)
                            SupabaseRepo.updateChoreCompleted(id, isCompleted)
                            return@launch
                        }
                    }
                }
                
                _state.value = curr.copy(chores = updatedList, members = updatedMembers)
                SupabaseRepo.updateChoreCompleted(id, isCompleted)
            }
        }
    }

    fun addChore(assignedTo: String, title: String, dueDate: String?, rewardAmount: Double = 0.0) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val newChore = com.example.data.Chore(
                    familyId = curr.familyGroup.id,
                    assignedTo = assignedTo,
                    title = title,
                    dueDate = dueDate,
                    rewardAmount = rewardAmount,
                    isCompleted = false
                )
                SupabaseRepo.addChore(newChore)
                
                val targetMember = curr.members.find { it.id == assignedTo }
                if (targetMember != null) {
                    SupabaseRepo.sendAppNotification(
                        userId = targetMember.userId,
                        title = "مهمة جديدة 📋",
                        message = "تم تكليفك بمهمة جديدة: $title بمكافأة $rewardAmount ريال."
                    )
                }
                
                // Reload data to see it immediately
                loadFamilyData()
            }
        }
    }

    fun updateSavingsGoal(memberId: String, newGoal: Double) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                SupabaseRepo.updateFamilyMemberSavingsGoal(memberId, newGoal)
                val updatedMembers = curr.members.map {
                    if (it.id == memberId) it.copy(savingsGoal = newGoal) else it
                }
                _state.value = curr.copy(members = updatedMembers)
            }
        }
    }

    // ── Pin ──
    fun togglePinMessage(messageId: String) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val msg = curr.messages.find { it.id == messageId } ?: return@launch
                val newPinned = !msg.isPinned
                SupabaseRepo.togglePinMessage(messageId, newPinned)
                val updated = curr.messages.map { if (it.id == messageId) it.copy(isPinned = newPinned) else it }
                _state.value = curr.copy(messages = updated)
            }
        }
    }

    // ── Reaction ──
    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val msg = curr.messages.find { it.id == messageId } ?: return@launch
                val myId = curr.myMemberInfo.id
                val existing = msg.reactions ?: ""
                val map = mutableMapOf<String, Int>()
                existing.split(", ").filter { it.contains(":") }.forEach { entry ->
                    val p = entry.split(":")
                    if (p.size == 2) map[p[0]] = p[1].toIntOrNull() ?: 1
                }
                val current = map[emoji] ?: 0
                if (current > 0) map[emoji] = current - 1 else map[emoji] = current + 1
                if (map[emoji]!! <= 0) map.remove(emoji)
                val reactionsStr = map.entries.joinToString(", ") { (e, c) -> "$e:$c" }
                SupabaseRepo.updateMessageReactions(messageId, reactionsStr)
                val updated = curr.messages.map { if (it.id == messageId) it.copy(reactions = reactionsStr.ifEmpty { null }) else it }
                _state.value = curr.copy(messages = updated)
            }
        }
    }

    // ── Poll ──
    fun sendPoll(question: String, options: List<String>) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val optsJson = Json.encodeToString(options)
                val meta = """{"question":"$question","options":$optsJson,"votes":{}}"""
                sendMessage("📊 $question", "POLL", meta)
            }
        }
    }

    // ── Voice Message ──
    fun sendVoiceMessage(audioUrl: String, transcription: String) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val msg = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    familyId = curr.familyGroup.id,
                    senderId = curr.myMemberInfo.id,
                    message = transcription,
                    messageType = "TEXT",
                    voiceUrl = audioUrl,
                    createdAt = java.time.Instant.now().toString()
                )
                SupabaseRepo.sendMessageWithVoice(msg)
                val updated = curr.messages + msg
                _state.value = curr.copy(messages = updated)
            }
        }
    }

    // ── Typing Indicator ──
    private var _typingUsers by mutableStateOf<Set<String>>(emptySet())
    val typingUsers: Set<String> get() = _typingUsers
    private var typingJob: kotlinx.coroutines.Job? = null

    fun onTyping(familyId: String) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            SupabaseRepo.setTypingStatus(familyId, true)
            kotlinx.coroutines.delay(3000)
            SupabaseRepo.setTypingStatus(familyId, false)
        }
    }

    fun startTypingMonitor(familyId: String) {
        viewModelScope.launch {
            while (isActive) {  // stops when ViewModel is cleared
                try {
                    val statuses = SupabaseRepo.getTypingStatuses(familyId)
                    _typingUsers = statuses.map { it.userId }.toSet()
                } catch (e: Exception) {
                    android.util.Log.w("FamilyVM", "getTypingStatuses failed: ${e.message}")
                }
                kotlinx.coroutines.delay(5000)  // reduced to 5s, consider Realtime in future
            }
        }
    }

    // ── Quick Replies ──
    fun getQuickReplies(): List<QuickReplyItem> = listOf(
        QuickReplyItem("👍", "تمام", "OK"),
        QuickReplyItem("🕒", "سأصل قريباً"),
        QuickReplyItem("🛒", "أضف حليب إلى القائمة", "add_to_list"),
        QuickReplyItem("🍞", "أضف خبز إلى القائمة", "add_to_list"),
        QuickReplyItem("💰", "أحتاج مصروف", "request_money"),
        QuickReplyItem("🍕", "شو رأيكم نطلب أكل؟"),
        QuickReplyItem("🌟", "أحسنتم جميعاً!"),
        QuickReplyItem("🙏", "شكراً جزيلاً"),
        QuickReplyItem("😊", "الله يسعدكم"),
        QuickReplyItem("🔥", "استمرار في التسبيحة!")
    )

    // ── Pinned Messages ──
    fun getPinnedMessages(): List<ChatMessage> {
        val curr = _state.value
        return if (curr is FamilyState.Active) curr.messages.filter { it.isPinned }.sortedByDescending { it.createdAt } else emptyList()
    }

    // ── Tasbiha ──
    private var _myTasbiha by mutableStateOf<TasbihaTree?>(null)
    val myTasbiha: TasbihaTree? get() = _myTasbiha
    private var _myAllTrees by mutableStateOf<List<TasbihaTree>>(emptyList())
    val myAllTrees: List<TasbihaTree> get() = _myAllTrees
    private var _familyTasbiha by mutableStateOf<List<TasbihaTree>>(emptyList())
    val familyTasbiha: List<TasbihaTree> get() = _familyTasbiha
    private var _activeChallenges by mutableStateOf<List<TasbihaChallenge>>(emptyList())
    val activeChallenges: List<TasbihaChallenge> get() = _activeChallenges
    private var _selectedTree by mutableStateOf<TasbihaTree?>(null)
    val selectedTree: TasbihaTree? get() = _selectedTree
    
    private var tasbihaSyncJob: kotlinx.coroutines.Job? = null

    fun loadTasbiha() {
        viewModelScope.launch {
            _myTasbiha = SupabaseRepo.getMyTasbiha()
            _myAllTrees = SupabaseRepo.getMyAllTrees()
            _familyTasbiha = SupabaseRepo.getFamilyTasbiha()
            _activeChallenges = SupabaseRepo.getActiveChallenges()
            if (_selectedTree == null && _myAllTrees.isNotEmpty()) {
                _selectedTree = _myAllTrees.first()
            }
        }
    }

    fun selectTree(tree: TasbihaTree) {
        _selectedTree = tree
    }

    fun createNewTree(gardenName: String) {
        viewModelScope.launch {
            val newTree = SupabaseRepo.createNewTree(gardenName)
            if (newTree != null) {
                _myAllTrees = SupabaseRepo.getMyAllTrees()
                _selectedTree = newTree
            }
        }
    }

    fun tasbihaClick() {
        val tree = _selectedTree ?: return
        val newScore = tree.score + 1
        val newClicks = tree.totalClicks + 1
        var newLevel = (newScore / 99) + 1
        if (newLevel > 5) newLevel = 5
        
        // Check streak
        val today = java.time.LocalDate.now().toString()
        val isNewDay = tree.lastStreakDate != today
        val newStreak = if (isNewDay) tree.streakDays + 1 else tree.streakDays
        
        // Check if just matured
        val wasMature = tree.isMature
        val isNowMature = newLevel >= 5
        
        val updated = tree.copy(
            score = newScore, level = newLevel, totalClicks = newClicks,
            lastTasbihAt = java.time.Instant.now().toString(),
            streakDays = newStreak,
            lastStreakDate = today,
            isMature = isNowMature,
            maturedAt = if (isNowMature && !wasMature) java.time.Instant.now().toString() else tree.maturedAt
        )
        
        // Update local state immediately for fast UI feedback
        _selectedTree = updated
        _myAllTrees = _myAllTrees.map { if (it.id == tree.id) updated else it }
        if (_myTasbiha?.id == tree.id) _myTasbiha = updated

        // Debounce network requests
        tasbihaSyncJob?.cancel()
        tasbihaSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500) // Wait 1.5 seconds of inactivity before syncing
            SupabaseRepo.clickTasbiha(updated)
            // Refresh family list to see others' progress
            _familyTasbiha = SupabaseRepo.getFamilyTasbiha()
        }

        viewModelScope.launch {
            // Share milestone to chat
            if (newLevel > tree.level) {
                val curr = _state.value
                if (curr is FamilyState.Active) {
                    val msg = "🌳 تسبيحة: ${updated.treeName} وصلت لمرحلة ${updated.stageName()}! 🎉"
                    sendMessage(msg, "TEXT", null)
                }
            }
            // Check if score threshold for share (every 100 clicks)
            if (newScore % 100 == 0 && newScore > 0) {
                val curr = _state.value
                if (curr is FamilyState.Active) {
                    val msg = "🌳 ${updated.treeName} وصلت ${newScore} تسبيحة! ${updated.stageEmoji()}"
                    sendMessage(msg, "TEXT", null)
                }
            }
            // New tree created
            if (!wasMature && isNowMature) {
                val curr = _state.value
                if (curr is FamilyState.Active) {
                    val msg = "🎉 مبروك! شجرة '${updated.treeName}' أثمرت لأول مرة! 🍎"
                    sendMessage(msg, "TEXT", null)
                }
            }
            // Streak milestones
            if (newStreak > 0 && newStreak % 7 == 0) {
                val curr = _state.value
                if (curr is FamilyState.Active) {
                    val msg = "🔥 ${updated.treeName} نشطة لمدة $newStreak أيام متتالية! استمروا!"
                    sendMessage(msg, "TEXT", null)
                }
            }
        }
    }

    fun renameTasbiha(newName: String) {
        viewModelScope.launch {
            val tree = _selectedTree ?: return@launch
            SupabaseRepo.renameTasbiha(tree.id, newName)
            _selectedTree = tree.copy(treeName = newName)
            _myAllTrees = _myAllTrees.map { if (it.id == tree.id) it.copy(treeName = newName) else it }
        }
    }

    // Daily reminder — call from HomeScreen
    fun checkTasbihaToday(): Boolean {
        val tree = _myTasbiha ?: return false
        val last = tree.lastTasbihAt ?: return false
        val today = java.time.LocalDate.now()
        return try {
            val lastDate = java.time.LocalDate.parse(last.take(10))
            lastDate == today
        } catch (_: Exception) { false }
    }

    fun getMemberTrees(memberId: String): List<TasbihaTree> {
        return _familyTasbiha.filter { it.userId == memberId }
    }

    fun getFamilyMembersWithTrees(): List<FamilyMemberWithTasbiha> {
        val curr = _state.value
        if (curr is FamilyState.Active) {
            return curr.members.map { member ->
                val memberTrees = _familyTasbiha.filter { it.userId == member.userId }
                FamilyMemberWithTasbiha(
                    member = member,
                    trees = memberTrees,
                    totalScore = memberTrees.sumOf { it.score },
                    matureTrees = memberTrees.count { it.isMature }
                )
            }.sortedByDescending { it.totalScore }
        }
        return emptyList()
    }
}
