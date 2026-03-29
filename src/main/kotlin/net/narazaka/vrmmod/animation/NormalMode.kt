package net.narazaka.vrmmod.animation

/**
 * Controls when actual vertex normals are used instead of uniform (0,1,0).
 */
enum class NormalMode {
    /** Use actual normals when a shader pack is active, uniform otherwise. */
    AUTO,
    /** Always use uniform upward normal (unlit look). */
    OFF,
    /** Always use actual vertex normals. */
    ON,
}
