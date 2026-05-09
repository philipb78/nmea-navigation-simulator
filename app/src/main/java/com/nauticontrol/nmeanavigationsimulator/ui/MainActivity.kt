package com.nauticontrol.nmeanavigationsimulator.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nauticontrol.nmeanavigationsimulator.R
import com.nauticontrol.nmeanavigationsimulator.databinding.ActivityMainBinding
import com.nauticontrol.nmeanavigationsimulator.model.ConnectionState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupInputs()
        observeUi()
    }

    private fun setupInputs() {
        binding.ipEditText.setText(viewModel.uiState.value.ipAddress)
        binding.portEditText.setText(viewModel.uiState.value.port)
        binding.speedConfigTextView.text = viewModel.uiState.value.speedConfigText
        binding.updateRateTextView.text = viewModel.uiState.value.updateRateText
        binding.deviationTextView.text = viewModel.uiState.value.deviationText
        binding.speedSlider.value = viewModel.uiState.value.settings.speedKnots.toFloat()
        binding.updateRateSlider.value = viewModel.uiState.value.settings.updateRateHz.toFloat()
        binding.deviationSlider.value = viewModel.uiState.value.settings.injectedDeviationNm.toFloat()

        binding.ipEditText.doAfterTextChanged { viewModel.updateIpAddress(it?.toString().orEmpty()) }
        binding.portEditText.doAfterTextChanged { viewModel.updatePort(it?.toString().orEmpty()) }
        binding.connectButton.setOnClickListener { viewModel.toggleConnection() }
        binding.simulationButton.setOnClickListener { viewModel.toggleSimulation() }
        binding.speedSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateSpeed(value)
        }
        binding.updateRateSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateRate(value)
        }
        binding.deviationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateDeviation(value)
        }
    }

    private fun observeUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.connectButton.text =
                        if (state.isConnectedOrConnecting) {
                            getString(R.string.disconnect)
                        } else {
                            getString(R.string.connect)
                        }
                    binding.simulationButton.text =
                        if (state.isSimulating) getString(R.string.stop_simulation) else getString(R.string.start_simulation)
                    binding.simulationButton.isEnabled = state.canToggleSimulation
                    binding.ipInputLayout.isEnabled = state.canEditConnectionSettings
                    binding.portInputLayout.isEnabled = state.canEditConnectionSettings
                    binding.ipInputLayout.renderError(state.ipAddressError)
                    binding.portInputLayout.renderError(state.portError)
                    binding.statusTextView.text = state.statusText
                    binding.statusTextView.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            when (state.connectionState) {
                                ConnectionState.CONNECTED -> R.color.status_connected
                                ConnectionState.CONNECTING -> R.color.route_color
                                ConnectionState.DISCONNECTED -> R.color.status_disconnected
                            }
                        )
                    )
                    binding.headingTextView.text = state.headingText
                    binding.speedTextView.text = state.speedText
                    binding.xteTextView.text = state.xteText
                    binding.waypointTextView.text = state.waypointText
                    binding.speedConfigTextView.text = state.speedConfigText
                    binding.updateRateTextView.text = state.updateRateText
                    binding.deviationTextView.text = state.deviationText
                    binding.logTextView.text = state.logLines.joinToString("\n")
                    binding.trackView.update(
                        route = state.route,
                        vesselTrack = state.vesselTrack,
                        vesselPosition = state.vesselPosition,
                        headingTrue = state.headingTrue
                    )
                }
            }
        }
    }
}

private fun TextInputLayout.renderError(errorMessage: String?) {
    error = errorMessage
    isErrorEnabled = !errorMessage.isNullOrEmpty()
}
