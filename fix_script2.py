import re

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "r") as f:
    content = f.read()

# I also need to replace `teamMemberName.text` with `binding.teamMemberName.text` etc.
# in testTeamMemberViewHolderBind, testTeamMemberViewHolderBind_nonLeaderAndIsCurrentUser, testTeamMemberViewHolderBind_noUsernameOrFullName

content = re.sub(r'(?<!binding\.)teamMemberAvatar', 'binding.teamMemberAvatar', content)
content = re.sub(r'(?<!binding\.)teamMemberName', 'binding.teamMemberName', content)
content = re.sub(r'(?<!binding\.)teamMemberUsername', 'binding.teamMemberUsername', content)
content = re.sub(r'(?<!binding\.)teamMemberRole', 'binding.teamMemberRole', content)
content = re.sub(r'(?<!binding\.)teamMemberRemoveButton', 'binding.teamMemberRemoveButton', content)

content = content.replace("binding.binding.", "binding.")
content = content.replace('root.performClick()', 'binding.root.performClick()')

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "w") as f:
    f.write(content)
