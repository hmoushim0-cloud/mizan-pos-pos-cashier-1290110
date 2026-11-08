package sa.factory.mizanposposcashier.engine

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class EscPosPaperStatus {
    READY,
    LOW_PAPER_WARNING,
    PAPER_OUT,
    COVER_OPEN,
    HEAD_OVERHEAT
}

data class ThermalReceiptJob(
    val jobId: String,
    val byteCount: Int,
    val rawBytes: ByteArray,
    val tlvQrBase64: String,
    val invoiceHashSha256: String,
    val paperStatus: EscPosPaperStatus,
    val printLatencyMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ThermalReceiptJob
        return jobId == other.jobId && rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = jobId.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

data class PosReceiptItem(
    val sku: String,
    val name: String,
    val quantity: Int,
    val unitPriceSar: Double
) {
    val totalSar: Double get() = quantity * unitPriceSar
}

class EscPosThermalPrinterProtocolEngine {

    companion object {
        val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val CMD_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01) // GS ! 1
        val CMD_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0
        val CMD_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V B 0
        val CMD_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // ESC p 0 25 250
        val LF = byteArrayOf(0x0A)
    }

    private var paperStatus: EscPosPaperStatus = EscPosPaperStatus.READY
    private var totalJobsCount: Int = 0
    private var totalBytesStreamed: Long = 0

    fun getPaperStatus(): EscPosPaperStatus = paperStatus

    fun setPaperStatus(status: EscPosPaperStatus) {
        this.paperStatus = status
    }

    fun getTotalJobsCount(): Int = totalJobsCount

    fun getTotalBytesStreamed(): Long = totalBytesStreamed

    fun validateSaudiVatNumber(vatNumber: String): Boolean {
        val clean = vatNumber.trim()
        return clean.length == 15 && clean.startsWith("3") && clean.endsWith("3") && clean.all { it.isDigit() }
    }

    fun computeSha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun buildTlvField(tag: Int, value: String): ByteArray {
        val valBytes = value.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(tag)
        out.write(valBytes.size)
        out.write(valBytes)
        return out.toByteArray()
    }

    fun generateZatcaTlvQr(
        sellerName: String,
        vatNumber: String,
        timestampIso: String,
        totalWithVat: String,
        vatAmount: String,
        invoiceHash: String,
        digitalSignature: String = "ZATCA_SIMULATED_ECDSA_P256_STAMP"
    ): String {
        val out = ByteArrayOutputStream()
        out.write(buildTlvField(1, sellerName))
        out.write(buildTlvField(2, vatNumber))
        out.write(buildTlvField(3, timestampIso))
        out.write(buildTlvField(4, totalWithVat))
        out.write(buildTlvField(5, vatAmount))
        out.write(buildTlvField(6, invoiceHash))
        out.write(buildTlvField(7, digitalSignature))

        val bytes = out.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun buildNativeEscPosQrCode(payload: String): ByteArray {
        val out = ByteArrayOutputStream()
        val dataBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val len = dataBytes.size + 3
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()

        // 1. Function 167: Set QR Model 2
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // 2. Function 169: Set QR Module size (6 dots)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06))
        // 3. Function 180: Store data in symbol storage area
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(dataBytes)
        // 4. Function 181: Print symbol data
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))

        return out.toByteArray()
    }

    fun buildReceiptPayload(
        storeName: String,
        vatNumber: String,
        invoiceNumber: String,
        items: List<PosReceiptItem>,
        paymentMethod: String = "Mada Contactless EMV"
    ): ThermalReceiptJob {
        val startTime = System.currentTimeMillis()
        val out = ByteArrayOutputStream()

        require(validateSaudiVatNumber(vatNumber)) { "Invalid Saudi VAT number: $vatNumber" }

        val subtotal = items.sumOf { it.totalSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestampIso = sdf.format(Date(startTime))

        val totalStr = String.format(Locale.US, "%.2f", total)
        val vatStr = String.format(Locale.US, "%.2f", vat)
        val subtotalStr = String.format(Locale.US, "%.2f", subtotal)

        val canonicalHashInput = "$invoiceNumber|$timestampIso|$storeName|$vatNumber|$totalStr|$vatStr"
        val invoiceHash = computeSha256(canonicalHashInput)
        val tlvQr = generateZatcaTlvQr(storeName, vatNumber, timestampIso, totalStr, vatStr, invoiceHash)

        // 1. Initialize Printer
        out.write(CMD_INIT)

        // 2. Header
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write(CMD_DOUBLE_HEIGHT)
        out.write("$storeName\n".toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_NORMAL_SIZE)
        out.write("فاتورة ضريبية مبسطة (ZATCA)\n".toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write("الرقم الضريبي: $vatNumber\n".toByteArray(StandardCharsets.UTF_8))
        out.write("رقم الفاتورة: $invoiceNumber\n".toByteArray(StandardCharsets.UTF_8))
        out.write("التاريخ: $timestampIso\n".toByteArray(StandardCharsets.UTF_8))
        out.write("------------------------------------------\n".toByteArray(StandardCharsets.UTF_8))

        // 3. Line Items
        out.write(CMD_ALIGN_LEFT)
        out.write(String.format(Locale.US, "%-20s %4s %8s %8s\n", "البند", "الكمية", "السعر", "الإجمالي").toByteArray(StandardCharsets.UTF_8))
        out.write("------------------------------------------\n".toByteArray(StandardCharsets.UTF_8))
        for (item in items) {
            val line = String.format(
                Locale.US,
                "%-20s %4d %8.2f %8.2f\n",
                item.name.take(20),
                item.quantity,
                item.unitPriceSar,
                item.totalSar
            )
            out.write(line.toByteArray(StandardCharsets.UTF_8))
        }
        out.write("------------------------------------------\n".toByteArray(StandardCharsets.UTF_8))

        // 4. Financial Totals
        out.write(String.format(Locale.US, "المجموع الفرعي:             %12s SAR\n", subtotalStr).toByteArray(StandardCharsets.UTF_8))
        out.write(String.format(Locale.US, "ضريبة القيمة المضافة (15%%):  %12s SAR\n", vatStr).toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_BOLD_ON)
        out.write(String.format(Locale.US, "الإجمالي النهائي:           %12s SAR\n", totalStr).toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write("طريقة السداد: $paymentMethod\n".toByteArray(StandardCharsets.UTF_8))
        out.write("------------------------------------------\n".toByteArray(StandardCharsets.UTF_8))

        // 5. Native QR Code
        out.write(CMD_ALIGN_CENTER)
        out.write(buildNativeEscPosQrCode(tlvQr))
        out.write(LF)
        out.write("رمز التحقق الضريبي المعتمد - هيئة الزكاة والضريبة والجمارك\n".toByteArray(StandardCharsets.UTF_8))
        out.write(LF)
        out.write(LF)

        // 6. Paper Cut & Cash Drawer Kick
        out.write(CMD_FEED_AND_CUT)
        out.write(CMD_DRAWER_KICK)

        val rawBytes = out.toByteArray()
        val latency = System.currentTimeMillis() - startTime

        totalJobsCount++
        totalBytesStreamed += rawBytes.size

        return ThermalReceiptJob(
            jobId = "JOB-$invoiceNumber",
            byteCount = rawBytes.size,
            rawBytes = rawBytes,
            tlvQrBase64 = tlvQr,
            invoiceHashSha256 = invoiceHash,
            paperStatus = paperStatus,
            printLatencyMs = latency
        )
    }
}
