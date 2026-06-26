package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiagnosticReportTest {
    @Test
    fun `diagnostic report stores values correctly`() {
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
            )

        assertThat(report.id).isEqualTo("id")
        assertThat(report.downloadedBytes).isEqualTo(500)
    }
}
