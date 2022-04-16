/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.bridges

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pan.alexander.tordnscrypt.data.bridges.RelayAddressFingerprint
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Named

private const val SIMULTANEOUS_CHECKS = 3

private const val MAX_RELAY_COUNT = 30

@ExperimentalCoroutinesApi
class BridgeInteractor @Inject constructor(
    private val repository: BridgeRepository,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher,
    private val pathVars: dagger.Lazy<PathVars>
) {

    private val modulesStatus = ModulesStatus.getInstance()

    private val timeouts = MutableSharedFlow<BridgePingData>()

    fun observeTimeouts() = timeouts.asSharedFlow()

    suspend fun measureTimeouts(bridges: List<String>) =
        withContext(dispatcherIo.limitedParallelism(SIMULTANEOUS_CHECKS)) {
            bridges.forEach {
                try {
                    launch {
                        timeouts.emit(
                            BridgePingData(it.hashCode(), repository.getTimeout(it))
                        )
                    }
                } catch (ignored: CancellationException) {
                } catch (e: Exception) {
                    loge("BridgeCheckerInteractor measureTimeouts", e)
                }
            }
        }

    suspend fun requestRelays(): List<RelayAddressFingerprint> = withContext(dispatcherIo) {
        try {
            var host = ""
            var port = 0
            if (modulesStatus.isTorReady) {
                host = LOOPBACK_ADDRESS
                port = pathVars.get().torSOCKSPort.toInt()
            }

            repository.getRelaysWithFingerprintAndAddress(host, port)
                .shuffled()
                .take(MAX_RELAY_COUNT)
        } catch (e: Exception) {
            loge("BridgeCheckerInteractor requestRelays", e)
            emptyList()
        }
    }
}
