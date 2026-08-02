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

/**
 * مصروف حقيقي مستخرج من zad_transactions (بنكي فعلي)، مش المصروف المعتمد من الشات
 * (approvedSpendSince — نظام مصروف/مهام منفصل تماماً). مفتاحة FamilyMember.id.
 */
data class ChildSpending(
    val monthlyTotal: Double,
    val budgetCeiling: Double,
    val categoryBreakdown: Map<String, Double>
)

class FamilyViewModel : ViewModel() {
    private val _state = MutableStateFlow<FamilyState>(FamilyState.Loading)
    val state: StateFlow<FamilyState> = _state.asStateFlow()
    
    private val _toastMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()
    
    private var currentFamilyId: String? = null

    private val _childrenSpending = MutableStateFlow<Map<String, ChildSpending>>(emptyMap())
    val childrenSpending: StateFlow<Map<String, ChildSpending>> = _childrenSpending.asStateFlow()

    // MainScreen يظبطه من manualKidsModeActive (زر "Switch to Kids Mode" اليدوي للأدمن) —
    // قبل كده sendMessage كان بيقرأ myMemberInfo.role الحقيقي بس، فالأدمن في وضع المعاينة
    // كان لسه بياخد ردود @Zad بمستوى بالغ في شات العائلة رغم إنه شايف واجهة أطفال.
    // الحسابات الحقيقية للأطفال مش متأثرة — role بتاعها أصلاً "child" من الداتابيز.
    var kidsModePreviewOverride: Boolean by mutableStateOf(false)

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
                if (myMember.role == "admin") {
                    loadChildrenSpending()
                    startRealtimeFamilySpending(myMember.familyId)
                }
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

    private fun startRealtimeFamilySpending(familyId: String) {
        viewModelScope.launch {
            RealtimeFamilySpendingRepo.subscribeToFamilyTransactions(familyId).collectLatest {
                loadChildrenSpending()
            }
        }
    }

    /**
     * مصروف حقيقي (بنكي) شهري لكل ابن مقابل سقف ميزانيته الخاص — أدمن بس (RLS بيمنع
     * غير كده أصلاً). منفصل تماماً عن approvedSpendSince (نظام مصروف/مهام الشات).
     */
    fun loadChildrenSpending() {
        viewModelScope.launch {
            val curr = _state.value
            if (curr !is FamilyState.Active || curr.myMemberInfo.role != "admin") return@launch
            val children = curr.members.filter { it.role == "child" }
            if (children.isEmpty()) {
                _childrenSpending.value = emptyMap()
                return@launch
            }

            val transactions = SupabaseRepo.getFamilyMemberTransactions(curr.familyGroup.id)
            val budgets = SupabaseRepo.getUsersBudgets(children.mapNotNull { it.userId })
            val currentMonth = java.time.LocalDate.now().monthValue
            val currentYear = java.time.LocalDate.now().year

            _childrenSpending.value = children.associate { child ->
                val childExpensesThisMonth = transactions.filter { tx ->
                    tx.userId == child.userId && tx.isExpense && tx.createdAt?.let { raw ->
                        try {
                            val d = java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            d.monthValue == currentMonth && d.year == currentYear
                        } catch (e: Exception) { false }
                    } == true
                }
                child.id to ChildSpending(
                    monthlyTotal = childExpensesThisMonth.sumOf { it.amount },
                    budgetCeiling = budgets[child.userId] ?: 0.0,
                    categoryBreakdown = childExpensesThisMonth.groupBy { it.category ?: "أخرى" }
                        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                )
            }
        }
    }

    fun sendMessage(message: String, type: String = "TEXT", meta: String? = null) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                // رسالة عادية مكتوبة بخط اليد (مش زر SOS/طلب مصروف/تصويت الجاهزين) بنفحصها محلياً
                // قبل الإرسال — لو فيها نية طوارئ أو طلب مصروف واضح بمبلغ، بنرفعها لنوعها الصح تلقائياً.
                var finalType = type
                var finalMeta = meta
                if (type == "TEXT") {
                    when (classifyMessageIntent(message)) {
                        MessageIntent.SOS -> finalType = "SOS"
                        MessageIntent.EXPENSE -> {
                            extractExpenseAmount(message)?.let { amount ->
                                finalType = "PURCHASE_REQUEST"
                                finalMeta = """{"amount":$amount, "status":"PENDING"}"""
                            }
                        }
                        MessageIntent.NORMAL -> {}
                    }
                }

                SupabaseRepo.sendMessage(curr.familyGroup.id, curr.myMemberInfo.id, message, finalType, finalMeta)

                if (finalType == "SOS") {
                    curr.members.filter { it.role == "admin" }.forEach { admin ->
                        SupabaseRepo.sendAppNotification(
                            userId = admin.userId,
                            title = "🚨 نداء طوارئ من ${curr.myMemberInfo.alias}",
                            message = message
                        )
                    }
                }

                // If the message mentions the AI
                if (finalType == "TEXT" && (message.contains("@Zad", ignoreCase = true) || message.contains("@زاد"))) {
                    val cleanMessage = message.replace(Regex("@(Zad|زاد)\\s*"), "").trim()
                    val effectiveRole = if (kidsModePreviewOverride) "child" else curr.myMemberInfo.role
                    val aiResponse = com.example.data.ZadAiRepository.askFamilyAssistant(cleanMessage, effectiveRole)
                    SupabaseRepo.sendMessage(curr.familyGroup.id, "zad_ai", aiResponse, "TEXT", null)
                } else if (finalType == "TEXT" && (message.contains("أضف") || message.contains("نقص") || message.contains("شراء"))) {
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
                            message = "تمت الموافقة على طلبك وتم خصم $amount ${com.example.data.MarketPrefs.currentMarket.currencySymbol}."
                        )
                        
                        val updatedMembers = curr.members.map {
                            if (it.id == memberToUpdate.id) it.copy(balance = newBalance) else it
                        }
                        _state.value = curr.copy(messages = updatedMessages, members = updatedMembers)
                        checkSpendLimits(memberToUpdate, updatedMessages, curr.members)
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
                            message = "أنجزت المهمة: ${choreToUpdate.title}. تمت إضافة ${choreToUpdate.rewardAmount} ${com.example.data.MarketPrefs.currentMarket.currencySymbol} لرصيدك."
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
                        message = "تم تكليفك بمهمة جديدة: $title بمكافأة $rewardAmount ${com.example.data.MarketPrefs.currentMarket.currencySymbol}."
                    )
                }
                
                // Reload data to see it immediately
                loadFamilyData()
            }
        }
    }

    // --- تحديات العائلة المالية (Feature 5: Gamified Financial Challenges) ---
    private val _financialChallenges = MutableStateFlow<List<FinancialChallenge>>(emptyList())
    val financialChallenges: StateFlow<List<FinancialChallenge>> = _financialChallenges.asStateFlow()

    private val _challengeProgress = MutableStateFlow<Map<String, List<FinancialChallengeProgress>>>(emptyMap())
    val challengeProgress: StateFlow<Map<String, List<FinancialChallengeProgress>>> = _challengeProgress.asStateFlow()

    fun loadFinancialChallenges() {
        viewModelScope.launch {
            val challenges = SupabaseRepo.getActiveFinancialChallenges()
            _financialChallenges.value = challenges
            _challengeProgress.value = challenges.associate { it.id to SupabaseRepo.getFinancialChallengeProgress(it.id) }
        }
    }

    fun createFinancialChallenge(title: String, targetAmount: Double, rewardAmount: Double, durationDays: Int) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val now = java.time.Instant.now()
                val newChallenge = FinancialChallenge(
                    familyId = curr.familyGroup.id,
                    challengeType = if (durationDays <= 7) "weekly" else "monthly",
                    title = title,
                    targetAmount = targetAmount,
                    rewardAmount = rewardAmount,
                    startDate = now.toString(),
                    endDate = now.plusSeconds(durationDays * 86400L).toString(),
                    isActive = true
                )
                SupabaseRepo.createFinancialChallenge(newChallenge)
                loadFinancialChallenges()
            }
        }
    }

    // مكافأة إتمام التحدي تتبع نفس منطق toggleChore()'s reward branch — تحديث رصيد
    // العضو + إشعار، فقط عند بلوغ الهدف لأول مرة
    fun contributeToChallenge(challengeId: String, memberId: String, amount: Double) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr !is FamilyState.Active) return@launch
            val challenge = _financialChallenges.value.find { it.id == challengeId } ?: return@launch
            val member = curr.members.find { it.id == memberId } ?: return@launch
            val existingProgress = _challengeProgress.value[challengeId]?.find { it.userId == member.userId }
            val newAmount = (existingProgress?.currentAmount ?: 0.0) + amount
            val justCompleted = newAmount >= challenge.targetAmount && existingProgress?.isCompleted != true

            SupabaseRepo.updateFinancialChallengeProgress(challengeId, member.userId, amount, newAmount >= challenge.targetAmount)

            if (justCompleted && challenge.rewardAmount > 0) {
                val newBalance = member.balance + challenge.rewardAmount
                SupabaseRepo.updateFamilyMemberBalance(member.id, newBalance)
                SupabaseRepo.sendAppNotification(
                    userId = member.userId,
                    title = "تحدي مكتمل! 🎉",
                    message = "أنجزت تحدي: ${challenge.title}. تمت إضافة ${challenge.rewardAmount} ${com.example.data.MarketPrefs.currentMarket.currencySymbol} لرصيدك."
                )
                val updatedMembers = curr.members.map { if (it.id == member.id) it.copy(balance = newBalance) else it }
                _state.value = curr.copy(
                    members = updatedMembers,
                    myMemberInfo = if (curr.myMemberInfo.id == member.id) curr.myMemberInfo.copy(balance = newBalance) else curr.myMemberInfo
                )
            }

            loadFinancialChallenges()
        }
    }

    // --- المناسبات الموسمية وصناديق التجميع (Seasonal & Event Budget Forecasting) ---
    private val _upcomingSeasonalEvents = MutableStateFlow<List<Pair<SeasonalEvent, SeasonalEventWindow?>>>(emptyList())
    val upcomingSeasonalEvents: StateFlow<List<Pair<SeasonalEvent, SeasonalEventWindow?>>> = _upcomingSeasonalEvents.asStateFlow()

    private val _sinkingFunds = MutableStateFlow<List<SinkingFund>>(emptyList())
    val sinkingFunds: StateFlow<List<SinkingFund>> = _sinkingFunds.asStateFlow()

    fun loadUpcomingSeasonalEvents() {
        viewModelScope.launch {
            _upcomingSeasonalEvents.value = SupabaseRepo.getUpcomingSeasonalEvents()
        }
    }

    fun loadSinkingFunds() {
        viewModelScope.launch {
            _sinkingFunds.value = SupabaseRepo.getSinkingFunds()
        }
    }

    fun createSinkingFund(name: String, targetAmount: Double, targetDate: String?, eventId: String?) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                val newFund = SinkingFund(
                    familyId = curr.familyGroup.id,
                    eventId = eventId,
                    name = name,
                    targetAmount = targetAmount,
                    targetDate = targetDate
                )
                SupabaseRepo.createSinkingFund(newFund)
                loadSinkingFunds()
            }
        }
    }

    fun contributeToSinkingFund(fundId: String, amount: Double) {
        viewModelScope.launch {
            SupabaseRepo.contributeSinkingFund(fundId, amount)
            loadSinkingFunds()
        }
    }

    // --- اقتراح هدف ادخار عائلي بالذكاء الاصطناعي ---
    private val _suggestedGoal = MutableStateFlow<com.example.data.ZadAiRepository.FamilyGoalSuggestion?>(null)
    val suggestedGoal: StateFlow<com.example.data.ZadAiRepository.FamilyGoalSuggestion?> = _suggestedGoal.asStateFlow()

    private val _isSuggestingGoal = MutableStateFlow(false)
    val isSuggestingGoal: StateFlow<Boolean> = _isSuggestingGoal.asStateFlow()

    fun suggestFamilyGoal() {
        viewModelScope.launch {
            val curr = _state.value
            if (curr !is FamilyState.Active) return@launch
            _isSuggestingGoal.value = true
            try {
                val totalBalance = curr.members.filter { it.role != "admin" }.sumOf { it.balance }
                val completedTasks = curr.chores.count { it.isCompleted }
                val tasbihaScore = _familyTasbiha.sumOf { it.score }
                _suggestedGoal.value = com.example.data.ZadAiRepository.suggestFamilyGoal(
                    curr.members, totalBalance, completedTasks, tasbihaScore
                )
            } catch (e: Exception) {
                android.util.Log.e("FamilyVM", "suggestFamilyGoal() FAILED: ${e.message}")
            } finally {
                _isSuggestingGoal.value = false
            }
        }
    }

    fun clearSuggestedGoal() {
        _suggestedGoal.value = null
    }

    fun createSuggestedGoal() {
        viewModelScope.launch {
            val curr = _state.value
            val suggestion = _suggestedGoal.value
            if (curr !is FamilyState.Active || suggestion == null) return@launch
            SupabaseRepo.createFamilyGoal(
                com.example.data.FamilyGoal(
                    familyId = curr.familyGroup.id,
                    targetAmount = suggestion.targetAmount,
                    currentAmount = 0.0,
                    monthYear = java.time.YearMonth.now().toString(),
                    rewardSuggestion = suggestion.rewardSuggestion
                )
            )
            _suggestedGoal.value = null
            loadFamilyData()
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

    fun updateSpendLimits(memberId: String, dailyLimit: Double?, weeklyLimit: Double?) {
        viewModelScope.launch {
            val curr = _state.value
            if (curr is FamilyState.Active) {
                SupabaseRepo.updateFamilyMemberLimits(memberId, dailyLimit, weeklyLimit)
                val updatedMembers = curr.members.map {
                    if (it.id == memberId) it.copy(dailyLimit = dailyLimit, weeklyLimit = weeklyLimit) else it
                }
                _state.value = curr.copy(members = updatedMembers)
            }
        }
    }

    // بعد كل موافقة على طلب مصروف، نتأكد إن العضو (الابن غالباً) ما تخطاش حد إنفاقه اليومي/الأسبوعي.
    // الاستهلاك نفسه محسوب من رسائل PURCHASE_REQUEST الموافق عليها بدل جدول منفصل — راجع approvedSpendSince.
    private fun checkSpendLimits(member: FamilyMember, messages: List<ChatMessage>, allMembers: List<FamilyMember>) {
        viewModelScope.launch {
            val now = java.time.Instant.now()
            member.dailyLimit?.takeIf { it > 0 }?.let { limit ->
                val spent = approvedSpendSince(messages, member.id, now.minus(1, java.time.temporal.ChronoUnit.DAYS))
                notifyIfNearLimit(member, allMembers, spent, limit, isWeekly = false)
            }
            member.weeklyLimit?.takeIf { it > 0 }?.let { limit ->
                val spent = approvedSpendSince(messages, member.id, now.minus(7, java.time.temporal.ChronoUnit.DAYS))
                notifyIfNearLimit(member, allMembers, spent, limit, isWeekly = true)
            }
        }
    }

    private suspend fun notifyIfNearLimit(member: FamilyMember, allMembers: List<FamilyMember>, spent: Double, limit: Double, isWeekly: Boolean) {
        val ratio = spent / limit
        if (ratio < 0.8) return
        val period = if (isWeekly) "الأسبوعي" else "اليومي"
        val (title, message) = if (ratio >= 1.0) {
            "⚠️ تجاوز الحد $period" to "${member.alias} تجاوز حد الإنفاق $period (${spent.toInt()} من ${limit.toInt()})."
        } else {
            "🔔 اقتراب من الحد $period" to "${member.alias} اقترب من حد الإنفاق $period (${spent.toInt()} من ${limit.toInt()})."
        }
        SupabaseRepo.sendAppNotification(userId = member.userId, title = title, message = message)
        allMembers.filter { it.role == "admin" && it.userId != member.userId }.forEach { admin ->
            SupabaseRepo.sendAppNotification(userId = admin.userId, title = title, message = message)
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
    private var pendingTasbihaDelta: Int = 0

    fun loadTasbiha() {
        viewModelScope.launch {
            _myTasbiha = SupabaseRepo.getMyTasbiha()
            _myAllTrees = SupabaseRepo.getMyAllTrees()
            _familyTasbiha = SupabaseRepo.getFamilyTasbiha()
            _activeChallenges = SupabaseRepo.getActiveChallenges()
            // ضمان وجود شجرة مختارة دايماً — عشان العدّ يشتغل من أول ضغطة
            if (_selectedTree == null) {
                _selectedTree = _myAllTrees.firstOrNull()
                    ?: _myTasbiha
                    ?: SupabaseRepo.createNewTree("بستاني الأول").also {
                        if (it != null) _myAllTrees = SupabaseRepo.getMyAllTrees()
                    }
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
        // مفيش شجرة مختارة؟ نستخدم أي شجرة متاحة بدل الفشل الصامت
        val tree = _selectedTree ?: _myAllTrees.firstOrNull() ?: _myTasbiha ?: run {
            loadTasbiha() // يحمّل أو ينشئ شجرة — الضغطة الجاية هتشتغل
            return
        }
        if (_selectedTree == null) _selectedTree = tree
        val newScore = tree.score + 1
        val newClicks = tree.totalClicks + 1
        var newLevel = (newScore / 99) + 1
        if (newLevel > 5) newLevel = 5

        // Streak صحيح: يزيد لو يوم متتالي، يرجع لـ 1 لو انقطعت أكتر من يوم
        val today = java.time.LocalDate.now()
        val todayStr = today.toString()
        val lastDate = tree.lastStreakDate?.let {
            try { java.time.LocalDate.parse(it) } catch (e: Exception) { null }
        }
        val newStreak = when {
            lastDate == null -> 1
            lastDate == today -> tree.streakDays.coerceAtLeast(1)
            lastDate == today.minusDays(1) -> tree.streakDays + 1
            else -> 1 // انقطع التتابع
        }
        
        // Check if just matured
        val wasMature = tree.isMature
        val isNowMature = newLevel >= 5
        
        val updated = tree.copy(
            score = newScore, level = newLevel, totalClicks = newClicks,
            lastTasbihAt = java.time.Instant.now().toString(),
            streakDays = newStreak,
            lastStreakDate = todayStr,
            isMature = isNowMature,
            maturedAt = if (isNowMature && !wasMature) java.time.Instant.now().toString() else tree.maturedAt
        )
        
        // Update local state immediately for fast UI feedback
        _selectedTree = updated
        _myAllTrees = _myAllTrees.map { if (it.id == tree.id) updated else it }
        if (_myTasbiha?.id == tree.id) _myTasbiha = updated

        // Accumulate this tap into the pending delta instead of letting a new
        // debounce cycle replace the previous one outright — a rapid burst of
        // taps batches into a single atomic server-side increment, so no tap
        // is silently dropped even though only one network call fires.
        pendingTasbihaDelta += 1

        tasbihaSyncJob?.cancel()
        tasbihaSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500) // Wait 1.5 seconds of inactivity before syncing
            flushTasbihaDelta(updated)
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

    private suspend fun flushTasbihaDelta(latest: TasbihaTree) {
        val delta = pendingTasbihaDelta
        if (delta <= 0) return
        pendingTasbihaDelta = 0
        val result = SupabaseRepo.incrementTasbihaClicks(latest, delta)
        if (result == null) {
            // Flush failed (network/RLS/etc) — put the delta back so the next
            // successful flush (next tap, or onCleared on the way out) still
            // accounts for these taps instead of silently dropping them.
            pendingTasbihaDelta += delta
        } else {
            _familyTasbiha = SupabaseRepo.getFamilyTasbiha()
        }
    }

    override fun onCleared() {
        super.onCleared()
        val delta = pendingTasbihaDelta
        val latest = _selectedTree
        if (delta > 0 && latest != null) {
            // viewModelScope is already cancelled by the time onCleared runs,
            // so the pending debounced flush (tasbihaSyncJob) never fires —
            // without this, leaving the screen mid-burst silently drops every
            // tap since the last successful sync. Fire-and-forget on a scope
            // that outlives the ViewModel; best-effort (won't survive an
            // immediate process kill, but covers normal navigation-away).
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                SupabaseRepo.incrementTasbihaClicks(latest, delta)
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
