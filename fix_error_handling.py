import sys

filepath = 'app/src/main/java/org/ole/planet/myplanet/lite/TeamsFragment.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

old_membership_error = """            val memberships = membershipResult.getOrElse {
                handleLoadError()
                return@launch
            }"""

new_membership_error = """            val memberships = membershipResult.getOrElse {
                handleLoadError()
                isLoading = false
                updateLoadingVisibility()
                return@launch
            }"""

if old_membership_error in content:
    content = content.replace(old_membership_error, new_membership_error)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
