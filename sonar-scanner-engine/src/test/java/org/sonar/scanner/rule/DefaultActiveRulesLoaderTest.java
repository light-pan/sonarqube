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

import com.google.common.collect.ImmutableSortedMap;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.scanner.bootstrap.GlobalProperties;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.Rules.*;
import org.sonarqube.ws.Rules.SearchResponse.Builder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DefaultActiveRulesLoaderTest {

  private static final int PAGE_SIZE_1 = 150;
  private static final int PAGE_SIZE_2 = 76;
  private static final RuleKey EXAMPLE_KEY = RuleKey.of("squid", "S108");
  private static final String FORMAT_KEY = "format";
  private static final String FORMAT_VALUE = "^[a-z][a-zA-Z0-9]*$";
  private static final String SEVERITY_VALUE = Severity.MINOR;

  private DefaultActiveRulesLoader loader;
  private GlobalProperties globalProperties;

  @Before
  public void setUp() {
    globalProperties = mock(GlobalProperties.class);
    loader = new DefaultActiveRulesLoader(globalProperties);
  }

  @Test
  public void feed_real_response_encode_qp() {
    int total = PAGE_SIZE_1 + PAGE_SIZE_2;


    Collection<LoadedActiveRule> activeRules = loader.load("c+-test_c+-values-17445");
    assertThat(activeRules).hasSize(total);
    assertThat(activeRules)
      .filteredOn(r -> r.getRuleKey().equals(EXAMPLE_KEY))
      .extracting(LoadedActiveRule::getParams)
      .extracting(p -> p.get(FORMAT_KEY))
      .containsExactly(FORMAT_VALUE);
    assertThat(activeRules)
      .filteredOn(r -> r.getRuleKey().equals(EXAMPLE_KEY))
      .extracting(LoadedActiveRule::getSeverity)
      .containsExactly(SEVERITY_VALUE);


    verifyNoMoreInteractions(globalProperties);
  }

  /**
   * Generates an imaginary protobuf result.
   *
   * @param numberOfRules the number of rules, that the response should contain
   * @param total the number of results on all pages
   * @return the binary stream
   */
  private InputStream responseOfSize(int numberOfRules, int total) {
    Builder rules = SearchResponse.newBuilder();
    Actives.Builder actives = Actives.newBuilder();

    IntStream.rangeClosed(1, numberOfRules)
      .mapToObj(i -> RuleKey.of("squid", "S" + i))
      .forEach(key -> {

        Rule.Builder ruleBuilder = Rule.newBuilder();
        ruleBuilder.setKey(key.toString());
        rules.addRules(ruleBuilder);

        Active.Builder activeBuilder = Active.newBuilder();
        activeBuilder.setCreatedAt("2014-05-27T15:50:45+0100");
        if (EXAMPLE_KEY.equals(key)) {
          activeBuilder.addParams(Rules.Active.Param.newBuilder().setKey(FORMAT_KEY).setValue(FORMAT_VALUE));
          activeBuilder.setSeverity(SEVERITY_VALUE);
        }
        ActiveList activeList = Rules.ActiveList.newBuilder().addActiveList(activeBuilder).build();
        actives.putAllActives(ImmutableSortedMap.of(key.toString(), activeList));
      });

    rules.setActives(actives);
    rules.setPs(numberOfRules);
    rules.setTotal(total);
    return new ByteArrayInputStream(rules.build().toByteArray());
  }
}
