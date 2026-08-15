package com.example.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug this file exists to stop from coming back.
 *
 * `txnKind`'s default expression computes the right value, so reading the data class makes
 * the classification look correct. It never reached the database: kotlinx.serialization
 * omits any property still equal to its default, and `zad_transactions.txn_kind` carries
 * `default 'expense'` in Postgres. Expenses matched by accident; income silently became
 * spending. Three real rows worth 30,000 were stored `is_expense=false, txn_kind='expense'`
 * and every money figure in the app — which filters on txn_kind, not is_expense — read them
 * as an overspend of −20,000 against a 10,000 budget.
 *
 * These assertions are on the encoded JSON, not on the Kotlin property, because the Kotlin
 * property was never wrong. Only the wire format was.
 */
class ZadTransactionSerializationTest {

    // encodeDefaults stays false, exactly like supabase-kt's own serializer — the point is
    // to prove @EncodeDefault survives that setting, not to test a friendlier config.
    private val json = Json { encodeDefaults = false }

    @Test
    fun `income keeps txn_kind on the wire`() {
        val tx = ZadTransaction(amount = 10000.0, title = "راتب", isExpense = false, category = "دخل")
        val obj = json.encodeToString(ZadTransaction.serializer(), tx).let {
            Json.parseToJsonElement(it).jsonObject
        }

        assertTrue("txn_kind اتحذف من الـ JSON — ده بالظبط البق", obj.containsKey("txn_kind"))
        assertEquals("income", obj["txn_kind"]!!.jsonPrimitive.content)
        assertEquals(false, obj["is_expense"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `expense keeps txn_kind on the wire`() {
        val tx = ZadTransaction(amount = 250.0, title = "بيتادرم", isExpense = true)
        val obj = Json.parseToJsonElement(
            json.encodeToString(ZadTransaction.serializer(), tx),
        ).jsonObject

        assertTrue(obj.containsKey("txn_kind"))
        assertEquals("expense", obj["txn_kind"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an explicit transfer is not rewritten by the default expression`() {
        val tx = ZadTransaction(
            amount = 5000.0, title = "سحب صراف", isExpense = true,
            txnKind = "transfer", transferTo = "cash", wallet = "cash",
        )
        val obj = Json.parseToJsonElement(
            json.encodeToString(ZadTransaction.serializer(), tx),
        ).jsonObject

        assertEquals("transfer", obj["txn_kind"]!!.jsonPrimitive.content)
        assertEquals("cash", obj["transfer_to"]!!.jsonPrimitive.content)
    }

    @Test
    fun `is_verified false is sent rather than left to the column default`() {
        // Postgres has `default true` here, so omitting the field stores the opposite of
        // what the object says and Task 27's unverified-count is permanently zero.
        val tx = ZadTransaction(amount = 40.0, title = "رسالة بنك", isExpense = true)
        val obj = Json.parseToJsonElement(
            json.encodeToString(ZadTransaction.serializer(), tx),
        ).jsonObject

        assertTrue(obj.containsKey("is_verified"))
        assertEquals(false, obj["is_verified"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `wallet is sent so the cash ledger cannot silently default`() {
        val tx = ZadTransaction(amount = 40.0, title = "قهوة", isExpense = true, wallet = "cash")
        val obj = Json.parseToJsonElement(
            json.encodeToString(ZadTransaction.serializer(), tx),
        ).jsonObject

        assertEquals("cash", obj["wallet"]!!.jsonPrimitive.content)
    }
}
