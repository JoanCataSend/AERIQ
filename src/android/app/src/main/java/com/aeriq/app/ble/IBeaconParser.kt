package com.aeriq.app.ble

/** Contrato BLE encontrado en el firmware del Sprint 0 (no GATT).
 * El array [manufacturerData] es el valor devuelto por ScanRecord.getManufacturerSpecificData(0x004C):
 *   02 15 | UUID[16] | MAJOR[2] | MINOR[2] | TxPower[1]
 */
object IBeaconParser {
    const val APPLE_MANUFACTURER_ID: Int = 0x004C
    const val PROJECT_ID: String = "EPSG-GTI-PROY-3A"
    const val O3_ID: Int = 14
    const val TEMPERATURE_ID: Int = 12

    fun parse(manufacturerData: ByteArray?): BeaconReading? {
        val data = manufacturerData ?: return null
        if (data.size < 23 || (data[0].toInt() and 0xFF) != 0x02 ||
            (data[1].toInt() and 0xFF) != 0x15) return null

        val uuid = buildString(16) {
            for (i in 2..17) append((data[i].toInt() and 0xFF).toChar())
        }
        if (uuid != PROJECT_ID) return null

        val major = u16(data[18], data[19])
        val type = (major shr 8) and 0xFF
        if (type != O3_ID && type != TEMPERATURE_ID) return null
        val counter = major and 0xFF
        val raw = u16(data[20], data[21])
        // En el firmware: O3 es uint16 (ppb); temperatura es int16 (°C).
        val value = if (type == O3_ID) raw else if (raw >= 32768) raw - 65536 else raw
        return BeaconReading(uuid, type, counter, value, data[22].toInt())
    }

    private fun u16(msb: Byte, lsb: Byte): Int =
        ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)
}

data class BeaconReading(
    val uuid: String,
    val typeId: Int,
    val counter: Int,
    val value: Int,
    val txPower: Int
) {
    val unit: String get() = if (typeId == IBeaconParser.O3_ID) "ppb" else "°C"
    val label: String get() = if (typeId == IBeaconParser.O3_ID) "Ozono" else "Temperatura"
}

data class LiveMeasurement(
    val address: String,
    val reading: BeaconReading,
    val rssi: Int,
    val receivedAtMillis: Long
)
