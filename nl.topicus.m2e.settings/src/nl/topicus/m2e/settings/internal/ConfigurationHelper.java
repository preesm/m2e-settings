package nl.topicus.m2e.settings.internal;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigurationHelper {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(ConfigurationHelper.class);

	private ConfigurationHelper() {
	}

	public static List<EclipseSettingsFile> readSettingsFileFromPom(Plugin eclipsePlugin) {

		Xpp3Dom configurationXpp3Dom = (Xpp3Dom) eclipsePlugin
				.getConfiguration();

		if (configurationXpp3Dom == null) {
			LOGGER.error("Configuration should be provided.");
			return null;
		}

		return readEclipsePreferences(configurationXpp3Dom);
	}

	private static List<EclipseSettingsFile> readEclipsePreferences(
			Xpp3Dom configurationXpp3Dom) {
		List<EclipseSettingsFile> settingsFiles = new ArrayList<EclipseSettingsFile>();
		Xpp3Dom preferencesDom = configurationXpp3Dom
				.getChild("additionalConfig");
		if (preferencesDom != null) {
			for (Xpp3Dom preferenceDom : preferencesDom.getChildren("file")) {
				Xpp3Dom nameXpp3Dom = preferenceDom.getChild("name");
				Xpp3Dom locationXpp3Dom = preferenceDom.getChild("location");

				if (locationXpp3Dom != null && nameXpp3Dom != null) {
					String location = locationXpp3Dom.getValue();
					String name = nameXpp3Dom.getValue();

					EclipseSettingsFile settingsFile = new EclipseSettingsFile();
					settingsFile.setName(name);
					settingsFile.setLocation(location);
					settingsFiles.add(settingsFile);
				}
			}
		}
		return settingsFiles;
	}
}
