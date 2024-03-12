import java.awt.image.BufferedImage
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

fun captureImage() {
    val hostName = "127.0.0.1" // or the IP address of the Python server if it's on a different machine
    val portNumber = 12345

    try {
        Socket(hostName, portNumber).use { socket ->
            PrintWriter(socket.getOutputStream(), true).use { out ->
                DataInputStream(socket.getInputStream()).use { dis ->
                    out.print("capture")
                    out.flush()
                    out.print("capture")
                    out.flush()
                    val imageSize = 320 * 240 * 4 // 4 bytes per float
                    val imageData = ByteArray(imageSize)
                    dis.readFully(imageData)
                    val array = decodeFloat2D(byteArray = imageData, 240, 320)
                    println(array.size)
                    println(array[0].size)
                    println(array[0][0])
                    saveGrayscaleImage(array, "test.png")
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun decodeFloat2D(byteArray: ByteArray, numRows: Int, numCols: Int): Array<FloatArray> {
    val byteBuffer = ByteBuffer.wrap(byteArray)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // Adjust if your data is big-endian

    val floatValues = mutableListOf<Float>()
    while (byteBuffer.hasRemaining()) {
        floatValues.add(byteBuffer.float)
    }

    val floatArray2D = Array(numRows) { FloatArray(numCols) }
    for (row in 0 until numRows) {
        for (col in 0 until numCols) {
            floatArray2D[row][col] = floatValues[row * numCols + col]
        }
    }

    return floatArray2D
}

fun saveGrayscaleImage(data: Array<FloatArray>, fileName: String) {
    val height = data.size
    val width = data[0].size
    val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)

    for (y in data.indices) {
        for (x in data[y].indices) {
            val color = (data[y][x]).toInt()
            val rgb = color shl 16 or (color shl 8) or color
            image.setRGB(x, y, rgb)
        }
    }

    val file = File(fileName)
    ImageIO.write(image, "png", file)
    println("Image saved to $fileName")
}

fun main() {
    captureImage()
}
