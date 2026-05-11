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
