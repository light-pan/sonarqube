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
package org.sonar.scanner.repository.settings;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringEscapeUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarqube.ws.Settings.FieldValues.Value;
import org.sonarqube.ws.Settings.Setting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultSettingsLoader implements SettingsLoader {

    private static final Logger LOG = Loggers.get(DefaultSettingsLoader.class);

    @Override
    public Map<String, String> load(@Nullable String componentKey) {
        Profiler profiler = Profiler.create(LOG);
        if (componentKey != null) {
            throw new IllegalStateException("Load setting with component key is not supported.");
        } else {
            profiler.startInfo("Load global settings");
        }
        profiler.stopInfo();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("sonar.core.id", "ci_sonarqube");
        return result;
    }

    @VisibleForTesting
    static Map<String, String> toMap(List<Setting> settingsList) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Setting s : settingsList) {
            if (!s.getInherited()) {
                switch (s.getValueOneOfCase()) {
                    case VALUE:
                        result.put(s.getKey(), s.getValue());
                        break;
                    case VALUES:
                        result.put(s.getKey(), s.getValues().getValuesList().stream().map(StringEscapeUtils::escapeCsv).collect(Collectors.joining(",")));
                        break;
                    case FIELDVALUES:
                        convertPropertySetToProps(result, s);
                        break;
                    default:
                        throw new IllegalStateException("Unknow property value for " + s.getKey());
                }
            }
        }
        return result;
    }

    private static void convertPropertySetToProps(Map<String, String> result, Setting s) {
        List<String> ids = new ArrayList<>();
        int id = 1;
        for (Value v : s.getFieldValues().getFieldValuesList()) {
            for (Map.Entry<String, String> entry : v.getValue().entrySet()) {
                result.put(s.getKey() + "." + id + "." + entry.getKey(), entry.getValue());
            }
            ids.add(String.valueOf(id));
            id++;
        }
        result.put(s.getKey(), ids.stream().collect(Collectors.joining(",")));
    }
}
