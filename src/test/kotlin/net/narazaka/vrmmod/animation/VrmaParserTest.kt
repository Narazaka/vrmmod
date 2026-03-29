package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.vrm.HumanBone
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class VrmaParserTest {

    private val vrmaDir = Path.of("../../vrm-anims/vrma")

    @Test
    fun `parse MovementBasic returns animation clips`() {
        val file = vrmaDir.resolve("Rig_Medium_MovementBasic.vrma").toFile()
        if (!file.exists()) {
            println("Skipping test: ${file.absolutePath} not found")
            return
        }

        val clips = VrmaParser.parse(file.inputStream())

        assertTrue(clips.isNotEmpty(), "Should parse at least one animation clip")
        println("Parsed ${clips.size} clips:")
        for (clip in clips) {
            println("  '${clip.name}' duration=${clip.duration}s bones=${clip.tracks.size}")
        }
    }

    @Test
    fun `Walking_A clip exists with positive duration`() {
        val file = vrmaDir.resolve("Rig_Medium_MovementBasic.vrma").toFile()
        if (!file.exists()) {
            println("Skipping test: ${file.absolutePath} not found")
            return
        }

        val clips = VrmaParser.parse(file.inputStream())
        val walkingClip = clips.find { it.name == "Walking_A" }

        assertNotNull(walkingClip, "Walking_A clip should exist")
        assertTrue(walkingClip!!.duration > 0f, "Walking_A duration should be > 0, was ${walkingClip.duration}")
    }

    @Test
    fun `hips bone has translation keyframes`() {
        val file = vrmaDir.resolve("Rig_Medium_MovementBasic.vrma").toFile()
        if (!file.exists()) {
            println("Skipping test: ${file.absolutePath} not found")
            return
        }

        val clips = VrmaParser.parse(file.inputStream())
        // Find any clip that has hips translation
        val clipWithHipsTranslation = clips.find { clip ->
            val hipsTrack = clip.tracks[HumanBone.HIPS]
            hipsTrack != null && hipsTrack.translationKeyframes.isNotEmpty()
        }

        assertNotNull(
            clipWithHipsTranslation,
            "At least one clip should have hips translation keyframes. " +
                "Clips: ${clips.map { "${it.name}: hips=${it.tracks[HumanBone.HIPS]}" }}",
        )
    }

    @Test
    fun `bones have rotation keyframes`() {
        val file = vrmaDir.resolve("Rig_Medium_MovementBasic.vrma").toFile()
        if (!file.exists()) {
            println("Skipping test: ${file.absolutePath} not found")
            return
        }

        val clips = VrmaParser.parse(file.inputStream())
        // At least one clip should have rotation keyframes for non-hips bones
        val clipWithRotations = clips.find { clip ->
            clip.tracks.any { (bone, track) ->
                bone != HumanBone.HIPS && track.rotationKeyframes.isNotEmpty()
            }
        }

        assertNotNull(
            clipWithRotations,
            "At least one clip should have rotation keyframes for non-hips bones",
        )

        // Print bone details for the first clip with rotations
        if (clipWithRotations != null) {
            println("Clip '${clipWithRotations.name}' bones with rotation keyframes:")
            for ((bone, track) in clipWithRotations.tracks) {
                if (track.rotationKeyframes.isNotEmpty()) {
                    println("  $bone: ${track.rotationKeyframes.size} rotation keyframes")
                }
            }
        }
    }

    @Test
    fun `sample returns BonePoseMap`() {
        val file = vrmaDir.resolve("Rig_Medium_MovementBasic.vrma").toFile()
        if (!file.exists()) {
            println("Skipping test: ${file.absolutePath} not found")
            return
        }

        val clips = VrmaParser.parse(file.inputStream())
        assertTrue(clips.isNotEmpty(), "Should have clips to sample")

        val clip = clips.first()
        val poseMap = clip.sample(0f)

        assertTrue(poseMap.isNotEmpty(), "BonePoseMap should not be empty at time 0")
        println("Sampled clip '${clip.name}' at t=0: ${poseMap.size} bone poses")

        // Sample at mid-duration
        val midPose = clip.sample(clip.duration / 2f)
        assertTrue(midPose.isNotEmpty(), "BonePoseMap should not be empty at mid duration")

        // Sample beyond duration (should loop)
        val loopedPose = clip.sample(clip.duration * 2.5f)
        assertTrue(loopedPose.isNotEmpty(), "BonePoseMap should not be empty after loop")
    }
}
