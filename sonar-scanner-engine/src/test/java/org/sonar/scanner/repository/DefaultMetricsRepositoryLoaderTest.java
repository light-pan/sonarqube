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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.scanner.bootstrap.GlobalProperties;

import java.io.IOException;
import java.io.Reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public class DefaultMetricsRepositoryLoaderTest {
  private GlobalProperties globalProperties;
  private DefaultMetricsRepositoryLoader metricsRepositoryLoader;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    globalProperties = mock(GlobalProperties.class);
    metricsRepositoryLoader = new DefaultMetricsRepositoryLoader(globalProperties);
  }

  @Test
  public void test() {
    MetricsRepository metricsRepository = metricsRepositoryLoader.load();
    assertThat(metricsRepository.metrics()).hasSize(3);
    verifyNoMoreInteractions(globalProperties);
  }

  @Test
  public void testIOError() throws IOException {
    Reader reader = mock(Reader.class);
    when(reader.read(any(char[].class), anyInt(), anyInt())).thenThrow(new IOException());
    exception.expect(IllegalStateException.class);
    metricsRepositoryLoader.load();
  }

  @Test
  public void testCloseError() throws IOException {
    Reader reader = mock(Reader.class);
    when(reader.read(any(char[].class), anyInt(), anyInt())).thenReturn(-1);
    doThrow(new IOException()).when(reader).close();
    exception.expect(IllegalStateException.class);
    metricsRepositoryLoader.load();
  }
}
