package nl.topicus.m2e.settings.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class ProjectSettingsConfigurator extends AbstractProjectConfigurator {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(ProjectSettingsConfigurator.class);

	private static final String ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN = "org.apache.maven.plugins:maven-eclipse-plugin";

	@Override
	public void configure(
			ProjectConfigurationRequest projectConfigurationRequest,
			IProgressMonitor monitor) throws CoreException {

		MavenProject mavenProject = projectConfigurationRequest
				.getMavenProject();

		Plugin eclipsePlugin = mavenProject.getBuild().getPluginsAsMap()
				.get(ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN);
		if (eclipsePlugin == null) {
			final String message = "Could not set eclipse settings, consider "
					+ ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN + "!";
			LOGGER.info(message);
		} else {
			final String message = "Using "
					+ ORG_APACHE_MAVEN_PLUGINS_MAVEN_ECLIPSE_PLUGIN
					+ " configuration";
			LOGGER.info(message);
			try {
				if (configureEclipseMeta(projectConfigurationRequest,
						eclipsePlugin, monitor)) {
					LOGGER.info("Project configured.");
				} else {
					LOGGER.error("Project not configured.");
				}
			} catch (IOException e) {
				LOGGER.error("Failure during settings configuration", e);
			}
		}

	}

	/**
	 * Use the org.apache.maven.plugins:maven-eclipse-plugin to force the
	 * eclipse settings.
	 *
	 * @param project
	 * @param buildPluginMap
	 * @param monitor
	 * @return
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws CoreException
	 */
	private boolean configureEclipseMeta(
			ProjectConfigurationRequest projectConfigurationRequest,
			Plugin eclipsePlugin, IProgressMonitor monitor)
			throws IOException, CoreException {

		List<EclipseSettingsFile> settingsFiles = ConfigurationHelper
				.readSettingsFileFromPom(eclipsePlugin);

		if (settingsFiles == null) {
			LOGGER.warn("No settings specified.");
			return false;
		}

		applyEclipsePreferencesPref(projectConfigurationRequest, settingsFiles,
				eclipsePlugin, monitor);

		return true;
	}

	private void applyEclipsePreferencesPref(
			ProjectConfigurationRequest projectConfigurationRequest,
			List<EclipseSettingsFile> settingsFiles, Plugin eclipsePlugin,
			IProgressMonitor monitor) throws IOException, CoreException {

		IProject project = projectConfigurationRequest.getProject();
		for (EclipseSettingsFile eclipsePreference : settingsFiles) {

			InputStream contentStream = null;
			try {
				contentStream = openStream(projectConfigurationRequest,
						eclipsePreference.getLocation(), eclipsePlugin,
						monitor);
				if (contentStream == null) {
					LOGGER.error("Could not find content for: "
							+ eclipsePreference.getLocation());
				} else {
					String prefName = eclipsePreference.getName();
					if (prefName.startsWith(".settings/")
							&& prefName.endsWith(".prefs")) {
						// use Eclipse Preference services
						ProjectPreferencesUtils.setOtherPreferences(project,
								contentStream,
								prefName.substring(10, prefName.length() - 6));
					} else {
						// copy file using IResource API
						IPath outputPath = Path
								.fromOSString(eclipsePreference.getName());
						deleteIfExist(project, outputPath);
						IFile outputFile = project.getFile(outputPath);
						Utils.createDirectory(outputFile.getParent());
						outputFile.create(contentStream, true, null);
					}
				}

			} catch (BackingStoreException e) {
				throw new IOException(e);
			} finally {
				if (contentStream != null) {
					contentStream.close();
				}
			}
		}

	}

	private void deleteIfExist(IProject project, IPath outputPath)
			throws CoreException {
		IResource outputCurrent = project.findMember(outputPath);
		if (outputCurrent != null)
			outputCurrent.delete(true, null);
	}

	private InputStream openStream(
			ProjectConfigurationRequest projectConfigurationRequest,
			String filePath, Plugin eclipsePlugin, IProgressMonitor monitor)
			throws IOException, CoreException {

		// first remvoe leading /
		final String cleanFilePath;
		if (filePath.startsWith("/"))
			cleanFilePath = filePath.substring(1);
		else
			cleanFilePath = filePath;

		// lookup in current file system
		final MavenProject mavenProject = projectConfigurationRequest
				.getMavenProject();

		final java.nio.file.Path path = FileSystems.getDefault().getPath(
				mavenProject.getBasedir().getAbsolutePath(), cleanFilePath);
		final boolean exists = path.toFile().exists();
		if (exists) {
			final String message = "Found entry on local file system";
			LOGGER.info(message);
			return Files.newInputStream(path);
		}

		// if not found, lookup in dependency jars
		List<JarFile> jarFiles = JarFileUtil.resolveJar(maven,
				eclipsePlugin.getDependencies(), monitor);
		for (JarFile jarFile : jarFiles) {
			ZipEntry entry = jarFile.getEntry(cleanFilePath);
			if (entry != null) {
				final String message = "Found entry in dependency jar "
						+ jarFile.getName();
				LOGGER.info(message);
				return jarFile.getInputStream(entry);
			}
		}
		final String message = "Entry " + cleanFilePath + " not found in "
				+ eclipsePlugin;
		LOGGER.warn(message);
		return null;
	}

}
