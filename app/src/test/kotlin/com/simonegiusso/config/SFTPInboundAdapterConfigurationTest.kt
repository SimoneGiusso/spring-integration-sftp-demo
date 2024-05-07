package com.simonegiusso.config

import com.simonegiusso.BaseIT
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.MessageHandler
import org.springframework.util.FileSystemUtils
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

private val log = KotlinLogging.logger {}

@Import(SFTPInboundAdapterConfigurationTest.TestFileConsumerConfiguration::class)
class SFTPInboundAdapterConfigurationTest: BaseIT() {

    companion object {
        val EXPECTED_FILES: List<String> = listOf(
            "File1_data.csv",
            "File2_data.csv"
        )
    }

    private lateinit var localDir: File

    @SpyBean
    private lateinit var fileConsumer: MessageHandler

    @BeforeEach
    fun setup(@Value("\${sftp.synchronizer.local-directory}") localDirPath: String) {
        localDir = File(localDirPath)
    }

    @AfterEach
    fun cleanup() {
        FileSystemUtils.deleteRecursively(localDir)
        assertFalse(localDir.exists())
    }

    @Test
    fun testFileDownloadAndFileDelivery() {
        await().pollInterval(1, SECONDS).atMost(10, SECONDS).untilAsserted {
            val downloadedFiles = localDir
                .also { assertTrue(localDir.exists()) }
                .listFiles()!!.map { file -> file.name }

            assertThat(downloadedFiles).isEqualTo(EXPECTED_FILES)
            assertFileMessages()
        }
    }

    private fun assertFileMessages() {
        verify(fileConsumer, times(EXPECTED_FILES.size)).handleMessage(any())
    }

    @TestConfiguration
    open class TestFileConsumerConfiguration {

        @Bean("fileConsumer")
        @Primary
        @ServiceActivator(inputChannel = "sftpChannel")
        open fun testFileConsumer(): MessageHandler =
            MessageHandler { message ->
                val fileName = (message.payload as File).name

                assertThat(fileName).isIn(EXPECTED_FILES)
                log.info { "Received $fileName file." }
            }

    }

}
