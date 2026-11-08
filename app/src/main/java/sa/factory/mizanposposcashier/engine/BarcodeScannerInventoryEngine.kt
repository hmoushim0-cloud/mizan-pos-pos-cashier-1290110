package sa.factory.mizanposposcashier.engine

enum class BarcodeSymbology {
    EAN_13,
    EAN_8,
    CODE_128,
    QR_CODE,
    UNKNOWN
}

enum class ScannerTriggerMode {
    MANUAL_TRIGGER,
    CONTINUOUS_AUTO_SENSE,
    AIM_AND_PULL
}

data class ScannedProductDetails(
    val sku: String,
    val barcode: String,
    val arabicName: String,
    val priceSar: Double,
    val category: String,
    var stockQuantity: Int
)

data class BarcodeScanResult(
    val rawBarcode: String,
    val symbology: BarcodeSymbology,
    val isValid: Boolean,
    val parsedSku: String?,
    val matchedProduct: ScannedProductDetails?,
    val decodeLatencyMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

class BarcodeScannerInventoryEngine {

    private val inventory = mutableMapOf<String, ScannedProductDetails>()
    private var triggerMode: ScannerTriggerMode = ScannerTriggerMode.CONTINUOUS_AUTO_SENSE
    private var totalScansProcessed: Long = 0
    private var successfulDecodes: Long = 0
    private var failedDecodes: Long = 0

    init {
        // Pre-populate core Saudi supermarket / retail catalog
        registerProduct(ScannedProductDetails("SKU-6281001", "6281001002018", "قهوة سعودية أصيلة خولاني (500 جم)", 65.0, "القهوة والمشروبات", 120))
        registerProduct(ScannedProductDetails("SKU-6281002", "6281002003025", "تمور سكري فاخر القصيم (1 كجم)", 45.0, "التمور والأغذية", 85))
        registerProduct(ScannedProductDetails("SKU-6281003", "6281003004032", "زيت زيتون الجوف العضوي (750 مل)", 55.0, "المنتجات الزراعية", 60))
        registerProduct(ScannedProductDetails("SKU-6281004", "6281004005049", "عسل سدر بلدي طبيعي (500 جم)", 180.0, "العسل والمغذيات", 40))
        registerProduct(ScannedProductDetails("SKU-6281005", "6281005006056", "ماء ورد طائفي مقطر (250 مل)", 35.0, "العناية والزيوت", 95))
    }

    fun registerProduct(product: ScannedProductDetails) {
        inventory[product.barcode] = product
    }

    fun getProductByBarcode(barcode: String): ScannedProductDetails? = inventory[barcode]

    fun getInventoryCount(): Int = inventory.size

    fun getTriggerMode(): ScannerTriggerMode = triggerMode

    fun setTriggerMode(mode: ScannerTriggerMode) {
        this.triggerMode = mode
    }

    fun getScanMetrics(): Triple<Long, Long, Long> = Triple(totalScansProcessed, successfulDecodes, failedDecodes)

    fun validateEan13Checksum(barcode: String): Boolean {
        if (barcode.length != 13 || !barcode.all { it.isDigit() }) return false
        val digits = barcode.map { it.toString().toInt() }
        var sum = 0
        for (i in 0 until 12) {
            sum += if (i % 2 == 0) digits[i] else digits[i] * 3
        }
        val remainder = sum % 10
        val checkDigit = if (remainder == 0) 0 else 10 - remainder
        return checkDigit == digits[12]
    }

    fun validateEan8Checksum(barcode: String): Boolean {
        if (barcode.length != 8 || !barcode.all { it.isDigit() }) return false
        val digits = barcode.map { it.toString().toInt() }
        var sum = 0
        for (i in 0 until 7) {
            sum += if (i % 2 == 0) digits[i] * 3 else digits[i]
        }
        val remainder = sum % 10
        val checkDigit = if (remainder == 0) 0 else 10 - remainder
        return checkDigit == digits[7]
    }

    fun detectSymbology(barcode: String): BarcodeSymbology {
        val clean = barcode.trim()
        return when {
            clean.length == 13 && clean.all { it.isDigit() } -> BarcodeSymbology.EAN_13
            clean.length == 8 && clean.all { it.isDigit() } -> BarcodeSymbology.EAN_8
            clean.startsWith("CODE128:") || clean.startsWith("C128-") || (clean.length in 6..30 && clean.matches(Regex("^[A-Z0-9_-]+$"))) -> BarcodeSymbology.CODE_128
            clean.startsWith("http://") || clean.startsWith("https://") || clean.length > 50 -> BarcodeSymbology.QR_CODE
            else -> BarcodeSymbology.UNKNOWN
        }
    }

    fun processBarcodeScan(rawBarcode: String): BarcodeScanResult {
        val startTime = System.currentTimeMillis()
        totalScansProcessed++

        val clean = rawBarcode.trim()
        val symbology = detectSymbology(clean)

        val isValid = when (symbology) {
            BarcodeSymbology.EAN_13 -> validateEan13Checksum(clean)
            BarcodeSymbology.EAN_8 -> validateEan8Checksum(clean)
            BarcodeSymbology.CODE_128 -> clean.isNotBlank()
            BarcodeSymbology.QR_CODE -> clean.isNotBlank()
            BarcodeSymbology.UNKNOWN -> false
        }

        if (!isValid) {
            failedDecodes++
            val latency = System.currentTimeMillis() - startTime
            return BarcodeScanResult(
                rawBarcode = clean,
                symbology = symbology,
                isValid = false,
                parsedSku = null,
                matchedProduct = null,
                decodeLatencyMs = latency
            )
        }

        successfulDecodes++
        val product = inventory[clean]
        val latency = System.currentTimeMillis() - startTime

        return BarcodeScanResult(
            rawBarcode = clean,
            symbology = symbology,
            isValid = true,
            parsedSku = product?.sku ?: "UNKNOWN-SKU",
            matchedProduct = product,
            decodeLatencyMs = latency
        )
    }

    fun deductInventory(barcode: String, quantity: Int): Boolean {
        val product = inventory[barcode] ?: return false
        if (product.stockQuantity >= quantity) {
            product.stockQuantity -= quantity
            return true
        }
        return false
    }

    fun restockInventory(barcode: String, quantity: Int): Boolean {
        val product = inventory[barcode] ?: return false
        product.stockQuantity += quantity
        return true
    }
}
