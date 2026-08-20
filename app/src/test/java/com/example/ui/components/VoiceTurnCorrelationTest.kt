package com.example.ui.components

import com.example.ui.viewmodels.AiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTurnCorrelationTest {
    @Test
    fun `late reply from interrupted turn is not selected for current turn`() {
        val oldUser = AiChatMessage(id = "user-old", text = "old", isUser = true)
        val newUser = AiChatMessage(id = "user-new", text = "new", isUser = true)
        val oldReply = AiChatMessage(
            id = "reply-old",
            text = "stale answer",
            isUser = false,
            replyToMessageId = oldUser.id
        )
        val messages = listOf(oldUser, newUser, oldReply)

        assertNull(voiceReplyForTurn(messages, newUser.id))
    }

    @Test
    fun `only reply linked to current voice turn is selected`() {
        val currentReply = AiChatMessage(
            id = "reply-current",
            text = "current answer",
            isUser = false,
            replyToMessageId = "user-current"
        )
        val messages = listOf(
            AiChatMessage(text = "unrelated", isUser = false, replyToMessageId = "user-old"),
            currentReply
        )

        assertEquals(currentReply, voiceReplyForTurn(messages, "user-current"))
    }

    @Test
    fun `persisted uncorrelated chat message never satisfies live turn`() {
        val restoredMessage = AiChatMessage(text = "restored", isUser = false)

        assertNull(voiceReplyForTurn(listOf(restoredMessage), "user-current"))
        assertNull(voiceReplyForTurn(listOf(restoredMessage), null))
    }
}
