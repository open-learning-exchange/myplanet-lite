package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.ole.planet.myplanet.lite.util.BirthDateString

data class FetchUsersRequest(
    val baseUrl: String,
    val credentials: org.ole.planet.myplanet.lite.profile.StoredCredentials?,
    val sessionCookie: String?,
    val planetCode: String?,
    val parentCode: String?,
    val pageSize: Int = 25,
    val skip: Int = 0,
    val searchTerm: String? = null,
    val excludedUserIds: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MembershipFindRequest(val selector: MembershipSelector)

@JsonClass(generateAdapter = true)
data class MembershipSelector(
    val userId: String,
    val teamType: String,
    val docType: String,
    val status: StatusClause,
)

@JsonClass(generateAdapter = true)
data class TeamMembershipFindRequest(val selector: TeamMembershipSelector)

@JsonClass(generateAdapter = true)
data class TeamMembershipSelector(
    val teamId: String,
    val docType: String,
    val status: StatusClause,
)

@JsonClass(generateAdapter = true)
data class StatusClause(
    @param:Json(name = $$"$or") val or: List<StatusCondition>,
)

@JsonClass(generateAdapter = true)
data class StatusCondition(
    @param:Json(name = $$"$exists") val exists: Boolean? = null,
    @param:Json(name = $$"$ne") val notEquals: String? = null,
)

@JsonClass(generateAdapter = true)
data class MembershipFindResponse(val docs: List<MembershipDocument>?)

@JsonClass(generateAdapter = true)
data class MultipleMemberCountFindRequest(
    val selector: MultipleMemberCountSelector,
    val fields: List<String>,
    val limit: Int? = null,
)

@JsonClass(generateAdapter = true)
data class MultipleMemberCountSelector(
    val teamId: IdsInClause,
    val docType: String,
    val status: StatusClause,
)

@JsonClass(generateAdapter = true)
data class MemberTeamIdDocument(
    @param:Json(name = "_id") val id: String?,
    val teamId: String?,
)

@JsonClass(generateAdapter = true)
data class MultipleMemberCountFindResponse(val docs: List<MemberTeamIdDocument>?)

@JsonClass(generateAdapter = true)
data class MembershipDocument(
    @param:Json(name = "_id") val id: String?,
    @param:Json(name = "_rev") val revision: String?,
    val teamId: String?,
    val userId: String?,
    val teamPlanetCode: String?,
    val teamType: String?,
    val userPlanetCode: String?,
    val docType: String?,
    val isLeader: Boolean?,
    val status: String?,
    val role: String? = null,
)

data class TeamMemberDetails(
    val username: String?,
    val fullName: String?,
    val isLeader: Boolean,
    val hasAvatar: Boolean,
    val membership: MembershipDocument?,
    val role: String? = null,
    val userId: String? = null,
    val userPlanetCode: String? = null,
)

data class TeamMemberProfileDetails(
    val username: String,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val language: String?,
    val level: String?,
    val gender: String?,
    val birthDate: String?,
    val hasAvatar: Boolean,
) {
    val fullName: String?
        get() {
            val parts = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }
            return if (parts.isEmpty()) null else parts.joinToString(" ")
        }
}

@JsonClass(generateAdapter = true)
data class TeamsFindRequest(val selector: TeamsSelector)

@JsonClass(generateAdapter = true)
data class TeamsSelector(
    val status: String,
    val type: String,
    val teamType: String,
    @param:Json(name = "_id") val ids: IdsInClause,
)

@JsonClass(generateAdapter = true)
data class IdsInClause(
    @param:Json(name = $$"$in") val ids: List<String>,
)

@JsonClass(generateAdapter = true)
data class NonMemberTeamsFindRequest(
    val selector: NonMemberTeamsSelector,
    val limit: Int? = null,
    val skip: Int? = null,
)

@JsonClass(generateAdapter = true)
data class NonMemberTeamsSelector(
    @param:Json(name = "_id") val ids: IdsNotInClause?,
    val status: String,
    val type: String,
    val teamType: String? = null,
)

@JsonClass(generateAdapter = true)
data class IdsNotInClause(
    @param:Json(name = $$"$nin") val ids: List<String>,
)

@JsonClass(generateAdapter = true)
data class TeamsFindResponse(val docs: List<TeamDocument>?)

@JsonClass(generateAdapter = true)
data class SearchTeamsFindRequest(val selector: SearchTeamsSelector)

@JsonClass(generateAdapter = true)
data class SearchTeamsSelector(
    val name: RegexCondition,
    @param:Json(name = "_id") val id: NotEqualCondition,
    val status: String = "active",
    val type: String = "team",
)

@JsonClass(generateAdapter = true)
data class RegexCondition(@param:Json(name = $$"$regex") val regex: String)

@JsonClass(generateAdapter = true)
data class NotEqualCondition(@param:Json(name = $$"$ne") val value: String = "")

@JsonClass(generateAdapter = true)
data class TeamDocument(
    @param:Json(name = "_id") val id: String?,
    @param:Json(name = "_rev") val revision: String?,
    val limit: Int?,
    val status: String?,
    val type: String?,
    val teamType: String?,
    val name: String?,
    @param:Json(name = "teamName") val teamName: String?,
    @param:Json(name = "planetCode") val planetCode: String?,
    val teamPlanetCode: String?,
    val description: String?,
    val services: String?,
    val rules: String?,
    val requests: List<Any>?,
    val createdDate: Long?,
    val createdBy: String?,
    @param:Json(name = "parentCode") val parentCode: String?,
    @param:Json(name = "public") val isPublic: Boolean?,
    @param:Json(name = "memberCount") val memberCount: Int?,
    @param:Json(name = "membersCount") val membersCount: Int?,
    val members: List<Any>?,
)

@JsonClass(generateAdapter = true)
data class JoinRequestFindRequest(val selector: JoinRequestSelector)

@JsonClass(generateAdapter = true)
data class JoinRequestSelector(
    val docType: String,
    val teamType: String? = null,
    val teamId: String? = null,
    val userId: String,
)

@JsonClass(generateAdapter = true)
data class TeamJoinRequestFindRequest(val selector: TeamJoinRequestSelector)

data class TeamJoinRequestDetails(
    val username: String,
    val fullName: String,
    val hasAvatar: Boolean,
    val request: JoinRequestDocument,
)

@JsonClass(generateAdapter = true)
data class TeamJoinRequestSelector(
    val teamId: String,
    val teamPlanetCode: String,
    val docType: String,
    val status: StatusClause,
)

@JsonClass(generateAdapter = true)
data class JoinRequestDocument(
    @param:Json(name = "_id") val id: String?,
    @param:Json(name = "_rev") val revision: String?,
    val docType: String?,
    val teamId: String?,
    val teamType: String?,
    val teamPlanetCode: String?,
    val userId: String?,
    val userPlanetCode: String?,
)

@JsonClass(generateAdapter = true)
data class JoinRequestFindResponse(val docs: List<JoinRequestDocument>?)

@JsonClass(generateAdapter = true)
data class JoinTeamRequest(
    val docType: String = "request",
    val teamId: String,
    val teamType: String = "local",
    val teamPlanetCode: String?,
    val userId: String,
    val userPlanetCode: String?,
)

@JsonClass(generateAdapter = true)
data class DeleteDocumentRequest(
    @param:Json(name = "_id") val id: String,
    @param:Json(name = "_rev") val revision: String,
    @param:Json(name = "_deleted") val deleted: Boolean,
)

@JsonClass(generateAdapter = true)
data class BulkMembershipDeleteRequest(val docs: List<BulkMembershipDeleteDoc>)

@JsonClass(generateAdapter = true)
data class BulkMembershipDeleteDoc(
    @param:Json(name = "_id") val id: String,
    @param:Json(name = "_rev") val revision: String,
    val teamId: String,
    val teamPlanetCode: String,
    val teamType: String,
    val userId: String,
    val userPlanetCode: String,
    val docType: String,
    val isLeader: Boolean,
    @param:Json(name = "_deleted") val deleted: Boolean,
)

@JsonClass(generateAdapter = true)
data class BulkMembershipAddRequest(val docs: List<BulkMembershipAddDoc>)

@JsonClass(generateAdapter = true)
data class BulkMembershipAddDoc(
    val teamId: String,
    val teamPlanetCode: String,
    val teamType: String,
    val userId: String,
    val userPlanetCode: String,
    val docType: String,
    val isLeader: Boolean,
)

@JsonClass(generateAdapter = true)
data class UserDocument(
    @param:Json(name = "_id") val _id: String?,
    @param:Json(name = "_attachments") val attachments: Attachments?,
    @param:Json(name = "planetCode") val planetCode: String?,
    @param:Json(name = "parentCode") val parentCode: String?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val language: String?,
    val phoneNumber: String?,
    @param:BirthDateString val birthDate: Long?,
    val gender: String?,
    val level: String?,
    val couchId: String? = null,
)

@JsonClass(generateAdapter = true)
data class Attachments(
    @param:Json(name = "img") val image: Attachment?,
)

@JsonClass(generateAdapter = true)
data class Attachment(
    @param:Json(name = "content_type") val contentType: String?,
    val revpos: Int?,
    val digest: String?,
    val length: Long?,
    val stub: Boolean?,
)

@JsonClass(generateAdapter = true)
data class UsersFindRequest(val selector: UserIdSelector)

@JsonClass(generateAdapter = true)
data class UserIdSelector(@param:Json(name = "_id") val ids: IdsInClause)

@JsonClass(generateAdapter = true)
data class UsersFindResponse(val docs: List<UserDocument>?)

data class AddTeamMemberRequest(
    val teamId: String,
    val teamPlanetCode: String,
    val teamType: String = "local",
    val userId: String,
    val userPlanetCode: String,
)
