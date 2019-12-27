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
package org.sonar.scanner.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class AnalyzeReport {
    private static JsonObject ruleJson = new JsonObject();

    /**
     * 读取报告文件和目录
     *
     * @param reportPath
     * @param rulePath
     * @return jsonObject
     */
    public static JsonObject readReport(String reportPath, String rulePath) {
        // 读取目录下的每个文件或者文件夹，并读取文件的内容写到目标文字中去
        File[] list = new File(reportPath).listFiles();
        JsonObject jsonRet = new JsonObject();
        JsonObject componentJson = new JsonObject();
        for (File file : list) {
            if (file.isFile()) {
                String fileName = file.getName();
                if (!fileName.contains("syntax-highlightings") && !fileName.contains("symbols")
                        && fileName.contains(".")) {
                    fileName = fileName.substring(0, fileName.lastIndexOf("."));
                    if (fileName.contains("-")) {
                        String jsonSite = fileName.substring(fileName.lastIndexOf("-") + 1);
                        JsonObject json = jsonRet.has(jsonSite) ? jsonRet.get(jsonSite).getAsJsonObject()
                                : new JsonObject();
                        JsonObject countJson = componentJson.has(jsonSite)
                                ? componentJson.get(jsonSite).getAsJsonObject()
                                : new JsonObject();
                        String jsonKey = fileName.substring(0, fileName.lastIndexOf("-"));
                        if (fileName.contains("component")) {
                            componentCount(file, jsonKey, jsonSite, json, countJson, jsonRet, componentJson);
                        } else if (fileName.contains("coverages")) {
                            coverageCount(file, jsonKey, jsonSite, json, countJson, jsonRet, componentJson);
                        } else if (fileName.contains("duplications")) {
                            duplicationCount(file, jsonKey, jsonSite, json, countJson, jsonRet, componentJson);
                        } else if (fileName.contains("issues")) {
                            iussueCount(file, rulePath, jsonKey, jsonSite, json, countJson, jsonRet, componentJson);
                        } else if (fileName.contains("measures")) {
                            JsonArray measuresArr = new JsonParser().parse(readToString(file.getPath()))
                                    .getAsJsonArray();
                            json.add(jsonKey, measuresArr);
                            jsonRet.add(jsonSite, json);
                            componentJson.add(jsonSite, countJson);
                        } else if (fileName.contains("source")) {
                            json.addProperty(jsonKey, readToString(file.getPath()));
                            jsonRet.add(jsonSite, json);
                            componentJson.add(jsonSite, countJson);
                        }
                    }
                }
            }
        }
        return coutData(componentJson, jsonRet);
    }

    private static void componentCount(File file, String jsonKey, String jsonSite, JsonObject json, JsonObject countJson, JsonObject jsonRet, JsonObject componentJson) {
        JsonObject component = new JsonParser().parse(readToString(file.getPath()))
                .getAsJsonObject();
        json.add(jsonKey, component);
        countJson.add(jsonKey, component);
        jsonRet.add(jsonSite, json);
        componentJson.add(jsonSite, countJson);
    }

    private static void iussueCount(File file, String rulePath, String jsonKey, String jsonSite, JsonObject json,
                                    JsonObject countJson, JsonObject jsonRet, JsonObject componentJson) {
        JsonArray issuesArr = new JsonParser().parse(readToString(file.getPath())).getAsJsonArray();
        JsonArray ruleArr = new JsonArray();
        int bug = 0, smell = 0, vulner = 0;
        for (int i = 0; i < issuesArr.size(); i++) {
            JsonObject issuesJosn = issuesArr.get(i).getAsJsonObject();
            if (issuesJosn.has("ruleRepository")) {
                String ruleLang = issuesJosn.get("ruleRepository").getAsString();
                if (ruleJson.has(ruleLang)) {
                    ruleArr = ruleJson.get(ruleLang).getAsJsonArray();
                } else {
                    rulePath = rulePath + file.separator + issuesJosn.get("ruleRepository").getAsString() + ".json";
                    File readFile = new File(rulePath);
                    if (readFile.exists()) {
                        ruleArr = new JsonParser().parse(readToString(new File(rulePath).getPath())).getAsJsonObject()
                                .get("rules").getAsJsonArray();
                        ruleJson.add(ruleLang, ruleArr);
                    }
                }
            }
            for (int j = 0; j < ruleArr.size(); j++) {
                JsonObject ruleJson = ruleArr.get(j).getAsJsonObject();
                if (ruleJson.get("key").getAsString().contains(issuesJosn.get("ruleKey").getAsString())) {
                    issuesJosn.addProperty("type", ruleJson.get("type").getAsString());
                    if ("CODE_SMELL".equals(ruleJson.get("type").getAsString())) {
                        smell++;
                    }
                    if ("BUG".equals(ruleJson.get("type").getAsString())) {
                        bug++;
                    }
                    if ("VULNERABILITY".equals(ruleJson.get("type").getAsString())) {
                        vulner++;
                    }
                }
            }
        }
        json.add(jsonKey, issuesArr);
        json.addProperty("issueNum", issuesArr.size());
        json.addProperty("issueBug", bug);
        json.addProperty("issueSemll", smell);
        json.addProperty("issueVulner", vulner);
        countJson.addProperty("issueNum", issuesArr.size());
        countJson.addProperty("issueBug", bug);
        countJson.addProperty("issueSemll", smell);
        countJson.addProperty("issueVulner", vulner);
        jsonRet.add(jsonSite, json);
        componentJson.add(jsonSite, countJson);
    }

    private static void duplicationCount(File file, String jsonKey, String jsonSite, JsonObject json,
                                         JsonObject countJson, JsonObject jsonRet, JsonObject componentJson) {
        JsonArray duplicationsArr = new JsonParser().parse(readToString(file.getPath()))
                .getAsJsonArray();
        json.add(jsonKey, duplicationsArr);
        int duplications = 0;
        for (int i = 0; i < duplicationsArr.size(); i++) {
            JsonObject duplicationsJson = duplicationsArr.get(i).getAsJsonObject();
            if (duplicationsJson.has("originPosition")) {
                JsonObject originPosition = duplicationsJson.get("originPosition")
                        .getAsJsonObject();
                if (originPosition.has("startLine") && originPosition.has("endLine")) {
                    duplications += originPosition.get("endLine").getAsInt()
                            - originPosition.get("startLine").getAsInt() + 1;
                }
            }
            if (duplicationsJson.has("duplicate")) {
                JsonArray duplicateArr = duplicationsJson.get("duplicate").getAsJsonArray();
                for (int j = 0; j < duplicateArr.size(); j++) {
                    JsonObject duplicateJson = duplicateArr.get(j).getAsJsonObject();
                    if (!duplicateJson.has("otherFileRef") && duplicateJson.has("range")) {
                        JsonObject rangeJson = duplicateJson.get("range").getAsJsonObject();
                        if (rangeJson.has("startLine") && rangeJson.has("endLine")) {
                            duplications += rangeJson.get("endLine").getAsInt()
                                    - rangeJson.get("startLine").getAsInt() + 1;
                        }
                    }
                }
            }
            json.addProperty("duplicate", duplications);
            countJson.addProperty("duplicate", duplications);
        }
        jsonRet.add(jsonSite, json);
        componentJson.add(jsonSite, countJson);
    }

    private static void coverageCount(File file, String jsonKey, String jsonSite, JsonObject json,
                                      JsonObject countJson, JsonObject jsonRet, JsonObject componentJson) {
        JsonArray coveragesArr = new JsonParser().parse(readToString(file.getPath()))
                .getAsJsonArray();
        json.add(jsonKey, coveragesArr);
        json.addProperty("coverageLines", coveragesArr.size());
        int coverageLine = 0;
        for (int i = 0; i < coveragesArr.size(); i++) {
            if (coveragesArr.get(i).getAsJsonObject().has("hits")
                    && coveragesArr.get(i).getAsJsonObject().get("hits").getAsBoolean()) {
                coverageLine++;
            }
        }
        json.addProperty("coverageLine", coverageLine);
        countJson.addProperty("coverageLines", coveragesArr.size());
        countJson.addProperty("coverageLine", coverageLine);
        jsonRet.add(jsonSite, json);
        componentJson.add(jsonSite, countJson);
    }

    /**
     * 统计jsonRet中子文件或子文件夹的问题和
     *
     * @param eJsonObject
     * @param jsonRet
     * @return
     */
    public static JsonObject coutData(JsonObject eJsonObject, JsonObject jsonRet) {
        insertFatherSign(eJsonObject);
        JsonObject countJson = eJsonObject.deepCopy();
        boolean end = true;
        while (end) {
            Iterator<String> coutKey = countJson.keySet().iterator();
            end = false;
            while (coutKey.hasNext()) {
                String key = coutKey.next();
                JsonObject forJson = countJson.get(key).getAsJsonObject();
                JsonObject componentJson = forJson.get("component").getAsJsonObject();
                if (!(componentJson.has("childRef") && componentJson.get("childRef").getAsJsonArray().size() != 0)) {
                    if (forJson.has("fatherRef")) {
                        insertPropertyInFatherRef(countJson, forJson, key);
                        end = true;
                    } else {
                        JsonObject comJson = jsonRet.get(key).getAsJsonObject();
                        comJson.addProperty("coverageLine", forJson.get("coverageLine").getAsInt());
                        comJson.addProperty("coverageLines", forJson.get("coverageLines").getAsInt());
                        comJson.addProperty("duplicate", forJson.get("duplicate").getAsInt());
                        comJson.addProperty("issueNum", forJson.get("issueNum").getAsInt());
                        comJson.addProperty("issueBug", forJson.get("issueBug").getAsInt());
                        comJson.addProperty("issueSemll", forJson.get("issueSemll").getAsInt());
                        comJson.addProperty("issueVulner", forJson.get("issueVulner").getAsInt());
                        comJson.addProperty("lines", forJson.get("lines").getAsInt());
                    }
                }
            }
        }
        return jsonRet;
    }

    private static void insertFatherSign(JsonObject eJsonObject) {
        Iterator<String> forkey = eJsonObject.keySet().iterator();
        while (forkey.hasNext()) {
            String key = forkey.next();
            JsonObject keyJson = eJsonObject.get(key).getAsJsonObject();
            JsonObject componentJson = keyJson.get("component").getAsJsonObject();
            Iterator<String> componentKey = componentJson.keySet().iterator();
            while (componentKey.hasNext()) {
                String contentKey = componentKey.next();
                if ("childRef".equals(contentKey)) {
                    JsonArray childRefArr = componentJson.get(contentKey).getAsJsonArray();
                    for (int i = 0; i < childRefArr.size(); i++) {
                        if (eJsonObject.has(childRefArr.get(i).getAsString())) {
                            JsonObject childJson = eJsonObject.get(childRefArr.get(i).getAsString()).getAsJsonObject();
                            childJson.addProperty("fatherRef", key);
                            eJsonObject.add(childRefArr.get(i).getAsString(), childJson);
                        }
                    }
                }
                if ("lines".equals(contentKey)) {
                    JsonObject childJson = eJsonObject.get(key).getAsJsonObject();
                    childJson.addProperty("lines", componentJson.get(contentKey).getAsString());
                    eJsonObject.add(key, childJson);
                }
            }
            if (!keyJson.has("coverageLine")) {
                keyJson.addProperty("coverageLine", 0);
            }
            if (!keyJson.has("coverageLines")) {
                keyJson.addProperty("coverageLines", 0);
            }
            if (!keyJson.has("duplicate")) {
                keyJson.addProperty("duplicate", 0);
            }
            if (!keyJson.has("issueNum")) {
                keyJson.addProperty("issueNum", 0);
            }
            if (!keyJson.has("issueBug")) {
                keyJson.addProperty("issueBug", 0);
            }
            if (!keyJson.has("issueSemll")) {
                keyJson.addProperty("issueSemll", 0);
            }
            if (!keyJson.has("issueVulner")) {
                keyJson.addProperty("issueVulner", 0);
            }
            if (!keyJson.has("lines")) {
                keyJson.addProperty("lines", 0);
            }
        }
    }

    private static void insertPropertyInFatherRef(JsonObject countJson, JsonObject forJson, String key) {
        JsonObject fatherJson = countJson.get(forJson.get("fatherRef").getAsString()).getAsJsonObject();
        fatherJson.addProperty("coverageLine",
                fatherJson.has("coverageLine")
                        ? fatherJson.get("coverageLine").getAsInt()
                        + forJson.get("coverageLine").getAsInt()
                        : forJson.get("coverageLine").getAsInt());
        fatherJson.addProperty("coverageLines",
                fatherJson.has("coverageLines")
                        ? fatherJson.get("coverageLines").getAsInt()
                        + forJson.get("coverageLines").getAsInt()
                        : forJson.get("coverageLines").getAsInt());
        fatherJson.addProperty("duplicate",
                fatherJson.has("duplicate")
                        ? fatherJson.get("duplicate").getAsInt() + forJson.get("duplicate").getAsInt()
                        : forJson.get("duplicate").getAsInt());
        fatherJson.addProperty("issueNum",
                fatherJson.has("issueNum")
                        ? fatherJson.get("issueNum").getAsInt() + forJson.get("issueNum").getAsInt()
                        : forJson.get("issueNum").getAsInt());
        fatherJson.addProperty("issueBug",
                fatherJson.has("issueBug")
                        ? fatherJson.get("issueBug").getAsInt() + forJson.get("issueBug").getAsInt()
                        : forJson.get("issueBug").getAsInt());
        fatherJson.addProperty("issueSemll",
                fatherJson.has("issueSemll")
                        ? fatherJson.get("issueSemll").getAsInt() + forJson.get("issueSemll").getAsInt()
                        : forJson.get("issueSemll").getAsInt());
        fatherJson.addProperty("issueVulner",
                fatherJson.has("issueVulner")
                        ? fatherJson.get("issueVulner").getAsInt()
                        + forJson.get("issueVulner").getAsInt()
                        : forJson.get("issueVulner").getAsInt());
        fatherJson.addProperty("lines",
                fatherJson.has("lines")
                        ? fatherJson.get("lines").getAsInt() + forJson.get("lines").getAsInt()
                        : forJson.get("lines").getAsInt());
        forJson.remove("fatherRef");
        JsonArray fatherComChildArr = fatherJson.get("component").getAsJsonObject().get("childRef")
                .getAsJsonArray();
        for (int i = 0; i < fatherComChildArr.size(); i++) {
            if (key.equals(fatherComChildArr.get(i).getAsString())) {
                fatherComChildArr.remove(i);
            }
        }
    }

    public static String readToString(String fileName) {
        String encoding = "UTF-8";
        File file = new File(fileName);
        long fileLength = file.length();
        byte[] fileContent = new byte[(int) fileLength];
        try {
            FileInputStream in = new FileInputStream(file);
            in.read(fileContent);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            return new String(fileContent, encoding);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean createJsonFile(String jsonString, String filePath, String fileName) {
        // 标记文件生成是否成功
        boolean flag = true;

        // 拼接文件完整路径
        String fullPath = filePath + File.separator + fileName + ".json";

        // 生成json格式文件
        try {
            // 保证创建一个新文件
            File file = new File(fullPath);
            if (!file.getParentFile().exists()) { // 如果父目录不存在，创建父目录
                file.getParentFile().mkdirs();
            }
            if (file.exists()) { // 如果已存在,删除旧文件
                file.delete();
            }
            file.createNewFile();

            if (jsonString.contains("'")) {
                // 将单引号转义一下，因为JSON串中的字符串类型可以单引号引起来的
                jsonString = jsonString.replaceAll("'", "\\'");
            }
            if (jsonString.contains("\"")) {
                // 将双引号转义一下，因为JSON串中的字符串类型可以单引号引起来的
                jsonString = jsonString.replaceAll("\"", "\\\"");
            }

            if (jsonString.contains("\r\n")) {
                // 将回车换行转换一下，因为JSON串中字符串不能出现显式的回车换行
                jsonString = jsonString.replaceAll("\r\n", "\\u000d\\u000a");
            }
            if (jsonString.contains("\n")) {
                // 将换行转换一下，因为JSON串中字符串不能出现显式的换行
                jsonString = jsonString.replaceAll("\n", "\\u000a");
            }

            // 格式化json字符串
            jsonString = formatJson(jsonString);

            // 将格式化后的字符串写入文件
            Writer write = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            write.write(jsonString);
            write.flush();
            write.close();
        } catch (Exception e) {
            flag = false;
            e.printStackTrace();
        }

        // 返回是否成功的标记
        return flag;
    }

    /**
     * 返回格式化JSON字符串。
     *
     * @param json 未格式化的JSON字符串。
     * @return 格式化的JSON字符串。
     */
    public static String formatJson(String json) {
        StringBuilder result = new StringBuilder();

        int length = json.length();
        int number = 0;
        char key = 0;

        // 遍历输入字符串。
        for (int i = 0; i < length; i++) {
            // 1、获取当前字符。
            key = json.charAt(i);

            // 2、如果当前字符是前方括号、前花括号做如下处理：
            if ((key == '[') || (key == '{')) {
                // （1）如果前面还有字符，并且字符为“：”，打印：换行和缩进字符字符串。
                if ((i - 1 > 0) && (json.charAt(i - 1) == ':')) {
                    result.append('\n');
                    result.append(indent(number));
                }

                // （2）打印：当前字符。
                result.append(key);

                // （3）前方括号、前花括号，的后面必须换行。打印：换行。
                result.append('\n');

                // （4）每出现一次前方括号、前花括号；缩进次数增加一次。打印：新行缩进。
                number++;
                result.append(indent(number));

                // （5）进行下一次循环。
                continue;
            }

            // 3、如果当前字符是后方括号、后花括号做如下处理：
            if ((key == ']') || (key == '}')) {
                // （1）后方括号、后花括号，的前面必须换行。打印：换行。
                result.append('\n');

                // （2）每出现一次后方括号、后花括号；缩进次数减少一次。打印：缩进。
                number--;
                result.append(indent(number));

                // （3）打印：当前字符。
                result.append(key);

                // （4）如果当前字符后面还有字符，并且字符不为“，”，打印：换行。
                if (((i + 1) < length) && (json.charAt(i + 1) != ',')) {
                    result.append('\n');
                }

                // （5）继续下一次循环。
                continue;
            }

            // 4、如果当前字符是逗号。逗号后面换行，并缩进，不改变缩进次数。
            /*
             * if ((key == ',')) { result.append(key); result.append('\n');
             * result.append(indent(number)); continue; }
             */

            // 5、打印：当前字符。
            result.append(key);
        }

        return result.toString();
    }

    /**
     * 返回指定次数的缩进字符串。每一次缩进三个空格，即SPACE。
     *
     * @param number 缩进次数。
     * @return 指定缩进次数的字符串。
     */
    private static String indent(int number) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < number; i++) {
            String SPACE = "   ";
            result.append(SPACE);
        }
        return result.toString();
    }
}
