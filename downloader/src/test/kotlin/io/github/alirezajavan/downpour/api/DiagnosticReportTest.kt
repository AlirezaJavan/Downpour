package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiagnosticReportTest {
    @Test
    fun `diagnostic report stores values correctly`() {
        val part = PartProgress(index = 0, startByte = 0, endByte = 499, currentOffset = 250)
        val report =
            DiagnosticReport(
                id = "id",
                url = "url",
                state = DownloadState.Queued,
                retryCount = 1,
                lastError = null,
                isResumeSupported = true,
                totalBytes = 1000,
                downloadedBytes = 500,
                etag = "etag",
                lastModified = "date",
                createdAtMillis = 100,
                updatedAtMillis = 200,
                parts = listOf(part),
            )

        assertThat(report.id).isEqualTo("id")
        assertThat(report.downloadedBytes).isEqualTo(500)
        assertThat(report.parts).hasSize(1)
        assertThat(report.parts.first().index).isEqualTo(0)
        assertThat(report.parts.first().downloadedBytes).isEqualTo(250)
        assertThat(report.parts.first().totalBytes).isEqualTo(500)
        assertThat(report.parts.first().progress).isEqualTo(0.5f)
    }
}
