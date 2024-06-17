package drone

data class Checkpoint(
    val longitude: Double,
    val latitude: Double,
    val yawDeg: Float,
    val absoluteHeight: Float,
    val speedMS: Float,
    val acceptanceRadius: Float,
)
