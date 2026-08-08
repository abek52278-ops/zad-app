package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * كان مفيش أي وعي بحالة الشبكة في التطبيق خالص — SyncOutbox.flush() بينادى بس من
 * TransactionSyncWorker (كل ٣ ساعات)، مفيش رد فعل فوري لما النت يرجع، ومفيش أي بانر
 * يقول للمستخدم إنه offline. register() مرة واحدة من MainActivity.onCreate (idempotent —
 * بيتأكد إنه مسجّل قبل كده مايعيدش)، وعلى أول انتقال offline→online بينادي SyncOutbox.flush
 * تلقائي بدل ما يستنى لحد ٣ ساعات.
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"
    private var registered = false

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun register(context: Context) {
        if (registered) return
        registered = true
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        // حالة أولية حقيقية بدل الافتراض "متصل" لحد أول callback
        val active = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        _isOnline.value = active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_isOnline.value
                _isOnline.value = true
                Log.d(TAG, "onAvailable — wasOffline=$wasOffline")
                if (wasOffline) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            SyncOutbox.flush(appContext)
                        } catch (e: Exception) {
                            Log.e(TAG, "flush() after reconnect failed: ${e.message}")
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                // ممكن يكون عندك شبكة تانية لسه شغالة (مثلاً وايفاي راح، بيانات لسه موجودة) —
                // بنتأكد من activeNetwork الفعلي بدل ما نفترض إن فقدان شبكة واحدة يعني offline
                val stillHasInternet = cm.activeNetwork?.let {
                    cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } ?: false
                _isOnline.value = stillHasInternet
                Log.d(TAG, "onLost — stillHasInternet=$stillHasInternet")
            }
        })
    }
}
