package com.prabhat.photocraft.export

class ExportGate {
    var isExporting: Boolean = false
        private set

    fun tryStart(): Boolean {
        if (isExporting) {
            return false
        }
        isExporting = true
        return true
    }

    fun finish() {
        isExporting = false
    }
}
