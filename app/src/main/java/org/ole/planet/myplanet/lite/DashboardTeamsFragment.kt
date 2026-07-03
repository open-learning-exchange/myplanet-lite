/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences

class DashboardTeamsFragment : Fragment(R.layout.fragment_dashboard_teams_feed) {

    private lateinit var emptyView: TextView
    private lateinit var teamContentContainer: FrameLayout
    private lateinit var voicesButton: ImageButton
    private lateinit var surveysButton: ImageButton

    private var selectedTeamId: String? = null
    private var selectedTeamName: String? = null
    private var currentSection: TeamSection = TeamSection.VOICES

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.teamsEmptyView)
        teamContentContainer = view.findViewById(R.id.teamContentContainer)
        voicesButton = view.findViewById(R.id.teamVoicesButton)
        surveysButton = view.findViewById(R.id.teamSurveysButton)

        voicesButton.setOnClickListener {
            switchSection(TeamSection.VOICES)
        }

        surveysButton.setOnClickListener {
            switchSection(TeamSection.SURVEYS)
        }

        updateActionSelection()

        selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        selectedTeamName = DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())

        showSelectionState(forceReload = true)
    }

    override fun onResume() {
        super.onResume()
        val previousTeamId = selectedTeamId
        val previousTeamName = selectedTeamName
        selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        selectedTeamName = DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())
        val selectionChanged = previousTeamId != selectedTeamId || previousTeamName != selectedTeamName
        showSelectionState(forceReload = selectionChanged)
    }

    private fun showSelectionState(forceReload: Boolean) {
        val teamId = selectedTeamId
        val teamName = selectedTeamName
        val hasSelection = !teamId.isNullOrBlank() && !teamName.isNullOrBlank()

        if (!teamId.isNullOrBlank() && !teamName.isNullOrBlank()) {
            emptyView.isVisible = false
            teamContentContainer.isVisible = true
            if (forceReload) {
                when (currentSection) {
                    TeamSection.VOICES -> loadTeamVoices(teamId, teamName)
                    TeamSection.SURVEYS -> loadTeamSurveys(teamId, teamName)
                }
            }
        } else {
            emptyView.text = getString(R.string.dashboard_teams_select_team_hint)
            emptyView.isVisible = true
            teamContentContainer.isVisible = false
            if (forceReload) {
                childFragmentManager.findFragmentById(R.id.teamContentContainer)?.let { fragment ->
                    childFragmentManager.beginTransaction()
                        .remove(fragment)
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    private fun loadTeamVoices(teamId: String, teamName: String) {
        val existing = childFragmentManager.findFragmentById(R.id.teamContentContainer)
        if (existing is DashboardVoicesFragment && existing.isTeamFeedFor(teamId, teamName)) {
            return
        }

        val fragment = DashboardVoicesFragment.newInstanceForTeam(teamId, teamName)
        childFragmentManager.beginTransaction()
            .replace(R.id.teamContentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun loadTeamSurveys(teamId: String, teamName: String) {
        val existing = childFragmentManager.findFragmentById(R.id.teamContentContainer)
        if (existing is DashboardTeamSurveysFragment && existing.isSurveyFeedFor(teamId, teamName)) {
            return
        }

        val fragment = DashboardTeamSurveysFragment.newInstanceForTeam(teamId, teamName)
        childFragmentManager.beginTransaction()
            .replace(R.id.teamContentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun switchSection(section: TeamSection) {
        if (currentSection == section) {
            return
        }
        currentSection = section
        updateActionSelection()
        val teamId = selectedTeamId
        val teamName = selectedTeamName
        val hasSelection = !teamId.isNullOrBlank() && !teamName.isNullOrBlank()
        if (hasSelection) {
            when (section) {
                TeamSection.VOICES -> loadTeamVoices(teamId, teamName)
                TeamSection.SURVEYS -> loadTeamSurveys(teamId, teamName)
            }
        } else {
            emptyView.text = getString(R.string.dashboard_teams_select_team_hint)
            emptyView.isVisible = true
        }
    }

    private fun updateActionSelection() {
        val voicesSelected = currentSection == TeamSection.VOICES
        voicesButton.isSelected = voicesSelected
        surveysButton.isSelected = currentSection == TeamSection.SURVEYS
        voicesButton.alpha = if (voicesSelected) 1f else 0.5f
        surveysButton.alpha = if (surveysButton.isSelected) 1f else 0.5f
    }

    private enum class TeamSection { VOICES, SURVEYS }
}
