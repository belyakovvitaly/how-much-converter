package converter.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FrameVotingTest {

    private val tag = Price(1299.0, "RUB")
    private val other = Price(450.0, "UAH")
    private val misread = Price(199.0, "RUB")

    /** Runs a sequence of frames through the voter and returns the end state. */
    private fun run(
        vararg frames: List<Price>,
        settings: VoteSettings = VoteSettings(),
    ): VoteState = frames.fold(VoteState()) { state, frame -> state.observe(frame, settings) }

    @Test
    fun `a reading seen once is never shown`() {
        // The whole point: a single bad frame must not reach the screen.
        assertEquals(emptyList(), run(listOf(misread)).confirmed)
    }

    @Test
    fun `a reading is shown once enough frames agree`() {
        assertEquals(emptyList(), run(listOf(tag), listOf(tag)).confirmed)
        assertEquals(listOf(tag), run(listOf(tag), listOf(tag), listOf(tag)).confirmed)
    }

    @Test
    fun `a confirmed reading survives a frame that missed it`() {
        val state = run(listOf(tag), listOf(tag), listOf(tag), emptyList())
        assertEquals(listOf(tag), state.confirmed, "one blurred frame should not blank the display")
    }

    @Test
    fun `a reading that goes away for good is dropped`() {
        var state = run(listOf(tag), listOf(tag), listOf(tag))
        assertEquals(listOf(tag), state.confirmed)

        // Score climbed to 3; it takes three empty frames to spend it, and the
        // fourth is the one that clears the display.
        repeat(3) { state = state.observe(emptyList()) }
        assertTrue(state.confirmed.isEmpty(), "still showing: ${state.confirmed}")
    }

    @Test
    fun `a long-seen reading does not outstay the thing it read`() {
        // The score is capped, so pointing at a price for a minute does not buy
        // a minute of showing it after looking away.
        var state = VoteState()
        repeat(30) { state = state.observe(listOf(tag)) }
        assertEquals(VoteSettings().maxScore, state.votes.single().score)

        repeat(VoteSettings().maxScore) { state = state.observe(emptyList()) }
        assertTrue(state.confirmed.isEmpty())
    }

    @Test
    fun `a flickering misreading never accumulates enough to be shown`() {
        // The real shape of the danger: a wrong reading that turns up now and
        // then among good ones. It gains a point and loses it again.
        val state = run(
            listOf(tag), listOf(tag, misread), listOf(tag),
            listOf(tag), listOf(tag, misread), listOf(tag),
        )
        assertEquals(listOf(tag), state.confirmed)
    }

    @Test
    fun `a new reading takes the display over from the one it replaced`() {
        // Pointing the camera from one price to another: the first is dropped
        // as its score runs out, rather than lingering beside the second.
        val state = run(
            listOf(misread), listOf(misread), listOf(misread),
            listOf(tag), listOf(tag), listOf(tag),
        )
        assertEquals(listOf(tag), state.confirmed)
    }

    @Test
    fun `two persistent readings are both shown, disagreement and all`() {
        // The limit worth being honest about: voting thins out noise, it does
        // not adjudicate. When a misreading is as persistent as the truth, both
        // appear — better that the reader sees the disagreement than that one
        // is picked confidently and wrongly.
        val state = run(
            listOf(tag, misread), listOf(tag, misread), listOf(tag, misread),
        )
        assertEquals(listOf(tag, misread), state.confirmed)
    }

    @Test
    fun `two prices in view are tracked apart`() {
        val state = run(listOf(tag, other), listOf(tag, other), listOf(tag, other))
        assertEquals(listOf(tag, other), state.confirmed)
    }

    @Test
    fun `readings keep the order they were first seen in`() {
        // So the panel does not reshuffle as prices come and go.
        val state = run(
            listOf(tag), listOf(tag), listOf(tag),
            listOf(other, tag), listOf(other, tag), listOf(other, tag),
        )
        assertEquals(listOf(tag, other), state.confirmed)
    }

    @Test
    fun `the same price twice in one frame counts once`() {
        val state = run(
            listOf(tag, tag), listOf(tag, tag),
        )
        assertEquals(1, state.votes.size)
        assertEquals(2, state.votes.single().score)
    }

    @Test
    fun `a price glimpsed in passing is forgotten rather than accumulated`() {
        // Panning across a shelf should not build a list of everything ever seen.
        var state = VoteState()
        for (amount in 1..10) {
            state = state.observe(listOf(Price(amount.toDouble(), "RUB")))
        }
        assertEquals(1, state.votes.size, "kept: ${state.votes.map { it.price }}")
        assertTrue(state.confirmed.isEmpty())
    }

    @Test
    fun `settings that could never confirm anything are refused`() {
        assertFailsWith<IllegalArgumentException> { VoteSettings(confirmAt = 2, dropAt = 2) }
        assertFailsWith<IllegalArgumentException> { VoteSettings(confirmAt = 5, maxScore = 3) }
    }
}
