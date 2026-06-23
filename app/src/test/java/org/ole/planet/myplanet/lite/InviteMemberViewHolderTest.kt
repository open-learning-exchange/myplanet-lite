package org.ole.planet.myplanet.lite

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.databinding.ItemInviteMemberBinding
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class InviteMemberViewHolderTest {
    private lateinit var context: Context
    private lateinit var binding: ItemInviteMemberBinding
    private lateinit var viewHolder: InviteMemberViewHolder

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
        val materialContext = ContextThemeWrapper(themedContext, com.google.android.material.R.style.Theme_MaterialComponents_DayNight)

        binding = ItemInviteMemberBinding.inflate(LayoutInflater.from(materialContext), FrameLayout(materialContext), false)
        viewHolder = InviteMemberViewHolder(binding, null, { _ -> })
    }

    @Test
    fun testBindPayload() {
        // Test isDisabled = true
        viewHolder.bindPayload(true)
        assertEquals(false, binding.inviteMemberAdd.isEnabled)
        assertEquals(0.5f, binding.inviteMemberAdd.alpha)

        // Test isDisabled = false
        viewHolder.bindPayload(false)
        assertEquals(true, binding.inviteMemberAdd.isEnabled)
        assertEquals(1f, binding.inviteMemberAdd.alpha)
    }
}
