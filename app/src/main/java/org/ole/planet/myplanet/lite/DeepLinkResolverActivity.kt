/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-01-20
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardServerCatalog
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository
import org.ole.planet.myplanet.lite.util.IntentUtils

class DeepLinkResolverActivity : ComponentActivity() {
    private val surveysRepository = DashboardSurveysRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppNavigator.resolveDeepLink(
            context = this,
            intent = intent,
            surveysRepository = surveysRepository,
            scope = lifecycleScope,
            onFinished = { finish() }
        )
    }




}
