/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-23
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.ole.planet.myplanet.lite.util.enableDrag

class TeamsActivity : BaseActivity(), CreateTeamDialogFragment.Listener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_teams)

        val root: View = findViewById(R.id.teamsRoot)
        val toolbar: MaterialToolbar = findViewById(R.id.teamsToolbar)
        val actionButton: FloatingActionButton = findViewById(R.id.teamsActionFab)

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        actionButton.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(CreateTeamDialogFragment.TAG) == null) {
                CreateTeamDialogFragment().show(supportFragmentManager, CreateTeamDialogFragment.TAG)
            }
        }
        actionButton.enableDrag()

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }
    }

    override fun onTeamCreated(teamId: String) {
        val teamsFragment = supportFragmentManager.findFragmentById(R.id.teamsFragmentContainer) as? TeamsFragment
        teamsFragment?.reloadTeams()
    }
}
