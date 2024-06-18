package drone

import java.io.File
// https://portal.opentopography.org/raster?opentopoID=OTSRTM.082015.4326.1

data class GridData(
    val ncols: Int,
    val nrows: Int,
    val xllcorner: Double,
    val yllcorner: Double,
    val cellsize: Double,
    val nodataValue: Double,
    val elevationData: List<List<Double>>
)

fun readAscFile(filePath: String): GridData {
    val lines = File(filePath).readLines()
    val header = mutableMapOf<String, String>()
    var index = 0

    // Read the header
    while (index < lines.size && lines[index].matches(Regex("^[a-zA-Z].*"))) {
        val parts = lines[index].split(Regex("\\s+"))
        header[parts[0].lowercase()] = parts[1]
        index++
    }

    // Parse header values
    val ncols = header["ncols"]!!.toInt()
    val nrows = header["nrows"]!!.toInt()
    val xllcorner = header["xllcorner"]!!.toDouble()
    val yllcorner = header["yllcorner"]!!.toDouble()
    val cellsize = header["cellsize"]!!.toDouble()
    val nodataValue = header["nodata_value"]!!.toDouble()

    // Read the elevation data
    val elevationData = mutableListOf<List<Double>>()
    while (index < lines.size) {
        if (lines[index].isEmpty()) {
            continue
        }
        val row = lines[index].split(Regex(" ")).filter { it.isNotEmpty() }.map { it.toDouble() }
        elevationData.add(row)
        index++
    }

    return GridData(ncols, nrows, xllcorner, yllcorner, cellsize, nodataValue, elevationData)
}

fun readPrjFile(filePath: String): String {
    return File(filePath).readText()
}

fun getGridIndices(gridData: GridData, latitude: Double, longitude: Double): Pair<Int, Int>? {
    // Calculate the row and column in the grid
    val col = ((longitude - gridData.xllcorner) / gridData.cellsize).toInt()
    val row = ((gridData.yllcorner + gridData.nrows * gridData.cellsize - latitude) / gridData.cellsize).toInt()

    // Check if the indices are within the grid bounds
    return if (col in 0 until gridData.ncols && row in 0 until gridData.nrows) {
        Pair(row, col)
    } else {
        null
    }
}

fun getElevationAtCoordinate(gridData: GridData, latitude: Double, longitude: Double): Double? {
    val indices = getGridIndices(gridData, latitude, longitude) ?: return null
    val (row, col) = indices

    return gridData.elevationData[row][col]
}


fun main() {
    val ascFilePath = "output_SRTMGL1.asc"

    val gridData = readAscFile(ascFilePath)

    // Example coordinates (latitude, longitude)
    val latitude = 51.578245
    val longitude = 9.971198

    val elevation = getElevationAtCoordinate(gridData, latitude, longitude)

    if (elevation != null) {
        println("Elevation at ($latitude, $longitude): $elevation")
    } else {
        println("Coordinates are out of bounds")
    }
}