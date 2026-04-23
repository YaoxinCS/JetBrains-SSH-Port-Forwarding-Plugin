package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object SshPortForwardingFileStorage {

    private const val STORAGE_DIR = "port-forwarding"
    private const val APPLICATION_FILE = "sessions.xml"
    private const val PROJECTS_DIR = "projects"

    private val LOG = Logger.getInstance(SshPortForwardingFileStorage::class.java)

    fun applicationStoragePath(): Path =
        PathManager.getConfigDir().resolve(STORAGE_DIR).resolve(APPLICATION_FILE)

    fun projectStoragePath(project: Project): Path =
        PathManager.getConfigDir()
            .resolve(STORAGE_DIR)
            .resolve(PROJECTS_DIR)
            .resolve("${projectStorageKey(project)}.xml")

    fun read(path: Path): SshPortForwardingState {
        if (!Files.isRegularFile(path)) return SshPortForwardingState()
        return try {
            Files.newInputStream(path).use(SshPortForwardingStorageCodec::read)
        } catch (t: Throwable) {
            LOG.warn("Failed to read SSH port forwarding storage from $path", t)
            SshPortForwardingState()
        }
    }

    fun write(path: Path, state: SshPortForwardingState): Boolean {
        return try {
            Files.createDirectories(path.parent)
            val tempFile = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
            try {
                Files.newOutputStream(tempFile).use { output ->
                    SshPortForwardingStorageCodec.write(state, output)
                }
                moveIntoPlace(tempFile, path)
            } finally {
                Files.deleteIfExists(tempFile)
            }
            true
        } catch (t: Throwable) {
            LOG.warn("Failed to write SSH port forwarding storage to $path", t)
            false
        }
    }

    private fun moveIntoPlace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Throwable) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun projectStorageKey(project: Project): String {
        val rawKey = project.locationHash
            .takeIf(String::isNotBlank)
            ?: project.basePath?.takeIf(String::isNotBlank)
            ?: project.name
        return sha256(rawKey)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
