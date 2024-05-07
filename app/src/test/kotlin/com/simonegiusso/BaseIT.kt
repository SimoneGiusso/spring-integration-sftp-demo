package com.simonegiusso

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
open class BaseIT {

    companion object {
        @Container
        val SFTP_SERVER_CONTAINER: GenericContainer<*> = GenericContainer(DockerImageName.parse("linuxserver/openssh-server"))
            .withCreateContainerCmdModifier{
                cmd -> cmd.withHostConfig(HostConfig().withPortBindings(PortBinding(Ports.Binding.bindPort(2222), ExposedPort(2222))))
            }
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .withEnv("USER_NAME", "test_user")
            .withEnv("USER_PASSWORD", "test_password")
            .withEnv("PASSWORD_ACCESS", "true")
            .withCopyFileToContainer(MountableFile.forClasspathResource("/sftp/deliveries/"), "deliveries")
    }

}
