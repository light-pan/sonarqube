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
import org.sonar.api.rule.RuleKey;
import org.sonar.scanner.bootstrap.GlobalProperties;
import org.sonar.scanner.platform.LocalServer;
import org.sonarqube.ws.CustomRules;
import org.sonarqube.ws.CustomRules.Rule;
import org.sonarqube.ws.CustomRules.SearchResponse;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.sonar.api.utils.DateUtils.dateToLong;
import static org.sonar.api.utils.DateUtils.parseDateTime;

public class DefaultActiveRulesLoader implements ActiveRulesLoader {

    private final GlobalProperties globalProperties;

    public DefaultActiveRulesLoader(GlobalProperties globalProperties) {
        this.globalProperties = globalProperties;
    }

    @Override
    /**
     * 自定义读取文件规则
     */
    public List<LoadedActiveRule> load(String language) {
        List<LoadedActiveRule> ruleList = new LinkedList<>();
        try {
            String fileName = globalProperties.property("sonar.jsonDir") + File.separator + language + ".json";
            String json = LocalServer.readFileContent(fileName);
            SearchResponse.Builder builder = SearchResponse.getDefaultInstance().toBuilder();
            JsonFormat.parser().merge(json, builder);
            SearchResponse response = builder.build();
            List<LoadedActiveRule> pageRules = readPage(response);
            ruleList.addAll(pageRules);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ruleList;
    }

    private static List<LoadedActiveRule> readPage(SearchResponse response) {
        List<LoadedActiveRule> loadedRules = new LinkedList<>();
        List<Rule> rulesList = response.getRulesList();
        for (Rule r : rulesList) {
            LoadedActiveRule loadedRule = new LoadedActiveRule();

            loadedRule.setRuleKey(RuleKey.parse(r.getKey()));
            loadedRule.setName(r.getName());
            loadedRule.setSeverity(r.getSeverity());
            loadedRule.setCreatedAt(dateToLong(parseDateTime(r.getCreatedAt())));
            loadedRule.setLanguage(r.getLang());
            loadedRule.setInternalKey(r.getInternalKey());
            if (r.hasTemplateKey()) {
                RuleKey templateRuleKey = RuleKey.parse(r.getTemplateKey());
                loadedRule.setTemplateRuleKey(templateRuleKey.rule());
            }

            Map<String, String> params = new HashMap<>();

            for (CustomRules.Rule.Param param : r.getParams().getParamsList()) {
                params.put(param.getKey(), param.getDefaultValue());
            }

            loadedRule.setParams(params);
            loadedRules.add(loadedRule);
        }

        return loadedRules;
    }
}
