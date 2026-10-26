package com.prabhat.photocraft

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.prabhat.photocraft.edit.Adjustment
import com.prabhat.photocraft.edit.AdjustmentGroup
import com.prabhat.photocraft.edit.Adjustments
import com.prabhat.photocraft.edit.CropGeometry
import com.prabhat.photocraft.edit.CropRatio
import com.prabhat.photocraft.edit.EditState
import com.prabhat.photocraft.edit.EditStateCodec
import com.prabhat.photocraft.edit.EditingState
import com.prabhat.photocraft.edit.Filter
import com.prabhat.photocraft.edit.FilterSettings
import com.prabhat.photocraft.edit.M5StateCodec
import com.prabhat.photocraft.export.DestinationAttestation
import com.prabhat.photocraft.export.DestinationCreation
import com.prabhat.photocraft.export.ExportDecision
import com.prabhat.photocraft.export.ExportGate
import com.prabhat.photocraft.export.ExportNaming
import com.prabhat.photocraft.export.ExportPicker
import com.prabhat.photocraft.export.ExportPlanner
import com.prabhat.photocraft.export.ExportQuality
import com.prabhat.photocraft.export.ExportRequest
import com.prabhat.photocraft.export.ExportSize
import com.prabhat.photocraft.export.JpegExporter
import com.prabhat.photocraft.image.PhotoLoader
import com.prabhat.photocraft.image.PreviewRenderer
import com.prabhat.photocraft.preset.Preset
import com.prabhat.photocraft.preset.PresetName
import com.prabhat.photocraft.preset.PresetStore
import com.prabhat.photocraft.ui.AdjustmentRowView
import com.prabhat.photocraft.ui.AdaptivePreviewLayout
import com.prabhat.photocraft.ui.CropOverlayView
import com.prabhat.photocraft.ui.CropPreviewView
import com.prabhat.photocraft.util.PhotoRestore
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private enum class EditorCategory {
        LIGHT,
        COLOR,
        EFFECTS,
        CROP
    }

    private lateinit var appRoot: FrameLayout
    private lateinit var mainColumn: View
    private lateinit var root: ScrollView
    private lateinit var emptyState: View
    private lateinit var editorPanel: View
    private lateinit var topTitle: TextView
    private lateinit var openPhotoButton: Button
    private lateinit var changePhotoButton: ImageButton
    private lateinit var previewCard: AdaptivePreviewLayout
    private lateinit var previewImage: CropPreviewView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var compareButton: ImageButton
    private lateinit var loadOverlay: View
    private lateinit var previewMeta: TextView
    private lateinit var editedBadge: TextView
    private lateinit var renderIndicator: ProgressBar
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var overflowButton: ImageButton
    private lateinit var editorToolbar: View
    private lateinit var categoryGroup: RadioGroup
    private lateinit var lightPanel: View
    private lateinit var colorPanel: View
    private lateinit var filterPanel: View
    private lateinit var cropPanel: View
    private lateinit var qualitySpinner: Spinner
    private lateinit var sizeSpinner: Spinner
    private lateinit var outputSummary: TextView
    private lateinit var filterSpinner: Spinner
    private lateinit var filterSummary: TextView
    private lateinit var filterIntensityLabel: TextView
    private lateinit var filterIntensitySeekBar: SeekBar
    private lateinit var presetNameInput: EditText
    private lateinit var presetSpinner: Spinner
    private lateinit var savePresetButton: Button
    private lateinit var applyPresetButton: Button
    private lateinit var deletePresetButton: Button
    private lateinit var presetStatus: TextView
    private lateinit var presetPanel: View
    private lateinit var historyPanel: View
    private lateinit var exportPanel: View
    private lateinit var actionSheetOverlay: View
    private lateinit var actionSheetTitle: TextView
    private lateinit var actionSheetCloseButton: ImageButton
    private lateinit var actionSheetScroll: ScrollView
    private lateinit var historyList: LinearLayout
    private lateinit var cropRatioSpinner: Spinner
    private lateinit var cropSummary: TextView
    private lateinit var cropWidthLabel: TextView
    private lateinit var cropHeightLabel: TextView
    private lateinit var cropWidthSeekBar: SeekBar
    private lateinit var cropHeightSeekBar: SeekBar
    private lateinit var cropHorizontalSeekBar: SeekBar
    private lateinit var cropVerticalSeekBar: SeekBar
    private lateinit var exportButton: Button
    private lateinit var exportProgress: ProgressBar
    private lateinit var exportStatus: TextView
    private lateinit var exportStatusRow: View
    private lateinit var exportCancelButton: Button
    private lateinit var lightControls: LinearLayout
    private lateinit var colorControls: LinearLayout
    private lateinit var errorPanel: View
    private lateinit var errorText: TextView
    private lateinit var errorRetryButton: Button

    private lateinit var loader: PhotoLoader
    private lateinit var renderer: PreviewRenderer
    private lateinit var exporter: JpegExporter
    private lateinit var presetStore: PresetStore
    private var backCallback: OnBackInvokedCallback? = null

    private val exportGate = ExportGate()
    private val rowsByAdjustment = LinkedHashMap<Adjustment, AdjustmentRowView>()
    private var editingState = EditingState()
    private var presets: List<Preset> = emptyList()
    private var suppressFilterEvents = false
    private var lastHistoryRevision = -1
    private var sourceBitmap: Bitmap? = null
    private var adjustedBitmap: Bitmap? = null
    private var currentDisplayName: String? = null
    private var selectedUri: Uri? = null
    private var selectedUriPersisted = false
    private var pendingUri: Uri? = null
    private var pendingUriPersisted = false
    private var pendingResetEdits = true
    private var pendingDestinationTitle: String? = null
    private var exportPickerStartedAtMs = 0L
    private var pendingExportRequest: ExportRequest? = null
    private var loadingPhoto = false
    private var compareHeld = false
    private var selectedCategory = EditorCategory.LIGHT
    private var selectedQuality = ExportQuality.HIGH
    private var selectedSize = ExportSize.ORIGINAL
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var suppressCropEvents = false
    private var suppressCategoryEvents = false
    private var errorAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loader = PhotoLoader(contentResolver)
        renderer = PreviewRenderer()
        exporter = JpegExporter(contentResolver)
        presetStore = PresetStore(this)
        restoreEditingState(savedInstanceState)
        restoreExportOptions(savedInstanceState)
        restoreEditorCategory(savedInstanceState)
        presets = presetStore.load()
        buildAdjustmentRows()
        setupSpinners()
        setupCropControls()
        setupFilterControls()
        setupPresetControls()
        setupCropGesture()
        wireControls()
        applyEdgeToEdge()
        registerBackHandler()
        applyFilterControlsFromState()
        refreshHistory()
        syncUiState()

        val restoredSelected = savedInstanceState?.let { state ->
            state.getString(STATE_SELECTED_URI)?.let { uri ->
                PhotoRestore.RestoredPhoto(
                    uri = uri,
                    persisted = state.getBoolean(
                        STATE_SELECTED_URI_PERSISTED,
                        false
                    ),
                    resetEdits = false
                )
            }
        }
        val restoredPending = savedInstanceState?.let { state ->
            state.getString(STATE_PENDING_URI)?.let { uri ->
                PhotoRestore.RestoredPhoto(
                    uri = uri,
                    persisted = state.getBoolean(
                        STATE_PENDING_URI_PERSISTED,
                        false
                    ),
                    resetEdits = state.getBoolean(
                        STATE_PENDING_RESET_EDITS,
                        true
                    )
                )
            }
        }
        val restorePlan = PhotoRestore.plan(restoredSelected, restoredPending)
        restorePlan.selected?.let { photo ->
            selectedUri = Uri.parse(photo.uri)
            selectedUriPersisted = photo.persisted
        }
        restorePlan.toLoad?.let { photo ->
            loadPhoto(
                uri = Uri.parse(photo.uri),
                persisted = photo.persisted,
                resetEdits = photo.resetEdits
            )
        }
    }

    private fun bindViews() {
        appRoot = findViewById(R.id.appRoot)
        mainColumn = findViewById(R.id.mainColumn)
        root = findViewById(R.id.root)
        emptyState = findViewById(R.id.emptyState)
        editorPanel = findViewById(R.id.editorPanel)
        topTitle = findViewById(R.id.topTitle)
        openPhotoButton = findViewById(R.id.openPhotoButton)
        changePhotoButton = findViewById(R.id.changePhotoButton)
        previewCard = findViewById(R.id.previewCard)
        previewImage = findViewById(R.id.previewImage)
        cropOverlay = findViewById(R.id.cropOverlay)
        compareButton = findViewById(R.id.compareButton)
        loadOverlay = findViewById(R.id.loadOverlay)
        previewMeta = findViewById(R.id.previewMeta)
        editedBadge = findViewById(R.id.editedBadge)
        renderIndicator = findViewById(R.id.renderIndicator)
        undoButton = findViewById(R.id.undoButton)
        redoButton = findViewById(R.id.redoButton)
        overflowButton = findViewById(R.id.overflowButton)
        editorToolbar = findViewById(R.id.editorToolbar)
        categoryGroup = findViewById(R.id.categoryGroup)
        lightPanel = findViewById(R.id.lightPanel)
        colorPanel = findViewById(R.id.colorPanel)
        filterPanel = findViewById(R.id.filterPanel)
        cropPanel = findViewById(R.id.cropPanel)
        qualitySpinner = findViewById(R.id.qualitySpinner)
        sizeSpinner = findViewById(R.id.sizeSpinner)
        outputSummary = findViewById(R.id.outputSummary)
        filterSpinner = findViewById(R.id.filterSpinner)
        filterSummary = findViewById(R.id.filterSummary)
        filterIntensityLabel = findViewById(R.id.filterIntensityLabel)
        filterIntensitySeekBar = findViewById(R.id.filterIntensitySeekBar)
        presetNameInput = findViewById(R.id.presetNameInput)
        presetSpinner = findViewById(R.id.presetSpinner)
        savePresetButton = findViewById(R.id.savePresetButton)
        applyPresetButton = findViewById(R.id.applyPresetButton)
        deletePresetButton = findViewById(R.id.deletePresetButton)
        presetStatus = findViewById(R.id.presetStatus)
        presetPanel = findViewById(R.id.presetPanel)
        historyPanel = findViewById(R.id.historyPanel)
        exportPanel = findViewById(R.id.exportPanel)
        actionSheetOverlay = findViewById(R.id.actionSheetOverlay)
        actionSheetTitle = findViewById(R.id.actionSheetTitle)
        actionSheetCloseButton = findViewById(R.id.actionSheetCloseButton)
        actionSheetScroll = findViewById(R.id.actionSheetScroll)
        historyList = findViewById(R.id.historyList)
        cropRatioSpinner = findViewById(R.id.cropRatioSpinner)
        cropSummary = findViewById(R.id.cropSummary)
        cropWidthLabel = findViewById(R.id.cropWidthLabel)
        cropHeightLabel = findViewById(R.id.cropHeightLabel)
        cropWidthSeekBar = findViewById(R.id.cropWidthSeekBar)
        cropHeightSeekBar = findViewById(R.id.cropHeightSeekBar)
        cropHorizontalSeekBar = findViewById(R.id.cropHorizontalSeekBar)
        cropVerticalSeekBar = findViewById(R.id.cropVerticalSeekBar)
        exportButton = findViewById(R.id.exportButton)
        exportProgress = findViewById(R.id.exportProgress)
        exportStatus = findViewById(R.id.exportStatus)
        exportStatusRow = findViewById(R.id.exportStatusRow)
        exportCancelButton = findViewById(R.id.exportCancelButton)
        lightControls = findViewById(R.id.lightControls)
        colorControls = findViewById(R.id.colorControls)
        errorPanel = findViewById(R.id.errorPanel)
        errorText = findViewById(R.id.errorText)
        errorRetryButton = findViewById(R.id.errorRetryButton)
    }

    private fun buildAdjustmentRows() {
        val rowListener = object : AdjustmentRowView.Listener {
            override fun onAdjustmentStarted(row: AdjustmentRowView) {
                editingState.beginGesture()
                syncUiState()
            }

            override fun onAdjustmentChanged(row: AdjustmentRowView, value: Int) {
                val adjustment = row.adjustment ?: return
                if (
                    editingState.update(
                        adjustment,
                        value,
                        getString(labelFor(adjustment))
                    )
                ) {
                    clearExportStatus()
                    requestPreviewRender()
                    syncUiState()
                }
            }

            override fun onAdjustmentStopped(row: AdjustmentRowView) {
                editingState.endGesture()
                syncUiState()
            }
        }

        for (adjustment in Adjustment.entries) {
            val row = AdjustmentRowView(this)
            row.bind(
                adjustment = adjustment,
                label = getString(labelFor(adjustment)),
                value = editingState.adjustments.value(adjustment),
                listener = rowListener
            )
            rowsByAdjustment[adjustment] = row
            val parent = if (adjustment.group == AdjustmentGroup.LIGHT) {
                lightControls
            } else {
                colorControls
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(2)
            }
            parent.addView(row, params)
        }
    }

    private fun setupSpinners() {
        val qualityAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ExportQuality.entries.map { getString(qualityLabel(it)) }
        )
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        qualitySpinner.adapter = qualityAdapter
        qualitySpinner.setSelection(selectedQuality.ordinal)
        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val quality = ExportQuality.entries.getOrNull(position) ?: return
                if (quality == selectedQuality) {
                    return
                }
                selectedQuality = quality
                clearExportStatus()
                syncUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val sizeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ExportSize.entries.map { getString(sizeLabel(it)) }
        )
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sizeSpinner.adapter = sizeAdapter
        sizeSpinner.setSelection(selectedSize.ordinal)
        sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val size = ExportSize.entries.getOrNull(position) ?: return
                if (size == selectedSize) {
                    return
                }
                selectedSize = size
                clearExportStatus()
                updateOutputSummary()
                syncUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val ratioAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            CropRatio.entries.map { getString(ratioLabel(it)) }
        )
        ratioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        cropRatioSpinner.adapter = ratioAdapter
        cropRatioSpinner.setSelection(editingState.cropRatio.ordinal)
        cropRatioSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressCropEvents) {
                    return
                }
                val ratio = CropRatio.entries.getOrNull(position) ?: return
                if (ratio == editingState.cropRatio || !canEditPhoto()) {
                    return
                }
                val width = currentSourceWidth()
                val height = currentSourceHeight()
                if (width <= 0 || height <= 0) {
                    return
                }
                val nextCrop = if (ratio == CropRatio.FREE) {
                    editingState.crop
                } else {
                    CropGeometry.centered(ratio, width, height)
                }
                if (
                    editingState.updateCrop(
                        nextCrop,
                        ratio,
                        getString(R.string.history_crop)
                    )
                ) {
                    clearExportStatus()
                    applyCropControlsFromState()
                    updateOutputSummary()
                    syncUiState()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupCropControls() {
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (!fromUser || suppressCropEvents || seekBar == null || !canEditPhoto()) {
                    return
                }
                updateCropFromControls(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (!canEditPhoto()) {
                    return
                }
                editingState.beginGesture()
                clearExportStatus()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                editingState.endGesture()
                applyCropControlsFromState()
                syncUiState()
            }
        }

        cropWidthSeekBar.setOnSeekBarChangeListener(seekListener)
        cropHeightSeekBar.setOnSeekBarChangeListener(seekListener)
        cropHorizontalSeekBar.setOnSeekBarChangeListener(seekListener)
        cropVerticalSeekBar.setOnSeekBarChangeListener(seekListener)
        applyCropControlsFromState()
    }

    private fun setupFilterControls() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            Filter.entries.map { getString(filterLabel(it)) }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter
        filterSpinner.setSelection(editingState.filter.filter.ordinal)
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressFilterEvents || !canEditPhoto()) {
                    return
                }
                val filter = Filter.entries.getOrNull(position) ?: return
                if (filter == editingState.filter.filter) {
                    return
                }
                if (
                    editingState.updateFilter(
                        FilterSettings.selecting(
                            filter,
                            editingState.filter.intensity
                        ),
                        getString(R.string.history_filter)
                    )
                ) {
                    afterFilterChange()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        filterIntensitySeekBar.max = FilterSettings.MAX_INTENSITY
        filterIntensitySeekBar.progress = editingState.filter.intensity
        filterIntensitySeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser || !canEditPhoto() || suppressFilterEvents) {
                        return
                    }
                    if (editingState.filter.filter == Filter.NATURAL) {
                        return
                    }
                    if (
                        editingState.updateFilter(
                            editingState.filter.filter,
                            progress,
                            getString(R.string.history_filter)
                        )
                    ) {
                        afterFilterChange()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    if (!canEditPhoto()) {
                        return
                    }
                    editingState.beginGesture()
                    clearExportStatus()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    editingState.endGesture()
                    applyFilterControlsFromState()
                    refreshHistory()
                    syncUiState()
                }
            }
        )
        updateFilterFeedback()
    }

    private fun setupPresetControls() {
        presetSpinner.adapter = presetAdapter(presets)
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                syncUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        savePresetButton.setOnClickListener { saveCurrentPreset() }
        applyPresetButton.setOnClickListener { applySelectedPreset() }
        deletePresetButton.setOnClickListener { confirmDeleteSelectedPreset() }
        if (presets.isNotEmpty()) {
            presetSpinner.setSelection(0)
        }
    }

    private fun setupCropGesture() {
        cropOverlay.setCropRatio(editingState.cropRatio)
        cropOverlay.setListener(
            object : CropOverlayView.Listener {
                override fun onCropStarted() {
                    if (!canEditPhoto()) {
                        return
                    }
                    editingState.beginGesture()
                    clearExportStatus()
                }

                override fun onCropChanged(crop: com.prabhat.photocraft.edit.CropSelection) {
                    if (!canEditPhoto()) {
                        return
                    }
                    if (
                        editingState.updateCrop(
                            crop,
                            editingState.cropRatio,
                            getString(R.string.history_crop)
                        )
                    ) {
                        applyCropControlsFromState()
                        updateOutputSummary()
                        syncUiState()
                    }
                }

                override fun onCropStopped() {
                    editingState.endGesture()
                    applyCropControlsFromState()
                    updateOutputSummary()
                    refreshHistory()
                    syncUiState()
                }

                override fun onCropCancelled() {
                    editingState.cancelGesture()
                    applyCropControlsFromState()
                    updateOutputSummary()
                    refreshHistory()
                    syncUiState()
                }
            }
        )
    }

    private fun presetAdapter(items: List<Preset>): ArrayAdapter<String> {
        val labels = if (items.isEmpty()) {
            listOf(getString(R.string.preset_none))
        } else {
            items.map { it.name }
        }
        return ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun reloadPresets(selectName: String? = null) {
        presets = presetStore.load()
        presetSpinner.adapter = presetAdapter(presets)
        if (presets.isNotEmpty()) {
            val index = selectName?.let { name ->
                presets.indexOfFirst { it.name == name }
            } ?: 0
            presetSpinner.setSelection(index.coerceIn(0, presets.lastIndex))
        }
        syncUiState()
    }

    private fun selectedPreset(): Preset? =
        presets.getOrNull(presetSpinner.selectedItemPosition)

    private fun saveCurrentPreset() {
        if (!canEditPhoto()) {
            return
        }
        val name = PresetName.normalize(presetNameInput.text.toString())
        if (name == null) {
            setPresetStatus(getString(R.string.preset_status_invalid), isError = true)
            return
        }
        when (presetStore.save(Preset(name, editingState.state))) {
            PresetStore.SaveResult.SAVED -> {
                presetNameInput.setText("")
                reloadPresets(name)
                setPresetStatus(getString(R.string.preset_status_saved, name), isError = false)
            }

            PresetStore.SaveResult.DUPLICATE -> {
                setPresetStatus(getString(R.string.preset_status_duplicate), isError = true)
            }

            PresetStore.SaveResult.STORAGE_FAILED -> {
                setPresetStatus(getString(R.string.preset_status_storage), isError = true)
            }
        }
    }

    private fun applySelectedPreset() {
        if (!canEditPhoto()) {
            return
        }
        val preset = selectedPreset() ?: return
        val previousState = editingState.state
        val label = getString(R.string.history_preset, preset.name)
        if (editingState.applyPreset(preset.state, label)) {
            afterHistoryChange(previousState)
            setPresetStatus(
                getString(R.string.preset_status_applied, preset.name),
                isError = false
            )
        }
    }

    private fun confirmDeleteSelectedPreset() {
        val preset = selectedPreset() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.preset_delete_title)
            .setMessage(getString(R.string.preset_delete_message, preset.name))
            .setNegativeButton(R.string.preset_delete_cancel, null)
            .setPositiveButton(R.string.preset_delete_confirm) { _, _ ->
                deletePreset(preset)
            }
            .show()
    }

    private fun deletePreset(preset: Preset) {
        if (presetStore.delete(preset.name)) {
            reloadPresets()
            setPresetStatus(
                getString(R.string.preset_status_deleted, preset.name),
                isError = false
            )
        } else {
            setPresetStatus(getString(R.string.preset_status_delete_failed), isError = true)
        }
    }

    private fun setPresetStatus(message: String, isError: Boolean) {
        presetStatus.text = message
        presetStatus.setTextColor(
            getColor(if (isError) R.color.error else R.color.text_secondary)
        )
    }

    private fun applyFilterControlsFromState() {
        suppressFilterEvents = true
        try {
            val settings = editingState.filter
            filterSpinner.setSelection(settings.filter.ordinal)
            filterIntensitySeekBar.progress = settings.intensity
            updateFilterFeedback()
        } finally {
            suppressFilterEvents = false
        }
    }

    private fun updateFilterFeedback() {
        val settings = editingState.filter
        filterSummary.text = getString(
            R.string.filter_summary,
            getString(filterLabel(settings.filter)),
            settings.intensity
        )
        filterIntensitySeekBar.contentDescription = getString(
            R.string.filter_summary,
            getString(filterLabel(settings.filter)),
            settings.intensity
        )
        filterIntensitySeekBar.isEnabled =
            canEditPhoto() && settings.filter != Filter.NATURAL
    }

    private fun refreshHistory() {
        if (lastHistoryRevision == editingState.revision && historyList.childCount > 0) {
            return
        }
        lastHistoryRevision = editingState.revision
        historyList.removeAllViews()
        val points = editingState.history(EditingState.DEFAULT_HISTORY_VISIBLE)
        for (point in points) {
            val label = historyLabel(point.label)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val labelView = TextView(this).apply {
                text = if (point.isCurrent) {
                    getString(R.string.history_current, label)
                } else {
                    label
                }
                setTextColor(
                    getColor(
                        if (point.isCurrent) R.color.text_primary else R.color.text_secondary
                    )
                )
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            row.addView(labelView)
            if (!point.isCurrent) {
                val restore = Button(this).apply {
                    text = getString(R.string.history_restore)
                    textSize = 11f
                    minHeight = 0
                    minimumHeight = dp(34)
                    minWidth = 0
                    minimumWidth = 0
                    setBackgroundResource(R.drawable.bg_button_secondary)
                    setTextColor(getColor(R.color.action_text))
                    isAllCaps = false
                    setOnClickListener {
                        if (!canEditPhoto()) {
                            return@setOnClickListener
                        }
                        val previousState = editingState.state
                        if (editingState.restoreTo(point.stepsBack)) {
                            afterHistoryChange(previousState)
                        }
                    }
                }
                row.addView(
                    restore,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(34)
                    )
                )
            }
            historyList.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun historyLabel(label: String): String =
        label.ifBlank { getString(R.string.history_original) }

    private fun wireControls() {
        openPhotoButton.setOnClickListener { openPhotoPicker() }
        changePhotoButton.setOnClickListener { openPhotoPicker() }

        undoButton.setOnClickListener {
            val previousState = editingState.state
            if (editingState.undo()) {
                afterHistoryChange(previousState)
            }
        }

        redoButton.setOnClickListener {
            val previousState = editingState.state
            if (editingState.redo()) {
                afterHistoryChange(previousState)
            }
        }

        overflowButton.setOnClickListener { showOverflowMenu() }
        actionSheetCloseButton.setOnClickListener {
            if (!exportGate.isExporting) {
                hideActionSheet()
            }
        }

        categoryGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressCategoryEvents) {
                return@setOnCheckedChangeListener
            }
            val category = when (checkedId) {
                R.id.lightCategory -> EditorCategory.LIGHT
                R.id.colorCategory -> EditorCategory.COLOR
                R.id.effectsCategory -> EditorCategory.EFFECTS
                R.id.cropCategory -> EditorCategory.CROP
                else -> return@setOnCheckedChangeListener
            }
            if (!canEditPhoto()) {
                applyCategoryVisibility()
                return@setOnCheckedChangeListener
            }
            if (category == selectedCategory) {
                return@setOnCheckedChangeListener
            }
            endCompare()
            selectedCategory = category
            applyCategoryVisibility()
            applyDisplayedBitmap()
            syncUiState()
        }
        setupComparisonControl()

        exportButton.setOnClickListener { startExport() }
        exportCancelButton.setOnClickListener {
            if (!exportGate.isExporting) {
                return@setOnClickListener
            }
            exportCancelButton.isEnabled = false
            exportStatus.text = getString(R.string.export_cancelling)
            exporter.cancel()
        }

        errorRetryButton.setOnClickListener {
            val action = errorAction
            hideError()
            action?.invoke()
        }
    }

    private fun showOverflowMenu() {
        if (!canEditPhoto()) {
            return
        }
        val popup = PopupMenu(this, overflowButton)
        popup.menu.add(Menu.NONE, MENU_PRESETS, 0, R.string.preset_title)
        popup.menu.add(Menu.NONE, MENU_HISTORY, 1, R.string.history_title)
        popup.menu.add(Menu.NONE, MENU_EXPORT, 2, R.string.export_title)
        popup.menu.add(Menu.NONE, MENU_RESET, 3, R.string.reset)
        popup.menu.add(Menu.NONE, MENU_CHANGE_PHOTO, 4, R.string.change_photo)
        popup.menu.findItem(MENU_RESET)?.isEnabled = editingState.isModified
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_PRESETS -> showActionSheet(R.string.preset_title, presetPanel)
                MENU_HISTORY -> showActionSheet(R.string.history_title, historyPanel)
                MENU_EXPORT -> showActionSheet(R.string.export_title, exportPanel)
                MENU_RESET -> {
                    val previousState = editingState.state
                    if (editingState.reset(getString(R.string.reset))) {
                        afterHistoryChange(previousState)
                    }
                }

                MENU_CHANGE_PHOTO -> openPhotoPicker()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun showActionSheet(titleRes: Int, panel: View) {
        if (sourceBitmap == null) {
            return
        }
        endCompare()
        presetPanel.visibility =
            if (panel === presetPanel) View.VISIBLE else View.GONE
        historyPanel.visibility =
            if (panel === historyPanel) View.VISIBLE else View.GONE
        exportPanel.visibility =
            if (panel === exportPanel) View.VISIBLE else View.GONE
        actionSheetTitle.setText(titleRes)
        if (panel === historyPanel) {
            refreshHistory()
        }
        if (panel === exportPanel) {
            updateOutputSummary()
        }
        actionSheetOverlay.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        actionSheetOverlay.visibility = View.VISIBLE
        mainColumn.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        actionSheetOverlay.requestFocus()
        actionSheetScroll.post { actionSheetScroll.scrollTo(0, 0) }
    }

    private fun hideActionSheet() {
        if (::actionSheetOverlay.isInitialized) {
            actionSheetOverlay.visibility = View.GONE
            actionSheetOverlay.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (::mainColumn.isInitialized) {
            mainColumn.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    private fun setupComparisonControl() {
        compareButton.setOnClickListener { }
        compareButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (compareHeld || !canEditPhoto() || sourceBitmap == null) {
                        return@setOnTouchListener false
                    }
                    view.isPressed = true
                    beginCompare()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!compareHeld) {
                        view.isPressed = false
                        return@setOnTouchListener false
                    }
                    view.isPressed = false
                    endCompare()
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    endCompare()
                    true
                }

                else -> false
            }
        }
        compareButton.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                endCompare()
            }
        }
    }

    private fun beginCompare() {
        if (compareHeld || !canEditPhoto() || sourceBitmap == null) {
            return
        }
        compareHeld = true
        applyDisplayedBitmap()
    }

    private fun endCompare() {
        if (!compareHeld) {
            return
        }
        compareHeld = false
        if (::compareButton.isInitialized) {
            compareButton.isPressed = false
        }
        if (::previewImage.isInitialized) {
            applyDisplayedBitmap()
        }
    }

    private fun applyCategoryVisibility() {
        suppressCategoryEvents = true
        categoryGroup.check(
            when (selectedCategory) {
                EditorCategory.LIGHT -> R.id.lightCategory
                EditorCategory.COLOR -> R.id.colorCategory
                EditorCategory.EFFECTS -> R.id.effectsCategory
                EditorCategory.CROP -> R.id.cropCategory
            }
        )
        suppressCategoryEvents = false

        val hasPhoto = sourceBitmap != null
        editorToolbar.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        lightPanel.visibility =
            if (selectedCategory == EditorCategory.LIGHT) View.VISIBLE else View.GONE
        colorPanel.visibility =
            if (selectedCategory == EditorCategory.COLOR) View.VISIBLE else View.GONE
        filterPanel.visibility =
            if (selectedCategory == EditorCategory.EFFECTS) View.VISIBLE else View.GONE
        cropPanel.visibility =
            if (selectedCategory == EditorCategory.CROP) View.VISIBLE else View.GONE
        cropOverlay.visibility = if (
            hasPhoto &&
            selectedCategory == EditorCategory.CROP &&
            !compareHeld
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun afterHistoryChange(previousState: EditState) {
        clearExportStatus()
        updateAllRows()
        applyCropControlsFromState()
        applyFilterControlsFromState()
        applyDisplayedBitmap()
        updateOutputSummary()
        if (
            editingState.adjustments != previousState.adjustments ||
            editingState.filter != previousState.filter
        ) {
            requestPreviewRender()
        }
        refreshHistory()
        syncUiState()
    }

    private fun afterFilterChange() {
        clearExportStatus()
        updateFilterFeedback()
        updateOutputSummary()
        requestPreviewRender()
        refreshHistory()
        syncUiState()
    }

    private fun openPhotoPicker() {
        if (loadingPhoto || exportGate.isExporting) {
            return
        }
        endCompare()
        hideActionSheet()
        hideError()

        try {
            startActivityForResult(PhotoPicker.pickerIntent(), REQUEST_PICK_IMAGE)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivityForResult(PhotoPicker.documentIntent(), REQUEST_PICK_IMAGE)
            } catch (_: ActivityNotFoundException) {
                showError(
                    message = getString(R.string.error_no_picker),
                    actionLabel = R.string.retry,
                    action = ::openPhotoPicker
                )
            }
        }
    }

    private fun startExport() {
        if (!canEditPhoto() || selectedUri == null) {
            return
        }
        endCompare()
        val request = buildExportRequest() ?: return
        val suggestedName = ExportNaming.suggestedFileName(currentDisplayName)

        pendingExportRequest = request
        pendingDestinationTitle = null
        exportPickerStartedAtMs = 0L
        hideError()
        clearExportStatus()
        if (!exportGate.tryStart()) {
            pendingExportRequest = null
            return
        }

        pendingDestinationTitle = suggestedName
        exportPickerStartedAtMs = System.currentTimeMillis()
        try {
            startActivityForResult(
                ExportPicker.createDocumentIntent(suggestedName),
                REQUEST_CREATE_JPEG
            )
        } catch (_: ActivityNotFoundException) {
            pendingExportRequest = null
            pendingDestinationTitle = null
            exportPickerStartedAtMs = 0L
            exportGate.finish()
            showError(
                message = getString(R.string.error_no_export_picker),
                actionLabel = R.string.retry_export,
                action = ::startExport
            )
        }
        syncUiState()
    }

    @Deprecated("Uses the framework activity-result API because this project has no AndroidX dependency.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CREATE_JPEG) {
            handleExportResult(resultCode, data)
            return
        }

        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK) {
            return
        }

        val uri = data?.data
        if (uri == null) {
            showError(
                message = getString(R.string.error_pick_failed),
                actionLabel = R.string.retry,
                action = ::openPhotoPicker
            )
            return
        }

        releaseSupersededPendingGrant(uri)
        val alreadyPersisted =
            (selectedUri == uri && selectedUriPersisted) ||
                (pendingUri == uri && pendingUriPersisted)
        val persisted = alreadyPersisted || takePersistableRead(uri, data.flags)
        val resetEdits = selectedUri != uri
        pendingResetEdits = resetEdits
        loadPhoto(
            uri = uri,
            persisted = persisted,
            resetEdits = resetEdits
        )
    }

    private fun handleExportResult(resultCode: Int, data: Intent?) {
        val requestedTitle = pendingDestinationTitle
        val pickerStartedAtMs = exportPickerStartedAtMs
        pendingDestinationTitle = null
        exportPickerStartedAtMs = 0L

        if (!exportGate.isExporting && !exportGate.tryStart()) {
            pendingExportRequest = null
            syncUiState()
            return
        }

        val source = selectedUri
        val destination = data?.data
        val decision = ExportDecision.decide(
            resultOk = resultCode == RESULT_OK,
            destinationAvailable = destination != null,
            sourceMatchesDestination = source != null && source == destination
        )

        when (decision) {
            ExportDecision.CANCELLED -> {
                pendingExportRequest = null
                exportGate.finish()
                syncUiState()
            }

            ExportDecision.MISSING_DESTINATION -> {
                pendingExportRequest = null
                exportGate.finish()
                showError(
                    message = getString(R.string.error_export_no_destination),
                    actionLabel = R.string.retry_export,
                    action = ::startExport
                )
                syncUiState()
            }

            ExportDecision.SOURCE_CONFLICT -> {
                pendingExportRequest = null
                exportGate.finish()
                showError(
                    message = getString(R.string.error_export_same_file),
                    actionLabel = R.string.retry_export,
                    action = ::startExport
                )
                syncUiState()
            }

            ExportDecision.START -> {
                val request = pendingExportRequest ?: buildExportRequest()
                pendingExportRequest = null
                if (source == null || destination == null || request == null) {
                    if (destination != null) {
                        exporter.discardDestination(source, destination, null)
                    }
                    exportGate.finish()
                    showError(
                        message = getString(
                            if (source == null) {
                                R.string.error_export_source_loading
                            } else {
                                R.string.error_export_invalid
                            }
                        ),
                        actionLabel = R.string.retry_pick,
                        action = ::openPhotoPicker
                    )
                    syncUiState()
                    return
                }
                val attestation = establishDestinationAttestation(
                    destination = destination,
                    requestedTitle = requestedTitle,
                    pickerStartedAtMs = pickerStartedAtMs
                )
                beginExport(source, destination, request, attestation)
            }
        }
    }

    private fun establishDestinationAttestation(
        destination: Uri,
        requestedTitle: String?,
        pickerStartedAtMs: Long
    ): DestinationAttestation {
        if (
            requestedTitle == null ||
            pickerStartedAtMs <= 0L ||
            destination.scheme != "content"
        ) {
            return DestinationAttestation.NONE
        }

        return try {
            if (!DocumentsContract.isDocumentUri(this, destination)) {
                return DestinationAttestation.NONE
            }
            var displayName: String? = null
            var preWriteSize: Long? = null
            var lastModifiedMs: Long? = null
            contentResolver.query(
                destination,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val modifiedIndex = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        preWriteSize = cursor.getLong(sizeIndex)
                    }
                    if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                        lastModifiedMs = cursor.getLong(modifiedIndex)
                    }
                }
            }
            val documentId = try {
                DocumentsContract.getDocumentId(destination)
            } catch (_: Exception) {
                null
            }
            DestinationCreation.establish(
                fromCreateAction = true,
                requestedTitle = requestedTitle,
                observedTitle = displayName,
                documentId = documentId,
                lastModifiedMs = lastModifiedMs,
                pickerStartedAtMs = pickerStartedAtMs,
                nowMs = System.currentTimeMillis(),
                preWriteSize = preWriteSize,
                isContentDocument = true
            )
        } catch (_: Exception) {
            DestinationAttestation.NONE
        }
    }

    private fun beginExport(
        source: Uri,
        destination: Uri,
        request: ExportRequest,
        attestation: DestinationAttestation
    ) {
        hideError()
        clearExportStatus()
        exportProgress.isIndeterminate = false
        exportProgress.max = 100
        exportProgress.progress = 0
        syncUiState()

        exporter.export(
            sourceUri = source,
            destinationUri = destination,
            request = request,
            attestation = attestation,
            onProgress = { stage, percent ->
                if (!isDestroyed && !isFinishing) {
                    exportStatus.text = getString(
                        R.string.export_progress,
                        getString(stageLabel(stage)),
                        percent
                    )
                    exportProgress.progress = percent
                    exportStatusRow.visibility = View.VISIBLE
                }
            },
            callback = { result ->
                if (isDestroyed || isFinishing) {
                    return@export
                }

                exportGate.finish()
                when (result) {
                    is JpegExporter.Result.Success -> {
                        showExportSuccess(result.width, result.height)
                    }

                    is JpegExporter.Result.Failure -> {
                        showExportFailure(result.reason)
                    }
                }
                syncUiState()
            }
        )
    }

    private fun updateExportProgress(stage: JpegExporter.Stage, percent: Int) {
        exportStatus.text = getString(
            R.string.export_progress,
            getString(stageLabel(stage)),
            percent
        )
        exportProgress.progress = percent
        exportStatusRow.visibility = View.VISIBLE
    }

    private fun buildExportRequest(): ExportRequest? {
        val width = currentSourceWidth()
        val height = currentSourceHeight()
        if (width <= 0 || height <= 0) {
            return null
        }
        return ExportRequest(
            adjustments = editingState.adjustments,
            crop = editingState.crop,
            quality = selectedQuality,
            outputSize = selectedSize,
            filter = editingState.filter,
            sourceWidth = width,
            sourceHeight = height
        )
    }

    private fun showExportSuccess(width: Int, height: Int) {
        exportStatus.text = getString(R.string.export_success, width, height)
        exportStatus.setTextColor(getColor(R.color.accent))
    }

    private fun showExportFailure(reason: JpegExporter.FailureReason) {
        if (reason == JpegExporter.FailureReason.CANCELLED) {
            exportStatus.text = getString(R.string.export_cancelled)
            exportStatus.setTextColor(getColor(R.color.text_secondary))
            exportStatusRow.visibility = View.VISIBLE
            return
        }
        val messageRes = when (reason) {
            JpegExporter.FailureReason.INVALID_URI -> R.string.error_export_invalid
            JpegExporter.FailureReason.ACCESS_DENIED -> R.string.error_export_access
            JpegExporter.FailureReason.DECODE_FAILED -> R.string.error_export_decode
            JpegExporter.FailureReason.UNSUPPORTED_OR_CORRUPT ->
                R.string.error_export_unsupported
            JpegExporter.FailureReason.TOO_LARGE -> R.string.error_export_too_large
            JpegExporter.FailureReason.SOURCE_CONFLICT -> R.string.error_export_same_file
            JpegExporter.FailureReason.WRITE_FAILED -> R.string.error_export_write
            JpegExporter.FailureReason.CANCELLED -> R.string.export_cancelled
        }
        val chooseAnother = when (reason) {
            JpegExporter.FailureReason.INVALID_URI,
            JpegExporter.FailureReason.ACCESS_DENIED,
            JpegExporter.FailureReason.UNSUPPORTED_OR_CORRUPT -> true

            else -> false
        }
        showError(
            message = getString(messageRes),
            actionLabel = if (chooseAnother) R.string.retry_pick else R.string.retry_export,
            action = if (chooseAnother) ::openPhotoPicker else ::startExport
        )
    }

    private fun clearExportStatus() {
        exportStatus.text = ""
        exportStatus.setTextColor(getColor(R.color.text_secondary))
        exportStatusRow.visibility = View.GONE
        exportProgress.isIndeterminate = false
        exportProgress.progress = 0
    }

    private fun loadPhoto(uri: Uri, persisted: Boolean, resetEdits: Boolean) {
        if (loadingPhoto || exportGate.isExporting) {
            return
        }

        clearExportStatus()
        hideError()
        pendingUri = uri
        pendingUriPersisted = persisted
        pendingResetEdits = resetEdits
        loadingPhoto = true
        loadOverlay.visibility = View.VISIBLE
        renderIndicator.visibility = View.GONE
        emptyState.visibility = View.GONE
        editorPanel.visibility = View.VISIBLE
        if (sourceBitmap == null) {
            previewMeta.text = getString(R.string.loading_photo)
        }
        syncUiState()

        loader.load(uri, EDIT_PREVIEW_MAX_EDGE_PX) { result ->
            if (isDestroyed || isFinishing) {
                return@load
            }

            loadingPhoto = false
            loadOverlay.visibility = View.GONE

            when (result) {
                is PhotoLoader.Result.Success -> showLoadedPhoto(uri, persisted, resetEdits, result)
                is PhotoLoader.Result.Failure -> showLoadFailure(uri, persisted, resetEdits, result.reason)
            }
        }
    }

    private fun showLoadedPhoto(
        uri: Uri,
        persisted: Boolean,
        resetEdits: Boolean,
        result: PhotoLoader.Result.Success
    ) {
        val previousUri = selectedUri
        val wasPersistedForSameUri = previousUri == uri && selectedUriPersisted

        if (previousUri != null && previousUri != uri && selectedUriPersisted) {
            releasePersistedRead(previousUri)
        }

        selectedUri = uri
        selectedUriPersisted = persisted || wasPersistedForSameUri
        pendingUri = null
        pendingUriPersisted = false
        pendingResetEdits = true

        sourceBitmap = result.bitmap
        adjustedBitmap = null
        currentDisplayName = result.displayName
        sourceWidth = result.sourceWidth.takeIf { it > 0 } ?: result.bitmap.width
        sourceHeight = result.sourceHeight.takeIf { it > 0 } ?: result.bitmap.height
        if (resetEdits) {
            editingState = EditingState()
            compareHeld = false
            selectedCategory = EditorCategory.LIGHT
        }

        cropOverlay.setImageAspect(sourceWidth, sourceHeight)
        applyCropControlsFromState()
        applyFilterControlsFromState()
        updatePreviewMetadata()
        updateAllRows()
        updateOutputSummary()
        refreshHistory()
        emptyState.visibility = View.GONE
        editorPanel.visibility = View.VISIBLE
        hideError()
        applyDisplayedBitmap()
        requestPreviewRender()
        syncUiState()

        if (resetEdits) {
            root.smoothScrollTo(0, 0)
        }
    }

    private fun showLoadFailure(
        uri: Uri,
        persisted: Boolean,
        resetEdits: Boolean,
        reason: PhotoLoader.FailureReason
    ) {
        val fallback = selectedUri?.takeIf { sourceBitmap == null && it != uri }
        if (persisted && uri != selectedUri) {
            releasePersistedRead(uri)
        }
        pendingUri = null
        pendingUriPersisted = false
        pendingResetEdits = true

        if (fallback != null) {
            loadPhoto(
                uri = fallback,
                persisted = selectedUriPersisted,
                resetEdits = false
            )
            return
        }

        if (sourceBitmap == null) {
            emptyState.visibility = View.VISIBLE
            editorPanel.visibility = View.GONE
        }

        val messageRes = when (reason) {
            PhotoLoader.FailureReason.INVALID_URI -> R.string.error_invalid_uri
            PhotoLoader.FailureReason.ACCESS_DENIED -> R.string.error_access
            PhotoLoader.FailureReason.DECODE_FAILED -> R.string.error_decode
            PhotoLoader.FailureReason.UNSUPPORTED_OR_CORRUPT -> R.string.error_unsupported
            PhotoLoader.FailureReason.TOO_LARGE -> R.string.error_too_large
        }

        val action = if (reason == PhotoLoader.FailureReason.DECODE_FAILED) {
            { loadPhoto(uri, persisted, resetEdits) }
        } else {
            ::openPhotoPicker
        }
        val actionLabel = if (reason == PhotoLoader.FailureReason.DECODE_FAILED) {
            R.string.retry
        } else {
            R.string.retry_pick
        }

        showError(getString(messageRes), actionLabel, action)
        syncUiState()
    }

    private fun requestPreviewRender() {
        val source = sourceBitmap ?: return
        hideError()
        renderIndicator.visibility = View.VISIBLE

        renderer.request(source, editingState.adjustments, editingState.filter, editingState.state.effects) { result ->
            if (isDestroyed || isFinishing || sourceBitmap !== source) {
                return@request
            }

            renderIndicator.visibility = View.GONE
            when (result) {
                is PreviewRenderer.Result.Success -> {
                    adjustedBitmap = result.bitmap.takeIf { it !== source }
                    applyDisplayedBitmap()
                    updatePreviewDescription()
                }

                is PreviewRenderer.Result.Failure -> {
                    val message = when (result.reason) {
                        PreviewRenderer.FailureReason.OUT_OF_MEMORY -> R.string.error_render_memory
                        PreviewRenderer.FailureReason.RENDER_FAILED -> R.string.error_render_failed
                    }
                    showError(
                        message = getString(message),
                        actionLabel = R.string.retry,
                        action = ::requestPreviewRender
                    )
                }
            }
        }
    }

    private fun applyDisplayedBitmap() {
        val bitmap = if (compareHeld) {
            sourceBitmap
        } else {
            adjustedBitmap ?: sourceBitmap
        } ?: return
        val showFullFrame = compareHeld || selectedCategory == EditorCategory.CROP
        previewImage.setBitmap(bitmap)
        previewImage.setCrop(
            if (showFullFrame) {
                com.prabhat.photocraft.edit.CropSelection.FULL
            } else {
                editingState.crop
            }
        )
        updatePreviewCardAspect(showFullFrame)
        updatePreviewDescription()
        applyCategoryVisibility()
    }

    private fun updatePreviewCardAspect(showFullFrame: Boolean) {
        val width = currentSourceWidth()
        val height = currentSourceHeight()
        if (width <= 0 || height <= 0) {
            return
        }
        if (showFullFrame) {
            previewCard.setImageAspect(width, height)
            return
        }
        val crop = try {
            ExportPlanner.pixelCrop(width, height, editingState.crop)
        } catch (_: IllegalArgumentException) {
            previewCard.setImageAspect(width, height)
            return
        }
        previewCard.setImageAspect(crop.width, crop.height)
    }

    private fun updatePreviewMetadata() {
        val bitmap = sourceBitmap ?: return
        val displayName = currentDisplayName?.takeIf { it.isNotBlank() }
        val name = displayName ?: getString(R.string.preview_untitled)
        val width = currentSourceWidth().takeIf { it > 0 } ?: bitmap.width
        val height = currentSourceHeight().takeIf { it > 0 } ?: bitmap.height
        previewMeta.text = getString(
            R.string.preview_meta,
            name,
            width,
            height
        )
        refreshTopBar(hasPhoto = true)
        updatePreviewDescription()
    }

    private fun updatePreviewDescription() {
        val bitmap = sourceBitmap ?: return
        val displayName = currentDisplayName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.preview_untitled)
        previewImage.contentDescription = if (compareHeld) {
            getString(R.string.preview_content_description_original, displayName)
        } else {
            getString(R.string.preview_content_description_named, displayName)
        }
        previewImage.importantForAccessibility = if (bitmap.width > 0) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun updateAllRows() {
        for ((adjustment, row) in rowsByAdjustment) {
            row.setValue(editingState.adjustments.value(adjustment))
        }
    }

    private fun applyCropControlsFromState() {
        suppressCropEvents = true
        try {
            val crop = editingState.crop
            val ratio = editingState.cropRatio
            cropRatioSpinner.setSelection(ratio.ordinal)
            cropOverlay.setCropRatio(ratio)

            val width = currentSourceWidth()
            val height = currentSourceHeight()
            val maximum = if (ratio.isFixed && width > 0 && height > 0) {
                CropGeometry.centered(ratio, width, height)
            } else {
                null
            }
            val widthProgress = if (maximum != null && maximum.width > 0f) {
                CropGeometry.sizeProgress(crop.width / maximum.width)
            } else {
                CropGeometry.sizeProgress(crop.width)
            }
            cropWidthSeekBar.progress = widthProgress
            cropHeightSeekBar.progress = CropGeometry.sizeProgress(crop.height)
            cropHorizontalSeekBar.progress = CropGeometry.positionProgress(
                CropGeometry.centerX(crop),
                crop.width
            )
            cropVerticalSeekBar.progress = CropGeometry.positionProgress(
                CropGeometry.centerY(crop),
                crop.height
            )

            cropWidthLabel.setText(
                if (ratio.isFixed) R.string.crop_size_label else R.string.crop_width_label
            )
            val fixed = ratio.isFixed
            cropHeightLabel.visibility = if (fixed) View.GONE else View.VISIBLE
            cropHeightSeekBar.visibility = if (fixed) View.GONE else View.VISIBLE
            updateCropFeedback()
        } finally {
            suppressCropEvents = false
        }
    }

    private fun updateCropFromControls(changed: SeekBar) {
        val width = currentSourceWidth()
        val height = currentSourceHeight()
        if (width <= 0 || height <= 0) {
            return
        }
        val ratio = editingState.cropRatio
        val widthFraction = CropGeometry.fractionFromProgress(cropWidthSeekBar.progress)
        val heightFraction = CropGeometry.fractionFromProgress(cropHeightSeekBar.progress)
        val maximum = if (ratio.isFixed) {
            CropGeometry.centered(ratio, width, height)
        } else {
            null
        }
        val spanX = maximum?.let { it.width * widthFraction } ?: widthFraction
        val spanY = maximum?.let { it.height * widthFraction } ?: heightFraction
        val currentCrop = editingState.crop
        val centerX = if (changed === cropHorizontalSeekBar) {
            CropGeometry.centerFromProgress(cropHorizontalSeekBar.progress, spanX)
        } else {
            CropGeometry.centerX(currentCrop)
        }
        val centerY = if (changed === cropVerticalSeekBar) {
            CropGeometry.centerFromProgress(cropVerticalSeekBar.progress, spanY)
        } else {
            CropGeometry.centerY(currentCrop)
        }
        val crop = CropGeometry.fromControls(
            ratio = ratio,
            sourceWidth = width,
            sourceHeight = height,
            widthFraction = widthFraction,
            heightFraction = heightFraction,
            centerX = centerX,
            centerY = centerY
        )
        if (
            editingState.updateCrop(
                crop,
                ratio,
                getString(R.string.history_crop)
            )
        ) {
            updateCropFeedback()
            updateOutputSummary()
            clearExportStatus()
            syncUiState()
        }
    }

    private fun updateCropFeedback() {
        val crop = editingState.crop
        val ratio = editingState.cropRatio
        cropOverlay.setCrop(crop)
        cropOverlay.setCropRatio(ratio)
        cropSummary.text = getString(
            R.string.crop_summary,
            (crop.width * 100f).roundToInt(),
            (crop.height * 100f).roundToInt(),
            getString(ratioLabel(ratio))
        )
        cropWidthSeekBar.contentDescription = getString(
            R.string.crop_control_description,
            cropWidthLabel.text,
            progressPercent(cropWidthSeekBar.progress)
        )
        cropHeightSeekBar.contentDescription = getString(
            R.string.crop_control_description,
            getString(R.string.crop_height_label),
            progressPercent(cropHeightSeekBar.progress)
        )
        cropHorizontalSeekBar.contentDescription = getString(
            R.string.crop_control_description,
            getString(R.string.crop_horizontal_label),
            progressPercent(cropHorizontalSeekBar.progress)
        )
        cropVerticalSeekBar.contentDescription = getString(
            R.string.crop_control_description,
            getString(R.string.crop_vertical_label),
            progressPercent(cropVerticalSeekBar.progress)
        )
    }

    private fun updateOutputSummary() {
        val width = currentSourceWidth()
        val height = currentSourceHeight()
        if (width <= 0 || height <= 0) {
            outputSummary.text = ""
            return
        }
        val plan = try {
            ExportPlanner.plan(
                sourceWidth = width,
                sourceHeight = height,
                crop = editingState.crop,
                size = selectedSize
            )
        } catch (_: IllegalArgumentException) {
            outputSummary.text = ""
            return
        }
        outputSummary.text = getString(
            R.string.output_summary,
            plan.output.width,
            plan.output.height,
            getString(sizeLabel(selectedSize))
        )
    }

    private fun canEditPhoto(): Boolean =
        sourceBitmap != null && !loadingPhoto && !exportGate.isExporting

    private fun currentSourceWidth(): Int =
        sourceWidth.takeIf { it > 0 } ?: sourceBitmap?.width ?: 0

    private fun currentSourceHeight(): Int =
        sourceHeight.takeIf { it > 0 } ?: sourceBitmap?.height ?: 0

    private fun progressPercent(progress: Int): Int =
        (progress * 100f / CropGeometry.PROGRESS_MAX).roundToInt()

    private fun refreshTopBar(hasPhoto: Boolean) {
        val visibility = if (hasPhoto) View.VISIBLE else View.GONE
        changePhotoButton.visibility = visibility
        undoButton.visibility = visibility
        redoButton.visibility = visibility
        overflowButton.visibility = visibility
        topTitle.text = if (hasPhoto) {
            currentDisplayName?.takeIf { it.isNotBlank() }
                ?: getString(R.string.app_name)
        } else {
            getString(R.string.app_name)
        }
    }

    private fun syncUiState() {
        val hasPhoto = sourceBitmap != null
        val exporting = exportGate.isExporting
        val busy = loadingPhoto || exporting
        val canEdit = hasPhoto && !busy
        if (compareHeld && !canEdit) {
            endCompare()
        }
        if (!hasPhoto) {
            hideActionSheet()
        }

        undoButton.isEnabled = canEdit && editingState.canUndo
        redoButton.isEnabled = canEdit && editingState.canRedo
        overflowButton.isEnabled = canEdit
        refreshTopBar(hasPhoto)
        for (index in 0 until categoryGroup.childCount) {
            categoryGroup.getChildAt(index).isEnabled = canEdit
        }
        compareButton.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        compareButton.isEnabled = canEdit
        changePhotoButton.isEnabled = !busy
        openPhotoButton.isEnabled = !busy
        errorRetryButton.isEnabled = !busy
        exportButton.isEnabled = canEdit
        exportButton.setText(
            if (exporting) R.string.exporting else R.string.export_jpeg
        )
        qualitySpinner.isEnabled = canEdit
        sizeSpinner.isEnabled = canEdit
        filterSpinner.isEnabled = canEdit
        filterIntensitySeekBar.isEnabled =
            canEdit && editingState.filter.filter != Filter.NATURAL
        presetNameInput.isEnabled = canEdit
        presetSpinner.isEnabled = canEdit && presets.isNotEmpty()
        savePresetButton.isEnabled = canEdit
        applyPresetButton.isEnabled = canEdit && selectedPreset() != null
        deletePresetButton.isEnabled = canEdit && selectedPreset() != null
        cropRatioSpinner.isEnabled = canEdit
        cropOverlay.isEnabled = canEdit && selectedCategory == EditorCategory.CROP
        cropWidthSeekBar.isEnabled = canEdit
        cropHeightSeekBar.isEnabled = canEdit
        cropHorizontalSeekBar.isEnabled = canEdit
        cropVerticalSeekBar.isEnabled = canEdit
        exportProgress.visibility = if (exporting) View.VISIBLE else View.GONE
        exportCancelButton.visibility = if (exporting) View.VISIBLE else View.GONE
        exportCancelButton.isEnabled = exporting
        actionSheetCloseButton.isEnabled = !exporting
        exportStatusRow.visibility = if (exporting || exportStatus.text.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        for (row in rowsByAdjustment.values) {
            row.setAdjustmentEnabled(canEdit)
        }
        refreshHistory()

        editedBadge.visibility = if (hasPhoto && editingState.isModified) {
            View.VISIBLE
        } else {
            View.GONE
        }
        applyCategoryVisibility()
        if (!hasPhoto) {
            outputSummary.text = ""
        }
    }

    private fun showError(message: String, actionLabel: Int, action: (() -> Unit)?) {
        hideActionSheet()
        val fullMessage = if (sourceBitmap != null) {
            message + "\n" + getString(R.string.error_current_photo_kept)
        } else {
            message
        }
        errorText.text = fullMessage
        errorRetryButton.setText(actionLabel)
        errorRetryButton.visibility = if (action != null) View.VISIBLE else View.GONE
        errorAction = action
        errorPanel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorAction = null
        errorPanel.visibility = View.GONE
    }

    private fun takePersistableRead(uri: Uri, flags: Int): Boolean {
        if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return false
        }

        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun releasePersistedRead(uri: Uri) {
        try {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
    }

    private fun releaseSupersededPendingGrant(nextUri: Uri) {
        val pending = pendingUri ?: return
        if (pending == nextUri) {
            return
        }
        if (pendingUriPersisted && !(pending == selectedUri && selectedUriPersisted)) {
            releasePersistedRead(pending)
            pendingUriPersisted = false
        }
    }

    private fun restoreEditingState(savedState: Bundle?) {
        if (savedState == null) {
            return
        }

        val restoredM5 = M5StateCodec.decode(savedState.getIntArray(STATE_M5_EDIT_STATE))
        if (restoredM5 != null) {
            val undo = M5StateCodec.decodeStack(savedState.getIntArray(STATE_M5_UNDO))
            val redo = M5StateCodec.decodeStack(savedState.getIntArray(STATE_M5_REDO))
            val undoLabels = savedState.getStringArray(STATE_M5_UNDO_LABELS)
                ?.toList()
                .orEmpty()
            val redoLabels = savedState.getStringArray(STATE_M5_REDO_LABELS)
                ?.toList()
                .orEmpty()
            editingState = EditingState.restored(
                EditingState.Snapshot(
                    state = restoredM5,
                    undo = undo,
                    redo = redo,
                    undoLabels = undoLabels,
                    redoLabels = redoLabels,
                    currentLabel = savedState.getString(STATE_M5_CURRENT_LABEL).orEmpty()
                )
            )
            return
        }

        val restored = EditStateCodec.decode(savedState.getIntArray(STATE_EDIT_STATE))
        if (restored != null) {
            val undo = EditStateCodec.decodeStack(savedState.getIntArray(STATE_UNDO))
            val redo = EditStateCodec.decodeStack(savedState.getIntArray(STATE_REDO))
            editingState = EditingState.restored(
                EditingState.Snapshot(restored, undo, redo)
            )
            return
        }

        val legacyAdjustments = EditStateCodec.decodeAdjustments(
            savedState.getIntArray(LEGACY_STATE_ADJUSTMENTS)
        ) ?: return
        val legacyUndo = decodeLegacyStack(savedState.getIntArray(LEGACY_STATE_UNDO))
        val legacyRedo = decodeLegacyStack(savedState.getIntArray(LEGACY_STATE_REDO))
        editingState = EditingState.restored(
            EditingState.Snapshot(
                state = EditState(adjustments = legacyAdjustments),
                undo = legacyUndo,
                redo = legacyRedo
            )
        )
    }

    private fun restoreExportOptions(savedState: Bundle?) {
        if (savedState == null) {
            return
        }
        selectedQuality = ExportQuality.entries.getOrNull(
            savedState.getInt(STATE_QUALITY, selectedQuality.ordinal)
        ) ?: ExportQuality.HIGH
        selectedSize = ExportSize.entries.getOrNull(
            savedState.getInt(STATE_SIZE, selectedSize.ordinal)
        ) ?: ExportSize.ORIGINAL
    }

    private fun restoreEditorCategory(savedState: Bundle?) {
        if (savedState == null) {
            return
        }
        selectedCategory = EditorCategory.entries.getOrNull(
            savedState.getInt(STATE_CATEGORY, selectedCategory.ordinal)
        ) ?: EditorCategory.LIGHT
    }

    private fun decodeLegacyStack(values: IntArray?): List<EditState> {
        if (values == null || values.isEmpty() || values.size % LEGACY_ADJUSTMENT_COUNT != 0) {
            return emptyList()
        }
        val states = ArrayList<EditState>(values.size / LEGACY_ADJUSTMENT_COUNT)
        var offset = 0
        while (offset < values.size) {
            val slice = values.copyOfRange(offset, offset + LEGACY_ADJUSTMENT_COUNT)
            val adjustments = EditStateCodec.decodeAdjustments(slice) ?: return emptyList()
            states.add(EditState(adjustments = adjustments))
            offset += LEGACY_ADJUSTMENT_COUNT
        }
        return states
    }

    private fun labelFor(adjustment: Adjustment): Int = when (adjustment) {
        Adjustment.EXPOSURE -> R.string.adjust_exposure
        Adjustment.BRIGHTNESS -> R.string.adjust_brightness
        Adjustment.CONTRAST -> R.string.adjust_contrast
        Adjustment.HIGHLIGHTS -> R.string.adjust_highlights
        Adjustment.SHADOWS -> R.string.adjust_shadows
        Adjustment.WHITES -> R.string.adjust_whites
        Adjustment.BLACKS -> R.string.adjust_blacks
        Adjustment.TEMPERATURE -> R.string.adjust_temperature
        Adjustment.TINT -> R.string.adjust_tint
        Adjustment.SATURATION -> R.string.adjust_saturation
        Adjustment.VIBRANCE -> R.string.adjust_vibrance
    }

    private fun filterLabel(filter: Filter): Int = when (filter) {
        Filter.NATURAL -> R.string.filter_natural
        Filter.WARM -> R.string.filter_warm
        Filter.COOL -> R.string.filter_cool
        Filter.BLACK_WHITE -> R.string.filter_black_white
        Filter.VINTAGE -> R.string.filter_vintage
        Filter.CINEMATIC -> R.string.filter_cinematic
    }

    private fun stageLabel(stage: JpegExporter.Stage): Int = when (stage) {
        JpegExporter.Stage.PREPARING -> R.string.export_stage_preparing
        JpegExporter.Stage.DECODING -> R.string.export_stage_decoding
        JpegExporter.Stage.PROCESSING -> R.string.export_stage_processing
        JpegExporter.Stage.WRITING -> R.string.export_stage_writing
    }

    private fun qualityLabel(quality: ExportQuality): Int = when (quality) {
        ExportQuality.HIGH -> R.string.quality_high
        ExportQuality.STANDARD -> R.string.quality_standard
        ExportQuality.SMALLER_FILE -> R.string.quality_smaller_file
    }

    private fun sizeLabel(size: ExportSize): Int = when (size) {
        ExportSize.ORIGINAL -> R.string.size_original
        ExportSize.PERCENT_75 -> R.string.size_75
        ExportSize.PERCENT_50 -> R.string.size_50
        ExportSize.PERCENT_25 -> R.string.size_25
        ExportSize.LONG_EDGE_2048 -> R.string.size_long_2048
        ExportSize.LONG_EDGE_1080 -> R.string.size_long_1080
    }

    private fun ratioLabel(ratio: CropRatio): Int = when (ratio) {
        CropRatio.FREE -> R.string.crop_ratio_free
        CropRatio.SQUARE -> R.string.crop_ratio_square
        CropRatio.FOUR_FIVE -> R.string.crop_ratio_four_five
        CropRatio.NINE_SIXTEEN -> R.string.crop_ratio_nine_sixteen
        CropRatio.SIXTEEN_NINE -> R.string.crop_ratio_sixteen_nine
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val snapshot = editingState.snapshot()
        outState.putIntArray(STATE_M5_EDIT_STATE, M5StateCodec.encode(snapshot.state))
        outState.putIntArray(STATE_M5_UNDO, M5StateCodec.encodeStack(snapshot.undo))
        outState.putIntArray(STATE_M5_REDO, M5StateCodec.encodeStack(snapshot.redo))
        outState.putStringArray(
            STATE_M5_UNDO_LABELS,
            snapshot.undoLabels.toTypedArray()
        )
        outState.putStringArray(
            STATE_M5_REDO_LABELS,
            snapshot.redoLabels.toTypedArray()
        )
        outState.putString(STATE_M5_CURRENT_LABEL, snapshot.currentLabel)
        outState.putIntArray(STATE_EDIT_STATE, EditStateCodec.encode(snapshot.state))
        outState.putIntArray(STATE_UNDO, EditStateCodec.encodeStack(snapshot.undo))
        outState.putIntArray(STATE_REDO, EditStateCodec.encodeStack(snapshot.redo))
        outState.putInt(STATE_QUALITY, selectedQuality.ordinal)
        outState.putInt(STATE_SIZE, selectedSize.ordinal)
        outState.putInt(STATE_CATEGORY, selectedCategory.ordinal)
        selectedUri?.let { uri ->
            outState.putString(STATE_SELECTED_URI, uri.toString())
            outState.putBoolean(STATE_SELECTED_URI_PERSISTED, selectedUriPersisted)
        }
        if (loadingPhoto) {
            PhotoRestore.pendingForSave(
                loading = true,
                pendingUri = pendingUri?.toString(),
                pendingPersisted = pendingUriPersisted,
                resetEdits = pendingResetEdits
            )?.let { photo ->
                outState.putString(STATE_PENDING_URI, photo.uri)
                outState.putBoolean(STATE_PENDING_URI_PERSISTED, photo.persisted)
                outState.putBoolean(STATE_PENDING_RESET_EDITS, photo.resetEdits)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            appRoot.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
                view.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    maxOf(bars.bottom, imeBottom)
                )
                insets
            }
        }
    }

    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val callback = OnBackInvokedCallback {
            if (!consumeActionSheetBack()) {
                finish()
            }
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backCallback = callback
    }

    private fun consumeActionSheetBack(): Boolean {
        if (
            ::actionSheetOverlay.isInitialized &&
            actionSheetOverlay.visibility == View.VISIBLE
        ) {
            if (!exportGate.isExporting) {
                hideActionSheet()
            }
            return true
        }
        return false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            if (::compareButton.isInitialized) {
                compareButton.isPressed = false
            }
            endCompare()
        }
    }

    @Deprecated("Legacy back dispatch for API levels below 33")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (consumeActionSheetBack()) {
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        if (::compareButton.isInitialized) {
            compareButton.isPressed = false
        }
        endCompare()
        super.onPause()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        }
        backCallback = null
        pendingExportRequest = null
        exporter.close()
        loader.close()
        renderer.close()
        if (isFinishing) {
            if (selectedUriPersisted) {
                selectedUri?.let(::releasePersistedRead)
            }
            val pending = pendingUri
            if (
                pendingUriPersisted &&
                pending != null &&
                !(pending == selectedUri && selectedUriPersisted)
            ) {
                releasePersistedRead(pending)
            }
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQUEST_PICK_IMAGE = 1001
        const val REQUEST_CREATE_JPEG = 1002
        const val MENU_PRESETS = 2001
        const val MENU_HISTORY = 2002
        const val MENU_EXPORT = 2003
        const val MENU_RESET = 2004
        const val MENU_CHANGE_PHOTO = 2005
        const val EDIT_PREVIEW_MAX_EDGE_PX = 1080
        const val LEGACY_ADJUSTMENT_COUNT = 11
        const val STATE_M5_EDIT_STATE = "m5_edit_state"
        const val STATE_M5_UNDO = "m5_undo"
        const val STATE_M5_REDO = "m5_redo"
        const val STATE_M5_UNDO_LABELS = "m5_undo_labels"
        const val STATE_M5_REDO_LABELS = "m5_redo_labels"
        const val STATE_M5_CURRENT_LABEL = "m5_current_label"
        const val STATE_EDIT_STATE = "m4_edit_state"
        const val STATE_UNDO = "m4_undo"
        const val STATE_REDO = "m4_redo"
        const val STATE_QUALITY = "m4_quality"
        const val STATE_SIZE = "m4_size"
        const val STATE_CATEGORY = "m5_2_category"
        const val STATE_SELECTED_URI = "m2_selected_uri"
        const val STATE_SELECTED_URI_PERSISTED = "m2_selected_uri_persisted"
        const val STATE_PENDING_URI = "m2_pending_uri"
        const val STATE_PENDING_URI_PERSISTED = "m2_pending_uri_persisted"
        const val STATE_PENDING_RESET_EDITS = "m2_pending_reset_edits"
        const val LEGACY_STATE_ADJUSTMENTS = "m2_adjustments"
        const val LEGACY_STATE_UNDO = "m2_undo"
        const val LEGACY_STATE_REDO = "m2_redo"
    }
}
