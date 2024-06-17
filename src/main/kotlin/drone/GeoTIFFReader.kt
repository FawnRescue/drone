import org.geotools.coverage.grid.GridCoverage2D
import org.geotools.coverage.grid.io.GridCoverage2DReader
import org.geotools.coverage.grid.io.GridFormatFinder
import org.geotools.coverage.grid.io.AbstractGridFormat
import org.geotools.geometry.DirectPosition2D
import org.opengis.coverage.grid.GridCoordinates
import org.opengis.geometry.DirectPosition
import org.opengis.referencing.crs.CoordinateReferenceSystem
import java.io.File

class GeoTIFFReader(tiffPath: String) {
    private val coverage: GridCoverage2D
    private val crs: CoordinateReferenceSystem

    init {
        val file = File(tiffPath)
        if (!file.exists()) {
            throw IllegalArgumentException("GeoTIFF file does not exist at path: $tiffPath")
        }

        val format: AbstractGridFormat = GridFormatFinder.findFormat(file)
        val reader: GridCoverage2DReader = format.getReader(file)
        coverage = reader.read()
        crs = coverage.coordinateReferenceSystem2D
    }

    fun getHeightAtCoordinates(lon: Double, lat: Double): Double {
        val position: DirectPosition = DirectPosition2D(crs, lon, lat)
        if (!coverage.envelope2D.contains(lon, lat)) {
            throw IllegalArgumentException("Coordinate ($lon, $lat) is outside coverage.")
        }

        val gridCoordinates: GridCoordinates = coverage.gridGeometry.worldToGrid(position)
        val x = gridCoordinates.getCoordinateValue(0)
        val y = gridCoordinates.getCoordinateValue(1)

        val raster = coverage.renderedImage.data
        return raster.getSampleDouble(x, y, 0)
    }
}

fun main() {
    try {
        val tiffPath = "height_data.tif"
        val coordinates = arrayOf(
            doubleArrayOf(9.971198, 51.578245)
        )

        val reader = GeoTIFFReader(tiffPath)
        for (coordinate in coordinates) {
            val height = reader.getHeightAtCoordinates(coordinate[0], coordinate[1])
            println("Height at (${coordinate[0]}, ${coordinate[1]}): $height")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
