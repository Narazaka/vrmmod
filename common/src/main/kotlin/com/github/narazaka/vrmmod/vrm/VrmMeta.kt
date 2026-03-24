package com.github.narazaka.vrmmod.vrm

/**
 * VRM 1.0 meta information.
 */
data class VrmMeta(
    val name: String,
    val version: String = "",
    val authors: List<String> = emptyList(),
    val copyrightInformation: String = "",
    val licenseUrl: String = "",
)
