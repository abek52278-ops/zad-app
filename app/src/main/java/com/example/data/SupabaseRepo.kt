package com.example.data

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "SupabaseRepo"

object SupabaseRepo {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Realtime)
        install(Functions)
        install(Storage)
    }

    // auth-kt's Auth plugin already persists/restores/refreshes sessions on its own —
    // alwaysAutoRefresh/autoLoadFromStorage/autoSaveToStorage all default to true, and
    // Android gets a working context-backed SessionManager for free via an AndroidX
    // Startup initializer (confirmed by decompiling auth-kt-android-3.0.3; install(Auth)
    // below never overrides sessionManager, so it resolves to the SDK's own default).
    // No app-side session persistence code needed — see the removed SessionHelper.

    suspend fun signUp(email: String, password: String): Boolean {
        Log.d(TAG, "signUp() → email=$email")
        return try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Log.d(TAG, "signUp() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "signUp() FAILED for email=$email, supabaseUrl=${BuildConfig.SUPABASE_URL}: $e", e)
            false
        }
    }

    suspend fun signIn(email: String, password: String): Boolean {
        Log.d(TAG, "signIn() → email=$email")
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Log.d(TAG, "signIn() SUCCESS → userId=${client.auth.currentUserOrNull()?.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "signIn() FAILED for email=$email, supabaseUrl=${BuildConfig.SUPABASE_URL}: $e", e)
            false
        }
    }

    suspend fun signOut() {
        Log.d(TAG, "signOut() called")
        try {
            client.auth.signOut()
            Log.d(TAG, "signOut() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "signOut() FAILED: $e", e)
        }
    }

    suspend fun resetPassword(email: String): Boolean {
        Log.d(TAG, "resetPassword() → email=$email")
        return try {
            client.auth.resetPasswordForEmail(email)
            Log.d(TAG, "resetPassword() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "resetPassword() FAILED for email=$email: $e", e)
            false
        }
    }

    // ─── User Budget ─────────────────────────────────────────────────────────
    /**
     * Task 19.0 — كتابة صريحة بتأكيد المستخدم. مختلفة عن captureMonthlyLimit (خطوة ٢،
     * بتكتب بس لو null وبتسيب limit_confirmed_at فاضي): دي بتحصل من فعل مستخدم مباشر
     * (حفظ في BudgetEditDialog)، فبتدهس أي قيمة قديمة وبتأكد فوراً — مفيش داعي لسؤال
     * تأكيد تاني بعدها.
     */
    suspend fun setMonthlyLimit(userId: String, limit: Double): Boolean {
        return try {
            client.postgrest["zad_users"].update(
                mapOf(
                    "monthly_limit" to limit,
                    "limit_confirmed_at" to java.time.Instant.now().toString()
                )
            ) { filter { eq("id", userId) } }
            Log.d(TAG, "setMonthlyLimit() SUCCESS → userId=$userId, limit=$limit")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setMonthlyLimit() FAILED: ${e.message}")
            false
        }
    }

    /**
     * اختيار السوق (البلد/العملة) بيتزامن للسيرفر عشان العقل والبوت يقرأوه من
     * zad_users.currency/country بدل الافتراض "ر.س" (المشكلة اللي خلّت البوت يقول
     * "مفيش ولا ريال" لمستخدم في مصر). بيتنادى من كل موقع بيتغيّر فيه السوق
     * (MarketSelectionScreen + شاشة "البلد والعملة" في البروفايل + TravelBanner).
     *
     * مش fire-and-forget: بيرجع Boolean عشان الكولر يقدر يبلغ المستخدم لو فشل بدل ما
     * يفشل بصمت (كان بيحصل قبل كده وده اللي خلّى zad_users.currency يقعد null حتى بعد
     * ما المستخدم يختار سوقه فعلاً). محاولة واحدة إعادة عند فشل الشبكة، قبل ما يرجّع false.
     */
    suspend fun syncMarketProfile(market: com.example.data.Market): Boolean =
        syncMarketProfile(market.currencyCode, market.countryCode)

    /** String-code version — lets SyncOutbox retry a queued market sync without needing to
     * reconstruct a Market enum (not @Serializable) from a stored payload. */
    suspend fun syncMarketProfile(currencyCode: String, countryCode: String): Boolean {
        val userId = client.auth.currentUserOrNull()?.id ?: return false
        repeat(2) { attempt ->
            try {
                client.postgrest["zad_users"].update(
                    mapOf(
                        "currency" to currencyCode,
                        "country" to countryCode
                    )
                ) { filter { eq("id", userId) } }
                Log.d(TAG, "syncMarketProfile() SUCCESS → userId=$userId, currency=$currencyCode")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "syncMarketProfile() FAILED (attempt ${attempt + 1}/2): ${e.message}")
                if (attempt == 0) kotlinx.coroutines.delay(1000)
            }
        }
        return false
    }

    /**
     * كل معاملات العيلة (المستخدم نفسه + أبناؤه لو أدمن) — يعتمد على RLS بس، مش فلترة
     * إضافية هنا: سياسة "family_admin_read_child_transactions" هي اللي بتحدد فعلياً مين
     * يشوف إيه (عضو عادي يرجعله صفوفه بس حتى لو طلب family_id العيلة كلها).
     */
    suspend fun getFamilyMemberTransactions(familyId: String): List<ZadTransaction> {
        return try {
            client.postgrest["zad_transactions"].select {
                filter { eq("family_id", familyId) }
            }.decodeList<ZadTransaction>()
        } catch (e: Exception) {
            Log.e(TAG, "getFamilyMemberTransactions() FAILED: ${e.message}")
            emptyList()
        }
    }

    @Serializable
    private data class UserMonthlyLimitRow(
        val id: String = "",
        @SerialName("monthly_limit") val monthlyLimit: Double? = null
    )

    /**
     * سقف ميزانية كل مستخدم في القائمة — نفس منطق get_family_admin_read_child_budget RLS.
     * Task 19.0 — كان بيقرا ZadUser.budget (العمود الميت). monthly_limit مش موجود على
     * ZadUser نفسها (Room entity، تعديل الـ schema بتاعها خارج نطاق التاسك ده)، فبيتقرا
     * بـ DTO خفيف هنا زي getMonthlyLimit().
     */
    suspend fun getUsersBudgets(userIds: List<String>): Map<String, Double> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            client.postgrest["zad_users"]
                .select(Columns.list("id", "monthly_limit")) {
                    filter { isIn("id", userIds) }
                }
                .decodeList<UserMonthlyLimitRow>()
                .associate { it.id to (it.monthlyLimit ?: 0.0) }
        } catch (e: Exception) {
            Log.e(TAG, "getUsersBudgets() FAILED: ${e.message}")
            emptyMap()
        }
    }

    // ─── Inventory ─────────────────────────────────────────────────────────
    suspend fun getInventory(): List<ZadInventory> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getInventory() → userId=$userId, table=zad_inventory")
            val result = if (userId != null) {
                client.postgrest["zad_inventory"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadInventory>()
            } else {
                client.postgrest["zad_inventory"].select().decodeList<ZadInventory>()
            }
            Log.d(TAG, "getInventory() → returned ${result.size} items")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getInventory() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addInventory(item: ZadInventory): Boolean {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            if (userId == null) {
                Log.w(TAG, "addInventory() skipped — user not authenticated")
                return false
            }
            val itemWithUser = item.copy(userId = userId)
            Log.d(TAG, "addInventory() → table=zad_inventory, item=${itemWithUser.itemName}, qty=${itemWithUser.quantity}, userId=$userId")
            client.postgrest["zad_inventory"].insert(itemWithUser)
            Log.d(TAG, "addInventory() SUCCESS — id=${itemWithUser.id}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "addInventory() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /** Upsert: يحدث الصف لو موجود (نفس id) أو يضيفه — أساسي للحقن الذكي */
    suspend fun upsertInventory(item: ZadInventory): Boolean {
        try {
            val userId = client.auth.currentUserOrNull()?.id ?: return false
            client.postgrest["zad_inventory"].upsert(item.copy(userId = userId))
            Log.d(TAG, "upsertInventory() SUCCESS — ${item.itemName} qty=${item.quantity}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "upsertInventory() FAILED: ${e.message}")
            return false
        }
    }

    @Serializable
    private data class ObservationParams(
        @SerialName("p_user") val user: String,
        @SerialName("p_item") val item: String,
        @SerialName("p_qty") val qty: Int,
        @SerialName("p_source") val source: String
    )

    /**
     * Task 18 — records a timestamped quantity observation and recomputes the item's
     * consumption rate server-side (zad_record_observation → zad_recompute_consumption).
     *
     * Why this exists: a quantity write on its own teaches the system nothing. Two
     * observations give one consumption sample; three samples make `rate_known` true, which
     * is what finally removes the item from the brain's `stock_unknown` list and stops it
     * asking about that item every day. Camera OCR and manual −/+ edits are free rate data,
     * so feeding them here makes rates converge in days instead of weeks — and the brain
     * then asks far fewer questions overall.
     *
     * The median-based rate math lives in SQL on purpose, so this client and the zad-brain
     * edge function share one implementation instead of two that drift apart.
     *
     * Task 12 note: when `ZadIngest` finally consolidates the seven write paths, this call
     * belongs inside `submit()` — every path would then record observations for free. Until
     * then callers invoke it directly, matching the existing `object SupabaseRepo` pattern.
     *
     * @param source one of question_answer | camera_ocr | manual | purchase (DB CHECK enforced)
     */
    suspend fun recordInventoryObservation(itemName: String, qty: Int, source: String): Boolean {
        try {
            val userId = client.auth.currentUserOrNull()?.id ?: return false
            client.postgrest.rpc(
                "zad_record_observation",
                Json.encodeToJsonElement(
                    ObservationParams(user = userId, item = itemName, qty = qty, source = source)
                ).jsonObject
            )
            Log.d(TAG, "recordInventoryObservation() SUCCESS — $itemName qty=$qty source=$source")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "recordInventoryObservation() FAILED: ${e.message}")
            return false
        }
    }

    suspend fun deleteInventory(id: String): Boolean {
        try {
            Log.d(TAG, "deleteInventory() → table=zad_inventory, id=$id")
            client.postgrest["zad_inventory"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deleteInventory() SUCCESS")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "deleteInventory() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // ─── Transactions ─────────────────────────────────────────────────────────
    suspend fun getTransactions(): List<ZadTransaction> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getTransactions() → userId=$userId, table=zad_transactions")
            val result = if (userId != null) {
                client.postgrest["zad_transactions"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadTransaction>()
            } else {
                client.postgrest["zad_transactions"].select().decodeList<ZadTransaction>()
            }
            Log.d(TAG, "getTransactions() → returned ${result.size} transactions")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getTransactions() FAILED: ${e.message}")
            emptyList()
        }
    }

    // Returns whether the push actually succeeded — this function has always caught its own
    // exceptions internally (never throws), so the many call sites wrapping it in try/catch
    // were dead code; a caller that needs to react to a failure (e.g. queue it in SyncOutbox)
    // must check the return value instead.
    suspend fun addTransaction(transaction: ZadTransaction): Boolean {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            if (userId == null) {
                Log.w(TAG, "addTransaction() skipped — user not authenticated")
                return false
            }
            val txWithUser = transaction.copy(userId = userId)
            Log.d(TAG, "addTransaction() → table=zad_transactions, title=${txWithUser.title}, amount=${txWithUser.amount}, isExpense=${txWithUser.isExpense}, userId=$userId")
            client.postgrest["zad_transactions"].insert(txWithUser)
            Log.d(TAG, "addTransaction() SUCCESS — id=${txWithUser.id}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "addTransaction() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    suspend fun getBehaviorProfile(): UserBehaviorProfile? {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            if (userId == null) {
                Log.w(TAG, "getBehaviorProfile() skipped — user not authenticated")
                return null
            }
            val result = client.postgrest["user_behavior_profile"].select {
                filter { eq("user_id", userId) }
            }.decodeSingleOrNull<UserBehaviorProfile>()
            Log.d(TAG, "getBehaviorProfile() → found=${result != null}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getBehaviorProfile() FAILED: ${e.message}")
            null
        }
    }

    suspend fun refreshBehaviorProfile(): Boolean {
        return try {
            Log.d(TAG, "refreshBehaviorProfile() → invoking update-behavior-profile")
            client.functions.invoke("update-behavior-profile")
            Log.d(TAG, "refreshBehaviorProfile() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "refreshBehaviorProfile() FAILED: ${e.message}")
            false
        }
    }

    suspend fun updateTransactionCategory(id: String, category: String) {
        try {
            Log.d(TAG, "updateTransactionCategory() → table=zad_transactions, id=$id, category=$category")
            client.postgrest["zad_transactions"].update(
                mapOf("category" to category)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateTransactionCategory() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateTransactionCategory() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun deleteTransaction(id: String) {
        try {
            Log.d(TAG, "deleteTransaction() → table=zad_transactions, id=$id")
            client.postgrest["zad_transactions"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deleteTransaction() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "deleteTransaction() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    // ─── Subscriptions ─────────────────────────────────────────────────────────
    suspend fun getSubscriptions(): List<ZadSubscription> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getSubscriptions() → userId=$userId, table=zad_subscriptions")
            val result = if (userId != null) {
                client.postgrest["zad_subscriptions"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadSubscription>()
            } else {
                client.postgrest["zad_subscriptions"].select().decodeList<ZadSubscription>()
            }
            Log.d(TAG, "getSubscriptions() → returned ${result.size} subscriptions")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getSubscriptions() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addSubscription(sub: ZadSubscription) {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            val subWithUser = sub.copy(userId = userId)
            Log.d(TAG, "addSubscription() → table=zad_subscriptions, title=${subWithUser.title}, amount=${subWithUser.amount}, userId=$userId")
            client.postgrest["zad_subscriptions"].insert(subWithUser)
            Log.d(TAG, "addSubscription() SUCCESS — id=${subWithUser.id}")
        } catch (e: Exception) {
            Log.e(TAG, "addSubscription() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun deleteSubscription(id: String) {
        try {
            Log.d(TAG, "deleteSubscription() → table=zad_subscriptions, id=$id")
            client.postgrest["zad_subscriptions"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deleteSubscription() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "deleteSubscription() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updateSubscriptionActive(id: String, isActive: Boolean) {
        try {
            Log.d(TAG, "updateSubscriptionActive() → table=zad_subscriptions, id=$id, isActive=$isActive")
            client.postgrest["zad_subscriptions"].update(
                mapOf("is_active" to isActive)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateSubscriptionActive() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateSubscriptionActive() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updateSubscriptionAutoDeduct(id: String, autoDeduct: Boolean) {
        try {
            Log.d(TAG, "updateSubscriptionAutoDeduct() → table=zad_subscriptions, id=$id, autoDeduct=$autoDeduct")
            client.postgrest["zad_subscriptions"].update(
                mapOf("auto_deduct" to autoDeduct)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateSubscriptionAutoDeduct() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateSubscriptionAutoDeduct() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updateSubscriptionRenewalDate(id: String, renewalDate: String) {
        try {
            Log.d(TAG, "updateSubscriptionRenewalDate() → table=zad_subscriptions, id=$id, renewalDate=$renewalDate")
            client.postgrest["zad_subscriptions"].update(
                mapOf("renewal_date" to renewalDate)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateSubscriptionRenewalDate() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateSubscriptionRenewalDate() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    // ─── Pharmacy ──────────────────────────────────────────────────────────────
    suspend fun getPharmacyItems(): List<ZadPharmacyItem> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getPharmacyItems() → userId=$userId, table=zad_pharmacy_items")
            val result = if (userId != null) {
                client.postgrest["zad_pharmacy_items"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadPharmacyItem>()
            } else {
                client.postgrest["zad_pharmacy_items"].select().decodeList<ZadPharmacyItem>()
            }
            Log.d(TAG, "getPharmacyItems() → returned ${result.size} items")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getPharmacyItems() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addPharmacyItem(item: ZadPharmacyItem) {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            val itemWithUser = item.copy(userId = userId)
            Log.d(TAG, "addPharmacyItem() → table=zad_pharmacy_items, name=${itemWithUser.name}, userId=$userId")
            client.postgrest["zad_pharmacy_items"].insert(itemWithUser)
            Log.d(TAG, "addPharmacyItem() SUCCESS — id=${itemWithUser.id}")
        } catch (e: Exception) {
            Log.e(TAG, "addPharmacyItem() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun deletePharmacyItem(id: String) {
        try {
            Log.d(TAG, "deletePharmacyItem() → table=zad_pharmacy_items, id=$id")
            client.postgrest["zad_pharmacy_items"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deletePharmacyItem() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "deletePharmacyItem() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updatePharmacyRefill(id: String, remainingQuantity: Int, price: Double, expiryDate: String?) {
        try {
            Log.d(TAG, "updatePharmacyRefill() → table=zad_pharmacy_items, id=$id, remainingQuantity=$remainingQuantity")
            client.postgrest["zad_pharmacy_items"].update(
                buildMap {
                    put("remaining_quantity", remainingQuantity)
                    put("price", price)
                    if (expiryDate != null) put("expiry_date", expiryDate)
                }
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updatePharmacyRefill() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updatePharmacyRefill() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updatePharmacyQuantity(id: String, remainingQuantity: Int) {
        try {
            Log.d(TAG, "updatePharmacyQuantity() → table=zad_pharmacy_items, id=$id, remainingQuantity=$remainingQuantity")
            client.postgrest["zad_pharmacy_items"].update(
                mapOf("remaining_quantity" to remainingQuantity)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updatePharmacyQuantity() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updatePharmacyQuantity() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    // ─── Dose Log (pharmacy adherence) ────────────────────────────────────────
    suspend fun getDoseLogs(): List<ZadDoseLog> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            val result = if (userId != null) {
                client.postgrest["zad_dose_log"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadDoseLog>()
            } else {
                client.postgrest["zad_dose_log"].select().decodeList<ZadDoseLog>()
            }
            Log.d(TAG, "getDoseLogs() → returned ${result.size} logs")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getDoseLogs() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addDoseLog(log: ZadDoseLog) {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            client.postgrest["zad_dose_log"].insert(log.copy(userId = userId))
            Log.d(TAG, "addDoseLog() SUCCESS — id=${log.id}")
        } catch (e: Exception) {
            Log.e(TAG, "addDoseLog() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun markDoseLogTaken(id: String, takenAt: String) {
        try {
            client.postgrest["zad_dose_log"].update(
                mapOf("taken_at" to takenAt)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "markDoseLogTaken() SUCCESS — id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "markDoseLogTaken() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    enum class DoseLogOutcome { INSERTED, DUPLICATE, FAILED }

    // Task 17.2.2 — the unique index on (user_id, item_id, scheduled_at) is what makes
    // tapping the notification button and the pharmacy-screen button for the SAME
    // scheduled dose safe: the second insert violates the constraint instead of silently
    // creating a second row, and the caller must NOT decrement stock again on DUPLICATE.
    suspend fun insertPharmacyDose(dose: ZadPharmacyDose): DoseLogOutcome {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            client.postgrest["zad_pharmacy_doses"].insert(dose.copy(userId = userId))
            DoseLogOutcome.INSERTED
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("23505") || msg.contains("duplicate key") || msg.contains("zad_doses_unique")) {
                Log.d(TAG, "insertPharmacyDose() → duplicate, already logged for this scheduled dose")
                DoseLogOutcome.DUPLICATE
            } else {
                Log.e(TAG, "insertPharmacyDose() FAILED: ${e.message}")
                DoseLogOutcome.FAILED
            }
        }
    }

    /** "فاضل قد إيه فعلاً؟" — resyncs a drifted count without deleting/re-adding the medication. */
    suspend fun confirmPharmacyQuantity(id: String, quantity: Int) {
        try {
            client.postgrest["zad_pharmacy_items"].update(
                mapOf("remaining_quantity" to quantity, "qty_confirmed_at" to java.time.Instant.now().toString())
            ) { filter { eq("id", id) } }
            Log.d(TAG, "confirmPharmacyQuantity() SUCCESS — id=$id, qty=$quantity")
        } catch (e: Exception) {
            Log.e(TAG, "confirmPharmacyQuantity() FAILED: ${e.message}")
        }
    }

    suspend fun setPharmacyUnitsPerDose(id: String, unitsPerDose: Double) {
        try {
            client.postgrest["zad_pharmacy_items"].update(
                mapOf("units_per_dose" to unitsPerDose)
            ) { filter { eq("id", id) } }
            Log.d(TAG, "setPharmacyUnitsPerDose() SUCCESS — id=$id, units=$unitsPerDose")
        } catch (e: Exception) {
            Log.e(TAG, "setPharmacyUnitsPerDose() FAILED: ${e.message}")
        }
    }

    suspend fun flagInvalidDoseTime(id: String, invalid: Boolean) {
        try {
            client.postgrest["zad_pharmacy_items"].update(
                mapOf("has_invalid_dose_time" to invalid)
            ) { filter { eq("id", id) } }
        } catch (e: Exception) {
            Log.e(TAG, "flagInvalidDoseTime() FAILED: ${e.message}")
        }
    }

    // ─── Home Maintenance ──────────────────────────────────────────────────────
    suspend fun getMaintenanceItems(): List<ZadMaintenanceItem> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getMaintenanceItems() → userId=$userId, table=zad_maintenance_items")
            val result = if (userId != null) {
                client.postgrest["zad_maintenance_items"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadMaintenanceItem>()
            } else {
                client.postgrest["zad_maintenance_items"].select().decodeList<ZadMaintenanceItem>()
            }
            Log.d(TAG, "getMaintenanceItems() → returned ${result.size} items")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getMaintenanceItems() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addMaintenanceItem(item: ZadMaintenanceItem) {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            val itemWithUser = item.copy(userId = userId)
            Log.d(TAG, "addMaintenanceItem() → table=zad_maintenance_items, name=${itemWithUser.name}, userId=$userId")
            client.postgrest["zad_maintenance_items"].insert(itemWithUser)
            Log.d(TAG, "addMaintenanceItem() SUCCESS — id=${itemWithUser.id}")
        } catch (e: Exception) {
            Log.e(TAG, "addMaintenanceItem() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun deleteMaintenanceItem(id: String) {
        try {
            Log.d(TAG, "deleteMaintenanceItem() → table=zad_maintenance_items, id=$id")
            client.postgrest["zad_maintenance_items"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deleteMaintenanceItem() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "deleteMaintenanceItem() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updateMaintenanceLastServiceDate(id: String, lastServiceDate: String) {
        try {
            Log.d(TAG, "updateMaintenanceLastServiceDate() → table=zad_maintenance_items, id=$id, lastServiceDate=$lastServiceDate")
            client.postgrest["zad_maintenance_items"].update(
                mapOf("last_service_date" to lastServiceDate)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateMaintenanceLastServiceDate() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateMaintenanceLastServiceDate() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    // ─── Debts ─────────────────────────────────────────────────────────────────
    suspend fun getDebts(): List<ZadDebt> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getDebts() → userId=$userId, table=zad_debts")
            val result = if (userId != null) {
                client.postgrest["zad_debts"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadDebt>()
            } else {
                client.postgrest["zad_debts"].select().decodeList<ZadDebt>()
            }
            Log.d(TAG, "getDebts() → returned ${result.size} debts")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getDebts() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addDebt(debt: ZadDebt): Boolean {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            val debtWithUser = debt.copy(userId = userId)
            Log.d(TAG, "addDebt() → table=zad_debts, name=${debtWithUser.name}, remainingBalance=${debtWithUser.remainingBalance}, userId=$userId")
            client.postgrest["zad_debts"].insert(debtWithUser)
            Log.d(TAG, "addDebt() SUCCESS — id=${debtWithUser.id}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "addDebt() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    suspend fun deleteDebt(id: String): Boolean {
        try {
            Log.d(TAG, "deleteDebt() → table=zad_debts, id=$id")
            client.postgrest["zad_debts"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deleteDebt() SUCCESS")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "deleteDebt() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    suspend fun updateDebtRemainingBalance(id: String, newBalance: Double): Boolean {
        try {
            Log.d(TAG, "updateDebtRemainingBalance() → table=zad_debts, id=$id, newBalance=$newBalance")
            client.postgrest["zad_debts"].update(
                mapOf("remaining_balance" to newBalance)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateDebtRemainingBalance() SUCCESS")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "updateDebtRemainingBalance() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // ─── Obligations (Task 26) ──────────────────────────────────────────────────
    // نفس نمط getDebts() — مش مخزّنة في Room، بتُحمّل من Supabase مباشرة. بس الصفوف
    // confirmed=true بتدخل في committed/available (BudgetMath.availableInCycle) — صفوف
    // auto_detected=false confirmed اتسجلت مباشرة برضو، confirmed=false لسه مستني تأكيد
    // العميل عن طريق زاد-برين، فبيتقروا هنا بس مايتحسبوش في "محجوز".
    suspend fun getObligations(): List<ZadObligation> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getObligations() → userId=$userId, table=zad_obligations")
            val result = if (userId != null) {
                client.postgrest["zad_obligations"].select {
                    filter { eq("user_id", userId); eq("active", true) }
                }.decodeList<ZadObligation>()
            } else {
                client.postgrest["zad_obligations"].select().decodeList<ZadObligation>()
            }
            Log.d(TAG, "getObligations() → returned ${result.size} obligations")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getObligations() FAILED: ${e.message}")
            emptyList()
        }
    }

    // ─── Task 27.2 — "why did this number change" ────────────────────────────────
    // zad_brain_runs.mutations already records every automated write (Task 16/18) with
    // old/new values; this is the first client read of that table (was write-only from
    // the Kotlin side before Task 27). No LLM call here — just reading history the
    // server already wrote, same as any other GET.

    @Serializable
    private data class BrainRunRow(
        @SerialName("started_at") val startedAt: String,
        val trigger: String,
        val mutations: List<JsonObject> = emptyList()
    )

    private fun jsonElementToDisplay(el: JsonElement): String {
        val prim = el as? JsonPrimitive ?: return el.toString()
        return prim.contentOrNull ?: prim.toString()
    }

    /** اسم عربي مفهوم بدل اسم الأداة التقني — بيغطي بس الأدوات اللي فعلاً بتظهر في mutations (executeTool في zad-brain) */
    private fun toolLabel(tool: String): String = when (tool) {
        "update_inventory_qty" -> "تعديل كمية مخزون"
        "set_transaction_category" -> "تصنيف معاملة"
        "merge_duplicate_expense" -> "دمج معاملة مكررة"
        "reconcile_cash_balance" -> "تسوية الكاش"
        "confirm_cycle_start" -> "تأكيد دورة الراتب"
        "confirm_obligation" -> "تسجيل التزام"
        else -> tool
    }

    data class BrainMutationEntry(
        val toolLabel: String,
        val old: String?,
        val new: String?,
        val trigger: String,
        val at: String
    )

    suspend fun getRecentBrainMutations(limit: Int = 15): List<BrainMutationEntry> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()
            val rows = client.postgrest["zad_brain_runs"]
                .select(Columns.list("started_at", "trigger", "mutations")) {
                    filter { eq("user_id", userId) }
                    order("started_at", Order.DESCENDING)
                    limit(20L)
                }
                .decodeList<BrainRunRow>()
            rows.flatMap { row ->
                row.mutations.map { m ->
                    BrainMutationEntry(
                        toolLabel = toolLabel(m["tool"]?.jsonPrimitive?.contentOrNull ?: "?"),
                        old = m["old"]?.let { jsonElementToDisplay(it) },
                        new = m["new"]?.let { jsonElementToDisplay(it) },
                        trigger = row.trigger,
                        at = row.startedAt
                    )
                }
            }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "getRecentBrainMutations() FAILED: ${e.message}")
            emptyList()
        }
    }

    // ─── Family ─────────────────────────────────────────────────────────
    suspend fun createFamilyGroup(): FamilyGroup? {
        return try {
            val inviteCode = "ZAD-" + (1000..9999).random().toString()
            Log.d(TAG, "createFamilyGroup() → table=family_groups, inviteCode=$inviteCode")
            val group = FamilyGroup(inviteCode = inviteCode)
            val result = client.postgrest["family_groups"].insert(group) {
                select()
            }.decodeSingle<FamilyGroup>()
            Log.d(TAG, "createFamilyGroup() SUCCESS — id=${result.id}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "createFamilyGroup() FAILED: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun joinFamilyGroup(inviteCode: String, alias: String, role: String = "member"): Boolean {
        return try {
            Log.d(TAG, "joinFamilyGroup() → table=family_groups, inviteCode=$inviteCode, alias=$alias, role=$role")
            val groups = client.postgrest["family_groups"].select {
                filter { eq("invite_code", inviteCode) }
            }.decodeList<FamilyGroup>()

            if (groups.isNotEmpty()) {
                val group = groups.first()
                val user = client.auth.currentUserOrNull() ?: return false
                val zadIdCode = "ZAD-" + (100000..999999).random().toString()
                val member = FamilyMember(
                    familyId = group.id,
                    userId = user.id,
                    zadId = zadIdCode,
                    role = role,
                    alias = alias
                )
                Log.d(TAG, "joinFamilyGroup() → table=family_members, familyId=${group.id}, userId=${user.id}")
                client.postgrest["family_members"].insert(member)
                Log.d(TAG, "joinFamilyGroup() SUCCESS")
                true
            } else {
                Log.w(TAG, "joinFamilyGroup() → No family found for inviteCode=$inviteCode")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "joinFamilyGroup() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun getMyFamilyMember(): FamilyMember? {
        return try {
            val user = client.auth.currentUserOrNull() ?: return null
            Log.d(TAG, "getMyFamilyMember() → table=family_members, userId=${user.id}")
            val members = client.postgrest["family_members"].select {
                filter { eq("user_id", user.id) }
            }.decodeList<FamilyMember>()
            val result = members.firstOrNull()
            Log.d(TAG, "getMyFamilyMember() → ${if (result != null) "found familyId=${result.familyId}" else "no family"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getMyFamilyMember() FAILED: ${e.message}")
            null
        }
    }

    suspend fun removeFamilyMember(memberId: String): Boolean {
        return try {
            Log.d(TAG, "removeFamilyMember() → table=family_members, id=$memberId")
            client.postgrest["family_members"].delete {
                filter { eq("id", memberId) }
            }
            Log.d(TAG, "removeFamilyMember() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "removeFamilyMember() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun updateFamilyMemberRole(memberId: String, newRole: String): Boolean {
        return try {
            Log.d(TAG, "updateFamilyMemberRole() → table=family_members, id=$memberId, newRole=$newRole")
            client.postgrest["family_members"].update(
                mapOf("role" to newRole)
            ) {
                filter { eq("id", memberId) }
            }
            Log.d(TAG, "updateFamilyMemberRole() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateFamilyMemberRole() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /** Returns false on failure (caught, not rethrown) — callers must check this instead of
     * assuming success, since the insert can fail silently otherwise (Task: Chat Send audit). */
    suspend fun sendMessage(familyId: String, senderId: String, message: String, messageType: String = "TEXT", metadata: String? = null): Boolean {
        return try {
            Log.d(TAG, "sendMessage() → table=chat_messages, familyId=$familyId, senderId=$senderId, type=$messageType, message=${message.take(30)}")
            val chatMsg = ChatMessage(
                familyId = familyId,
                senderId = senderId,
                message = message,
                messageType = messageType,
                metadata = metadata
            )
            client.postgrest["chat_messages"].insert(chatMsg)
            Log.d(TAG, "sendMessage() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun updateMessageMetadata(messageId: String, metadata: String) {
        try {
            Log.d(TAG, "updateMessageMetadata() → table=chat_messages, id=$messageId")
            client.postgrest["chat_messages"].update(
                mapOf("metadata" to metadata)
            ) { filter { eq("id", messageId) } }
            Log.d(TAG, "updateMessageMetadata() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateMessageMetadata() FAILED: ${e.message}")
        }
    }

    suspend fun togglePinMessage(messageId: String, isPinned: Boolean) {
        try {
            client.postgrest["chat_messages"].update(
                mapOf("is_pinned" to isPinned)
            ) { filter { eq("id", messageId) } }
            Log.d(TAG, "togglePinMessage() SUCCESS: id=$messageId, pinned=$isPinned")
        } catch (e: Exception) {
            Log.e(TAG, "togglePinMessage() FAILED: ${e.message}")
        }
    }

    suspend fun updateMessageReactions(messageId: String, reactions: String) {
        try {
            val payload: Map<String, String?> = if (reactions.isEmpty()) mapOf("reactions" to null)
            else mapOf("reactions" to reactions)
            client.postgrest["chat_messages"].update(payload) { filter { eq("id", messageId) } }
            Log.d(TAG, "updateMessageReactions() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateMessageReactions() FAILED: ${e.message}")
        }
    }

    suspend fun sendMessageWithVoice(msg: ChatMessage) {
        try {
            client.postgrest["chat_messages"].insert(
                mapOf(
                    "family_id" to msg.familyId,
                    "sender_id" to msg.senderId,
                    "message" to msg.message,
                    "message_type" to msg.messageType,
                    "voice_url" to msg.voiceUrl
                )
            )
            Log.d(TAG, "sendMessageWithVoice() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessageWithVoice() FAILED: ${e.message}")
        }
    }

    // ─── Grocery ─────────────────────────────────────────────────────────
    suspend fun updateGroceryPurchased(id: String, isPurchased: Boolean) {
        try {
            Log.d(TAG, "updateGroceryPurchased() → table=shared_grocery_list, id=$id, isPurchased=$isPurchased")
            client.postgrest["shared_grocery_list"].update(
                mapOf("is_purchased" to isPurchased)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateGroceryPurchased() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateGroceryPurchased() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun addGroceryItem(item: SharedGroceryItem): SharedGroceryItem? {
        return try {
            Log.d(TAG, "addGroceryItem() → table=shared_grocery_list, item=${item.itemName}")
            val inserted = client.postgrest["shared_grocery_list"]
                .insert(item) { select() }
                .decodeSingle<SharedGroceryItem>()
            Log.d(TAG, "addGroceryItem() SUCCESS: inserted id=${inserted.id}")
            inserted
        } catch (e: Exception) {
            Log.e(TAG, "addGroceryItem() FAILED: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ─── Shopping List (Personal) ─────────────────────────────────────────────────────────
    suspend fun getShoppingList(): List<ZadShoppingItem> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
            Log.d(TAG, "getShoppingList() → userId=$userId, table=zad_shopping_list")
            val result = if (userId != null) {
                client.postgrest["zad_shopping_list"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<ZadShoppingItem>()
            } else {
                client.postgrest["zad_shopping_list"].select().decodeList<ZadShoppingItem>()
            }
            Log.d(TAG, "getShoppingList() → returned ${result.size} items")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getShoppingList() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun addShoppingItem(item: ZadShoppingItem) {
        try {
            val userId = client.auth.currentUserOrNull()?.id
            val itemWithUser = item.copy(userId = userId)
            Log.d(TAG, "addShoppingItem() → table=zad_shopping_list, itemName=${itemWithUser.itemName}, userId=$userId")
            client.postgrest["zad_shopping_list"].insert(itemWithUser)
            Log.d(TAG, "addShoppingItem() SUCCESS — id=${itemWithUser.id}")
        } catch (e: Exception) {
            Log.e(TAG, "addShoppingItem() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun toggleShoppingItemPurchased(id: String, purchased: Boolean) {
        try {
            Log.d(TAG, "toggleShoppingItemPurchased() → id=$id, purchased=$purchased")
            client.postgrest["zad_shopping_list"].update(
                mapOf("is_purchased" to purchased)
            ) { filter { eq("id", id) } }
            Log.d(TAG, "toggleShoppingItemPurchased() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "toggleShoppingItemPurchased() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun deleteShoppingItem(id: String) {
        try {
            Log.d(TAG, "deleteShoppingItem() → table=zad_shopping_list, id=$id")
            client.postgrest["zad_shopping_list"].delete {
                filter { eq("id", id) }
            }
            Log.d(TAG, "deleteShoppingItem() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "deleteShoppingItem() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    // ─── Chores ──────────────────────────────────────────────────────────────
    suspend fun addChore(chore: Chore) {
        try {
            Log.d(TAG, "addChore() → table=family_chores, title=${chore.title}, assignedTo=${chore.assignedTo}")
            client.postgrest["family_chores"].insert(chore)
            Log.d(TAG, "addChore() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "addChore() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun updateChoreCompleted(id: String, isCompleted: Boolean) {
        try {
            Log.d(TAG, "updateChoreCompleted() → table=family_chores, id=$id, isCompleted=$isCompleted")
            client.postgrest["family_chores"].update(
                mapOf("is_completed" to isCompleted)
            ) {
                filter { eq("id", id) }
            }
            Log.d(TAG, "updateChoreCompleted() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateChoreCompleted() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    // ─── Wallet & Goals ──────────────────────────────────────────────────────
    suspend fun updateFamilyMemberBalance(memberId: String, newBalance: Double): Boolean {
        try {
            Log.d(TAG, "updateFamilyMemberBalance() → table=family_members, id=$memberId, newBalance=$newBalance")
            client.postgrest["family_members"].update(
                mapOf("balance" to newBalance)
            ) {
                filter { eq("id", memberId) }
            }
            Log.d(TAG, "updateFamilyMemberBalance() SUCCESS")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "updateFamilyMemberBalance() FAILED: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    suspend fun updateFamilyMemberSavingsGoal(memberId: String, newGoal: Double) {
        try {
            Log.d(TAG, "updateFamilyMemberSavingsGoal() → table=family_members, id=$memberId, newGoal=$newGoal")
            client.postgrest["family_members"].update(
                mapOf("savings_goal" to newGoal)
            ) { filter { eq("id", memberId) } }
            Log.d(TAG, "updateFamilyMemberSavingsGoal() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateFamilyMemberSavingsGoal() FAILED: ${e.message}")
        }
    }

    suspend fun updateFamilyMemberLimits(memberId: String, dailyLimit: Double?, weeklyLimit: Double?) {
        try {
            Log.d(TAG, "updateFamilyMemberLimits() → table=family_members, id=$memberId, daily=$dailyLimit, weekly=$weeklyLimit")
            client.postgrest["family_members"].update(
                mapOf("daily_limit" to dailyLimit, "weekly_limit" to weeklyLimit)
            ) { filter { eq("id", memberId) } }
            Log.d(TAG, "updateFamilyMemberLimits() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateFamilyMemberLimits() FAILED: ${e.message}")
        }
    }

    suspend fun updateFamilyMemberAlias(alias: String) {
        try {
            val myMember = getMyFamilyMember() ?: return
            client.postgrest["family_members"].update(
                mapOf("alias" to alias)
            ) { filter { eq("id", myMember.id) } }
            Log.d(TAG, "updateFamilyMemberAlias() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateFamilyMemberAlias() FAILED: ${e.message}")
        }
    }

    // ─── User Profile ─────────────────────────────────────────────────────────
    suspend fun getUserProfile(): ZadUser? {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return null
            Log.d(TAG, "getUserProfile() → userId=$userId, table=zad_users")
            val users = client.postgrest["zad_users"].select {
                filter { eq("id", userId) }
            }.decodeList<ZadUser>()
            val profile = users.firstOrNull()
            Log.d(TAG, "getUserProfile() → name=${profile?.name}, avatar=${profile?.avatarUri}")
            profile
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile() FAILED: ${e.message}")
            null
        }
    }

    /** يرفع صورة البروفايل فعليًا لـ Supabase Storage (bucket: avatars) ويرجّع الرابط العام، أو null لو فشل. */
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): String? {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return null
            val ext = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val path = "$userId/avatar.$ext"
            Log.d(TAG, "uploadAvatar() → path=$path, bytes=${bytes.size}")
            client.storage["avatars"].upload(path, bytes) { upsert = true }
            val publicUrl = client.storage["avatars"].publicUrl(path)
            Log.d(TAG, "uploadAvatar() SUCCESS → $publicUrl")
            "$publicUrl?t=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.e(TAG, "uploadAvatar() FAILED: ${e.message}", e)
            null
        }
    }

    /** name=null بيسيب الاسم المخزن زي ما هو — عشان أبلود صورة لوحده متمسحش الاسم لو لسه ماتحملش. */
    suspend fun updateUserProfile(name: String?, avatarUri: String?): Boolean {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return false
            val current = getUserProfile() ?: ZadUser(id = userId)
            Log.d(TAG, "updateUserProfile() → userId=$userId, name=$name, avatarUri=$avatarUri")
            client.postgrest["zad_users"].upsert(
                current.copy(name = name ?: current.name, avatarUri = avatarUri ?: current.avatarUri)
            )
            Log.d(TAG, "updateUserProfile() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfile() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // read-modify-write off the cached profile — a bare upsert(ZadUser(id=..., emergencyFundBalance=...))
    // would clobber every other column to its default, same reason updateUserProfile/updateUserBudget
    // above do the same read-modify-write instead of a bare upsert.
    suspend fun updateEmergencyFund(newValue: Double): Boolean {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return false
            val current = getUserProfile() ?: ZadUser(id = userId)
            Log.d(TAG, "updateEmergencyFund() → userId=$userId, newValue=$newValue")
            client.postgrest["zad_users"].upsert(current.copy(emergencyFundBalance = newValue))
            Log.d(TAG, "updateEmergencyFund() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateEmergencyFund() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteAccount(): Boolean {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return false
            Log.d(TAG, "deleteAccount() → userId=$userId, invoking delete-account edge function")
            // The old version only deleted 5 tables client-side and never touched
            // auth.users, so the account (and email) survived forever with orphaned
            // rows in every other table. delete-account runs server-side with the
            // service role: it cleans every table lacking an ON DELETE CASCADE to
            // auth.users, then deletes the auth user itself, which cascades the rest.
            client.functions.invoke("delete-account")
            client.auth.signOut()
            Log.d(TAG, "deleteAccount() SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount() FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // ─── Notifications ────────────────────────────────────────────────────────
    suspend fun getAppNotifications(userId: String): List<AppNotification> {
        return try {
            Log.d(TAG, "getAppNotifications() → table=app_notifications, userId=$userId")
            client.postgrest["app_notifications"]
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<AppNotification>()
        } catch (e: Exception) {
            Log.e(TAG, "getAppNotifications() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendAppNotification(userId: String, title: String, message: String) {
        try {
            Log.d(TAG, "sendAppNotification() → table=app_notifications, userId=$userId")
            val notif = AppNotification(userId = userId, title = title, message = message)
            client.postgrest["app_notifications"].insert(notif)
            Log.d(TAG, "sendAppNotification() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "sendAppNotification() FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun markAppNotificationRead(id: String) {
        try {
            Log.d(TAG, "markAppNotificationRead() → table=app_notifications, id=$id")
            client.postgrest["app_notifications"].update(
                mapOf("is_read" to true)
            ) { filter { eq("id", id) } }
            Log.d(TAG, "markAppNotificationRead() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "markAppNotificationRead() FAILED: ${e.message}")
        }
    }

    // ─── Zad Brain Insights (emit_insight output — home_card/bell/voice) ───────
    suspend fun getPendingInsights(userId: String): List<ZadInsight> {
        return try {
            client.postgrest["zad_insights"]
                .select {
                    filter { eq("user_id", userId); eq("status", "pending") }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ZadInsight>()
        } catch (e: Exception) {
            Log.e(TAG, "getPendingInsights() FAILED: ${e.message}")
            emptyList()
        }
    }

    @Serializable
    private data class MonthlyLimitRow(
        @SerialName("monthly_limit") val monthlyLimit: Double? = null,
        @SerialName("limit_confirmed_at") val limitConfirmedAt: String? = null
    )

    /** Task 19.0 — null معناها لسه متسجلش. مفيش حاجة تعرض أو تحسب على ده قبل التأكيد. */
    suspend fun getMonthlyLimit(userId: String): Pair<Double?, String?> {
        return try {
            val row = client.postgrest["zad_users"]
                .select(Columns.list("monthly_limit", "limit_confirmed_at")) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<MonthlyLimitRow>()
            Pair(row?.monthlyLimit, row?.limitConfirmedAt)
        } catch (e: Exception) {
            Log.e(TAG, "getMonthlyLimit() FAILED: ${e.message}")
            Pair(null, null)
        }
    }

    @Serializable
    private data class CycleSettingsRow(
        @SerialName("cycle_start_day") val cycleStartDay: Int? = null,
        @SerialName("cycle_anchor") val cycleAnchor: String = "day_of_month"
    )

    /**
     * Task 25 — cycleStartDay=null معناها زاد-برين لسه ماكتشفش دورة راتب المستخدم (أو
     * اكتشفها ومحتاجة تأكيد لسه، الأداة confirm_cycle_start في zad-brain هي اللي بتكتب هنا
     * بعد التأكيد). الكلاينت بيرجع لشهر تقويمي عادي في الحالة دي — CycleMath نفسها بتعمل
     * الـ fallback ده، مش لازم شرط هنا.
     */
    suspend fun getCycleSettings(userId: String): Pair<Int?, String> {
        return try {
            val row = client.postgrest["zad_users"]
                .select(Columns.list("cycle_start_day", "cycle_anchor")) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<CycleSettingsRow>()
            Pair(row?.cycleStartDay, row?.cycleAnchor ?: "day_of_month")
        } catch (e: Exception) {
            Log.e(TAG, "getCycleSettings() FAILED: ${e.message}")
            Pair(null, "day_of_month")
        }
    }

    @Serializable
    private data class LocaleConfigRow(
        @SerialName("dedupe_window_hours") val dedupeWindowHours: Int = 36,
        @SerialName("amount_tolerance_pct") val amountTolerancePct: Double = 5.0
    )

    @Serializable
    private data class HabitChipsParams(
        @SerialName("p_user") val user: String,
        @SerialName("p_same_weekday") val sameWeekday: Boolean
    )

    /**
     * Task 22 — "قهوة ٢٥" جنب "رصيدك اتصرف عليه ٤ مرات آخر ٦٠ يوم بمبلغ ثابت تقريباً"،
     * محسوبة كلها في zad_habit_chips() (فلتر stddev هناك، مش هنا). فاضية لو المستخدم
     * جديد أو مفيش نمط ثابت — الشاشة تختفي الصف بدل ما تعرض حاجة فاضية.
     */
    suspend fun getHabitChips(sameWeekday: Boolean = false): List<HabitChip> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return emptyList()
            client.postgrest.rpc(
                "zad_habit_chips",
                Json.encodeToJsonElement(HabitChipsParams(user = userId, sameWeekday = sameWeekday)).jsonObject
            ).decodeList<HabitChip>()
        } catch (e: Exception) {
            Log.e(TAG, "getHabitChips() FAILED: ${e.message}")
            emptyList()
        }
    }

    /** Task 20 — (نافذة الساعات، نسبة التسامح٪) لبلد معين، أو null لو فشل/مش موجود */
    suspend fun getLocaleConfig(country: String): Pair<Int, Double>? {
        return try {
            val row = client.postgrest["zad_locale_config"]
                .select(Columns.list("dedupe_window_hours", "amount_tolerance_pct")) {
                    filter { eq("country", country) }
                }
                .decodeSingleOrNull<LocaleConfigRow>()
            row?.let { Pair(it.dedupeWindowHours, it.amountTolerancePct) }
        } catch (e: Exception) {
            Log.e(TAG, "getLocaleConfig() FAILED: ${e.message}")
            null
        }
    }

    /**
     * Task 19.0 — بينقل السقف الشهري المحفوظ على الجهاز لعموده الخاص على السيرفر.
     * targeted update بـ map مش upsert لـ ZadUser: الـ upsert بيكتب كل الأعمدة فبيدهس
     * أي حاجة اتكتبت من جهاز تاني، والعمود ده تحديداً مالوش نسخة تانية يترجع منها.
     * بيكتب بس لو العمود لسه null — أول جهاز يلتقط بيكسب، والباقي مابيدهسوش.
     * limit_confirmed_at بيفضل null — القيمة ملتقطة مش مؤكدة، ومحدش يقرأها قبل التأكيد.
     */
    suspend fun captureMonthlyLimit(userId: String, limit: Double): Boolean {
        return try {
            val (existing, _) = getMonthlyLimit(userId)
            if (existing != null) {
                Log.d(TAG, "captureMonthlyLimit() skipped — already set to $existing")
                return false
            }
            client.postgrest["zad_users"].update(
                mapOf("monthly_limit" to limit)
            ) { filter { eq("id", userId) } }
            Log.d(TAG, "captureMonthlyLimit() → userId=$userId, limit=$limit")
            true
        } catch (e: Exception) {
            Log.e(TAG, "captureMonthlyLimit() FAILED: ${e.message}")
            false
        }
    }

    suspend fun updateInsightStatus(id: String, status: String) {
        try {
            client.postgrest["zad_insights"].update(
                mapOf("status" to status, "updated_at" to java.time.Instant.now().toString())
            ) { filter { eq("id", id) } }
        } catch (e: Exception) {
            Log.e(TAG, "updateInsightStatus() FAILED: ${e.message}")
        }
    }

    @Serializable
    private data class MemoryUpsertParams(
        @SerialName("p_user") val user: String,
        @SerialName("p_scope") val scope: String,
        @SerialName("p_note") val note: String,
        @SerialName("p_conf") val conf: Double
    )

    /**
     * Task 28 — "رفض بمعنى". status='dismissed' زي قبل كده، بس معاه dismiss_reason —
     * not_relevant/wrong_data يدخلوا dismissed_keys الدائمة في buildSnapshot (زاد-برين)،
     * timing يتستبعد منها عمداً فيرجع pending تاني أول ما نفس dedupe_key يتكتب تاني.
     * الملاحظة في zad_memory نفس آلية remember() اللي زاد-برين بيستخدمها، بس هنا استدعاء
     * مباشر لـ zad_memory_upsert — قرار حتمي من فعل مستخدم مباشر، مش قرار LLM.
     */
    suspend fun dismissInsightWithReason(insight: ZadInsight, reason: String) {
        try {
            client.postgrest["zad_insights"].update(
                mapOf(
                    "status" to "dismissed",
                    "dismiss_reason" to reason,
                    "updated_at" to java.time.Instant.now().toString()
                )
            ) { filter { eq("id", insight.id) } }
        } catch (e: Exception) {
            Log.e(TAG, "dismissInsightWithReason() status update FAILED: ${e.message}")
        }
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val note = DismissalMemory.noteFor(reason, insight) ?: return
        try {
            client.postgrest.rpc(
                "zad_memory_upsert",
                Json.encodeToJsonElement(
                    MemoryUpsertParams(user = userId, scope = note.scope, note = note.note, conf = note.confidence)
                ).jsonObject
            )
        } catch (e: Exception) {
            Log.e(TAG, "dismissInsightWithReason() memory upsert FAILED: ${e.message}")
        }
    }

    // ── Telegram binding (Phase B4, PRODUCT_PLAN.md) ──────────────────────────────
    @Serializable
    private data class TelegramBindingRow(@SerialName("bound_at") val boundAt: String? = null)

    // بدون 0/O/1/I عشان يتكتب يدوي في تليجرام من غير لبس
    private val BINDING_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * كود ربط تليجرام لمرة واحدة، صالح ١٠ دقايق. الصف ده لوحده مايربطش حاجة — EPIC_1_4.md:
     * "a chat_id is never an identity". الربط الفعلي (chat_id + bound_at) بيحصل من
     * zad-telegram-bot (edge function) لما العميل يبعت /start <code> في تليجرام.
     */
    suspend fun generateTelegramBindingCode(): String? {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return null
            val code = (1..8).map { BINDING_CODE_CHARS.random() }.joinToString("")
            val expiresAt = java.time.Instant.now().plusSeconds(600).toString()
            Log.d(TAG, "generateTelegramBindingCode() → userId=$userId")
            client.postgrest["telegram_bindings"].insert(
                mapOf("user_id" to userId, "binding_code" to code, "code_expires_at" to expiresAt)
            )
            code
        } catch (e: Exception) {
            Log.e(TAG, "generateTelegramBindingCode() FAILED: ${e.message}")
            null
        }
    }

    /** true لو المستخدم عنده تليجرام مربوط فعلاً (فيه صف bound_at مش null) */
    suspend fun isTelegramLinked(): Boolean {
        return try {
            val userId = client.auth.currentUserOrNull()?.id ?: return false
            client.postgrest["telegram_bindings"].select(Columns.list("bound_at")) {
                filter { eq("user_id", userId) }
            }.decodeList<TelegramBindingRow>().any { it.boundAt != null }
        } catch (e: Exception) {
            Log.e(TAG, "isTelegramLinked() FAILED: ${e.message}")
            false
        }
    }

    suspend fun unlinkTelegram() {
        try {
            val userId = client.auth.currentUserOrNull()?.id ?: return
            Log.d(TAG, "unlinkTelegram() → userId=$userId")
            client.postgrest["telegram_bindings"].delete { filter { eq("user_id", userId) } }
        } catch (e: Exception) {
            Log.e(TAG, "unlinkTelegram() FAILED: ${e.message}")
        }
    }

    // ── Tasbiha ──

    suspend fun getMyTasbiha(): TasbihaTree? {
        try {
            val myMember = getMyFamilyMember() ?: return null
            val existing = client.postgrest["family_tasbiha"].select().decodeList<TasbihaTree>().filter {
                it.userId == myMember.userId && it.familyId == myMember.familyId
            }
            if (existing.isNotEmpty()) return existing.first()
            // Create new
            val newTree = TasbihaTree(familyId = myMember.familyId, userId = myMember.userId)
            val inserted = client.postgrest["family_tasbiha"].insert(newTree).decodeSingle<TasbihaTree>()
            Log.d(TAG, "getMyTasbiha() → created new tree: id=${inserted.id}")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "getMyTasbiha() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun getMyAllTrees(): List<TasbihaTree> {
        try {
            val myMember = getMyFamilyMember() ?: return emptyList()
            return client.postgrest["family_tasbiha"].select().decodeList<TasbihaTree>().filter {
                it.userId == myMember.userId && it.familyId == myMember.familyId
            }.sortedByDescending { it.score }
        } catch (e: Exception) {
            Log.e(TAG, "getMyAllTrees() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun createNewTree(gardenName: String): TasbihaTree? {
        try {
            val myMember = getMyFamilyMember() ?: return null
            val newTree = TasbihaTree(
                familyId = myMember.familyId,
                userId = myMember.userId,
                gardenName = gardenName,
                treeName = "شجرة جديدة"
            )
            val inserted = client.postgrest["family_tasbiha"].insert(newTree).decodeSingle<TasbihaTree>()
            Log.d(TAG, "createNewTree() SUCCESS — id=${inserted.id}")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "createNewTree() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun getFamilyTasbiha(): List<TasbihaTree> {
        try {
            val myMember = getMyFamilyMember() ?: return emptyList()
            return client.postgrest["family_tasbiha"].select().decodeList<TasbihaTree>().filter {
                it.familyId == myMember.familyId
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFamilyTasbiha() FAILED: ${e.message}")
            return emptyList()
        }
    }

    @Serializable
    private data class TasbihaIncrementParams(
        @SerialName("p_tree_id") val treeId: String,
        @SerialName("p_delta") val delta: Int,
        @SerialName("p_level") val level: Int,
        @SerialName("p_tree_emoji") val treeEmoji: String,
        @SerialName("p_is_mature") val isMature: Boolean,
        @SerialName("p_matured_at") val maturedAt: String?,
        @SerialName("p_streak_days") val streakDays: Int,
        @SerialName("p_last_streak_date") val lastStreakDate: String?,
        @SerialName("p_last_tasbih_at") val lastTasbihAt: String?
    )

    // Atomic delta increment (increment_tasbiha_clicks RPC) — replaces the old
    // absolute-value clickTasbiha(). The caller accumulates rapid taps into a
    // single `delta` instead of overwriting total_clicks/score outright, so a
    // debounced batch flush (or a retry) can never clobber another session's
    // count or drop taps that happened between reads.
    suspend fun incrementTasbihaClicks(updatedTree: TasbihaTree, delta: Int): TasbihaTree? {
        return try {
            val result = client.postgrest.rpc(
                "increment_tasbiha_clicks",
                Json.encodeToJsonElement(
                    TasbihaIncrementParams(
                        treeId = updatedTree.id,
                        delta = delta,
                        level = updatedTree.level,
                        treeEmoji = updatedTree.stageEmoji(),
                        isMature = updatedTree.isMature,
                        maturedAt = updatedTree.maturedAt,
                        streakDays = updatedTree.streakDays,
                        lastStreakDate = updatedTree.lastStreakDate,
                        lastTasbihAt = updatedTree.lastTasbihAt
                    )
                ).jsonObject
            ).decodeSingle<TasbihaTree>()
            Log.d(TAG, "incrementTasbihaClicks() SUCCESS — delta=$delta, newTotal=${result.totalClicks}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "incrementTasbihaClicks() FAILED: ${e.message}")
            null
        }
    }

    suspend fun renameTasbiha(treeId: String, newName: String) {
        try {
            client.postgrest["family_tasbiha"].update(
                mapOf("tree_name" to newName)
            ) { filter { eq("id", treeId) } }
            Log.d(TAG, "renameTasbiha() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "renameTasbiha() FAILED: ${e.message}")
        }
    }

    suspend fun updateTreeEmoji(treeId: String, emoji: String) {
        try {
            client.postgrest["family_tasbiha"].update(
                mapOf("tree_emoji" to emoji)
            ) { filter { eq("id", treeId) } }
            Log.d(TAG, "updateTreeEmoji() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateTreeEmoji() FAILED: ${e.message}")
        }
    }

    // ── Tasbiha Challenges ──

    suspend fun getActiveChallenges(): List<TasbihaChallenge> {
        try {
            val myMember = getMyFamilyMember() ?: return emptyList()
            return client.postgrest["family_tasbiha_challenges"].select().decodeList<TasbihaChallenge>().filter {
                it.familyId == myMember.familyId && it.isActive
            }
        } catch (e: Exception) {
            Log.e(TAG, "getActiveChallenges() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun createChallenge(challenge: TasbihaChallenge): TasbihaChallenge? {
        try {
            val inserted = client.postgrest["family_tasbiha_challenges"].insert(challenge).decodeSingle<TasbihaChallenge>()
            Log.d(TAG, "createChallenge() SUCCESS")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "createChallenge() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun getChallengeProgress(challengeId: String): List<TasbihaChallengeProgress> {
        try {
            return client.postgrest["tasbiha_challenge_progress"].select().decodeList<TasbihaChallengeProgress>().filter {
                it.challengeId == challengeId
            }
        } catch (e: Exception) {
            Log.e(TAG, "getChallengeProgress() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun updateChallengeProgress(challengeId: String, userId: String, clicks: Int) {
        try {
            val existing = client.postgrest["tasbiha_challenge_progress"].select().decodeList<TasbihaChallengeProgress>().find {
                it.challengeId == challengeId && it.userId == userId
            }
            if (existing != null) {
                client.postgrest["tasbiha_challenge_progress"].update(
                    mapOf("current_clicks" to (existing.currentClicks + clicks))
                ) { filter { eq("id", existing.id) } }
            } else {
                client.postgrest["tasbiha_challenge_progress"].insert(
                    mapOf(
                        "challenge_id" to challengeId,
                        "user_id" to userId,
                        "current_clicks" to clicks
                    )
                )
            }
            Log.d(TAG, "updateChallengeProgress() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateChallengeProgress() FAILED: ${e.message}")
        }
    }

    // ── Financial Challenges ──

    suspend fun getActiveFinancialChallenges(): List<FinancialChallenge> {
        try {
            val myMember = getMyFamilyMember() ?: return emptyList()
            return client.postgrest["family_financial_challenges"].select().decodeList<FinancialChallenge>().filter {
                it.familyId == myMember.familyId && it.isActive
            }
        } catch (e: Exception) {
            Log.e(TAG, "getActiveFinancialChallenges() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun createFinancialChallenge(challenge: FinancialChallenge): FinancialChallenge? {
        try {
            val inserted = client.postgrest["family_financial_challenges"].insert(challenge).decodeSingle<FinancialChallenge>()
            Log.d(TAG, "createFinancialChallenge() SUCCESS")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "createFinancialChallenge() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun getFinancialChallengeProgress(challengeId: String): List<FinancialChallengeProgress> {
        try {
            return client.postgrest["financial_challenge_progress"].select().decodeList<FinancialChallengeProgress>().filter {
                it.challengeId == challengeId
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFinancialChallengeProgress() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun updateFinancialChallengeProgress(challengeId: String, userId: String, addedAmount: Double, isCompleted: Boolean) {
        try {
            val existing = client.postgrest["financial_challenge_progress"].select().decodeList<FinancialChallengeProgress>().find {
                it.challengeId == challengeId && it.userId == userId
            }
            if (existing != null) {
                client.postgrest["financial_challenge_progress"].update(
                    mapOf(
                        "current_amount" to (existing.currentAmount + addedAmount),
                        "is_completed" to isCompleted
                    )
                ) { filter { eq("id", existing.id) } }
            } else {
                client.postgrest["financial_challenge_progress"].insert(
                    mapOf(
                        "challenge_id" to challengeId,
                        "user_id" to userId,
                        "current_amount" to addedAmount,
                        "is_completed" to isCompleted
                    )
                )
            }
            Log.d(TAG, "updateFinancialChallengeProgress() SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "updateFinancialChallengeProgress() FAILED: ${e.message}")
        }
    }

    // ── Seasonal Events & Sinking Funds ──

    suspend fun getUpcomingSeasonalEvents(withinDays: Int = 90): List<Pair<SeasonalEvent, SeasonalEventWindow?>> {
        try {
            val myMember = getMyFamilyMember() ?: return emptyList()
            val events = client.postgrest["seasonal_events"].select().decodeList<SeasonalEvent>().filter {
                it.familyId == null || it.familyId == myMember.familyId
            }
            val now = java.time.Instant.now()
            val horizon = now.plus(java.time.Duration.ofDays(withinDays.toLong()))
            val windows = client.postgrest["seasonal_event_windows"].select().decodeList<SeasonalEventWindow>()
            val result = mutableListOf<Pair<SeasonalEvent, SeasonalEventWindow?>>()
            for (event in events) {
                if (event.isRecurring) {
                    val upcoming = windows.filter { it.eventId == event.id }
                        .mapNotNull { w ->
                            try {
                                val start = java.time.Instant.parse(w.startDate)
                                if (!start.isBefore(now) && start.isBefore(horizon)) start to w else null
                            } catch (e: Exception) { null }
                        }
                        .minByOrNull { it.first }?.second
                    if (upcoming != null) result.add(event to upcoming)
                } else {
                    val start = event.startDate?.let { try { java.time.Instant.parse(it) } catch (e: Exception) { null } }
                    if (start != null && !start.isBefore(now) && start.isBefore(horizon)) result.add(event to null)
                }
            }
            return result.sortedBy { (event, window) ->
                try { java.time.Instant.parse(window?.startDate ?: event.startDate ?: "") } catch (e: Exception) { java.time.Instant.MAX }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUpcomingSeasonalEvents() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun createCustomSeasonalEvent(event: SeasonalEvent): SeasonalEvent? {
        try {
            val inserted = client.postgrest["seasonal_events"].insert(event).decodeSingle<SeasonalEvent>()
            Log.d(TAG, "createCustomSeasonalEvent() SUCCESS")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "createCustomSeasonalEvent() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun getSinkingFunds(): List<SinkingFund> {
        try {
            val myMember = getMyFamilyMember() ?: return emptyList()
            return client.postgrest["sinking_funds"].select().decodeList<SinkingFund>().filter {
                it.familyId == myMember.familyId && it.isActive
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSinkingFunds() FAILED: ${e.message}")
            return emptyList()
        }
    }

    suspend fun createSinkingFund(fund: SinkingFund): SinkingFund? {
        try {
            val inserted = client.postgrest["sinking_funds"].insert(fund).decodeSingle<SinkingFund>()
            Log.d(TAG, "createSinkingFund() SUCCESS")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "createSinkingFund() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun createFamilyGoal(goal: FamilyGoal): FamilyGoal? {
        try {
            val inserted = client.postgrest["family_goals"].insert(goal).decodeSingle<FamilyGoal>()
            Log.d(TAG, "createFamilyGoal() SUCCESS")
            return inserted
        } catch (e: Exception) {
            Log.e(TAG, "createFamilyGoal() FAILED: ${e.message}")
            return null
        }
    }

    suspend fun contributeSinkingFund(fundId: String, addedAmount: Double) {
        try {
            val existing = client.postgrest["sinking_funds"].select().decodeList<SinkingFund>().find { it.id == fundId }
            if (existing != null) {
                client.postgrest["sinking_funds"].update(
                    mapOf("current_amount" to (existing.currentAmount + addedAmount))
                ) { filter { eq("id", fundId) } }
                Log.d(TAG, "contributeSinkingFund() SUCCESS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "contributeSinkingFund() FAILED: ${e.message}")
        }
    }

    // ── Member Presence ──

    suspend fun updateLastSeen() {
        try {
            val myMember = getMyFamilyMember() ?: return
            val now = java.time.Instant.now().toString()
            client.postgrest["family_members"].update(
                mapOf("last_seen_at" to now)
            ) { filter { eq("id", myMember.id) } }
        } catch (e: Exception) {
            Log.e(TAG, "updateLastSeen() FAILED: ${e.message}")
        }
    }

    // ── Typing Status ──

    suspend fun setTypingStatus(familyId: String, isTyping: Boolean) {
        try {
            val myMember = getMyFamilyMember() ?: return
            val existing = client.postgrest["family_typing_status"].select().decodeList<TypingStatus>().find {
                it.familyId == familyId && it.userId == myMember.userId
            }
            val now = java.time.Instant.now().toString()
            if (existing != null) {
                client.postgrest["family_typing_status"].update(
                    mapOf("is_typing" to isTyping, "updated_at" to now)
                ) { filter { eq("id", existing.id) } }
            } else {
                client.postgrest["family_typing_status"].insert(
                    TypingStatus(familyId = familyId, userId = myMember.userId, isTyping = isTyping)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "setTypingStatus() FAILED: ${e.message}")
        }
    }

    suspend fun getTypingStatuses(familyId: String): List<TypingStatus> {
        return try {
            client.postgrest["family_typing_status"].select().decodeList<TypingStatus>().filter {
                it.familyId == familyId && it.isTyping
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTypingStatuses() FAILED: ${e.message}")
            emptyList()
        }
    }

    // ─── Affiliate Shopping ─────────────────────────────────────────────────
    suspend fun getAffiliateProducts(): List<AffiliateProduct> {
        return try {
            client.postgrest["affiliate_products"].select().decodeList<AffiliateProduct>()
        } catch (e: Exception) {
            Log.e(TAG, "getAffiliateProducts() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun recordAffiliateClick(click: AffiliateClick) {
        try {
            client.postgrest["affiliate_clicks"].insert(click)
        } catch (e: Exception) {
            Log.e(TAG, "recordAffiliateClick() FAILED: ${e.message}")
        }
    }

    suspend fun getAffiliateClicks(userId: String): List<AffiliateClick> {
        return try {
            client.postgrest["affiliate_clicks"].select { filter { eq("user_id", userId) } }.decodeList<AffiliateClick>()
        } catch (e: Exception) {
            Log.e(TAG, "getAffiliateClicks() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAffiliateClickStats(): List<AffiliateClick> {
        return try {
            client.postgrest["affiliate_clicks"].select().decodeList<AffiliateClick>()
        } catch (e: Exception) {
            Log.e(TAG, "getAffiliateClickStats() FAILED: ${e.message}")
            emptyList()
        }
    }

    suspend fun recordCatalogRequest(request: AffiliateCatalogRequest) {
        try {
            client.postgrest["affiliate_catalog_requests"].insert(request)
        } catch (e: Exception) {
            Log.e(TAG, "recordCatalogRequest() FAILED: ${e.message}")
        }
    }

    // Recursive JSON serializer that correctly handles nested Maps, Lists, and primitives.
    // org.json.JSONObject(map) fails on nested structures — this helper solves that.
    private fun anyToJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> org.json.JSONObject.quote(value)
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Map<*, *> -> {
                val entries = value.entries.joinToString(",") { (k, v) ->
                    "${org.json.JSONObject.quote(k.toString())}:${anyToJson(v)}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val items = value.joinToString(",") { anyToJson(it) }
                "[$items]"
            }
            else -> org.json.JSONObject.quote(value.toString())
        }
    }

    // Dedicated SupervisorJob scope (not viewModelScope) so an in-flight request survives
    // the original caller's coroutine being cancelled (e.g. a recomposition that relaunched
    // the LaunchedEffect) — the next identical call just awaits the same Deferred instead of
    // firing a second HTTP request. Keyed on functionName+payload so distinct actions/args
    // never collide.
    private val edgeCallScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val edgeCallMutex = kotlinx.coroutines.sync.Mutex()
    private val inFlightEdgeCalls = mutableMapOf<String, kotlinx.coroutines.Deferred<Map<String, Any?>>>()

    /** 30s — يمنع ANR على شبكة بطيئة، ويكفي كل الأكشنات ما عدا البحث الحي (راجع fetchLiveMarketPrices). */
    const val DEFAULT_EDGE_TIMEOUT_MS = 30_000L

    suspend fun callEdgeFunction(
        functionName: String,
        body: Map<String, Any>,
        timeoutMs: Long = DEFAULT_EDGE_TIMEOUT_MS
    ): Map<String, Any?> {
        // Use anyToJson instead of org.json.JSONObject to handle nested Maps/Lists correctly
        val jsonStr = anyToJson(body)
        val requestKey = "$functionName:$jsonStr"

        val deferred = edgeCallMutex.withLock {
            inFlightEdgeCalls[requestKey] ?: edgeCallScope.async {
                try {
                    executeEdgeFunctionWithBackoff(functionName, jsonStr, timeoutMs)
                } finally {
                    edgeCallMutex.withLock { inFlightEdgeCalls.remove(requestKey) }
                }
            }.also { inFlightEdgeCalls[requestKey] = it }
        }
        return deferred.await()
    }

    // 429 (rate limit) gets a bounded exponential backoff — never an infinite retry loop.
    // Retry-After (seconds, per RFC 6585) wins when the server sends one; otherwise
    // 1s/2s/4s + jitter. Any other non-2xx status fails immediately, same as before.
    private suspend fun executeEdgeFunctionWithBackoff(
        functionName: String,
        jsonStr: String,
        timeoutMs: Long = DEFAULT_EDGE_TIMEOUT_MS
    ): Map<String, Any?> {
        val maxRetries = 3
        var attempt = 0
        while (true) {
            try {
                Log.d(TAG, "callEdgeFunction($functionName) payload: ${jsonStr.take(500)} (attempt ${attempt + 1})")
                val urlString = "${BuildConfig.SUPABASE_URL}/functions/v1/$functionName"
                val token = client.auth.currentSessionOrNull()?.accessToken ?: BuildConfig.SUPABASE_ANON_KEY

                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                // إنشاء الاتصال نفسه له سقف ثابت — لو TCP مش بيتفتح، مالوش لازمة ننتظر مهلة
                // القراءة الطويلة. اللي بيطول هو رد السيرفر (بحث حي)، مش الـ handshake.
                connection.connectTimeout = DEFAULT_EDGE_TIMEOUT_MS.toInt()
                connection.readTimeout = timeoutMs.toInt()
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { os ->
                    val input = jsonStr.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode

                if (responseCode == 429 && attempt < maxRetries) {
                    connection.errorStream?.close()
                    val retryAfterMs = connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000L)
                        ?: ((1000L shl attempt) + kotlin.random.Random.nextLong(0, 300))
                    Log.w(TAG, "callEdgeFunction($functionName) 429 rate limited — retry ${attempt + 1}/$maxRetries in ${retryAfterMs}ms")
                    kotlinx.coroutines.delay(retryAfterMs)
                    attempt++
                    continue
                }

                val raw = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (responseCode !in 200..299) {
                    throw Exception("HTTP Error $responseCode: $raw")
                }

                Log.d(TAG, "callEdgeFunction($functionName) success: ${raw.take(300)}")
                return jsonStringToMap(raw)
            } catch (e: Exception) {
                Log.e(TAG, "callEdgeFunction($functionName) FAILED: ${e.message}")
                throw e
            }
        }
    }

    private fun jsonStringToMap(json: String): Map<String, Any?> {
        val obj = org.json.JSONObject(json)
        return obj.keys().asSequence().associateWith { key ->
            val value = obj.get(key)
            when (value) {
                is org.json.JSONObject -> jsonStringToMap(value.toString())
                is org.json.JSONArray -> (0 until value.length()).map { i ->
                    val item = value.get(i)
                    if (item is org.json.JSONObject) jsonStringToMap(item.toString()) else item
                }
                org.json.JSONObject.NULL -> null
                else -> value
            }
        }
    }
}
