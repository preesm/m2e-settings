package nl.topicus.m2e.settings.internal;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;

public final class Utils {
	public static void createDirectory(IContainer folder) throws CoreException {
		if (!folder.exists()) {
			IContainer parent = folder.getParent();
			if (parent instanceof IFolder) {
				createDirectory(parent);
			}
			((IFolder) folder).create(true, true, null);
		}
	}
}
