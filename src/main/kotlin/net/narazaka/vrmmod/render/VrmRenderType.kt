package net.narazaka.vrmmod.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.Util
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
//? if HAS_ITEM_RENDER_STATE {
import net.minecraft.util.TriState
//?}
import java.util.function.Function

/**
 * Custom RenderTypes that use TRIANGLES mode instead of QUADS.
 *
 * Iris overwrites per-vertex normals with a face normal for QUADS,
 * but preserves per-vertex normals for TRIANGLES.
 * Using TRIANGLES mode allows smooth shading to work correctly with Iris.
 *
 * Access to package-private RenderType.create() and protected RenderStateShard
 * fields is provided by Access Widener (vrmmod.accesswidener).
 */
object VrmRenderType {

    private val ENTITY_CUTOUT_NO_CULL_TRIANGLES: Function<ResourceLocation, RenderType> = Util.memoize { texture ->
        val state = RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
            //? if HAS_ITEM_RENDER_STATE {
            .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
            //?} else {
            /*.setTextureState(RenderStateShard.TextureStateShard(texture, false, false))*/
            //?}
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .createCompositeState(true)
        RenderType.create(
            "vrm_entity_cutout_no_cull",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            1536,
            state,
        )
    }

    private val ENTITY_TRANSLUCENT_TRIANGLES: Function<ResourceLocation, RenderType> = Util.memoize { texture ->
        val state = RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            //? if HAS_ITEM_RENDER_STATE {
            .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
            //?} else {
            /*.setTextureState(RenderStateShard.TextureStateShard(texture, false, false))*/
            //?}
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .createCompositeState(true)
        RenderType.create(
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
