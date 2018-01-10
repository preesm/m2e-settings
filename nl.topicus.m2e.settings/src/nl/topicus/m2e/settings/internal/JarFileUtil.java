package nl.topicus.m2e.settings.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.embedder.IMaven;

public final class JarFileUtil {

	private JarFileUtil() {
	}

	public static List<JarFile> resolveJar(IMaven maven,
			List<Dependency> dependencies, IProgressMonitor monitor)
			throws IOException, CoreException {
		List<JarFile> jarFiles = new ArrayList<>();
		for (int i = 0; i < dependencies.size(); i++) {
			Dependency dependency = dependencies.get(i);

			// create artifact based on dependency
			Artifact artifact = maven.resolve(dependency.getGroupId(),
					dependency.getArtifactId(), dependency.getVersion(),
					dependency.getType(), dependency.getClassifier(),
					maven.getArtifactRepositories(), monitor);
			jarFiles.add(new JarFile(artifact.getFile()));
		}
		return jarFiles;

	}
}
