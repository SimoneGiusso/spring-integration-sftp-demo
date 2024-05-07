package com.simonegiusso.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.constraints.NotNull
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.kex.KeyExchangeFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.annotation.InboundChannelAdapter
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory
import org.springframework.messaging.MessageHandler
import org.springframework.validation.annotation.Validated
import java.io.File


private val log = KotlinLogging.logger {}

/**
 * Spring SFTP Adapter configuration
 * @see <a href="https://docs.spring.io/spring-integration/reference/sftp/inbound.html">SFTP Inbound Channel Adapter</a>
 */
@Configuration
@EnableConfigurationProperties(SFTPAdapterInBoundConfiguration.SftpConfiguration::class)
open class SFTPAdapterInBoundConfiguration {

    companion object {
        val DEPRECATED_KEY_EXCHANGE_FACTORIES: List<KeyExchangeFactory> = NamedFactory.setUpTransformedFactories(
            false,
            listOf(BuiltinDHFactories.dhg1),
            ClientBuilder.DH2KEX
        )
    }
    private fun sshClient(sftpConfiguration: SftpConfiguration) =
        SshClient.setUpDefaultClient()
            .also { sshClient ->
                sshClient.keyExchangeFactories.addAll(DEPRECATED_KEY_EXCHANGE_FACTORIES)
                sshClient.addPasswordIdentity(sftpConfiguration.password)
                sshClient.serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE
            }

    open fun sftpSessionFactory(sftpConfiguration: SftpConfiguration) =
        DefaultSftpSessionFactory(sshClient(sftpConfiguration), false)
            .also { sftpSessionFactory ->
                sftpSessionFactory.setHost(sftpConfiguration.host)
                sftpSessionFactory.setPort(sftpConfiguration.port)
                sftpSessionFactory.setUser(sftpConfiguration.username)
                sftpSessionFactory.setTimeout(sftpConfiguration.timeout)
            }

    @Bean
    open fun sftpInboundFileSynchronizer(sftpConfiguration: SftpConfiguration) =
        SftpInboundFileSynchronizer(sftpSessionFactory(sftpConfiguration))
            .also { fileSynchronizer ->
                fileSynchronizer.setRemoteDirectory(sftpConfiguration.synchronizer.folder)
                fileSynchronizer.setFilter(SftpSimplePatternFileListFilter("*_data.csv"))
            }

    @Bean
    @InboundChannelAdapter(channel = "sftpChannel", poller = Poller(fixedDelay = "\${sftp.synchronizer.polling-interval}", maxMessagesPerPoll = "-1"))
    open fun sftpMessageSource(sftpInboundFileSynchronizer: SftpInboundFileSynchronizer, sftpConfiguration: SftpConfiguration) =
        SftpInboundFileSynchronizingMessageSource(sftpInboundFileSynchronizer)
            .also { fileSynchronizerMessageSource ->
                fileSynchronizerMessageSource.setLocalDirectory(File(sftpConfiguration.synchronizer.localDirectory))
                fileSynchronizerMessageSource.setAutoCreateLocalDirectory(true)
                fileSynchronizerMessageSource.maxFetchSize = 1 // Comment this line and the test pass
            }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel")
    open fun fileConsumer(): MessageHandler =
        MessageHandler { message ->
            if (message.payload is File) log.info { "Received ${(message.payload as File).name} file." }
            else log.error { "Invalid message payload of type ${message.payload::class.java.name} received!" }

    }

    @ConfigurationProperties("sftp")
    @Validated
    data class SftpConfiguration(
        val host: @NotNull String,
        val port: @NotNull Int,
        val username: @NotNull String,
        val password: @NotNull String,
        val timeout: @NotNull Int,
        val synchronizer: SftpSynchronizerConfiguration
    ) {
        class SftpSynchronizerConfiguration(
            val folder: @NotNull String,
            val localDirectory: @NotNull String
        )
    }

}
