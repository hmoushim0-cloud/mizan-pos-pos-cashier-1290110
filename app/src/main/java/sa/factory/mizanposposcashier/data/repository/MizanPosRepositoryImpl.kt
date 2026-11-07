package sa.factory.mizanposposcashier.data.repository

import sa.factory.mizanposposcashier.domain.repository.MizanPosRepository
import sa.factory.mizanposposcashier.hardware.HardwareSensorDriver
import sa.factory.mizanposposcashier.model.MizanPosItem
import sa.factory.mizanposposcashier.model.MizanPosOrder
import sa.factory.mizanposposcashier.model.MizanPosTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MizanPosRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : MizanPosRepository {

    private val _catalog = MutableStateFlow<List<MizanPosItem>>(listOf(
        MizanPosItem("1", "الباقة التنفيذية المعتمدة", "تكامل مباشر مع ESC/POS Thermal Receipt Printer + Barcode Scanner", 180.0, "معتمد", "الخدمات الرئيسية"),
        MizanPosItem("2", "خدمة المعالجة الفورية", "أداء أصيل بسرعة 60 إطار في الثانية", 95.0, "فوري", "المراقبة الحية"),
        MizanPosItem("3", "باقة الصيانة والدعم السحابي", "تشفير كامل للبيانات المحلية ومزامنة آمنة", 250.0, "شامل", "التقارير الضريبية")
    ))
    override val catalogFlow: Flow<List<MizanPosItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<MizanPosItem>>(emptyList())
    override val activeCart: StateFlow<List<MizanPosItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<MizanPosTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            MizanPosTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: MizanPosItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: MizanPosItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): MizanPosOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = MizanPosOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
