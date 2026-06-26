import sys

with open("app/src/main/java/org/ole/planet/myplanet/lite/dashboard/CreateVoiceActivity.kt", "r") as f:
    content = f.read()

target = """            pendingImages.values.forEach { pending ->
                val pattern = Regex("(!\\[[^\\]]*\\]\\()${Regex.escape(pending.fileName)}(\\))")
                processed = pattern.replace(processed) { matchResult ->
                    val prefix = matchResult.groupValues.getOrNull(1).orEmpty()
                    val suffix = matchResult.groupValues.getOrNull(2).orEmpty()
                    "$prefix${pending.file.toURI()}$suffix"
                }
            }"""

replacement = """            val pendingByFileName = pendingImages.values.associateBy { it.fileName }
            processed = IMAGE_MARKDOWN_REGEX.replace(processed) { matchResult ->
                val path = matchResult.groupValues.getOrNull(1).orEmpty()
                val pending = pendingByFileName[path]
                if (pending != null) {
                    val prefix = matchResult.value.substringBeforeLast("(") + "("
                    "$prefix${pending.file.toURI()})"
                } else {
                    matchResult.value
                }
            }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/org/ole/planet/myplanet/lite/dashboard/CreateVoiceActivity.kt", "w") as f:
        f.write(content)
