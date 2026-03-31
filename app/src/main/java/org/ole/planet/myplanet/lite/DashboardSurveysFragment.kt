/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-24
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences

class DashboardSurveysFragment : Fragment(R.layout.fragment_dashboard_surveys) {

    private lateinit var emptyView: TextView
    private lateinit var contentContainer: FrameLayout
    private var currentTeamId: String? = null
    private var currentTeamName: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.dashboardSurveysEmptyView)
        contentContainer = view.findViewById(R.id.dashboardSurveysContentContainer)
        refreshSelectionState(forceReload = true)
    }

    override fun onResume() {
        super.onResume()
        refreshSelectionState(forceReload = false)
    }

    private fun refreshSelectionState(forceReload: Boolean) {
        val selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        val selectedTeamName = DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())
        val hasSelection = !selectedTeamId.isNullOrBlank() && !selectedTeamName.isNullOrBlank()

        if (hasSelection) {
            emptyView.isVisible = false
            contentContainer.isVisible = true
            val selectionChanged = selectedTeamId != currentTeamId || selectedTeamName != currentTeamName
            if (selectionChanged || forceReload) {
                currentTeamId = selectedTeamId
                currentTeamName = selectedTeamName
                val teamId = requireNotNull(selectedTeamId)
                val teamName = requireNotNull(selectedTeamName)
                loadSurveysFragment(teamId, teamName)
            }
        } else {
            emptyView.text = getString(R.string.dashboard_teams_select_team_hint)
            emptyView.isVisible = true
            contentContainer.isVisible = false
            currentTeamId = null
            currentTeamName = null
            childFragmentManager.findFragmentById(R.id.dashboardSurveysContentContainer)?.let { fragment ->
                childFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commit()
            }
        }
    }

    private fun loadSurveysFragment(teamId: String, teamName: String) {
        val existing = childFragmentManager.findFragmentById(R.id.dashboardSurveysContentContainer)
        if (existing is DashboardTeamSurveysFragment && existing.isSurveyFeedFor(teamId, teamName)) {
            return
        }
        val fragment = DashboardTeamSurveysFragment.newInstanceForTeam(teamId, teamName)
        childFragmentManager.beginTransaction()
            .replace(R.id.dashboardSurveysContentContainer, fragment)
            .commit()
    }
}
