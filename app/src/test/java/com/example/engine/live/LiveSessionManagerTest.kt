package com.example.engine.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionManagerTest {

    @Test
    fun testLiveSessionStateValues() {
        val states = LiveSessionState.values()
        assertTrue(states.contains(LiveSessionState.IDLE_LISTENING_WAKEWORD))
        assertTrue(states.contains(LiveSessionState.CONNECTING))
        assertTrue(states.contains(LiveSessionState.LIVE_ACTIVE))
        assertTrue(states.contains(LiveSessionState.DISCONNECTING))
        assertTrue(states.contains(LiveSessionState.ERROR))
    }

    @Test
    fun testSystemInstructionPresence() {
        val instruction = GeminiLiveClient.SYSTEM_INSTRUCTION
        assertNotNull(instruction)
        assertTrue(instruction.contains("You are Jarvis"))
        assertTrue(instruction.contains("Voice-First Brevity"))
        assertTrue(instruction.contains("Natural Flow & Interruption"))
        assertTrue(instruction.contains("Language Adaptation"))
        assertTrue(instruction.contains("Session Closure"))
        assertTrue(instruction.contains("Going to sleep now, just say Hello whenever you need me."))
    }

    @Test
    fun testAudioStreamRecorderConstants() {
        assertEquals(16000, AudioStreamRecorder.SAMPLE_RATE)
        assertEquals(1600, AudioStreamRecorder.CHUNK_SIZE_BYTES)
        assertTrue(AudioStreamRecorder.SPEECH_ENERGY_THRESHOLD > 0)
    }
}
