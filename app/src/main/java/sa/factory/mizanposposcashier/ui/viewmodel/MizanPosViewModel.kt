package sa.factory.mizanposposcashier.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.mizanposposcashier.domain.usecase.*
import sa.factory.mizanposposcashier.model.MizanPosItem
import sa.factory.mizanposposcashier.model.MizanPosOrder
import sa.factory.mizanposposcashier.model.MizanPosTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MizanPosUiState(
    val catalog: List<MizanPosItem> = emptyList(),
    val filteredCatalog: List<MizanPosItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<MizanPosItem> = emptyList(),
    val telemetry: MizanPosTelemetry? = null,
    val lastOrder: MizanPosOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class MizanPosViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MizanPosUiState())
    val uiState: StateFlow<MizanPosUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: MizanPosItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: MizanPosItem) = manageCartUseCase.remove(item)

    fun checkout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase()
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}
