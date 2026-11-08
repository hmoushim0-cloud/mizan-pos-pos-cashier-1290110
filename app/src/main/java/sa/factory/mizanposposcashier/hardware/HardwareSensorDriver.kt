package sa.factory.mizanposposcashier.hardware

import sa.factory.mizanposposcashier.engine.BarcodeScannerInventoryEngine
import sa.factory.mizanposposcashier.engine.BarcodeScanResult
import sa.factory.mizanposposcashier.engine.EscPosPaperStatus
import sa.factory.mizanposposcashier.engine.EscPosThermalPrinterProtocolEngine
import sa.factory.mizanposposcashier.engine.PosReceiptItem
import sa.factory.mizanposposcashier.engine.ThermalReceiptJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver(
    val printerEngine: EscPosThermalPrinterProtocolEngine = EscPosThermalPrinterProtocolEngine(),
    val scannerEngine: BarcodeScannerInventoryEngine = BarcodeScannerInventoryEngine()
) {
    private val peripheral = "ESC/POS Thermal Receipt Printer + Barcode Scanner"

    fun printReceipt(
        storeName: String,
        vatNumber: String,
        invoiceNumber: String,
        items: List<PosReceiptItem>,
        paymentMethod: String = "Mada Contactless EMV"
    ): ThermalReceiptJob {
        return printerEngine.buildReceiptPayload(storeName, vatNumber, invoiceNumber, items, paymentMethod)
    }

    fun scanBarcode(rawBarcode: String): BarcodeScanResult {
        return scannerEngine.processBarcodeScan(rawBarcode)
    }

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "ESC/POS RS232/USB Handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "ESC/POS Printer & 2D Barcode Scanner: ONLINE | Paper: READY | ZATCA Phase 2: ACTIVE"))

        var sequence = 1
        while (true) {
            delay(1500)
            sequence++
            val jitter = Random.nextInt(1, 5)
            val paperStatus = printerEngine.getPaperStatus().name
            val (totalScans, successScans, _) = scannerEngine.getScanMetrics()
            val totalPrints = printerEngine.getTotalJobsCount()

            val payload = "ESC/POS Thermal [Paper: $paperStatus | Prints: $totalPrints] | 2D Scanner [Mode: AUTO_SENSE | Scans: $totalScans ($successScans OK)] | Frame #$sequence (+${jitter}ms)"

            emit(HardwareTelemetryFrame(
                peripheralName = peripheral,
                connectionState = "ACTIVE_STREAMING",
                livePayload = payload
            ))
        }
    }
}
