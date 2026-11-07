package sa.factory.mizanposposcashier.domain.repository

import sa.factory.mizanposposcashier.model.MizanPosItem
import sa.factory.mizanposposcashier.model.MizanPosOrder
import sa.factory.mizanposposcashier.model.MizanPosTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MizanPosRepository {
    val catalogFlow: Flow<List<MizanPosItem>>
    val activeCart: StateFlow<List<MizanPosItem>>
    val telemetryFlow: Flow<MizanPosTelemetry>
    
    fun addToCart(item: MizanPosItem)
    fun removeFromCart(item: MizanPosItem)
    fun clearCart()
    suspend fun checkout(paymentMethod: String): MizanPosOrder
    suspend fun refreshCatalog()
}
