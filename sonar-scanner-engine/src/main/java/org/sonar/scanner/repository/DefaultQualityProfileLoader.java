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
import org.apache.commons.io.IOUtils;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;
import org.sonar.scanner.bootstrap.ScannerWsClient;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.client.GetRequest;

import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.function.BinaryOperator;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.sonar.core.config.ScannerProperties.ORGANIZATION;
import static org.sonar.scanner.util.ScannerUtils.encodeForUrl;

public class DefaultQualityProfileLoader implements QualityProfileLoader {
  private static final String WS_URL = "/api/qualityprofiles/search.protobuf";

  private final Configuration settings;
  private final ScannerWsClient wsClient;

  public DefaultQualityProfileLoader(Configuration settings, ScannerWsClient wsClient) {
    this.settings = settings;
    this.wsClient = wsClient;
  }

  @Override
  public List<QualityProfile> loadDefault(@Nullable String profileName) {
//    StringBuilder url = new StringBuilder(WS_URL + "?defaults=true");
//    return loadAndOverrideIfNeeded(profileName, url);
    return loadFromLocal(profileName);
  }

  @Override
  public List<QualityProfile> load(String projectKey, @Nullable String profileName) {
//    StringBuilder url = new StringBuilder(WS_URL + "?projectKey=").append(encodeForUrl(projectKey));
//    return loadAndOverrideIfNeeded(profileName, url);
    return loadFromLocal(profileName);
  }

  private List<QualityProfile> loadFromLocal(@Nullable String profileName) {
    SearchWsResponse profiles;
    try {
      String json = readFileContent("/Users/lightpan/code/java/json/profiles.json");
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

  private String readFileContent(String fileName) {
    File file = new File(fileName);
    BufferedReader reader = null;
    StringBuilder sbf = new StringBuilder();
    try {
      reader = new BufferedReader(new FileReader(file));
      String tempStr;
      while ((tempStr = reader.readLine()) != null) {
        sbf.append(tempStr);
      }
      reader.close();
      return sbf.toString();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
    }
    return sbf.toString();
  }

  private List<QualityProfile> loadAndOverrideIfNeeded(@Nullable String profileName, StringBuilder url) {
    getOrganizationKey().ifPresent(k -> url.append("&organization=").append(encodeForUrl(k)));
    Map<String, QualityProfile> result = call(url.toString());

    if (profileName != null) {
      StringBuilder urlForName = new StringBuilder(WS_URL + "?profileName=");
      urlForName.append(encodeForUrl(profileName));
      getOrganizationKey().ifPresent(k -> urlForName.append("&organization=").append(encodeForUrl(k)));
      result.putAll(call(urlForName.toString()));
    }
    if (result.isEmpty()) {
      throw MessageException.of("No quality profiles have been found, you probably don't have any language plugin installed.");
    }

    return new ArrayList<>(result.values());
  }

  private Optional<String> getOrganizationKey() {
    return settings.get(ORGANIZATION);
  }

  private Map<String, QualityProfile> call(String url) {
    GetRequest getRequest = new GetRequest(url);
    InputStream is = wsClient.call(getRequest).contentStream();
    SearchWsResponse profiles;

    try {
      profiles = SearchWsResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load quality profiles", e);
    } finally {
      IOUtils.closeQuietly(is);
    }

    List<QualityProfile> profilesList = profiles.getProfilesList();
    return profilesList.stream()
      .collect(toMap(QualityProfile::getLanguage, identity(), throwingMerger(), LinkedHashMap::new));
  }

  private static <T> BinaryOperator<T> throwingMerger() {
    return (u, v) -> {
      throw new IllegalStateException(String.format("Duplicate key %s", u));
    };
  }

}
