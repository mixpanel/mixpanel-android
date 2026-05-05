package com.mixpanel.sessionreplaydemo.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.sessionreplay.MPSessionReplay
import com.mixpanel.sessionreplaydemo.Constants
import com.mixpanel.sessionreplaydemo.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textDashboard
        MPSessionReplay.getInstance()?.addSafeView(textView)
//        MPSessionReplay.getInstance()?.flush {
//            Log.d("SessionReplay", "Flush completed")
//
//            // Re-initialize SessionReplay with new configuration
//            // This demonstrates that re-initialization properly cleans up the previous instance
//            // and creates a new one, ensuring only one instance exists at a time
//            val config =
//                MPSessionReplayConfig(
//                    wifiOnly = false,
//                    autoMaskedViews = mutableSetOf(AutoMaskedView.Text),
//                    enableLogging = true,
//                )
//            val token = "YOUR_PROJECT_TOKEN"
//            val mixpanel = MixpanelAPI.getInstance(this.context, token, true)
//
//            // Re-initialization: The previous instance will be cleaned up (deinitialize() called)
//            // and a new instance will be created with the new configuration
//            MPSessionReplay.initialize(
//                textView.context.applicationContext,
//                token,
//                mixpanel.distinctId,
//                config
//            ) { result ->
//                result.fold(
//                    onSuccess = { instance ->
//                        activity?.runOnUiThread {
//                            Log.d("SessionReplay", "Re-initialized with new configuration")
//                        }
//                    },
//                    onFailure = { error ->
//                        when (error) {
//                            is MPSessionReplayError.Disabled -> {
//                                activity?.runOnUiThread {
//                                    Log.d("SessionReplay", "Session Replay disabled: ${error.reason}")
//                                    textView.text = "Session Replay disabled: ${error.reason}"
//                                }
//                            }
//                            is MPSessionReplayError.InitializationError -> {
//                                activity?.runOnUiThread {
//                                    Log.e("SessionReplay", "Re-initialization failed", error.cause)
//                                    textView.text = "Re-initialization failed: ${error.cause.message}"
//                                }
//                            }
//                            else -> {
//                                activity?.runOnUiThread {
//                                    Log.e("SessionReplay", "Re-initialization failed", error)
//                                    textView.text = "Re-initialization failed: ${error.message}"
//                                }
//                            }
//                        }
//                    }
//                )
//            }
//        }

        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        MixpanelAPI
            .getInstance(this.context, Constants.MIXPANEL_TOKEN, true)
            .track("Tapped Dashboard")

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
