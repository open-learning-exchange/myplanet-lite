import re

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "r") as f:
    content = f.read()

# Make sure we use material theme since item_team_member.xml might use material views.
content = content.replace('@Config(sdk = [33])', '@Config(sdk = [33], theme = "@style/Theme.MaterialComponents")')

# Also, the setup has `setTheme(androidx.appcompat.R.style.Theme_AppCompat)` which might break it. Let's make it Theme.MaterialComponents
content = content.replace('setTheme(androidx.appcompat.R.style.Theme_AppCompat)', 'setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)')

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "w") as f:
    f.write(content)
