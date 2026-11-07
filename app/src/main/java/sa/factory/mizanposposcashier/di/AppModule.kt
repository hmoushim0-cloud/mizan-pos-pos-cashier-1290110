package sa.factory.mizanposposcashier.di

import sa.factory.mizanposposcashier.data.repository.MizanPosRepositoryImpl
import sa.factory.mizanposposcashier.domain.repository.MizanPosRepository
import sa.factory.mizanposposcashier.domain.usecase.*
import sa.factory.mizanposposcashier.hardware.HardwareSensorDriver
import sa.factory.mizanposposcashier.ui.viewmodel.MizanPosViewModel

class DiContainer {
    val sensorDriver = HardwareSensorDriver()
    val repository: MizanPosRepository = MizanPosRepositoryImpl(sensorDriver)

    val getCatalogUseCase = GetCatalogUseCase(repository)
    val manageCartUseCase = ManageCartUseCase(repository)
    val processCheckoutUseCase = ProcessCheckoutUseCase(repository)
    val observeHardwareTelemetryUseCase = ObserveHardwareTelemetryUseCase(repository)

    fun provideViewModel(): MizanPosViewModel = MizanPosViewModel(
        getCatalogUseCase,
        manageCartUseCase,
        processCheckoutUseCase,
        observeHardwareTelemetryUseCase
    )
}
