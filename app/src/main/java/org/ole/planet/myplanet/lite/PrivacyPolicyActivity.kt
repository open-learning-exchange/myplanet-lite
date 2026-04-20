/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-09
 */

package org.ole.planet.myplanet.lite

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import io.noties.markwon.Markwon

class PrivacyPolicyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_privacy_policy)

        val root: View = findViewById(R.id.privacyPolicyRoot)
        val toolbar: MaterialToolbar = findViewById(R.id.privacyPolicyToolbar)
        val contentView: TextView = findViewById(R.id.privacyPolicyContent)

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val markwon = Markwon.builder(this).build()
        val markdown = resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
        markwon.setMarkdown(contentView, markdown)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }
    }


}
