/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.bootstrap;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.core.platform.PluginInfo;
import org.sonar.home.cache.FileCache;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Downloads the plugins installed on server and stores them in a local user cache
 * (see {@link FileCacheProvider}).
 */
public class ScannerPluginInstaller implements PluginInstaller {

  private static final Logger LOG = Loggers.get(ScannerPluginInstaller.class);

  private final FileCache fileCache;
  private final ScannerPluginPredicate pluginPredicate;
  private final GlobalProperties globalProps;

  public ScannerPluginInstaller(FileCache fileCache, ScannerPluginPredicate pluginPredicate, GlobalProperties globalProps) {
    this.fileCache = fileCache;
    this.pluginPredicate = pluginPredicate;
    this.globalProps = globalProps;
  }

  @Override
  public Map<String, ScannerPlugin> installRemotes() {
    return loadPlugins(listInstalledPluginsFromLocal());
  }

  private Map<String, ScannerPlugin> loadPlugins(InstalledPlugin[] remotePlugins) {
    Map<String, ScannerPlugin> infosByKey = new HashMap<>(remotePlugins.length);

    Profiler profiler = Profiler.create(LOG).startDebug("Load plugins");
    for (InstalledPlugin installedPlugin : remotePlugins) {
      if (pluginPredicate.apply(installedPlugin.key)) {
        File jarFile = download(installedPlugin);
        PluginInfo info = PluginInfo.create(jarFile);
        infosByKey.put(info.getKey(), new ScannerPlugin(installedPlugin.key, installedPlugin.updatedAt, info));
      }
    }

    profiler.stopDebug();
    return infosByKey;
  }

  /**
   * Returns empty on purpose. This method is used only by medium tests.
   */
  @Override
  public List<Object[]> installLocals() {
    return Collections.emptyList();
  }

  @VisibleForTesting
  File download(final InstalledPlugin remote) {
    try {
      return fileCache.getFromLocal(remote.filename, remote.hash, globalProps.property("sonar.jarDir"));
    } catch (Exception e) {
      throw new IllegalStateException("Fail to download plugin: " + remote.key, e);
    }
  }

  InstalledPlugin[] listInstalledPluginsFromLocal() {
    Profiler profiler = Profiler.create(LOG).startInfo("Load plugins index");
    InstalledPlugins installedPlugins;
    String filePath = globalProps.property("sonar.jsonDir") + File.separator + "plugin.json";
    try (Reader reader = new FileReader(filePath)) {
      installedPlugins = new Gson().fromJson(reader, InstalledPlugins.class);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    profiler.stopInfo();
    return installedPlugins.plugins;
  }

  private static class InstalledPlugins {
    InstalledPlugin[] plugins;
  }

  static class InstalledPlugin {
    String key;
    String hash;
    String filename;
    long updatedAt;
  }
}
