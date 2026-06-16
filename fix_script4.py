import re

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "r") as f:
    content = f.read()

# Fix @Config
content = content.replace('@Config(sdk = [33], theme = "@style/Theme.MaterialComponents")', '@Config(sdk = [33])')
# The error said: "No parameter with name 'theme' found". Robolectric @Config doesn't have a `theme` parameter maybe? Or maybe it's not defined. Wait, it's defined in recent Robolectric, but maybe not here, or wait maybe no parameter with name `theme` found because `theme` isn't imported correctly? Wait, Robolectric's Config doesn't have `theme`? Or maybe the project is using an older Robolectric. Actually, context theme can be set at runtime as we did.

# Let's revert back the `@Config` to `@Config(sdk = [33])` but keep the `setTheme` in `setUp()` which is `Theme_MaterialComponents_DayNight`.
content = content.replace('setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)', 'setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)')

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "w") as f:
    f.write(content)
