/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.modules.compat.internal;

import com.liferay.modules.compat.internal.adaptor.ServiceAdaptor;
import com.liferay.modules.compat.internal.configuration.ModuleCompatExtenderConfiguration;
import com.liferay.petra.string.CharPool;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayInputStream;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayOutputStream;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.BundleTracker;

/**
 * @author Matthew Tambara
 */
@Component(
	configurationPid = "com.liferay.modules.compat.internal.configuration.ModuleCompatExtenderConfiguration",
	immediate = true
)
public class ModuleCompatExtender {

	@Activate
	protected void activate(
			BundleContext bundleContext, Map<String, String> properties)
		throws IOException {

		Bundle modulesCompatBundle = bundleContext.getBundle();

		URL url = modulesCompatBundle.getEntry("META-INF/compat.properties");

		Properties compatProperties = new Properties();

		try (InputStream inputStream = url.openStream()) {
			compatProperties.load(inputStream);
		}

		_compatSymbolicNames = compatProperties.stringPropertyNames();

		_uninstallBundles(bundleContext);

		ModuleCompatExtenderConfiguration moduleCompatExtenderConfiguration =
			ConfigurableUtil.createConfigurable(
				ModuleCompatExtenderConfiguration.class, properties);

		if (!moduleCompatExtenderConfiguration.enabled()) {
			return;
		}

		Pattern pattern = Pattern.compile(
			moduleCompatExtenderConfiguration.modulesWhitelist());

		Matcher matcher = pattern.matcher(StringPool.BLANK);

		_bundleTracker = new BundleTracker<List<ServiceAdaptor<?, ?>>>(
			bundleContext, ~Bundle.UNINSTALLED, null) {

			@Override
			public List<ServiceAdaptor<?, ?>> addingBundle(
				Bundle bundle, BundleEvent bundleEvent) {

				String location = bundle.getLocation();

				Bundle compatBundle = bundleContext.getBundle(
					location.concat("-compat"));

				if (compatBundle != null) {
					return null;
				}

				String symbolicName = bundle.getSymbolicName();

				matcher.reset(symbolicName);

				if (matcher.matches()) {
					String exportedPackages = compatProperties.getProperty(
						symbolicName);

					if (Validator.isNull(exportedPackages)) {
						return null;
					}

					try {
						return _installCompatBundle(
							bundle, bundleContext,
							_generateExportString(
								modulesCompatBundle, exportedPackages));
					}
					catch (Exception e) {
						_log.error(
							"Unable to install compat fragment bundle for " +
								bundle,
							e);
					}
				}

				return null;
			}

			@Override
			public void removedBundle(
				Bundle bundle, BundleEvent event,
				List<ServiceAdaptor<?, ?>> serviceAdaptors) {

				for (ServiceAdaptor<?, ?> serviceAdaptor : serviceAdaptors) {
					serviceAdaptor.close();
				}
			}

		};

		_bundleTracker.open();
	}

	@Deactivate
	protected void deactivate(BundleContext bundleContext) {
		if (_bundleTracker != null) {
			_bundleTracker.close();
		}

		_uninstallBundles(bundleContext);
	}

	private String _generateExportString(Bundle bundle, String exportedPackages)
		throws IOException {

		String[] exports = StringUtil.split(exportedPackages);

		if (exports.length == 0) {
			return StringPool.BLANK;
		}

		StringBundler sb = new StringBundler(exports.length * 4);

		for (String export : exports) {
			export = export.trim();

			sb.append(export);

			sb.append(";version=\"");

			String packageInfoPath = export.replace(
				StringPool.PERIOD, StringPool.SLASH);

			URL url = bundle.getEntry(packageInfoPath.concat("/packageinfo"));

			try (InputStream inputStream = url.openStream();
				Reader reader = new InputStreamReader(inputStream);
				BufferedReader bufferedReader = new BufferedReader(reader)) {

				String versionLine = bufferedReader.readLine();

				sb.append(versionLine.substring(8));
			}

			sb.append("\",");
		}

		sb.setStringAt(StringPool.QUOTE, sb.index() - 1);

		return sb.toString();
	}

	private List<ServiceAdaptor<?, ?>> _installCompatBundle(
			Bundle bundle, BundleContext bundleContext, String exportBundles)
		throws Exception {

		String symbolicName = bundle.getSymbolicName();

		try (UnsyncByteArrayOutputStream unsyncByteArrayOutputStream =
				new UnsyncByteArrayOutputStream()) {

			try (JarOutputStream jarOutputStream = new JarOutputStream(
					unsyncByteArrayOutputStream)) {

				Manifest manifest = new Manifest();

				Attributes attributes = manifest.getMainAttributes();

				attributes.putValue(Constants.BUNDLE_MANIFESTVERSION, "2");

				attributes.putValue(
					Constants.BUNDLE_SYMBOLICNAME,
					symbolicName.concat(".compat"));

				attributes.putValue(Constants.BUNDLE_VERSION, "1.0.0");
				attributes.putValue(Constants.EXPORT_PACKAGE, exportBundles);
				attributes.putValue(Constants.FRAGMENT_HOST, symbolicName);
				attributes.putValue("Manifest-Version", "2");

				jarOutputStream.putNextEntry(
					new ZipEntry(JarFile.MANIFEST_NAME));

				manifest.write(jarOutputStream);

				jarOutputStream.closeEntry();
			}

			String location = bundle.getLocation();

			Bundle compatBundle = bundleContext.installBundle(
				location.concat("-compat"),
				new UnsyncByteArrayInputStream(
					unsyncByteArrayOutputStream.unsafeGetByteArray(), 0,
					unsyncByteArrayOutputStream.size()));

			if (_log.isInfoEnabled()) {
				_log.info("Compat fragment " + compatBundle + " installed");
			}

			_refreshBundles(
				bundleContext, Collections.<Bundle>singleton(compatBundle));
		}

		Dictionary<String, String> headers = bundle.getHeaders();

		List<ServiceAdaptor<?, ?>> serviceAdaptors = new ArrayList<>();

		for (String adaptorLine : StringUtil.split(
				headers.get("Liferay-Modules-Compat-Adaptors"))) {

			String[] classNames = StringUtil.split(adaptorLine, CharPool.COLON);

			if (classNames.length != 2) {
				_log.error(
					StringBundler.concat(
						"Invalid format in bundle: ", String.valueOf(bundle),
						"'s Liferay-Modules-Compat-Adaptors line : ",
						adaptorLine));
			}

			try {
				Class<?> fromClass = bundle.loadClass(
					StringUtil.trim(classNames[0]));

				Class<?> toClass = bundle.loadClass(
					StringUtil.trim(classNames[1]));

				serviceAdaptors.add(
					new ServiceAdaptor<>(bundleContext, fromClass, toClass));
			}
			catch (ClassNotFoundException cnfe) {
				_log.error(
					StringBundler.concat(
						"Invalid class name in bundle: ",
						String.valueOf(bundle),
						"'s Liferay-Modules-Compat-Adaptors line : ",
						adaptorLine),
					cnfe);
			}
		}

		if (serviceAdaptors.isEmpty()) {
			return null;
		}

		return serviceAdaptors;
	}

	private void _refreshBundles(
		BundleContext bundleContext, Collection<Bundle> bundles) {

		Bundle systemBundle = bundleContext.getBundle(0);

		FrameworkWiring frameworkWiring = systemBundle.adapt(
			FrameworkWiring.class);

		frameworkWiring.refreshBundles(bundles);
	}

	private void _uninstallBundles(BundleContext bundleContext) {
		List<Bundle> bundles = new ArrayList<>();

		Set<String> compatSymbolicNames = _compatSymbolicNames;

		for (Bundle bundle : bundleContext.getBundles()) {
			String symbolicName = bundle.getSymbolicName();

			if (compatSymbolicNames.contains(symbolicName)) {
				bundles.add(bundle);

				continue;
			}

			if (symbolicName.endsWith(".compat") &&
				compatSymbolicNames.contains(
					symbolicName.substring(0, symbolicName.length() - 7))) {

				try {
					bundle.uninstall();
				}
				catch (BundleException be) {
					_log.error("Unable to uninstall " + bundle, be);
				}
			}
		}

		_refreshBundles(bundleContext, bundles);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ModuleCompatExtender.class.getName());

	private BundleTracker<List<ServiceAdaptor<?, ?>>> _bundleTracker;
	private Set<String> _compatSymbolicNames;

}