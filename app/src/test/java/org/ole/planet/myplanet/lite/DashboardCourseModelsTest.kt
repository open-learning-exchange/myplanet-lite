package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseDocument

class DashboardCourseModelsTest {
    @Test
    fun `maps coverFileName to the CouchDB course attachment path`() {
        val document = CourseDocument(
            id = "course/id",
            courseTitle = "My course",
            description = null,
            coverFileName = "course cover.webp",
        )

        val item = document.toCourseItem(defaultTitle = "Course", stepNum = null)

        assertEquals("courses/course%2Fid/course%20cover.webp", item.coverPath)
    }

    @Test
    fun `does not create a cover path without coverFileName`() {
        val document = CourseDocument(
            id = "course-id",
            courseTitle = "My course",
            description = null,
        )

        val item = document.toCourseItem(defaultTitle = "Course", stepNum = null)

        assertNull(item.coverPath)
    }
}
