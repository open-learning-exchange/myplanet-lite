import re

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "r") as f:
    content = f.read()

# Replace manual building of binding with inflater
# And replace setting properties later manually using binding.something with just inflating it.
def replace_manual_building(match):
    return """val binding = ItemTeamMemberBinding.inflate(
            android.view.LayoutInflater.from(context)
        )"""

content = re.sub(r'val root = LinearLayout\(context\).*?val binding = ItemTeamMemberBinding\.bind\(\n.*?root\.apply \{\n.*?addView.*?\n.*?\}\n.*?\)', replace_manual_building, content, flags=re.DOTALL)

with open("app/src/test/java/org/ole/planet/myplanet/lite/DashboardTeamMembersSupportTest.kt", "w") as f:
    f.write(content)
