package com.nauticontrol.nmeanavigationsimulator.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.slider.RangeSlider
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupInputs()
        observeUi()
    }

    private fun setupInputs() {
        val settings = viewModel.uiState.value.settings
        binding.ipEditText.setText(viewModel.uiState.value.ipAddress)
        binding.portEditText.setText(viewModel.uiState.value.port)
        binding.speedConfigTextView.text = viewModel.uiState.value.speedConfigText
        binding.updateRateTextView.text = viewModel.uiState.value.updateRateText
        binding.deviationTextView.text = viewModel.uiState.value.deviationText
        binding.windDirectionTextView.text = viewModel.uiState.value.windDirectionText
        binding.windSpeedTextView.text = viewModel.uiState.value.windSpeedText
        binding.depthTextView.text = viewModel.uiState.value.depthText
        binding.waterTemperatureTextView.text = viewModel.uiState.value.waterTemperatureText
        binding.currentDirectionTextView.text = viewModel.uiState.value.currentDirectionText
        binding.currentSpeedTextView.text = viewModel.uiState.value.currentSpeedText
        bindRangeSlider(binding.speedRangeSlider, settings.speedKnotsMin, settings.speedKnotsMax)
        binding.updateRateSlider.value = settings.updateRateHz.toFloat()
        binding.deviationSlider.value = settings.injectedDeviationNm.toFloat()
        bindRangeSlider(binding.windDirectionRangeSlider, settings.windDirectionTrueMin, settings.windDirectionTrueMax)
        bindRangeSlider(binding.windSpeedRangeSlider, settings.windSpeedKnotsMin, settings.windSpeedKnotsMax)
        bindRangeSlider(binding.depthRangeSlider, settings.depthMetersMin, settings.depthMetersMax)
        bindRangeSlider(
            binding.waterTemperatureRangeSlider,
            settings.waterTemperatureCelsiusMin,
            settings.waterTemperatureCelsiusMax
        )
        bindRangeSlider(binding.currentDirectionRangeSlider, settings.currentDirectionTrueMin, settings.currentDirectionTrueMax)
        bindRangeSlider(binding.currentSpeedRangeSlider, settings.currentSpeedKnotsMin, settings.currentSpeedKnotsMax)

        binding.ipEditText.doAfterTextChanged { viewModel.updateIpAddress(it?.toString().orEmpty()) }
        binding.portEditText.doAfterTextChanged { viewModel.updatePort(it?.toString().orEmpty()) }
        binding.connectButton.setOnClickListener { viewModel.toggleConnection() }
        binding.simulationButton.setOnClickListener { viewModel.toggleSimulation() }
        binding.vesselSectionButton.setOnClickListener { viewModel.toggleVesselControls() }
        binding.windSectionButton.setOnClickListener { viewModel.toggleWindControls() }
        binding.waterSectionButton.setOnClickListener { viewModel.toggleWaterControls() }
        binding.currentSectionButton.setOnClickListener { viewModel.toggleCurrentControls() }
        binding.speedRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateSpeedRange(min, max)
        })
        binding.updateRateSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateRate(value)
        }
        binding.deviationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.updateDeviation(value)
        }
        binding.windDirectionRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateWindDirectionRange(min, max)
        })
        binding.windSpeedRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateWindSpeedRange(min, max)
        })
        binding.depthRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateDepthRange(min, max)
        })
        binding.waterTemperatureRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateWaterTemperatureRange(min, max)
        })
        binding.currentDirectionRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateCurrentDirectionRange(min, max)
        })
        binding.currentSpeedRangeSlider.addOnChangeListener(rangeListener { min, max ->
            viewModel.updateCurrentSpeedRange(min, max)
        })
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
                    binding.windDirectionTextView.text = state.windDirectionText
                    binding.windSpeedTextView.text = state.windSpeedText
                    binding.depthTextView.text = state.depthText
                    binding.waterTemperatureTextView.text = state.waterTemperatureText
                    binding.currentDirectionTextView.text = state.currentDirectionText
                    binding.currentSpeedTextView.text = state.currentSpeedText
                    syncRangeSlider(binding.speedRangeSlider, state.settings.speedKnotsMin, state.settings.speedKnotsMax)
                    syncRangeSlider(
                        binding.windDirectionRangeSlider,
                        state.settings.windDirectionTrueMin,
                        state.settings.windDirectionTrueMax
                    )
                    syncRangeSlider(
                        binding.windSpeedRangeSlider,
                        state.settings.windSpeedKnotsMin,
                        state.settings.windSpeedKnotsMax
                    )
                    syncRangeSlider(binding.depthRangeSlider, state.settings.depthMetersMin, state.settings.depthMetersMax)
                    syncRangeSlider(
                        binding.waterTemperatureRangeSlider,
                        state.settings.waterTemperatureCelsiusMin,
                        state.settings.waterTemperatureCelsiusMax
                    )
                    syncRangeSlider(
                        binding.currentDirectionRangeSlider,
                        state.settings.currentDirectionTrueMin,
                        state.settings.currentDirectionTrueMax
                    )
                    syncRangeSlider(
                        binding.currentSpeedRangeSlider,
                        state.settings.currentSpeedKnotsMin,
                        state.settings.currentSpeedKnotsMax
                    )
                    binding.vesselControlsLayout.visibility = state.vesselControlsExpanded.toVisibility()
                    binding.windControlsLayout.visibility = state.windControlsExpanded.toVisibility()
                    binding.waterControlsLayout.visibility = state.waterControlsExpanded.toVisibility()
                    binding.currentControlsLayout.visibility = state.currentControlsExpanded.toVisibility()
                    binding.vesselSectionButton.text =
                        sectionTitle(getString(R.string.vessel_controls), state.vesselControlsExpanded)
                    binding.windSectionButton.text =
                        sectionTitle(getString(R.string.wind_controls), state.windControlsExpanded)
                    binding.waterSectionButton.text =
                        sectionTitle(getString(R.string.water_controls), state.waterControlsExpanded)
                    binding.currentSectionButton.text =
                        sectionTitle(getString(R.string.current_controls), state.currentControlsExpanded)
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

private fun bindRangeSlider(slider: RangeSlider, min: Double, max: Double) {
    slider.values = listOf(min.toFloat(), max.toFloat())
}

private fun syncRangeSlider(slider: RangeSlider, min: Double, max: Double) {
    val target = listOf(min.toFloat(), max.toFloat())
    if (slider.values != target) {
        slider.values = target
    }
}

private fun rangeListener(onRange: (Float, Float) -> Unit): RangeSlider.OnChangeListener {
    return RangeSlider.OnChangeListener { slider, _, fromUser ->
        if (fromUser) {
            val values = slider.values
            onRange(values[0], values[1])
        }
    }
}

private fun TextInputLayout.renderError(errorMessage: String?) {
    error = errorMessage
    isErrorEnabled = !errorMessage.isNullOrEmpty()
}

private fun Boolean.toVisibility(): Int = if (this) View.VISIBLE else View.GONE

private fun sectionTitle(title: String, expanded: Boolean): String {
    val marker = if (expanded) "v" else ">"
    return "$marker $title"
}
