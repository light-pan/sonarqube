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
import org.apache.commons.io.IOUtils;
import org.sonar.scanner.bootstrap.ScannerWsClient;
import org.sonarqube.ws.Rules.ListResponse;
import org.sonarqube.ws.Rules.ListResponse.Rule;

import java.io.*;
import java.util.List;

public class DefaultRulesLoader implements RulesLoader {
  private static final String RULES_SEARCH_URL = "/api/rules/list.protobuf";

  private final ScannerWsClient wsClient;

  public DefaultRulesLoader(ScannerWsClient wsClient) {
    this.wsClient = wsClient;
  }

  @Override
  public List<Rule> load() {
//    GetRequest getRequest = new GetRequest(RULES_SEARCH_URL);
//    ListResponse list = loadFromStream(wsClient.call(getRequest).contentStream());

    String json = readFileContent("/Users/lightpan/code/java/json/listRules.json");
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

  private String readFileContent(String fileName) {
    File file = new File(fileName);
    BufferedReader reader = null;
    StringBuffer sbf = new StringBuffer();
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
  private static ListResponse loadFromStream(InputStream is) {
    try {
      return ListResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to get rules", e);
    } finally {
      IOUtils.closeQuietly(is);
    }
  }

}
