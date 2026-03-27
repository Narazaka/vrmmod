package net.narazaka.vrmmod.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.Util
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.TriState
import java.util.function.Function

/**
 * Custom RenderTypes that use TRIANGLES mode instead of QUADS.
 *
 * Iris overwrites per-vertex normals with a face normal for QUADS,
 * but preserves per-vertex normals for TRIANGLES.
 * Using TRIANGLES mode allows smooth shading to work correctly with Iris.
 *
 * Uses reflection to access package-private RenderType.create() and
 * protected RenderStateShard fields, since placing classes in
 * net.minecraft.client.renderer causes JPMS conflicts on NeoForge.
 */
object VrmRenderType {

    // Reflection: RenderType.create(String, VertexFormat, Mode, int, CompositeState)
    private val createMethod = RenderType::class.java.getDeclaredMethod(
        "create",
        String::class.java,
        VertexFormat::class.java,
        VertexFormat.Mode::class.java,
        Int::class.javaPrimitiveType,
        RenderType.CompositeState::class.java,
    ).also { it.isAccessible = true }

    // Reflection: protected static fields from RenderStateShard
    private fun <T> getStaticField(name: String): T {
        @Suppress("UNCHECKED_CAST")
        return RenderStateShard::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(null) as T
    }

    private val RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER: RenderStateShard.ShaderStateShard =
        getStaticField("RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER")
    private val RENDERTYPE_ENTITY_TRANSLUCENT_SHADER: RenderStateShard.ShaderStateShard =
        getStaticField("RENDERTYPE_ENTITY_TRANSLUCENT_SHADER")
    private val NO_TRANSPARENCY: RenderStateShard.TransparencyStateShard =
        getStaticField("NO_TRANSPARENCY")
    private val TRANSLUCENT_TRANSPARENCY: RenderStateShard.TransparencyStateShard =
        getStaticField("TRANSLUCENT_TRANSPARENCY")
    private val NO_CULL: RenderStateShard.CullStateShard =
        getStaticField("NO_CULL")
    private val LIGHTMAP: RenderStateShard.LightmapStateShard =
        getStaticField("LIGHTMAP")
    private val OVERLAY: RenderStateShard.OverlayStateShard =
        getStaticField("OVERLAY")

    private fun create(name: String, format: VertexFormat, mode: VertexFormat.Mode, bufferSize: Int, state: RenderType.CompositeState): RenderType =
        createMethod.invoke(null, name, format, mode, bufferSize, state) as RenderType

    private val ENTITY_CUTOUT_NO_CULL_TRIANGLES: Function<ResourceLocation, RenderType> = Util.memoize { texture ->
        val state = RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
            .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
            .setTransparencyState(NO_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(true)
        create(
            "vrm_entity_cutout_no_cull",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            1536,
            state,
        )
    }

    private val ENTITY_TRANSLUCENT_TRIANGLES: Function<ResourceLocation, RenderType> = Util.memoize { texture ->
        val state = RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(true)
        create(
            "vrm_entity_translucent",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            1536,
            state,
        )
    }

    fun entityCutoutNoCullTriangles(texture: ResourceLocation): RenderType =
        ENTITY_CUTOUT_NO_CULL_TRIANGLES.apply(texture)

    fun entityTranslucentTriangles(texture: ResourceLocation): RenderType =
        ENTITY_TRANSLUCENT_TRIANGLES.apply(texture)
}
