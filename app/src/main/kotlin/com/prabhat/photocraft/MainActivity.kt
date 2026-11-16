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
import android.view.GestureDetector
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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ClipDrawable
import android.view.Gravity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.prabhat.photocraft.edit.SelfieMaskSegmenter
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
import com.prabhat.photocraft.edit.HslColor
import com.prabhat.photocraft.edit.HslAdjustment
import com.prabhat.photocraft.edit.HslSettings
import com.prabhat.photocraft.edit.ToneGrade
import com.prabhat.photocraft.edit.ColorGradingSettings
import com.prabhat.photocraft.edit.PhotoEffects
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
        MASKING,
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
    private lateinit var maskingPanel: View
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
    private lateinit var gradingControls: LinearLayout
    private lateinit var effectsControls: LinearLayout
    private lateinit var mixEntryButton: View
    private lateinit var mixOverlay: View
    private lateinit var mixSheet: View
    private lateinit var mixTargetButton: ImageButton
    private lateinit var mixDoneButton: View
    private lateinit var mixSlider: SeekBar
    private lateinit var mixSliderLabel: TextView
    private lateinit var mixSliderValue: TextView
    private lateinit var mixPropHue: TextView
    private lateinit var mixPropSaturation: TextView
    private lateinit var mixPropLuminance: TextView
    private val mixSwatchButtons = LinkedHashMap<HslColor, View>()
    private val mixSwatchDots = LinkedHashMap<HslColor, View>()
    private var selectedMixColor: HslColor = HslColor.RED
    private var selectedMixProperty: MixProperty = MixProperty.HUE
    private var mixTargetActive = false
    private var mixOverlayVisible = false
    private var suppressMixSliderEvents = false
    private var mixSliderGestureChanged = false
    private var mixTargetDragChanged = false
    private var mixTargetLastX = 0f
    private var mixTargetLastY = 0f
    private var mixTargetAccum = 0f

    private enum class MixProperty { HUE, SATURATION, LUMINANCE }
    private val advancedSeekBars = LinkedHashMap<String, SeekBar>()
    private val advancedLabels = LinkedHashMap<String, TextView>()
    private val advancedLabelTitles = LinkedHashMap<String, String>()
    private val localMaskAdjustmentSliders = LinkedHashMap<String, LocalMaskSlider>()
    private val selectedMaskAdjustmentSliders = LinkedHashMap<Adjustment, LocalMaskSlider>()

    private data class LocalMaskSlider(
        val type: com.prabhat.photocraft.edit.MaskType,
        val adjustment: Adjustment,
        val seekBar: SeekBar,
        val label: TextView
    )

    // Masking flow
    private lateinit var maskBrushOverlay: View
    private lateinit var maskCreateMenu: View
    private lateinit var maskSelectedTitle: TextView
    private lateinit var maskSelectedAdjustments: LinearLayout
    private lateinit var maskDoneButton: Button
    private lateinit var maskStatusTextView: TextView
    private lateinit var maskAddButton: View
    private lateinit var maskToolAdd: ImageButton
    private lateinit var maskToolSubtract: ImageButton
    private lateinit var maskToolInvert: ImageButton
    private lateinit var maskToolDelete: ImageButton
    private lateinit var maskSizeSeek: SeekBar
    private lateinit var maskFeatherSeek: SeekBar
    private lateinit var maskFlowSeek: SeekBar
    private var maskActiveTool = "Add"
    private var maskNewComponentOnStroke = true
    private lateinit var maskComponentsList: LinearLayout
    private lateinit var maskComponentsHeader: View
    private var maskEditingLayer = false
    private var maskStrokeActive = false
    private var maskLastX = 0f
    private var maskLastY = 0f
    private var maskHasLast = false
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
    private val selfieMaskSegmenter = SelfieMaskSegmenter()
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
        setupMaskingControls()
        setupFilterControls()
        setupAdvancedControls()
        setupMixControls()
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
        maskingPanel = findViewById(R.id.maskingPanel)
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
        maskBrushOverlay = findViewById(R.id.maskBrushOverlay)
        maskCreateMenu = findViewById(R.id.maskCreateMenu)
        maskSelectedTitle = findViewById(R.id.maskSelectedTitle)
        maskSelectedAdjustments = findViewById(R.id.maskSelectedAdjustments)
        maskComponentsList = findViewById(R.id.maskComponentsList)
        maskComponentsHeader = findViewById(R.id.maskComponentsHeader)
        maskDoneButton = findViewById(R.id.maskDoneButton)
        maskStatusTextView = findViewById(R.id.maskStatusText)
        maskAddButton = findViewById(R.id.maskAddButton)
        maskToolAdd = findViewById(R.id.maskToolAdd)
        maskToolSubtract = findViewById(R.id.maskToolSubtract)
        maskToolInvert = findViewById(R.id.maskToolInvert)
        maskToolDelete = findViewById(R.id.maskToolDelete)
        maskSizeSeek = findViewById(R.id.maskSizeSeek)
        maskFeatherSeek = findViewById(R.id.maskFeatherSeek)
        maskFlowSeek = findViewById(R.id.maskFlowSeek)
        lightControls = findViewById(R.id.lightControls)
        colorControls = findViewById(R.id.colorControls)
        gradingControls = findViewById(R.id.gradingControls)
        effectsControls = findViewById(R.id.effectsControls)
        mixEntryButton = findViewById(R.id.mixEntryButton)
        mixOverlay = findViewById(R.id.mixOverlay)
        mixSheet = findViewById(R.id.mixSheet)
        mixTargetButton = findViewById(R.id.mixTargetButton)
        mixDoneButton = findViewById(R.id.mixDoneButton)
        mixSlider = findViewById(R.id.mixSlider)
        mixSliderLabel = findViewById(R.id.mixSliderLabel)
        mixSliderValue = findViewById(R.id.mixSliderValue)
        mixPropHue = findViewById(R.id.mixPropHue)
        mixPropSaturation = findViewById(R.id.mixPropSaturation)
        mixPropLuminance = findViewById(R.id.mixPropLuminance)
        bindMixSwatch(HslColor.RED, R.id.mixSwatchRed, R.id.mixSwatchRedDot)
        bindMixSwatch(HslColor.ORANGE, R.id.mixSwatchOrange, R.id.mixSwatchOrangeDot)
        bindMixSwatch(HslColor.YELLOW, R.id.mixSwatchYellow, R.id.mixSwatchYellowDot)
        bindMixSwatch(HslColor.GREEN, R.id.mixSwatchGreen, R.id.mixSwatchGreenDot)
        bindMixSwatch(HslColor.CYAN, R.id.mixSwatchCyan, R.id.mixSwatchCyanDot)
        bindMixSwatch(HslColor.BLUE, R.id.mixSwatchBlue, R.id.mixSwatchBlueDot)
        bindMixSwatch(HslColor.PURPLE, R.id.mixSwatchPurple, R.id.mixSwatchPurpleDot)
        bindMixSwatch(HslColor.MAGENTA, R.id.mixSwatchMagenta, R.id.mixSwatchMagentaDot)
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
                if (selectedCategory == EditorCategory.MASKING) {
                    val masking = editingState.state.masking
                    val selectedLayer = masking.selected()
                    val currentAdjustments = selectedLayer.adjustments.adjustments
                    val updatedAdjustments = currentAdjustments.with(adjustment, value)
                    val updatedLayer = selectedLayer.copy(
                        adjustments = selectedLayer.adjustments.copy(adjustments = updatedAdjustments)
                    )
                    val updatedMasking = when (masking.selectedMask) {
                        com.prabhat.photocraft.edit.MaskType.SUBJECT -> masking.copy(subject = updatedLayer)
                        com.prabhat.photocraft.edit.MaskType.BACKGROUND -> masking.copy(background = updatedLayer)
                    }
                    if (editingState.updateMasking(updatedMasking, "Local Mask Adjustment")) {
                        clearExportStatus()
                        requestPreviewRender()
                        syncUiState()
                    }
                } else {
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


    private enum class GradeRegion { SHADOWS, MIDTONES, HIGHLIGHTS }

    private fun setupAdvancedControls() {
        gradingControls.removeAllViews()
        effectsControls.removeAllViews()
        advancedSeekBars.clear()
        advancedLabels.clear()
        advancedLabelTitles.clear()

        for (region in GradeRegion.entries) {
            val initial = gradeFor(editingState.state.colorGrading, region)
            val title = region.name.lowercase().replaceFirstChar { it.uppercase() }
            addAdvancedSlider(
                gradingControls, "grade_${region.name}_hue", "$title · Hue",
                -180, 180, initial.hue
            ) { value ->
                val state = editingState.state.colorGrading
                updateColorGrading(withGrade(state, region, gradeFor(state, region).copy(hue = value)))
            }
            addAdvancedSlider(
                gradingControls, "grade_${region.name}_amount", "$title · Amount",
                0, 100, initial.amount
            ) { value ->
                val state = editingState.state.colorGrading
                updateColorGrading(withGrade(state, region, gradeFor(state, region).copy(amount = value)))
            }
            addAdvancedSlider(
                gradingControls, "grade_${region.name}_lum", "$title · Luminance",
                -100, 100, initial.luminance
            ) { value ->
                val state = editingState.state.colorGrading
                updateColorGrading(withGrade(state, region, gradeFor(state, region).copy(luminance = value)))
            }
        }

        addAdvancedSlider(
            gradingControls, "grade_balance", "Balance",
            -100, 100, editingState.state.colorGrading.balance
        ) { value ->
            updateColorGrading(editingState.state.colorGrading.copy(balance = value))
        }
        addAdvancedSlider(
            gradingControls, "grade_blending", "Blending",
            0, 100, editingState.state.colorGrading.blending,
            defaultValue = 50
        ) { value ->
            updateColorGrading(editingState.state.colorGrading.copy(blending = value))
        }

        addAdvancedSlider(
            effectsControls, "effect_texture", "Texture",
            -100, 100, editingState.state.effects.texture
        ) { value ->
            updateEffects(editingState.state.effects.copy(texture = value))
        }
        addAdvancedSlider(
            effectsControls, "effect_sharpness", "Sharpness",
            -100, 100, editingState.state.effects.sharpness
        ) { value ->
            updateEffects(editingState.state.effects.copy(sharpness = value))
        }
        addAdvancedSlider(
            effectsControls, "effect_details", "Details",
            -100, 100, editingState.state.effects.details
        ) { value ->
            updateEffects(editingState.state.effects.copy(details = value))
        }
        addAdvancedSlider(
            effectsControls, "effect_vignette", "Vignette",
            -100, 100, editingState.state.effects.vignette
        ) { value ->
            updateEffects(editingState.state.effects.copy(vignette = value))
        }
        addAdvancedSlider(
            effectsControls, "effect_noise", "Noise Reduction",
            -100, 100, editingState.state.effects.noiseReduction
        ) { value ->
            updateEffects(editingState.state.effects.copy(noiseReduction = value))
        }

        applyAdvancedControlsFromState()
    }

    private fun attachDoubleTapReset(seekBar: SeekBar, onReset: () -> Unit) {
        var suppressTouch = false
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (!seekBar.isEnabled) return false
                suppressTouch = true
                seekBar.post {
                    if (seekBar.isEnabled) onReset()
                }
                return true
            }
        })
        seekBar.setOnTouchListener { _, event ->
            if (suppressTouch) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    suppressTouch = false
                }
                true
            } else {
                detector.onTouchEvent(event)
                if (suppressTouch) {
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun addAdvancedSlider(
        container: LinearLayout,
        key: String,
        title: String,
        minimum: Int,
        maximum: Int,
        initialValue: Int,
        defaultValue: Int = 0,
        onValueChanged: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        val label = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
        }
        val seekBar = SeekBar(this).apply {
            this.max = maximum - minimum
            progress = (initialValue - minimum).coerceIn(0, maximum - minimum)
            contentDescription = title
        }
        advancedSeekBars[key] = seekBar
        advancedLabels[key] = label
        advancedLabelTitles[key] = title
        row.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        row.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        container.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        fun currentValue(): Int = seekBar.progress + minimum
        fun showValue(value: Int) {
            label.text = "$title: $value"
        }
        showValue(initialValue)

        fun resetToDefault() {
            if (!canEditPhoto() || !seekBar.isEnabled) return
            if (currentValue() == defaultValue) return
            editingState.beginGesture()
            clearExportStatus()
            seekBar.progress = (defaultValue - minimum).coerceIn(0, seekBar.max)
            onValueChanged(defaultValue)
            editingState.endGesture()
            refreshHistory()
            syncUiState()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + minimum
                showValue(value)
                if (fromUser && canEditPhoto()) {
                    onValueChanged(value)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                if (canEditPhoto()) {
                    editingState.beginGesture()
                    clearExportStatus()
                }
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                if (canEditPhoto()) {
                    editingState.endGesture()
                    refreshHistory()
                    syncUiState()
                }
            }
        })
        attachDoubleTapReset(seekBar) { resetToDefault() }
    }

    private fun replaceHsl(
        settings: HslSettings,
        color: HslColor,
        adjustment: HslAdjustment
    ): HslSettings = when (color) {
        HslColor.RED -> settings.copy(red = adjustment)
        HslColor.ORANGE -> settings.copy(orange = adjustment)
        HslColor.YELLOW -> settings.copy(yellow = adjustment)
        HslColor.GREEN -> settings.copy(green = adjustment)
        HslColor.CYAN -> settings.copy(cyan = adjustment)
        HslColor.BLUE -> settings.copy(blue = adjustment)
        HslColor.PURPLE -> settings.copy(purple = adjustment)
        HslColor.MAGENTA -> settings.copy(magenta = adjustment)
    }

    private fun gradeFor(settings: ColorGradingSettings, region: GradeRegion): ToneGrade =
        when (region) {
            GradeRegion.SHADOWS -> settings.shadows
            GradeRegion.MIDTONES -> settings.midtones
            GradeRegion.HIGHLIGHTS -> settings.highlights
        }

    private fun withGrade(
        settings: ColorGradingSettings,
        region: GradeRegion,
        grade: ToneGrade
    ): ColorGradingSettings = when (region) {
        GradeRegion.SHADOWS -> settings.copy(shadows = grade)
        GradeRegion.MIDTONES -> settings.copy(midtones = grade)
        GradeRegion.HIGHLIGHTS -> settings.copy(highlights = grade)
    }

    private fun updateColorGrading(settings: ColorGradingSettings) {
        if (editingState.updateColorGrading(settings, "Color Grading / HSL")) {
            clearExportStatus()
            requestPreviewRender()
            syncUiState()
            if (mixOverlayVisible) syncMixUi()
        }
    }

    private fun updateEffects(effects: PhotoEffects) {
        if (editingState.updateEffects(effects, "Photo Effects")) {
            clearExportStatus()
            requestPreviewRender()
            syncUiState()
        }
    }

    private fun applyAdvancedControlsFromState() {
        val grading = editingState.state.colorGrading
        val effects = editingState.state.effects

        fun setValue(key: String, value: Int, minimum: Int) {
            val bar = advancedSeekBars[key] ?: return
            val safe = (value - minimum).coerceIn(0, bar.max)
            if (bar.progress != safe) bar.progress = safe
            advancedLabels[key]?.text = "${advancedLabelTitles[key] ?: key}: $value"
        }

        for (region in GradeRegion.entries) {
            val grade = gradeFor(grading, region)
            setValue("grade_${region.name}_hue", grade.hue, -180)
            setValue("grade_${region.name}_amount", grade.amount, 0)
            setValue("grade_${region.name}_lum", grade.luminance, -100)
        }
        setValue("grade_balance", grading.balance, -100)
        setValue("grade_blending", grading.blending, 0)
        setValue("effect_texture", effects.texture, -100)
        setValue("effect_sharpness", effects.sharpness, -100)
        setValue("effect_details", effects.details, -100)
        setValue("effect_vignette", effects.vignette, -100)
        setValue("effect_noise", effects.noiseReduction, -100)
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
            applyAdvancedControlsFromState()
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



    private fun setupLocalMaskAdjustmentControls(
        container: LinearLayout,
        type: com.prabhat.photocraft.edit.MaskType
    ) {
        val initialMasking = editingState.state.masking
        val initialLayer = if (type == com.prabhat.photocraft.edit.MaskType.SUBJECT) {
            initialMasking.subject
        } else {
            initialMasking.background
        }

        fun addGroupTitle(title: String) {
            container.addView(TextView(this).apply {
                text = title
                textSize = 12f
                letterSpacing = 0.08f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(10), 0, dp(2))
                contentDescription = title
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        fun addAdjustment(adjustment: com.prabhat.photocraft.edit.Adjustment) {
            val initial = initialLayer.adjustments.adjustments.value(adjustment)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val label = TextView(this).apply {
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
            }
            val seek = SeekBar(this).apply {
                max = 200
                progress = initial + 100
                contentDescription = "${type.name} ${adjustment.name}"
            }

            fun showValue(value: Int) {
                label.text = localMaskAdjustmentLabel(adjustment, value)
            }
            showValue(initial)

            fun applyValue(value: Int) {
                val current = editingState.state.masking
                val layer = if (type ==
                    com.prabhat.photocraft.edit.MaskType.SUBJECT
                ) current.subject else current.background

                val updatedLayer = layer.copy(
                    adjustments = layer.adjustments.copy(
                        adjustments = layer.adjustments.adjustments
                            .with(adjustment, value)
                    )
                )
                val updated = if (type ==
                    com.prabhat.photocraft.edit.MaskType.SUBJECT
                ) current.copy(subject = updatedLayer)
                else current.copy(background = updatedLayer)

                if (editingState.updateMasking(
                    updated, "Local ${type.name} ${adjustment.name}"
                )) {
                    clearExportStatus()
                    requestPreviewRender()
                    syncUiState()
                }
            }

            fun resetToDefault() {
                if (!canEditPhoto() || !seek.isEnabled) return
                if (seek.progress - 100 == 0) return
                editingState.beginGesture()
                clearExportStatus()
                seek.progress = 100
                applyValue(0)
                editingState.endGesture()
                refreshHistory()
                syncUiState()
            }

            localMaskAdjustmentSliders["${type.name}_${adjustment.name}"] =
                LocalMaskSlider(type, adjustment, seek, label)

            seek.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    val value = progress - 100
                    showValue(value)
                    if (!fromUser || !canEditPhoto()) return
                    applyValue(value)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {
                    if (canEditPhoto()) editingState.beginGesture()
                }

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    if (canEditPhoto()) editingState.endGesture()
                }
            })
            attachDoubleTapReset(seek) { resetToDefault() }

            row.addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            row.addView(seek, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        addGroupTitle("LIGHT")
        com.prabhat.photocraft.edit.Adjustment.values()
            .filter { it.group ==
                com.prabhat.photocraft.edit.AdjustmentGroup.LIGHT }
            .forEach { addAdjustment(it) }

        addGroupTitle("COLOR")
        com.prabhat.photocraft.edit.Adjustment.values()
            .filter { it.group ==
                com.prabhat.photocraft.edit.AdjustmentGroup.COLOR }
            .forEach { addAdjustment(it) }
    }

    private fun setupMaskingControls() {
        localMaskAdjustmentSliders.clear()
        selectedMaskAdjustmentSliders.clear()
        maskSelectedAdjustments.removeAllViews()
        buildSelectedMaskAdjustmentControls()
        // keep legacy hidden containers empty-safe; selected-mask controls used instead
        setupLocalMaskAdjustmentControls(
            findViewById(R.id.maskSubjectAdjustments),
            com.prabhat.photocraft.edit.MaskType.SUBJECT
        )
        setupLocalMaskAdjustmentControls(
            findViewById(R.id.maskBackgroundAdjustments),
            com.prabhat.photocraft.edit.MaskType.BACKGROUND
        )

        fun beginMaskParamGesture() { if (canEditPhoto()) editingState.beginGesture() }
        fun endMaskParamGesture(label: String) {
            if (!canEditPhoto()) return
            editingState.endGesture()
            refreshHistory(); syncUiState(); refreshMaskUi()
        }

        // size/feather/flow write masking state
        maskSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || !canEditPhoto()) return
                val value = (progress + 5).toFloat()
                editingState.updateMasking(editingState.state.masking.copy(brushSize = value), "Brush Size")
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { beginMaskParamGesture() }
            override fun onStopTrackingTouch(bar: SeekBar?) { endMaskParamGesture("Brush Size") }
        })
        maskFeatherSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || !canEditPhoto()) return
                editingState.updateMasking(editingState.state.masking.copy(brushFeather = progress.toFloat()), "Brush Feather")
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { beginMaskParamGesture() }
            override fun onStopTrackingTouch(bar: SeekBar?) { endMaskParamGesture("Brush Feather") }
        })
        maskFlowSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || !canEditPhoto()) return
                val value = (progress + 1).toFloat()
                editingState.updateMasking(editingState.state.masking.copy(brushFlow = value), "Brush Flow")
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { beginMaskParamGesture() }
            override fun onStopTrackingTouch(bar: SeekBar?) { endMaskParamGesture("Brush Flow") }
        })

        maskAddButton.setOnClickListener {
            if (!canEditPhoto()) return@setOnClickListener
            maskCreateMenu.visibility = if (maskCreateMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.maskSubjectButton).setOnClickListener {
            maskCreateMenu.visibility = View.GONE
            autoSelectMask(com.prabhat.photocraft.edit.MaskType.SUBJECT)
        }
        findViewById<Button>(R.id.maskBackgroundButton).setOnClickListener {
            maskCreateMenu.visibility = View.GONE
            autoSelectMask(com.prabhat.photocraft.edit.MaskType.BACKGROUND)
        }
        findViewById<Button>(R.id.maskBrushButton).setOnClickListener {
            if (!canEditPhoto()) return@setOnClickListener
            maskCreateMenu.visibility = View.GONE
            val updated = editingState.state.masking.addBrushLayer()
            editingState.updateMasking(updated, "Add ${updated.selected().name}")
            maskActiveTool = "Add"
            enterMaskEditMode()
            refreshMaskUi(); syncUiState(); refreshHistory()
        }

        maskToolAdd.setOnClickListener {
            maskActiveTool = "Add"
            maskNewComponentOnStroke = true
            refreshMaskUi()
        }
        maskToolSubtract.setOnClickListener {
            maskActiveTool = "Subtract"
            maskNewComponentOnStroke = true
            refreshMaskUi()
        }
        maskToolInvert.setOnClickListener {
            if (!canEditPhoto()) return@setOnClickListener
            val masking = editingState.state.masking
            val layer = masking.selected()
            val updatedLayer = layer.copy(inverted = !layer.inverted)
            editingState.updateMasking(masking.updateSelected(updatedLayer), "Invert ${layer.name}")
            refreshMaskUi(); syncUiState(); refreshHistory(); requestPreviewRender()
        }
        maskToolDelete.setOnClickListener {
            if (!canEditPhoto()) return@setOnClickListener
            val masking = editingState.state.masking
            val layer = masking.selected()
            val updated = if (layer.selectedComponentIndex in layer.components.indices) {
                editingState.updateMasking(masking.updateSelected(layer.deleteSelectedComponent()), "Delete Component")
                return@setOnClickListener refreshMaskUi().also { syncUiState(); refreshHistory(); requestPreviewRender() }
            } else if (masking.selectedMask == com.prabhat.photocraft.edit.MaskType.BRUSH && layer.components.isEmpty() && layer.selection == null) {
                masking.deleteSelectedBrush()
            } else {
                masking.updateSelected(layer.copy(selection = null, components = emptyList(), selectedComponentIndex = -1, inverted = false))
            }
            editingState.updateMasking(updated, "Delete Mask")
            exitMaskEditMode(keepEdits = true)
            refreshMaskUi(); syncUiState(); refreshHistory(); requestPreviewRender()
        }
        maskDoneButton.setOnClickListener { exitMaskEditMode(keepEdits = true) }

        previewImage.setMaskTouchListener { x, y, action ->
            handleMaskTouch(x, y, action)
        }

        refreshMaskUi()
    }

    private fun buildSelectedMaskAdjustmentControls() {
        selectedMaskAdjustmentSliders.clear()
        maskSelectedAdjustments.removeAllViews()
        fun addGroupTitle(title: String) {
            maskSelectedAdjustments.addView(TextView(this).apply {
                text = title
                textSize = 12f
                letterSpacing = 0.08f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(10), 0, dp(2))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        fun addAdjustment(adjustment: Adjustment) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
            val label = TextView(this).apply { textSize = 13f; setTextColor(getColor(R.color.text_secondary)) }
            val seek = SeekBar(this).apply { max = 200; progress = 100; contentDescription = adjustment.name }
            fun valueOf() = seek.progress - 100
            fun show(v: Int) { label.text = localMaskAdjustmentLabel(adjustment, v) }
            show(0)
            fun applyValue(value: Int, record: Boolean) {
                val masking = editingState.state.masking
                val layer = masking.selected()
                val updatedLayer = layer.copy(
                    adjustments = layer.adjustments.copy(
                        adjustments = layer.adjustments.adjustments.with(adjustment, value)
                    )
                )
                editingState.updateMasking(masking.updateSelected(updatedLayer), "Local ${layer.name} ${adjustment.name}")
                clearExportStatus(); requestPreviewRender(); syncUiState()
            }
            fun reset() {
                if (!canEditPhoto() || !seek.isEnabled) return
                if (valueOf() == 0) return
                editingState.beginGesture(); clearExportStatus()
                seek.progress = 100; applyValue(0, true)
                editingState.endGesture(); refreshHistory(); syncUiState(); refreshMaskUi()
            }
            selectedMaskAdjustmentSliders[adjustment] = LocalMaskSlider(
                com.prabhat.photocraft.edit.MaskType.BRUSH, adjustment, seek, label
            )
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress - 100; show(v)
                    if (!fromUser || !canEditPhoto() || !maskEditingLayer) return
                    applyValue(v, false)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) { if (canEditPhoto()) editingState.beginGesture() }
                override fun onStopTrackingTouch(bar: SeekBar?) { if (canEditPhoto()) { editingState.endGesture(); refreshHistory(); syncUiState(); refreshMaskUi() } }
            })
            attachDoubleTapReset(seek) { reset() }
            row.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(seek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            maskSelectedAdjustments.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        addGroupTitle("LIGHT")
        Adjustment.values().filter { it.group == AdjustmentGroup.LIGHT }.forEach { addAdjustment(it) }
        addGroupTitle("COLOR")
        Adjustment.values().filter { it.group == AdjustmentGroup.COLOR }.forEach { addAdjustment(it) }
    }

    private fun enterMaskEditMode() {
        maskEditingLayer = true
        maskCreateMenu.visibility = View.GONE
        refreshMaskUi()
        syncUiState()
    }

    private fun exitMaskEditMode(keepEdits: Boolean) {
        maskEditingLayer = false
        maskStrokeActive = false
        maskHasLast = false
        maskCreateMenu.visibility = View.GONE
        refreshMaskUi()
        syncUiState()
    }

    private fun refreshMaskUi() {
        if (!::maskBrushOverlay.isInitialized) return
        val hasPhoto = sourceBitmap != null
        val inMask = selectedCategory == EditorCategory.MASKING && hasPhoto
        val editing = inMask && maskEditingLayer && !compareHeld
        maskBrushOverlay.visibility = if (editing) View.VISIBLE else View.GONE
        maskSelectedTitle.visibility = if (editing) View.VISIBLE else View.GONE
        maskSelectedAdjustments.visibility = if (editing) View.VISIBLE else View.GONE
        maskDoneButton.visibility = if (editing) View.VISIBLE else View.GONE
        if (inMask) {
            val masking = editingState.state.masking
            val layer = masking.selected()
            val name = layer.name
            val mode = if (maskActiveTool == "Subtract") "Subtract from" else "Add to"
            maskStatusTextView.text = if (editing) "$name • $mode" else "Tap + to create a mask."
            maskSelectedTitle.text = name.uppercase()
            val addActive = maskActiveTool != "Subtract"
            maskToolAdd.alpha = if (addActive) 1f else 0.55f
            maskToolAdd.setColorFilter(getColor(if (addActive) R.color.accent else R.color.text_primary))
            maskToolSubtract.alpha = if (!addActive) 1f else 0.55f
            maskToolSubtract.setColorFilter(getColor(if (!addActive) R.color.accent else R.color.text_primary))
            val sizeProg = (masking.brushSize.toInt() - 5).coerceIn(0, maskSizeSeek.max)
            if (maskSizeSeek.progress != sizeProg) maskSizeSeek.progress = sizeProg
            val featherProg = masking.brushFeather.toInt().coerceIn(0, maskFeatherSeek.max)
            if (maskFeatherSeek.progress != featherProg) maskFeatherSeek.progress = featherProg
            val flowProg = (masking.brushFlow.toInt() - 1).coerceIn(0, maskFlowSeek.max)
            if (maskFlowSeek.progress != flowProg) maskFlowSeek.progress = flowProg
            previewImage.setMaskSelection(if (!compareHeld) layer.effectiveSelection() else null)
            rebuildMaskComponentsUi(layer)
            if (editing && selectedMaskAdjustmentSliders.isNotEmpty()) {
                for ((adj, control) in selectedMaskAdjustmentSliders) {
                    val v = layer.adjustments.adjustments.value(adj)
                    val p = (v + 100).coerceIn(0, control.seekBar.max)
                    if (control.seekBar.progress != p) control.seekBar.progress = p
                    control.label.text = localMaskAdjustmentLabel(adj, v)
                }
            }
        } else {
            maskStatusTextView.text = "Tap + to create a mask."
            previewImage.setMaskSelection(null)
        }
        maskAddButton.isEnabled = hasPhoto && !exportGate.isExporting
    }

    private fun rebuildMaskComponentsUi(layer: com.prabhat.photocraft.edit.MaskLayer) {
        if (!::maskComponentsList.isInitialized) return
        val show = maskEditingLayer && selectedCategory == EditorCategory.MASKING && !compareHeld
        maskComponentsHeader.visibility = if (show) View.VISIBLE else View.GONE
        maskComponentsList.visibility = if (show) View.VISIBLE else View.GONE
        maskComponentsList.removeAllViews()
        if (!show) return
        if (layer.components.isEmpty()) {
            val t = TextView(this).apply {
                text = "No components yet. Use Add/Subtract to paint."
                setTextColor(getColor(R.color.text_secondary)); textSize = 12f
                setPadding(0, dp(4), 0, dp(4))
            }
            maskComponentsList.addView(t)
            return
        }
        layer.components.forEachIndexed { index, comp ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val selected = index == layer.selectedComponentIndex && !maskNewComponentOnStroke
            val label = TextView(this).apply {
                text = if (comp.add) "+ ${comp.name}" else "- ${comp.name}"
                setTextColor(getColor(if (selected) R.color.accent else R.color.text_primary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    if (!canEditPhoto()) return@setOnClickListener
                    val m = editingState.state.masking
                    editingState.updateMasking(m.updateSelected(m.selected().selectComponent(index)), "Select Component")
                    maskNewComponentOnStroke = false
                    refreshMaskUi(); syncUiState()
                }
            }
            val del = TextView(this).apply {
                text = "Delete"
                setTextColor(getColor(R.color.error)); textSize = 12f
                setPadding(dp(12), dp(6), dp(4), dp(6))
                setOnClickListener {
                    if (!canEditPhoto()) return@setOnClickListener
                    val m = editingState.state.masking
                    val l = m.selected()
                    if (index !in l.components.indices) return@setOnClickListener
                    // select then delete
                    var nl = l.selectComponent(index)
                    nl = nl.deleteSelectedComponent()
                    editingState.updateMasking(m.updateSelected(nl), "Delete Component")
                    maskNewComponentOnStroke = true
                    refreshMaskUi(); syncUiState(); refreshHistory(); requestPreviewRender()
                }
            }
            row.addView(label)
            row.addView(del)
            maskComponentsList.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun autoSelectMask(type: com.prabhat.photocraft.edit.MaskType) {
        val bitmap = sourceBitmap ?: run { maskStatusTextView.text = "Open a photo first"; return }
        val current = editingState.state.masking
        val existingSubject = current.subject.selection

        fun applySubjectMask(subject: com.prabhat.photocraft.edit.MaskSelection) {
            val inverse = ByteArray(subject.coverage.size) { i ->
                (255 - (subject.coverage[i].toInt() and 0xFF)).toByte()
            }
            val background = com.prabhat.photocraft.edit.MaskSelection(subject.width, subject.height, inverse)
            val updated = current.copy(
                subject = current.subject.copy(selection = subject, inverted = false),
                background = current.background.copy(selection = background, inverted = false),
                selectedMask = type
            )
            editingState.updateMasking(updated, "Select ${type.name.lowercase().replaceFirstChar { it.uppercase() }}")
            enterMaskEditMode(); refreshMaskUi(); syncUiState(); refreshHistory()
        }

        if (type == com.prabhat.photocraft.edit.MaskType.BACKGROUND && existingSubject != null) {
            applySubjectMask(existingSubject); return
        }

        maskStatusTextView.text = "Detecting subject..."
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val detected = selfieMaskSegmenter.createMask(bitmap)
                val inverse = ByteArray(detected.coverage.size) { i ->
                    (255 - (detected.coverage[i].toInt() and 0xFF)).toByte()
                }
                val background = com.prabhat.photocraft.edit.MaskSelection(detected.width, detected.height, inverse)
                val latest = editingState.state.masking.copy(
                    subject = editingState.state.masking.subject.copy(selection = detected, inverted = false),
                    background = editingState.state.masking.background.copy(selection = background, inverted = false),
                    selectedMask = type
                )
                editingState.updateMasking(latest, "Select ${type.name.lowercase().replaceFirstChar { it.uppercase() }}")
                enterMaskEditMode(); refreshMaskUi(); syncUiState(); refreshHistory()
            } catch (error: Exception) {
                maskStatusTextView.text = "Subject detection failed: ${error.message ?: "try again"}"
            }
        }
    }

    private fun paintMaskAt(sx: Float, sy: Float) {
        val bmp = sourceBitmap ?: return
        val m = editingState.state.masking
        editingState.updateMasking(
            m.paintSelectedComponent(
                bmp.width, bmp.height, sx, sy,
                m.brushSize, m.brushFlow.toInt(),
                feather = m.brushFeather.toInt()
            ),
            "Paint ${m.selected().name}"
        )
    }

    private fun handleMaskTouch(x: Float, y: Float, action: Int): Boolean {
        if (!maskEditingLayer || selectedCategory != EditorCategory.MASKING || compareHeld || sourceBitmap == null || !canEditPhoto()) {
            return false
        }
        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val bmp = sourceBitmap ?: return false
                editingState.beginGesture()
                maskStrokeActive = true
                maskHasLast = false
                val layer = editingState.state.masking.selected()
                val needNew = maskNewComponentOnStroke || layer.selectedComponentIndex !in layer.components.indices
                if (needNew) {
                    val add = maskActiveTool != "Subtract"
                    editingState.updateMasking(
                        editingState.state.masking.ensureComponentForStroke(add, bmp.width, bmp.height),
                        "Paint ${layer.name}"
                    )
                }
                paintMaskAt(x, y)
                maskLastX = x; maskLastY = y; maskHasLast = true
                refreshMaskUi(); requestPreviewRender()
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!maskStrokeActive) return true
                val m = editingState.state.masking
                val spacing = kotlin.math.max(1f, m.brushSize * 0.25f)
                val dx = x - maskLastX
                val dy = y - maskLastY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist >= spacing) {
                    val steps = kotlin.math.max(1, (dist / spacing).toInt())
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        paintMaskAt(maskLastX + dx * t, maskLastY + dy * t)
                    }
                } else {
                    paintMaskAt(x, y)
                }
                maskLastX = x; maskLastY = y; maskHasLast = true
                refreshMaskUi(); requestPreviewRender()
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (maskStrokeActive) {
                    maskStrokeActive = false
                    editingState.endGesture()
                    maskNewComponentOnStroke = true
                    refreshMaskUi(); requestPreviewRender(); refreshHistory(); syncUiState()
                }
                return true
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (maskStrokeActive) {
                    maskStrokeActive = false
                    editingState.cancelGesture()
                    maskNewComponentOnStroke = true
                    refreshMaskUi(); requestPreviewRender(); refreshHistory(); syncUiState()
                }
                return true
            }
        }
        return false
    }

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
                R.id.maskingCategory -> EditorCategory.MASKING
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
            if (category != EditorCategory.MASKING) {
                maskEditingLayer = false
                maskStrokeActive = false
            }
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
        closeMixPanel()
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
                EditorCategory.MASKING -> R.id.maskingCategory
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
        maskingPanel.visibility =
            if (selectedCategory == EditorCategory.MASKING) View.VISIBLE else View.GONE
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
        if (selectedCategory != EditorCategory.MASKING) {
            maskCreateMenu.visibility = View.GONE
            // keep editing state but hide overlay; leaving category returns via refreshMaskUi
        }
        if (::maskBrushOverlay.isInitialized) refreshMaskUi()
    }

    private fun afterHistoryChange(previousState: EditState) {
        clearExportStatus()
        updateAllRows()
        applyAdvancedControlsFromState()
        applyLocalMaskControlsFromState()
        applyCropControlsFromState()
        applyFilterControlsFromState()
        if (mixOverlayVisible) syncMixUi()
        applyDisplayedBitmap()
        updateOutputSummary()
        if (
            editingState.adjustments != previousState.adjustments ||
            editingState.filter != previousState.filter ||
            editingState.state.effects != previousState.effects ||
            editingState.state.colorGrading != previousState.colorGrading ||
            editingState.state.masking != previousState.masking
        ) {
            requestPreviewRender()
        }
        if (maskEditingLayer && editingState.state.masking.selected().selection == null &&
            editingState.state.masking.selectedMask != com.prabhat.photocraft.edit.MaskType.BRUSH) {
            // subject/background may become unselected in some cases; keep overlay logic simple
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
        closeMixPanel()
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
        closeMixPanel()
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
            colorGrading = editingState.state.colorGrading,
            sourceWidth = width,
            sourceHeight = height,
            masking = editingState.state.masking,
            effects = editingState.state.effects
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
        applyAdvancedControlsFromState()
        applyLocalMaskControlsFromState()
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

        renderer.request(
            source, editingState.adjustments, editingState.filter,
            editingState.state.effects, editingState.state.colorGrading,
            editingState.state.masking
        ) { result ->
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

    private fun applyLocalMaskControlsFromState() {
        val masking = editingState.state.masking
        if (localMaskAdjustmentSliders.isNotEmpty()) {
            for (control in localMaskAdjustmentSliders.values) {
                val layer = when (control.type) {
                    com.prabhat.photocraft.edit.MaskType.SUBJECT -> masking.subject
                    com.prabhat.photocraft.edit.MaskType.BACKGROUND -> masking.background
                    else -> masking.selected()
                }
                val value = layer.adjustments.adjustments.value(control.adjustment)
                val progress = (value + 100).coerceIn(0, control.seekBar.max)
                if (control.seekBar.progress != progress) control.seekBar.progress = progress
                control.label.text = localMaskAdjustmentLabel(control.adjustment, value)
            }
        }
        if (selectedMaskAdjustmentSliders.isNotEmpty()) {
            val layer = masking.selected()
            for ((adj, control) in selectedMaskAdjustmentSliders) {
                val value = layer.adjustments.adjustments.value(adj)
                val progress = (value + 100).coerceIn(0, control.seekBar.max)
                if (control.seekBar.progress != progress) control.seekBar.progress = progress
                control.label.text = localMaskAdjustmentLabel(adj, value)
            }
        }
        if (::maskBrushOverlay.isInitialized) refreshMaskUi()
    }

    private fun localMaskAdjustmentLabel(adjustment: Adjustment, value: Int): String =
        "${adjustment.name.lowercase().replaceFirstChar { it.uppercase() }}: $value"

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
        if (::mixEntryButton.isInitialized) {
            mixEntryButton.isEnabled = canEdit
            mixEntryButton.alpha = if (canEdit) 1f else 0.45f
        }
        if (::mixOverlay.isInitialized && !canEdit && mixOverlayVisible) {
            closeMixPanel()
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


    private fun bindMixSwatch(color: HslColor, buttonId: Int, dotId: Int) {
        val button = findViewById<View>(buttonId)
        val dot = findViewById<View>(dotId)
        mixSwatchButtons[color] = button
        mixSwatchDots[color] = dot
        button.setOnClickListener { selectMixColor(color) }
    }

    private fun setupMixControls() {
        mixEntryButton.setOnClickListener {
            if (canEditPhoto()) openMixPanel()
        }
        mixDoneButton.setOnClickListener { closeMixPanel() }
        mixTargetButton.setOnClickListener { toggleMixTarget() }
        mixOverlay.setOnClickListener { /* consume background taps */ }
        mixSheet.setOnClickListener { /* consume */ }

        mixPropHue.setOnClickListener { selectMixProperty(MixProperty.HUE) }
        mixPropSaturation.setOnClickListener { selectMixProperty(MixProperty.SATURATION) }
        mixPropLuminance.setOnClickListener { selectMixProperty(MixProperty.LUMINANCE) }

        attachMixSliderDoubleTapReset()

        mixSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressMixSliderEvents) return
                val value = mixSliderToValue(progress)
                updateMixValueLabel(value)
                if (fromUser && canEditPhoto() && mixOverlayVisible) {
                    mixSliderGestureChanged = true
                    applyMixHslValue(value, recordHistory = false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (!canEditPhoto() || !mixOverlayVisible) return
                mixSliderGestureChanged = false
                editingState.beginGesture()
                clearExportStatus()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!canEditPhoto() || !mixOverlayVisible) return
                if (mixSliderGestureChanged) {
                    editingState.endGesture()
                } else {
                    editingState.cancelGesture()
                }
                mixSliderGestureChanged = false
                refreshHistory()
                syncUiState()
                syncMixUi()
            }
        })

        previewImage.setMixTargetTouchListener { viewX, viewY, action ->
            handleMixTargetTouch(viewX, viewY, action)
        }

        syncMixUi()
    }

    private fun openMixPanel() {
        if (!canEditPhoto()) return
        endCompare()
        hideActionSheet()
        mixOverlayVisible = true
        mixOverlay.visibility = View.VISIBLE
        mixOverlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        mainColumn.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        mixTargetActive = false
        selectedMixProperty = MixProperty.HUE
        syncMixUi()
        mixOverlay.bringToFront()
        mixOverlay.requestFocus()
    }

    private fun closeMixPanel() {
        if (!::mixOverlay.isInitialized) return
        if (mixTargetDragChanged) {
            // safety: end dangling gesture
            editingState.endGesture()
            mixTargetDragChanged = false
        }
        mixTargetActive = false
        mixOverlayVisible = false
        mixOverlay.visibility = View.GONE
        mixOverlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        mainColumn.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        syncUiState()
    }

    private fun toggleMixTarget() {
        if (!canEditPhoto() || !mixOverlayVisible) return
        mixTargetActive = !mixTargetActive
        if (!mixTargetActive && mixTargetDragChanged) {
            editingState.endGesture()
            mixTargetDragChanged = false
            refreshHistory()
            syncUiState()
        }
        syncMixUi()
    }

    private fun selectMixColor(color: HslColor) {
        if (selectedMixColor == color) return
        selectedMixColor = color
        syncMixUi() // swatch switch: show saved values, no history
    }

    private fun selectMixProperty(property: MixProperty) {
        if (selectedMixProperty == property) return
        selectedMixProperty = property
        syncMixUi()
    }

    private fun syncMixUi() {
        if (!::mixOverlay.isInitialized) return
        for ((color, button) in mixSwatchButtons) {
            val dot = mixSwatchDots[color]
            val selected = color == selectedMixColor
            dot?.visibility = if (selected) View.VISIBLE else View.GONE
            button.alpha = if (selected) 1f else 0.9f
        }

        mixTargetButton.isSelected = mixTargetActive
        mixTargetButton.alpha = if (mixTargetActive) 1f else 0.85f

        fun styleProp(view: TextView, active: Boolean) {
            view.setTextColor(getColor(if (active) R.color.text_primary else R.color.text_secondary))
            view.alpha = if (active) 1f else 0.8f
        }
        styleProp(mixPropHue, selectedMixProperty == MixProperty.HUE)
        styleProp(mixPropSaturation, selectedMixProperty == MixProperty.SATURATION)
        styleProp(mixPropLuminance, selectedMixProperty == MixProperty.LUMINANCE)

        val (labelRes, min, max, value) = currentMixPropertyState()
        mixSliderLabel.setText(labelRes)
        suppressMixSliderEvents = true
        mixSlider.max = max - min
        mixSlider.progress = (value - min).coerceIn(0, mixSlider.max)
        suppressMixSliderEvents = false
        updateMixValueLabel(value)
        updateMixSliderTrack()
    }

    private fun currentMixPropertyState(): QuadInt {
        val adj = editingState.state.colorGrading.hsl.adjustment(selectedMixColor)
        return when (selectedMixProperty) {
            MixProperty.HUE -> QuadInt(R.string.mix_hue, -180, 180, adj.hue)
            MixProperty.SATURATION -> QuadInt(R.string.mix_saturation, -100, 100, adj.saturation)
            MixProperty.LUMINANCE -> QuadInt(R.string.mix_luminance, -100, 100, adj.luminance)
        }
    }

    private data class QuadInt(val label: Int, val min: Int, val max: Int, val value: Int)

    private fun mixSliderToValue(progress: Int): Int = when (selectedMixProperty) {
        MixProperty.HUE -> progress - 180
        else -> progress - 100
    }

    private fun updateMixValueLabel(value: Int) {
        mixSliderValue.text = if (value > 0) "+$value" else value.toString()
    }

    private fun applyMixHslValue(value: Int, recordHistory: Boolean) {
        val state = editingState.state.colorGrading
        val old = state.hsl.adjustment(selectedMixColor)
        val updatedAdj = when (selectedMixProperty) {
            MixProperty.HUE -> old.copy(hue = value.coerceIn(-180, 180))
            MixProperty.SATURATION -> old.copy(saturation = value.coerceIn(-100, 100))
            MixProperty.LUMINANCE -> old.copy(luminance = value.coerceIn(-100, 100))
        }
        val updated = state.copy(hsl = replaceHsl(state.hsl, selectedMixColor, updatedAdj))
        if (editingState.updateColorGrading(updated, "HSL ${selectedMixColor.name.lowercase().replaceFirstChar { it.uppercase() }}")) {
            if (!recordHistory) {
                // inside gesture updates are fine; outside would create history but we manage gestures
            }
            clearExportStatus()
            requestPreviewRender()
            syncUiState()
        }
    }

    private fun attachMixSliderDoubleTapReset() {
        var suppressTouch = false
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (!mixOverlayVisible || !canEditPhoto() || !mixSlider.isEnabled) return false
                val state = currentMixPropertyState()
                if (state.value == 0) return true
                suppressTouch = true
                editingState.beginGesture()
                clearExportStatus()
                applyMixHslValue(0, recordHistory = false)
                editingState.endGesture()
                refreshHistory()
                syncUiState()
                syncMixUi()
                return true
            }
        })
        mixSlider.setOnTouchListener { _, event ->
            if (suppressTouch) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    suppressTouch = false
                }
                true
            } else {
                detector.onTouchEvent(event)
                if (suppressTouch) true else false
            }
        }
    }

    private fun handleMixTargetTouch(viewX: Float, viewY: Float, action: Int): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (!mixOverlayVisible || !mixTargetActive || !canEditPhoto() || sourceBitmap == null) {
                    return false
                }
                editingState.beginGesture()
                clearExportStatus()
                mixTargetDragChanged = false
                mixTargetLastX = viewX
                mixTargetLastY = viewY
                mixTargetAccum = 0f
                sampleMixColorAt(viewX, viewY)?.let { if (it != selectedMixColor) selectedMixColor = it }
                syncMixUi()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = viewX - mixTargetLastX
                val dy = viewY - mixTargetLastY
                mixTargetLastX = viewX
                mixTargetLastY = viewY
                val delta = dx - dy
                val scale = when (selectedMixProperty) {
                    MixProperty.HUE -> 0.35f
                    else -> 0.25f
                }
                mixTargetAccum += delta * scale
                val step = mixTargetAccum.toInt()
                if (step != 0) {
                    mixTargetAccum -= step
                    val cur = currentMixPropertyState().value
                    val next = (cur + step).coerceIn(currentMixPropertyState().min, currentMixPropertyState().max)
                    if (next != cur) {
                        mixTargetDragChanged = true
                        applyMixHslValue(next, recordHistory = false)
                        syncMixUi()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mixTargetDragChanged) {
                    editingState.endGesture()
                } else {
                    editingState.cancelGesture()
                }
                mixTargetDragChanged = false
                refreshHistory()
                syncUiState()
                syncMixUi()
                return true
            }
        }
        return false
    }

    private fun sampleMixColorAt(viewX: Float, viewY: Float): HslColor? {
        val bitmap = sourceBitmap ?: return null
        // map view coords to source bitmap coords similar to CropPreviewView logic
        val crop = if (selectedCategory == EditorCategory.CROP || compareHeld) com.prabhat.photocraft.edit.CropSelection.FULL else editingState.crop
        val vw = previewImage.width.toFloat()
        val vh = previewImage.height.toFloat()
        if (vw <= 0f || vh <= 0f) return null
        val srcLeft = crop.left * bitmap.width
        val srcTop = crop.top * bitmap.height
        val srcW = (crop.right - crop.left) * bitmap.width
        val srcH = (crop.bottom - crop.top) * bitmap.height
        if (srcW <= 0f || srcH <= 0f) return null
        val scale = minOf(vw / srcW, vh / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val left = (vw - dw) / 2f
        val top = (vh - dh) / 2f
        if (viewX < left || viewX > left + dw || viewY < top || viewY > top + dh) return null
        val sx = (srcLeft + (viewX - left) / dw * srcW).toInt().coerceIn(0, bitmap.width - 1)
        val sy = (srcTop + (viewY - top) / dh * srcH).toInt().coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(sx, sy)
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val hsl = rgbToHslLocal(r, g, b)
        val hue = hsl[0]
        return HslColor.entries.minByOrNull { hueDistance(hue, it.centerHue) }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun rgbToHslLocal(r: Float, g: Float, b: Float): FloatArray {
        val maximum = maxOf(r, g, b)
        val minimum = minOf(r, g, b)
        val delta = maximum - minimum
        var hue = 0f
        val lightness = (maximum + minimum) / 2f
        val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        if (delta != 0f) {
            hue = when (maximum) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
        }
        val wrapped = hue % 360f
        return floatArrayOf(if (wrapped < 0f) wrapped + 360f else wrapped, saturation, lightness)
    }

    private fun hslToRgbLocal(h: Float, s: Float, l: Float): IntArray {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        val c = (1f - kotlin.math.abs(2f * ll - 1f)) * ss
        val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
        val m = ll - c / 2f
        val rgb = when {
            hh < 60f -> floatArrayOf(c, x, 0f)
            hh < 120f -> floatArrayOf(x, c, 0f)
            hh < 180f -> floatArrayOf(0f, c, x)
            hh < 240f -> floatArrayOf(0f, x, c)
            hh < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        fun to255(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return intArrayOf(to255(rgb[0]), to255(rgb[1]), to255(rgb[2]))
    }

    private fun updateMixSliderTrack() {
        val colors = mixTrackColors()
        val gradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
        gradient.cornerRadius = dp(2).toFloat()
        val height = dp(4)
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#3A3A42"))
            cornerRadius = dp(2).toFloat()
        }
        // set progress drawable layers
        val progress = mixSlider.progressDrawable
        if (progress is LayerDrawable) {
            val bgLayer = progress.findDrawableByLayerId(android.R.id.background)
            (bgLayer as? GradientDrawable)?.setColor(Color.parseColor("#3A3A42"))
            val progLayer = progress.findDrawableByLayerId(android.R.id.progress)
            if (progLayer is ClipDrawable) {
                // replace clip drawable content? easier set whole drawable
            }
        }
        val clip = ClipDrawable(gradient, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        val layers = LayerDrawable(arrayOf(bg, clip))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        layers.setLayerHeight(0, height)
        layers.setLayerHeight(1, height)
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL)
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL)
        mixSlider.progressDrawable = layers
        mixSlider.invalidate()
    }

    private fun mixTrackColors(): IntArray {
        val adj = editingState.state.colorGrading.hsl.adjustment(selectedMixColor)
        val baseHue = selectedMixColor.centerHue
        return when (selectedMixProperty) {
            MixProperty.HUE -> {
                IntArray(7) { i ->
                    val h = baseHue + (i - 3) * 60f
                    val rgb = hslToRgbLocal(h, 0.85f, 0.55f)
                    Color.rgb(rgb[0], rgb[1], rgb[2])
                }
            }
            MixProperty.SATURATION -> {
                val h = baseHue
                IntArray(5) { i ->
                    val sat = i / 4f
                    val rgb = hslToRgbLocal(h, sat, 0.5f)
                    Color.rgb(rgb[0], rgb[1], rgb[2])
                }
            }
            MixProperty.LUMINANCE -> {
                val h = baseHue
                val sat = (0.7f + adj.saturation / 200f).coerceIn(0.1f, 1f)
                IntArray(5) { i ->
                    val lum = i / 4f
                    val rgb = hslToRgbLocal(h, sat, lum)
                    Color.rgb(rgb[0], rgb[1], rgb[2])
                }
            }
        }
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
        if (::maskBrushOverlay.isInitialized && maskEditingLayer && selectedCategory == EditorCategory.MASKING) {
            exitMaskEditMode(keepEdits = true)
            return true
        }
        if (::maskCreateMenu.isInitialized && maskCreateMenu.visibility == View.VISIBLE) {
            maskCreateMenu.visibility = View.GONE
            return true
        }
        if (::mixOverlay.isInitialized && mixOverlay.visibility == View.VISIBLE) {
            closeMixPanel()
            return true
        }
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

