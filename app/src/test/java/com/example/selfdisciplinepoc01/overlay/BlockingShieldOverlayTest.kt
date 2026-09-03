package com.example.selfdisciplinepoc01.overlay

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class BlockingShieldOverlayTest {

    private lateinit var fakeWindowManagerHandler: FakeWindowManagerHandler
    private lateinit var fakeWindowManager: WindowManager
    private lateinit var overlay: BlockingShieldOverlay
    private lateinit var testView: View

    private class FakeWindowManagerHandler : InvocationHandler {
        val addedViews = mutableListOf<View>()
        val removedViews = mutableListOf<View>()
        var throwOnAdd: RuntimeException? = null
        var throwOnRemove: RuntimeException? = null

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            when (method.name) {
                "addView" -> {
                    throwOnAdd?.let { throw it }
                    val v = args?.get(0) as View
                    addedViews.add(v)
                    return null
                }
                "removeView" -> {
                    throwOnRemove?.let { throw it }
                    val v = args?.get(0) as View
                    removedViews.add(v)
                    return null
                }
                "removeViewImmediate" -> {
                    throwOnRemove?.let { throw it }
                    val v = args?.get(0) as View
                    removedViews.add(v)
                    return null
                }
                else -> return null
            }
        }
    }

    @Before
    fun setUp() {
        fakeWindowManagerHandler = FakeWindowManagerHandler()
        fakeWindowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java),
            fakeWindowManagerHandler
        ) as WindowManager

        val fakeContext = object : ContextWrapper(null) {}
        testView = View(fakeContext)

        overlay = BlockingShieldOverlay(fakeContext, fakeWindowManager)
        overlay.viewFactory = { testView }
    }

    @Test
    fun test1_initialState_isNotShown() {
        assertFalse(overlay.isShown())
        assertEquals(0, fakeWindowManagerHandler.addedViews.size)
    }

    @Test
    fun test2_show_becomesShown_andAddsView() {
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        assertTrue(overlay.isShown())
        assertEquals(1, fakeWindowManagerHandler.addedViews.size)
        assertEquals(testView, fakeWindowManagerHandler.addedViews[0])
    }

    @Test
    fun test3_duplicateShow_stillOnlyOneViewAdded() {
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        assertTrue(overlay.isShown())
        assertEquals(1, fakeWindowManagerHandler.addedViews.size)
    }

    @Test
    fun test4_hide_becomesHidden_andRemovesView() {
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        assertTrue(overlay.isShown())

        overlay.hide("test")
        assertFalse(overlay.isShown())
        assertEquals(1, fakeWindowManagerHandler.removedViews.size)
        assertEquals(testView, fakeWindowManagerHandler.removedViews[0])
    }

    @Test
    fun test5_duplicateHide_isSafeNoOp() {
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        overlay.hide("first_hide")
        assertEquals(1, fakeWindowManagerHandler.removedViews.size)

        overlay.hide("second_hide")
        assertEquals(1, fakeWindowManagerHandler.removedViews.size)
        assertFalse(overlay.isShown())
    }

    @Test
    fun test6_cleanup_removesViewAndResetsState() {
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        overlay.cleanup()
        assertFalse(overlay.isShown())
        assertEquals(1, fakeWindowManagerHandler.removedViews.size)

        // Multiple cleanups
        overlay.cleanup()
        overlay.cleanup()
        assertFalse(overlay.isShown())
    }

    @Test
    fun test7_windowManagerFailure_doesNotCrash_andRemainsHidden() {
        fakeWindowManagerHandler.throwOnAdd = WindowManager.BadTokenException("Simulated BadTokenException")
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        assertFalse(overlay.isShown())
        assertEquals(0, fakeWindowManagerHandler.addedViews.size)
    }

    @Test
    fun test8_windowManagerRemoveFailure_doesNotCrash() {
        overlay.show(1000L, 2000L, 1L, "com.android.chrome")
        fakeWindowManagerHandler.throwOnRemove = IllegalArgumentException("View not attached to window manager")
        overlay.hide("test_failure")
        assertFalse(overlay.isShown())
    }
}
