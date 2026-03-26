package net.narazaka.vrmmod.vroidhub

import net.minecraft.network.chat.Component

/**
 * Generates license display items per VRoid Hub guidelines.
 * https://developer.vroid.com/guidelines/conditions_of_use.html
 *
 * VRM 1.0: data from latest_character_model_version.vrm_meta.*
 * VRM 0.0: data from license.*
 */
object VRoidHubLicenseDisplay {

    data class LicenseItem(
        val label: Component,
        val value: Component,
        val isOk: Boolean?,  // true=OK, false=NG, null=未設定/special
    )

    fun buildItems(model: CharacterModel): List<LicenseItem> {
        val vrmMeta = model.latest_character_model_version?.vrm_meta
        return if (vrmMeta != null) {
            buildVrm10Items(vrmMeta)
        } else {
            buildVrm00Items(model.license)
        }
    }

    private fun buildVrm10Items(meta: VrmMeta): List<LicenseItem> {
        val items = mutableListOf<LicenseItem>()

        // アバター利用
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.avatar_use"),
            value = when (meta.avatarPermission) {
                "everyone" -> ok()
                "onlySeparatelyLicensedPerson", "onlyAuthor" -> ng()
                else -> notSet()
            },
            isOk = when (meta.avatarPermission) { "everyone" -> true; null -> null; else -> false },
        ))

        // 暴力表現での利用
        items.add(boolItem("vrmmod.vroidhub.license.violent_usage", meta.allowExcessivelyViolentUsage))

        // 性的表現での利用
        items.add(boolItem("vrmmod.vroidhub.license.sexual_usage", meta.allowExcessivelySexualUsage))

        // 宗教・政治目的での利用 (VRM 1.0 only)
        items.add(boolItem("vrmmod.vroidhub.license.political_religious", meta.allowPoliticalOrReligiousUsage))

        // 反社会的・憎悪表現での利用 (VRM 1.0 only)
        items.add(boolItem("vrmmod.vroidhub.license.antisocial_hate", meta.allowAntisocialOrHateUsage))

        // 法人利用
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.corporate_use"),
            value = when (meta.commercialUsage) {
                "corporation" -> ok()
                "personalProfit", "personalNonProfit" -> ng()
                else -> notSet()
            },
            isOk = when (meta.commercialUsage) { "corporation" -> true; null -> null; else -> false },
        ))

        // 個人の商用利用
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.personal_commercial"),
            value = when (meta.commercialUsage) {
                "corporation", "personalProfit" -> ok()
                "personalNonProfit" -> ng()
                else -> notSet()
            },
            isOk = when (meta.commercialUsage) { "corporation", "personalProfit" -> true; null -> null; else -> false },
        ))

        // 再配布
        items.add(boolItem("vrmmod.vroidhub.license.redistribution", meta.allowRedistribution))

        // 改変
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.modification"),
            value = when (meta.modification) {
                "allowModification", "allowModificationRedistribution" -> ok()
                "prohibited" -> ng()
                else -> notSet()
            },
            isOk = when (meta.modification) { "allowModification", "allowModificationRedistribution" -> true; null -> null; else -> false },
        ))

        // 改変物の再配布 (VRM 1.0 only)
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.modification_redistribution"),
            value = when (meta.modification) {
                "allowModificationRedistribution" -> ok()
                "allowModification", "prohibited" -> ng()
                else -> notSet()
            },
            isOk = when (meta.modification) { "allowModificationRedistribution" -> true; null -> null; else -> false },
        ))

        // クレジット表記
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.credit"),
            value = when (meta.creditNotation) {
                "required" -> Component.translatable("vrmmod.vroidhub.license.required")
                "unnecessary" -> Component.translatable("vrmmod.vroidhub.license.not_required")
                else -> notSet()
            },
            isOk = when (meta.creditNotation) { "unnecessary" -> true; "required" -> false; else -> null },
        ))

        return items
    }

    private fun buildVrm00Items(license: CharacterModelLicense?): List<LicenseItem> {
        if (license == null) return emptyList()
        val items = mutableListOf<LicenseItem>()

        // アバター利用
        items.add(allowDisallowItem("vrmmod.vroidhub.license.avatar_use",
            license.characterization_allowed_user, okValue = "everyone", ngValue = "author"))

        // 暴力表現での利用
        items.add(allowDisallowItem("vrmmod.vroidhub.license.violent_usage",
            license.violent_expression))

        // 性的表現での利用
        items.add(allowDisallowItem("vrmmod.vroidhub.license.sexual_usage",
            license.sexual_expression))

        // 法人利用
        items.add(allowDisallowItem("vrmmod.vroidhub.license.corporate_use",
            license.corporate_commercial_use))

        // 個人の商用利用
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.personal_commercial"),
            value = when (license.personal_commercial_use) {
                "profit" -> ok()
                "nonprofit" -> Component.translatable("vrmmod.vroidhub.license.nonprofit_only")
                "disallow" -> ng()
                else -> notSet()
            },
            isOk = when (license.personal_commercial_use) { "profit" -> true; "disallow" -> false; else -> null },
        ))

        // 再配布
        items.add(allowDisallowItem("vrmmod.vroidhub.license.redistribution",
            license.redistribution))

        // 改変
        items.add(allowDisallowItem("vrmmod.vroidhub.license.modification",
            license.modification))

        // クレジット表記
        items.add(LicenseItem(
            label = Component.translatable("vrmmod.vroidhub.license.credit"),
            value = when (license.credit) {
                "necessary" -> Component.translatable("vrmmod.vroidhub.license.required")
                "unnecessary" -> Component.translatable("vrmmod.vroidhub.license.not_required")
                else -> notSet()
            },
            isOk = when (license.credit) { "unnecessary" -> true; "necessary" -> false; else -> null },
        ))

        return items
    }

    private fun boolItem(labelKey: String, value: Boolean?): LicenseItem = LicenseItem(
        label = Component.translatable(labelKey),
        value = when (value) { true -> ok(); false -> ng(); else -> notSet() },
        isOk = value,
    )

    private fun allowDisallowItem(
        labelKey: String,
        value: String,
        okValue: String = "allow",
        ngValue: String = "disallow",
    ): LicenseItem = LicenseItem(
        label = Component.translatable(labelKey),
        value = when (value) { okValue -> ok(); ngValue -> ng(); else -> notSet() },
        isOk = when (value) { okValue -> true; ngValue -> false; else -> null },
    )

    private fun ok() = Component.translatable("vrmmod.vroidhub.license.ok")
    private fun ng() = Component.translatable("vrmmod.vroidhub.license.ng")
    private fun notSet() = Component.translatable("vrmmod.vroidhub.license.not_set")
}
