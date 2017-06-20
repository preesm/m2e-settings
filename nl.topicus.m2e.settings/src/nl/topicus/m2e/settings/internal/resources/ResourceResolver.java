/*******************************************************************************
 * Copyright 2010 Mohan KR
 * Copyright 2010 Basis Technology Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package nl.topicus.m2e.settings.internal.resources;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.topicus.m2e.settings.internal.ProjectSettingsConfigurator;


/**
 * A utility class to resolve resources, which includes searching in resources
 * specified with the {@literal <dependencies>} of the Maven pluginWrapper
 * configuration.
 *
 * @since 0.9.8
 */
public final class ResourceResolver {


	private static final Logger LOG =
	        LoggerFactory.getLogger(ResourceResolver.class);

	private final ClassRealm pluginRealm;
	private final IPath projectLocation;
	private final List<IPath> projectLocations;

	public ResourceResolver(final ClassRealm pluginRealm,
	        final IPath projectLocation, final List<IPath> projectLocations) {
		if (!Objects.nonNull(projectLocation)) {
			throw new IllegalArgumentException();
		}
		if (!Objects.nonNull(projectLocations)) {
			throw new IllegalArgumentException();
		}
		this.pluginRealm = pluginRealm;
		this.projectLocation = projectLocation;
		this.projectLocations = projectLocations;
	}

	/**
	 * Resolve the resource location as per the maven pluginWrapper spec.
	 * <ol>
	 * <li>As a resource.</li>
	 * <li>As a URL.</li>
	 * <li>As a filesystem resource.</li>
	 * </ol>
	 *
	 * @param location
	 *            the resource location as a string.
	 * @return the {@code URL} of the resolved location or {@code null}.
	 */
	public URL resolveLocation(final String location) {
		if (location == null || location.isEmpty()) {
			return null;
		}
		URL url = null;
		for (final IPath path : projectLocations) {
			url = getResourceRelativeFromIPath(path, location);
			if (url != null) {
				return url;
			}
		}
		url = getResourceFromPluginRealm(location);
		if (url == null) {
			url = getResourceFromRemote(location);
		}
		if (url == null) {
			url = getResourceFromFileSystem(location);
		}
		if (url == null) {
			url = getResourceRelativeFromProjectLocation(location);
		}
		return url;
	}

	private URL getResourceFromPluginRealm(final String resource) {
		if (pluginRealm == null) {
			return null;
		}
		String fixedResource =
		        resource.startsWith("/") ? resource.substring(1) : resource;
		try {
			List<URL> urls =
			        Collections.list(pluginRealm.getResources(fixedResource));
			if (urls.isEmpty()) {
				return null;
			}
			if (urls.size() > 1) {
				LOG.warn(
				        "Resource appears more than once on classpath, this is "
				                + "dangerous because it makes resolving this resource "
				                + "dependant on classpath ordering; location {} found in {}",
				        fixedResource, urls);
			}
			return urls.get(0);
		} catch (IOException e) {
			LOG.warn("getResources() failed: " + fixedResource, e);
			return null;
		}
	}

	private URL getResourceFromRemote(final String resource) {
		try {
			return new URL(resource);
		} catch (final IOException e) {
			LOG.trace("Could not open resource {} from remote", resource, e);
		}
		return null;
	}

	private URL getResourceFromFileSystem(final String resource) {
		try {
			final Path path = Paths.get(resource);
			if (Files.exists(path)) {
				return path.toUri().toURL();
			}
		} catch (InvalidPathException | IOException e) {
			LOG.trace("Could not open resource {} from file system", resource,
			        e);
		}
		return null;
	}

	private URL getResourceRelativeFromProjectLocation(final String resource) {
		return getResourceRelativeFromIPath(projectLocation, resource);
	}

	private URL getResourceRelativeFromIPath(final IPath path,
	        final String resource) {
		try {
			final File file = path.append(resource).toFile();
			if (file.exists()) {
				return file.toURI().toURL();
			}
		} catch (final IOException e) {
			LOG.trace("Could not open resource {} relative to project location",
			        resource, e);
		}
		return null;
	}





	public static ResourceResolver getResourceResolver(
			ProjectConfigurationRequest projectConfigurationRequest, IProgressMonitor monitor) throws CoreException {


		IMavenProjectFacade mavenProjectFacade = projectConfigurationRequest.getMavenProjectFacade();
		List<MojoExecution> mojoExecutions = mavenProjectFacade.getMojoExecutions(ProjectSettingsConfigurator.GROUP_ID,
				ProjectSettingsConfigurator.ARTIFACT_ID, monitor, ProjectSettingsConfigurator.GOAL);

		if (mojoExecutions.size() < 1) {
			LOG.warn("Could not access Mojo Execution for plugin ID "+ProjectSettingsConfigurator.PLUGIN_ID);
			return null;
		}
		MojoExecution mojoExecution = mojoExecutions.get(0);
		IProject eclipseProject = projectConfigurationRequest.getProject();
		IPath location = eclipseProject.getLocation();
		@SuppressWarnings("deprecation")
		final MavenSession session = projectConfigurationRequest.getMavenSession();

		// call for side effect of ensuring that the realm is set in the
		// descriptor.
		final IMaven mvn = MavenPlugin.getMaven();
		final List<IPath> pluginDepencyProjectLocations = new ArrayList<>();
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final IMavenProjectRegistry mavenProjectRegistry =
		        MavenPlugin.getMavenProjectRegistry();
		final IMavenProjectFacade[] projects =
		        mavenProjectRegistry.getProjects();
		final List<Dependency> dependencies =
		        mojoExecution.getPlugin().getDependencies();
		for (final Dependency dependency : dependencies) {
			for (final IMavenProjectFacade projectFacade : projects) {
				final IProject project = projectFacade.getProject();
				if (!project.isAccessible()) {
					LOG.debug("Project registry contains closed project {}",
					        project);
					// this is actually a bug somewhere in registry refresh
					// logic, closed projects should not be there
					continue;
				}
				final ArtifactKey artifactKey = projectFacade.getArtifactKey();
				if (artifactKey.getGroupId().equals(dependency.getGroupId())
				        && artifactKey.getArtifactId()
				                .equals(dependency.getArtifactId())
				        && artifactKey.getVersion()
				                .equals(dependency.getVersion())) {
					final IResource outputLocation =
					        root.findMember(projectFacade.getOutputLocation());
					if (outputLocation != null) {
						pluginDepencyProjectLocations.add(outputLocation.getLocation());
					}
				}
			}
		}
		try {
			final Mojo configuredMojo =
			        mvn.getConfiguredMojo(session, mojoExecution, Mojo.class);
			mvn.releaseMojo(configuredMojo, mojoExecution);
		} catch (CoreException e) {
			if (pluginDepencyProjectLocations.isEmpty()) {
				throw e;
			}
			LOG.trace("Could not get mojo", e);
		}
		return new ResourceResolver(mojoExecution.getMojoDescriptor()
		        .getPluginDescriptor().getClassRealm(), location,
		        pluginDepencyProjectLocations);
	}

}
