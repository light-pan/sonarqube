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
package org.sonar.scanner.rule;

import com.google.protobuf.util.JsonFormat;
import org.sonar.scanner.bootstrap.GlobalProperties;
import org.sonar.scanner.platform.LocalServer;
import org.sonarqube.ws.Rules.ListResponse;
import org.sonarqube.ws.Rules.ListResponse.Rule;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DefaultRulesLoader implements RulesLoader {

  private final GlobalProperties globalProperties;

  public DefaultRulesLoader(GlobalProperties globalProperties) {
    this.globalProperties = globalProperties;
  }

  @Override
  public List<Rule> load() {
    String json = LocalServer.readFileContent(globalProperties.property("sonar.jsonDir") + File.separator + "listRules.json");
    ListResponse list = loadFromLocal(json);
    return list.getRulesList();
  }

  private static ListResponse loadFromLocal(String json) {
    try {
      ListResponse.Builder builder = ListResponse.getDefaultInstance().toBuilder();
      JsonFormat.parser().merge(json, builder);
      return builder.build();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to get rules", e);
    }
  }
}
