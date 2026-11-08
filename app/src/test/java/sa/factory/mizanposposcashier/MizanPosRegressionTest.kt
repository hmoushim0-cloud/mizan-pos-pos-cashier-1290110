package sa.factory.mizanposposcashier

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.mizanposposcashier.data.repository.MizanPosRepositoryImpl
import sa.factory.mizanposposcashier.engine.*
import sa.factory.mizanposposcashier.hardware.HardwareSensorDriver
import sa.factory.mizanposposcashier.model.MizanPosItem

class MizanPosRegressionTest {

    private lateinit var printerEngine: EscPosThermalPrinterProtocolEngine
    private lateinit var scannerEngine: BarcodeScannerInventoryEngine
    private lateinit var sensorDriver: HardwareSensorDriver
    private lateinit var repository: MizanPosRepositoryImpl

    @Before
    fun setUp() {
        printerEngine = EscPosThermalPrinterProtocolEngine()
        scannerEngine = BarcodeScannerInventoryEngine()
        sensorDriver = HardwareSensorDriver(printerEngine, scannerEngine)
        repository = MizanPosRepositoryImpl(sensorDriver)
    }

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val item1 = MizanPosItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = MizanPosItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada Contactless EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testSaudiVatNumberValidation() {
        // Saudi VAT number: exactly 15 digits, starts with 3 and ends with 3
        assertTrue(printerEngine.validateSaudiVatNumber("310123456700003"))
        assertTrue(printerEngine.validateSaudiVatNumber("300000000000003"))

        assertFalse(printerEngine.validateSaudiVatNumber("110123456700003")) // Does not start with 3
        assertFalse(printerEngine.validateSaudiVatNumber("310123456700004")) // Does not end with 3
        assertFalse(printerEngine.validateSaudiVatNumber("3101234567")) // Length < 15
        assertFalse(printerEngine.validateSaudiVatNumber("3101234567ABC03")) // Non-digits
    }

    @Test
    fun testZatcaTlvBase64Generation() {
        val tlvBase64 = printerEngine.generateZatcaTlvQr(
            sellerName = "شركة ميزان للتجزئة المحدودة",
            vatNumber = "310123456700003",
            timestampIso = "2026-11-08T08:00:00Z",
            totalWithVat = "115.00",
            vatAmount = "15.00",
            invoiceHash = "a1b2c3d4e5f6"
        )

        assertNotNull(tlvBase64)
        assertTrue(tlvBase64.isNotEmpty())

        val decodedBytes = java.util.Base64.getDecoder().decode(tlvBase64)
        assertTrue(decodedBytes.isNotEmpty())
        assertEquals(1, decodedBytes[0].toInt()) // Tag 1 (Seller Name)
    }

    @Test
    fun testEscPosBinaryCommandStructure() {
        assertArrayEquals(byteArrayOf(0x1B, 0x40), EscPosThermalPrinterProtocolEngine.CMD_INIT)
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x00), EscPosThermalPrinterProtocolEngine.CMD_FEED_AND_CUT)

        val qrBytes = printerEngine.buildNativeEscPosQrCode("TEST_QR_PAYLOAD")
        assertTrue(qrBytes.isNotEmpty())
        assertEquals(0x1D.toByte(), qrBytes[0]) // GS command prefix
    }

    @Test
    fun testThermalReceiptJobCreation() {
        val items = listOf(
            PosReceiptItem("SKU-1", "قهوة سعودية خولاني", 2, 65.0),
            PosReceiptItem("SKU-2", "تمور سكري القصيم", 1, 45.0)
        )

        val job = printerEngine.buildReceiptPayload(
            storeName = "سوبرماركت ميزان المركزي",
            vatNumber = "310123456700003",
            invoiceNumber = "INV-2026-9901",
            items = items,
            paymentMethod = "Mada Contactless EMV"
        )

        assertEquals("JOB-INV-2026-9901", job.jobId)
        assertTrue(job.byteCount > 100)
        assertTrue(job.rawBytes.isNotEmpty())
        assertEquals(EscPosPaperStatus.READY, job.paperStatus)
        assertEquals(1, printerEngine.getTotalJobsCount())
        assertTrue(printerEngine.getTotalBytesStreamed() > 0)
    }

    @Test
    fun testPrinterPaperStatusTransitions() {
        assertEquals(EscPosPaperStatus.READY, printerEngine.getPaperStatus())

        printerEngine.setPaperStatus(EscPosPaperStatus.LOW_PAPER_WARNING)
        assertEquals(EscPosPaperStatus.LOW_PAPER_WARNING, printerEngine.getPaperStatus())

        printerEngine.setPaperStatus(EscPosPaperStatus.PAPER_OUT)
        assertEquals(EscPosPaperStatus.PAPER_OUT, printerEngine.getPaperStatus())
    }

    @Test
    fun testEan13ChecksumValidation() {
        // Valid EAN-13 barcodes
        assertTrue(scannerEngine.validateEan13Checksum("6281001002018"))
        assertTrue(scannerEngine.validateEan13Checksum("6281002003025"))

        // Invalid checksum
        assertFalse(scannerEngine.validateEan13Checksum("6281001002019"))
        assertFalse(scannerEngine.validateEan13Checksum("12345"))
    }

    @Test
    fun testEan8ChecksumValidation() {
        // Valid EAN-8: 96385074
        // Digits: 9, 6, 3, 8, 5, 0, 7 => Sum: (9*3 + 6) + (3*3 + 8) + (5*3 + 0) + (7*3) = 27+6 + 9+8 + 15+0 + 21 = 33+17+15+21 = 86 => 90-86 = 4
        assertTrue(scannerEngine.validateEan8Checksum("96385074"))
        assertFalse(scannerEngine.validateEan8Checksum("96385079"))
    }

    @Test
    fun testBarcodeScannerInventoryLookup() {
        val result = scannerEngine.processBarcodeScan("6281001002018")

        assertTrue(result.isValid)
        assertEquals(BarcodeSymbology.EAN_13, result.symbology)
        assertEquals("SKU-6281001", result.parsedSku)
        assertNotNull(result.matchedProduct)
        assertEquals("قهوة سعودية أصيلة خولاني (500 جم)", result.matchedProduct?.arabicName)

        val deducted = scannerEngine.deductInventory("6281001002018", 5)
        assertTrue(deducted)
        assertEquals(115, scannerEngine.getProductByBarcode("6281001002018")?.stockQuantity)
    }

    @Test
    fun testHardwareSensorDriverIntegration() = runBlocking {
        val frame = sensorDriver.observePeripheralStream().first()
        assertNotNull(frame)
        assertEquals("ESC/POS Thermal Receipt Printer + Barcode Scanner", frame.peripheralName)
        assertEquals("CONNECTING", frame.connectionState)

        val scanResult = sensorDriver.scanBarcode("6281002003025")
        assertTrue(scanResult.isValid)
        assertEquals("SKU-6281002", scanResult.parsedSku)

        val receiptJob = sensorDriver.printReceipt(
            storeName = "متجر ميزان التجريبي",
            vatNumber = "310123456700003",
            invoiceNumber = "INV-7788",
            items = listOf(PosReceiptItem("SKU-1", "منتج أ", 1, 50.0))
        )
        assertNotNull(receiptJob)
        assertTrue(receiptJob.byteCount > 50)
    }
}
