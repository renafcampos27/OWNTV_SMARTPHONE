package tv.own.owntv.mobile.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelOrderMergeTest {
    @Test fun firstOrderKeepsTheEditedSequence() {
        assertEquals(listOf(3L,1L,2L),mergeChannelOrder(emptyList(),listOf(3L,1L,2L)))
    }
    @Test fun hiddenPositionsSurviveVisibleReordering() {
        assertEquals(listOf(3L,9L,1L,8L,2L),mergeChannelOrder(listOf(1L,9L,2L,8L,3L),listOf(3L,1L,2L)))
    }
    @Test fun newChannelsCanJoinWithoutDuplicateIds() {
        assertEquals(listOf(4L,9L,2L,1L),mergeChannelOrder(listOf(1L,9L,2L),listOf(4L,2L,1L,4L)))
    }
    @Test fun emptyEditedSubsetLeavesExistingOrder() {
        assertEquals(listOf(1L,9L,2L),mergeChannelOrder(listOf(1L,9L,2L),emptyList()))
    }
}
