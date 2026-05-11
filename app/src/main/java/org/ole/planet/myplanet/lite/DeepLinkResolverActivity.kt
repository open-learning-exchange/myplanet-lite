/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-01-20
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.lite.util.IntentUtils

class DeepLinkResolverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val postId = IntentUtils.extractDeepLinkPostId(intent)
        val nextIntent = Intent(this, SplashScreen::class.java).apply {
            if (postId != null) {
                putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(nextIntent)
        finish()
    }
}
