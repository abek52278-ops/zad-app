package com.example.ui.viewmodels

import com.example.data.ZadAiRepository.AgentExecuted
import com.example.data.ZadAiRepository.AgentProposal
import com.example.data.ZadAiRepository.AgentTurnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the `497ddec` fallback-safety contract: when the server reports a mid-turn
 * failure that already executed writes (`partial: true`), the client must always show
 * what happened and must never fall back to the [[ACTION]] protocol — falling back here
 * would re-execute the same write a second time. See `docs/agent/PROGRESS.md`'s Phase 2
 * entry and `zad-brain/index.ts`'s `handleAgentTurn`.
 */
class BuildAgentTurnReplyTest {

    private fun result(
        reply: String = "",
        executed: List<AgentExecuted> = emptyList(),
        proposals: List<AgentProposal> = emptyList(),
        toolAttempted: Boolean = false,
        partial: Boolean = false,
    ) = AgentTurnResult(reply, executed, proposals, toolAttempted, partial)

    @Test
    fun `plain reply with no tools renders as-is`() {
        val text = buildAgentTurnReply(result(reply = "عندك 3 علب لبن في المخزون."))
        assertEquals("عندك 3 علب لبن في المخزون.", text)
    }

    @Test
    fun `executed tools render as checkmark lines`() {
        val text = buildAgentTurnReply(
            result(executed = listOf(AgentExecuted(tool = "add_inventory_item", summary = "اتضاف \"لبن\" (2) للمخزون")))
        )
        assertEquals("✅ اتضاف \"لبن\" (2) للمخزون", text)
    }

    @Test
    fun `money proposals render as a confirmation prompt`() {
        val text = buildAgentTurnReply(
            result(proposals = listOf(AgentProposal(tool = "log_transaction", summary = "مصروف: 50 ريال — بقالة", input = emptyMap())))
        )
        assertTrue(text!!.contains("🤔 أأكد ده؟"))
        assertTrue(text.contains("مصروف: 50 ريال — بقالة"))
    }

    @Test
    fun `nothing happened and not partial falls back to the old protocol`() {
        val text = buildAgentTurnReply(result())
        assertNull(text)
    }

    @Test
    fun `partial with executed writes always renders and never falls back`() {
        val text = buildAgentTurnReply(
            result(
                executed = listOf(AgentExecuted(tool = "add_inventory_item", summary = "اتضاف \"لبن\" (2) للمخزون")),
                partial = true,
            )
        )
        assertEquals("✅ اتضاف \"لبن\" (2) للمخزون", text)
    }

    @Test
    fun `partial with only a pending proposal still renders and never falls back`() {
        val text = buildAgentTurnReply(
            result(
                proposals = listOf(AgentProposal(tool = "log_transaction", summary = "مصروف: 50 ريال — بقالة", input = emptyMap())),
                partial = true,
            )
        )
        assertTrue(text!!.contains("مصروف: 50 ريال — بقالة"))
    }

    /**
     * Not reachable today — the server only sets `partial: true` alongside a non-empty
     * `executed` or `proposals` (`handleAgentTurn`'s own guard). This asserts the client
     * doesn't *depend* on that guarantee: if it were ever relaxed, this must still render
     * something and return true from `tryAgentTurn`, not silently fall back.
     */
    @Test
    fun `partial with nothing else still renders instead of returning null`() {
        val text = buildAgentTurnReply(result(partial = true))
        assertTrue(text != null && text.isNotBlank())
    }
}
