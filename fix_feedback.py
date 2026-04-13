import sys

filepath = 'app/src/main/java/org/ole/planet/myplanet/lite/TeamsFragment.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Ensure availableTeams is cleared on initial load in loadTeams
# The current loadTeams has availableTeams.clear() at line 156.
# Let's double check.

# Optimization: ensure isLoading is set to false in error paths of coroutineScope
old_available_error = """                val availableResult = availableTeamsDeferred.await()
                val availableData = availableResult.getOrElse {
                    handleLoadError()
                    isLoading = false
                    updateLoadingVisibility()
                    return@coroutineScope
                }"""

# It seems I already have isLoading = false there.
# Let's check the teamsResult error path.

old_teams_error = """                val teamsResult = teamsDeferred.await()
                memberTeams = teamsResult.getOrElse {
                    handleLoadError()
                    return@coroutineScope
                }"""

new_teams_error = """                val teamsResult = teamsDeferred.await()
                memberTeams = teamsResult.getOrElse {
                    handleLoadError()
                    isLoading = false
                    updateLoadingVisibility()
                    return@coroutineScope
                }"""

if old_teams_error in content:
    content = content.replace(old_teams_error, new_teams_error)

# Re-check availableTeams.clear() in loadTeams
# Line 156: availableTeams.clear()
# But wait, my fetchAvailableTeamsData doesn't clear it, and loadTeams uses it.
# loadTeams has availableTeams.clear() at the beginning.

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
