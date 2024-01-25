package bluetooth

import AdvertisementDataRetrievalKeys
import dev.bluefalcon.*

class BluetoothClient : BlueFalconDelegate {
    private val serverUUID = "c32a2876-a31c-4cf0-a97d-1f003d91ebf8"
    private val keyCharacteristicUUID = "362b5dee-7a49-4438-8507-91483a32d5d3"
    private val blueFalcon = BlueFalcon(ApplicationContext(), serverUUID)

    init {
        blueFalcon.delegates.add(this)
        blueFalcon.scan()
    }

    override fun didCharacteristcValueChanged(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic
    ) {
        TODO("Not yet implemented")
    }

    override fun didConnect(bluetoothPeripheral: BluetoothPeripheral) {
        TODO("Not yet implemented")
    }

    override fun didDisconnect(bluetoothPeripheral: BluetoothPeripheral) {
        TODO("Not yet implemented")
    }

    override fun didDiscoverCharacteristics(bluetoothPeripheral: BluetoothPeripheral) {
        TODO("Not yet implemented")
    }

    override fun didDiscoverDevice(
        bluetoothPeripheral: BluetoothPeripheral,
        advertisementData: Map<AdvertisementDataRetrievalKeys, Any>
    ) {
        println("Discovered device: ${bluetoothPeripheral.name}")
    }

    override fun didDiscoverServices(bluetoothPeripheral: BluetoothPeripheral) {
        TODO("Not yet implemented")
    }

    override fun didReadDescriptor(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor
    ) {
        TODO("Not yet implemented")
    }

    override fun didRssiUpdate(bluetoothPeripheral: BluetoothPeripheral) {
        TODO("Not yet implemented")
    }

    override fun didUpdateMTU(bluetoothPeripheral: BluetoothPeripheral) {
        TODO("Not yet implemented")
    }

    override fun didWriteCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        success: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun didWriteDescriptor(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor
    ) {
        TODO("Not yet implemented")
    }

}

fun main() {
    val client = BluetoothClient()
}
