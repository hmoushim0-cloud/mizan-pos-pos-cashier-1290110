package sa.factory.mizanposposcashier.data.remote

import sa.factory.mizanposposcashier.model.MizanPosItem
import sa.factory.mizanposposcashier.model.MizanPosOrder

interface MizanPosApiService {
    suspend fun fetchCatalog(): List<MizanPosItem>
    suspend fun submitOrder(order: MizanPosOrder): Boolean
}
