package net.narazaka.vrmmod.render

/**
 * Detects whether a shader pack (Iris/OptiFine) is currently active.
 * Uses reflection to avoid hard dependency on Iris.
 */
object ShaderDetector {

    private val irisApiClass: Class<*>? = try {
        Class.forName("net.irisshaders.iris.api.v0.IrisApi")
    } catch (_: ClassNotFoundException) {
        null
    }

    private val getInstanceMethod = irisApiClass?.getMethod("getInstance")
    private val isShaderPackInUseMethod = irisApiClass?.getMethod("isShaderPackInUse")

    fun isShaderPackActive(): Boolean {
        if (irisApiClass == null) return false
        return try {
            val instance = getInstanceMethod!!.invoke(null)
            isShaderPackInUseMethod!!.invoke(instance) as Boolean
        } catch (_: Exception) {
            false
        }
    }
}
