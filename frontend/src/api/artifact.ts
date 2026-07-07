import http from './http';

export interface ArtifactSourceRef {
  type: string | null;
  id: string | null;
  title: string | null;
}

export interface ArtifactResponse {
  id: number;
  userId: number;
  sessionId: number | null;
  title: string;
  artifactType: string;
  content: string;
  sourceType: string;
  sourceRefs: ArtifactSourceRef[];
  status: string;
  downloadUrl: string;
  downloadFilename: string;
  downloadContentType: string;
  createdAt: string;
  updatedAt: string;
}

export interface SaveArtifactToKnowledgeResponse {
  artifactId: number;
  documentId: number;
  filename: string;
  status: string;
}

export function getArtifact(artifactId: number) {
  return http.get<ArtifactResponse>(`/artifacts/${artifactId}`);
}

export function downloadArtifact(artifactId: number) {
  return http.get<Blob>(`/artifacts/${artifactId}/download`, {
    responseType: 'blob',
  });
}

export function saveArtifactToKnowledge(artifactId: number) {
  return http.post<SaveArtifactToKnowledgeResponse>(`/artifacts/${artifactId}/save-to-knowledge`, {});
}
