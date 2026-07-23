/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wavemaker.runtime.prefab.util;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.wavemaker.runtime.prefab.config.PrefabServletConfig;
import com.wavemaker.runtime.prefab.config.PrefabsConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Dilip Kumar
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PrefabServletConfig.class)
@Disabled
public class UtilsTest {
    private static final String SAMPLE_PREFAB_DIR = "/testPrefab";
    private static final String JAR_FILE = SAMPLE_PREFAB_DIR + File.separator + PrefabConstants.PREFAB_DEFAULT_LIB_DIR
        + File.separator + "sample.jar";
    @Autowired
    private PrefabUtils prefabUtils;

    @Autowired
    private PrefabsConfig prefabsConfig;

    @Test
    public void testIsReadableJarFile() throws Exception {
        Assertions.assertFalse(Utils.isReadableJarFile(new File("not/valid/path")));
        Assertions.assertFalse(Utils.isReadableJarFile(new File("not_valid_test.jar")));
        Assertions.assertFalse(Utils.isReadableJarFile(null));
        Assertions.assertFalse(Utils.isReadableJarFile(getClassPathResourceAsFile(SAMPLE_PREFAB_DIR)));
        Assertions.assertTrue(Utils.isReadableJarFile(getClassPathResourceAsFile(JAR_FILE)));
    }

    @Test
    public void testReadJarFilesForPrefab() throws Exception {
        Assertions.assertEquals(1, prefabUtils.readJarFilesForPrefab(getClassPathResourceAsFile("/testPrefab")).length);
        Assertions.assertEquals(PrefabConstants.ZERO_FILES.length, prefabUtils.readJarFilesForPrefab(new File("not/valid/dir"))
            .length);
        Assertions.assertEquals(PrefabConstants.ZERO_FILES.length, prefabUtils.readJarFilesForPrefab(null).length);
    }

    public static String getClassPathResource(String path) {
        return UtilsTest.class.getClass().getResource(path).getPath();
    }

    public static File getClassPathResourceAsFile(String path) {
        return new File(getClassPathResource(path));
    }

    @Test
    public void testGetPrefabLibDirectory() throws Exception {
        Assertions.assertEquals(new File(SAMPLE_PREFAB_DIR, prefabsConfig.getPrefabLibDir()),
            prefabUtils.getPrefabLibDirectory(new File(SAMPLE_PREFAB_DIR))
        );
    }

    @Test
    public void testGetPrefabConfigDirectory() throws Exception {
        Assertions.assertEquals(new File(SAMPLE_PREFAB_DIR, prefabsConfig.getPrefabConfigDir()),
            prefabUtils.getPrefabConfigDirectory(new File(SAMPLE_PREFAB_DIR))
        );
    }

    @Test
    public void testIsReadableDirectory() throws Exception {
        Assertions.assertFalse(Utils.isReadableDirectory(new File("not/valid/path")));
        Assertions.assertFalse(Utils.isReadableDirectory(new File("not_valid_test.jar")));
        Assertions.assertFalse(Utils.isReadableDirectory(null));
        Assertions.assertTrue(Utils.isReadableDirectory(getClassPathResourceAsFile(SAMPLE_PREFAB_DIR)));
        Assertions.assertFalse(Utils.isReadableDirectory(getClassPathResourceAsFile(JAR_FILE)));
    }

    @Test
    public void testListPrefabDirectories() throws Exception {
        Assertions.assertEquals(0, prefabUtils.listPrefabDirectories(new File("/not/valid/dir")).length);
        Assertions.assertEquals(1, prefabUtils.listPrefabDirectories(getClassPathResourceAsFile(File.separator)).length);
        Assertions.assertEquals(0, prefabUtils.listPrefabDirectories(getClassPathResourceAsFile(JAR_FILE)).length);
    }

    @Test
    public void testConvertToURLS() throws Exception {
        File[] files = new File[]{getClassPathResourceAsFile(JAR_FILE)};
        URL[] urls = Utils.convertToURLS(prefabUtils.getPrefabConfigDirectory(getClassPathResourceAsFile(SAMPLE_PREFAB_DIR)), files);
        Assertions.assertEquals((files.length + 1), urls.length);
        Assertions.assertTrue(Arrays.asList(urls).contains(files[0].toURI().toURL()));
    }

}
