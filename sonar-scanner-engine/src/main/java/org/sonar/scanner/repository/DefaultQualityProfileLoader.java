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
package org.sonar.scanner.repository;

import com.google.protobuf.util.JsonFormat;
import org.sonar.api.config.Configuration;
import org.sonar.scanner.platform.LocalServer;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class DefaultQualityProfileLoader implements QualityProfileLoader {

  private final Configuration settings;

  public DefaultQualityProfileLoader(Configuration settings) {
    this.settings = settings;
  }

  @Override
  public List<QualityProfile> loadDefault(@Nullable String profileName) {
    return loadFromLocal(profileName);
  }

  @Override
  public List<QualityProfile> load(String projectKey, @Nullable String profileName) {
    return loadFromLocal(profileName);
  }

  private List<QualityProfile> loadFromLocal(@Nullable String profileName) {
    SearchWsResponse profiles;
    try {
      if(!settings.get("sonar.jsonDir").isPresent()) {
        throw new IllegalStateException("Failed to load quality profiles");
      }
      String json = LocalServer.readFileContent(settings.get("sonar.jsonDir").get() + File.separator + "profiles.json");
      SearchWsResponse.Builder builder = SearchWsResponse.getDefaultInstance().toBuilder();
      JsonFormat.parser().merge(json, builder);
      profiles = builder.build();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load quality profiles", e);
    }

    List<QualityProfile> profilesList = profiles.getProfilesList();
    Map<String, QualityProfile> result =  profilesList.stream()
            .collect(toMap(QualityProfile::getLanguage, identity(), throwingMerger(), LinkedHashMap::new));
    return new ArrayList<>(result.values());
  }

  private static <T> BinaryOperator<T> throwingMerger() {
    return (u, v) -> {
      throw new IllegalStateException(String.format("Duplicate key %s", u));
    };
  }
}
