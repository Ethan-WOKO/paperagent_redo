package com.yanban.api.project;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectObjectStorage {

    String createPrefix(Long userId);

    ProjectObjectEntry storeFile(String prefix, String relativePath, MultipartFile file);

    /** Writes one immutable revision object. Callers must always use a freshly allocated prefix. */
    default ProjectObjectEntry storeBytes(String prefix, String relativePath, byte[] content, String contentType) {
        throw new UnsupportedOperationException("Project revision byte writes are not configured");
    }

    void writeManifest(String prefix, List<ProjectObjectEntry> files);

    List<ProjectObjectEntry> readManifest(String prefix);

    byte[] readFile(String prefix, String relativePath, long maxBytes);

    void deletePrefix(String prefix);
}
