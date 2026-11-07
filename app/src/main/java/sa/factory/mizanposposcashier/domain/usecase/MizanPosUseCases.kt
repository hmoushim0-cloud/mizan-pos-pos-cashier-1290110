package sa.factory.mizanposposcashier.domain.usecase

import sa.factory.mizanposposcashier.domain.repository.MizanPosRepository
import sa.factory.mizanposposcashier.model.MizanPosItem
import sa.factory.mizanposposcashier.model.MizanPosOrder
import sa.factory.mizanposposcashier.model.MizanPosTelemetry
import kotlinx.coroutines.flow.Flow

class GetCatalogUseCase(private val repository: MizanPosRepository) {
    operator fun invoke(): Flow<List<MizanPosItem>> = repository.catalogFlow
}

class ManageCartUseCase(private val repository: MizanPosRepository) {
    val cart = repository.activeCart
    fun add(item: MizanPosItem) = repository.addToCart(item)
    fun remove(item: MizanPosItem) = repository.removeFromCart(item)
    fun clear() = repository.clearCart()
}

class ProcessCheckoutUseCase(private val repository: MizanPosRepository) {
    suspend operator fun invoke(paymentMethod: String = "Mada Contactless EMV"): MizanPosOrder =
        repository.checkout(paymentMethod)
}

class ObserveHardwareTelemetryUseCase(private val repository: MizanPosRepository) {
    operator fun invoke(): Flow<MizanPosTelemetry> = repository.telemetryFlow
}
