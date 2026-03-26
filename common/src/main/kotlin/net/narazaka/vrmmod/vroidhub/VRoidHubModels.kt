package net.narazaka.vrmmod.vroidhub

data class VRoidHubResponse<T>(
    val data: T? = null,
    val error: VRoidHubError? = null,
)

data class VRoidHubError(
    val code: String? = null,
    val message: String? = null,
)

data class CharacterModel(
    val id: String = "",
    val name: String? = null,
    val is_downloadable: Boolean = false,
    val is_other_users_available: Boolean = false,
    val character: CharacterInfo? = null,
    val license: CharacterModelLicense? = null,
    val latest_character_model_version: CharacterModelVersion? = null,
    val portrait_image: PortraitImage? = null,
    val age_limit: AgeLimit? = null,
)

data class AgeLimit(
    val is_r18: Boolean = false,
    val is_r15: Boolean = false,
    val is_adult: Boolean = false,
)

data class CharacterInfo(
    val name: String = "",
    val user: UserInfo? = null,
)

data class UserInfo(
    val id: String = "",
    val name: String = "",
)

data class CharacterModelLicense(
    val characterization_allowed_user: String = "default",
    val violent_expression: String = "default",
    val sexual_expression: String = "default",
    val corporate_commercial_use: String = "default",
    val personal_commercial_use: String = "default",
    val modification: String = "default",
    val redistribution: String = "default",
    val credit: String = "default",
)

data class CharacterModelVersion(
    val id: String = "",
    val spec_version: String? = null,
    val vrm_meta: VrmMeta? = null,
)

/** VRM 1.0 meta from latest_character_model_version.vrm_meta (API uses camelCase) */
data class VrmMeta(
    val avatarPermission: String? = null,
    val allowExcessivelyViolentUsage: Boolean? = null,
    val allowExcessivelySexualUsage: Boolean? = null,
    val allowPoliticalOrReligiousUsage: Boolean? = null,
    val allowAntisocialOrHateUsage: Boolean? = null,
    val commercialUsage: String? = null,
    val allowRedistribution: Boolean? = null,
    val modification: String? = null,
    val creditNotation: String? = null,
)

data class PortraitImage(
    val sq150: ImageInfo? = null,
    val sq300: ImageInfo? = null,
)

data class ImageInfo(
    val url: String = "",
)

data class DownloadLicense(
    val id: String = "",
    val character_model_id: String = "",
)

data class TokenResponse(
    val access_token: String = "",
    val token_type: String = "",
    val expires_in: Int = 0,
    val refresh_token: String = "",
)

data class AccountInfo(
    val user_detail: UserDetail? = null,
)

data class UserDetail(
    val user: UserInfo? = null,
)
